package com.glass.headunit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

/** Finds whatever app is playing right now, so the card is not tied to particular players. */
final class Proxy {

    static final String VRADIO = "com.ilv.vradio";

    private static boolean accessLogged;

    private Proxy() { }

    /**
     * The app playing right now, else the app we already follow if it still has a session, else any app with a
     * titled session. Skips this app's own session and Android Auto's. Null when nothing qualifies or the
     * notification access that reading sessions needs is off.
     */
    static MediaController findActive(Context ctx, MediaController current) {
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> all = m.getActiveSessions(new ComponentName(ctx, MediaListener.class));
            MediaController stay = null, any = null;
            for (MediaController c : all) {
                String pkg = c.getPackageName();
                if (pkg.equals(ctx.getPackageName()) || pkg.equals("com.google.android.projection.gearhead")) continue;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return c;
                if (current != null && c.getSessionToken().equals(current.getSessionToken())) stay = c;
                if (any == null && c.getMetadata() != null) any = c;
            }
            return stay != null ? stay : any;
        } catch (SecurityException e) {
            if (!accessLogged) { accessLogged = true; Spike.log("notification access is OFF: the panel cannot see what is playing"); }
            return null;
        }
    }

    /** Packages that have a media session right now (empty when notification access is off). */
    static java.util.Set<String> activePackages(Context ctx) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController c : m.getActiveSessions(new ComponentName(ctx, MediaListener.class))) out.add(c.getPackageName());
        } catch (SecurityException ignored) { }
        return out;
    }

    /** The app's name for display, falling back to its package. */
    static String label(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) { return pkg; }
    }

    /** That app's media session, or null when it has none (not running) or notification access is off. */
    static MediaController find(Context ctx, String pkg) {
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController c : m.getActiveSessions(new ComponentName(ctx, MediaListener.class))) {
                if (pkg.equals(c.getPackageName())) return c;
            }
        } catch (SecurityException e) {
            if (!accessLogged) { accessLogged = true; Spike.log("notification access is OFF: the panel cannot see what is playing"); }
        }
        return null;
    }

    /** Installed media apps worth offering as a player: every app with a media browser service, plus VRadio. {package, label} */
    static java.util.List<String[]> players(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        java.util.LinkedHashMap<String, String> found = new java.util.LinkedHashMap<>();
        try {
            for (ResolveInfo r : pm.queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0)) {
                String pkg = r.serviceInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || pkg.equals("com.google.android.projection.gearhead")) continue;
                found.put(pkg, String.valueOf(pm.getApplicationLabel(r.serviceInfo.applicationInfo)));
            }
        } catch (Exception ignored) { }
        if (!found.containsKey(VRADIO)) {
            try { found.put(VRADIO, String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(VRADIO, 0)))); }
            catch (PackageManager.NameNotFoundException ignored) { }
        }
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : found.entrySet()) out.add(new String[]{e.getKey(), e.getValue()});
        return out;
    }

    /** Asks a player that is not running to start: a media Play button first, its own screen as a last resort. */
    static void wake(Context ctx, String pkg, boolean openScreen) {
        if (!openScreen) {
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                ctx.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                        .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)));
            }
            Spike.log("switch: sent a media Play button to " + pkg);
            return;
        }
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { Spike.log("switch: " + pkg + " has no launch screen"); return; }
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Spike.log("switch: opened " + pkg + " on the phone");
        } catch (Exception e) {
            Spike.log("switch: could not open " + pkg + " (" + e.getClass().getSimpleName() + ")");
        }
    }
}

package com.glass.headunit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects to another app's media browser service as a client and reads its library.
 * Risk 1 of the design: many apps only let Google's Android Auto connect and refuse everyone else.
 */
final class BrowseProbe {

    interface Callback {
        /** children is empty when refused; error is null on success. */
        void done(List<MediaBrowserCompat.MediaItem> children, String error);
    }

    private BrowseProbe() { }

    /** parentId null = the app's root. */
    static void load(Context ctx, String pkg, String parentId, Callback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        ComponentName svc = findService(ctx, pkg);
        if (svc == null) {
            Spike.log("probe " + pkg + ": no media browser service found");
            main.post(() -> cb.done(new ArrayList<>(), "no media browser service (not installed?)"));
            return;
        }
        final boolean[] finished = {false};
        final MediaBrowserCompat[] holder = new MediaBrowserCompat[1];

        Runnable fail = () -> { };
        final java.util.function.Consumer<String> finishError = msg -> main.post(() -> {
            if (finished[0]) return;
            finished[0] = true;
            Spike.log("probe " + pkg + ": " + msg);
            try { if (holder[0] != null) holder[0].disconnect(); } catch (Exception ignored) { }
            cb.done(new ArrayList<>(), msg);
        });

        main.post(() -> {
            holder[0] = new MediaBrowserCompat(ctx, svc, new MediaBrowserCompat.ConnectionCallback() {
                @Override public void onConnected() {
                    MediaBrowserCompat b = holder[0];
                    String id = parentId != null ? parentId : b.getRoot();
                    Spike.log("probe " + pkg + ": connected, root=" + b.getRoot() + ", loading " + id);
                    b.subscribe(id, new MediaBrowserCompat.SubscriptionCallback() {
                        @Override public void onChildrenLoaded(String p, List<MediaBrowserCompat.MediaItem> items) {
                            if (finished[0]) return;
                            finished[0] = true;
                            Spike.log("probe " + pkg + ": " + items.size() + " items under " + p);
                            b.disconnect();
                            cb.done(items, null);
                        }
                        @Override public void onError(String p) { finishError.accept("browse error for " + p); }
                    });
                }
                @Override public void onConnectionFailed() { finishError.accept("connection REFUSED"); }
                @Override public void onConnectionSuspended() { finishError.accept("connection suspended"); }
            }, null);
            holder[0].connect();
            main.postDelayed(() -> finishError.accept("timed out after 8 s (app may not have started)"), 8000);
        });
    }

    private static ComponentName findService(Context ctx, String pkg) {
        Intent i = new Intent("android.media.browse.MediaBrowserService").setPackage(pkg);
        List<ResolveInfo> r = ctx.getPackageManager().queryIntentServices(i, 0);
        if (r == null || r.isEmpty()) return null;
        return new ComponentName(r.get(0).serviceInfo.packageName, r.get(0).serviceInfo.name);
    }
}

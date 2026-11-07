package com.glass.headunit;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Follows the car without switching the GPS on: it listens to the passive provider, which only delivers fixes that
 * other apps (Google Maps while navigating) already asked for, and every 10 minutes reads the last known position
 * as a fallback. It tells the listener when the car has moved far enough, or on the first usable fix.
 */
final class LocationFollower {

    interface Listener { void onMoved(double lat, double lon); }

    private static final long FALLBACK_MS = 10 * 60_000L;

    private final Context ctx;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private boolean started;
    private final RefreshGate gate = new RefreshGate();

    private final LocationListener passive = new LocationListener() {
        @Override public void onLocationChanged(Location l) { consider(l.getLatitude(), l.getLongitude(), "passive"); }
    };

    private final Runnable fallback = new Runnable() {
        @Override public void run() {
            double[] at = Where.get(ctx);
            if (at != null) consider(at[0], at[1], "last known");
            handler.postDelayed(this, FALLBACK_MS);
        }
    };

    LocationFollower(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (started) return;
        started = true;
        try {
            lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000L, 0f, passive);
            Spike.log("location: listening to the passive provider (no GPS of our own)");
        } catch (SecurityException e) {
            Spike.log("location: no permission, passive listening is off");
        } catch (Exception e) {
            Spike.log("location: passive provider unavailable " + e.getMessage());
        }
        handler.post(fallback);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(fallback);
        try { if (lm != null) lm.removeUpdates(passive); } catch (Exception ignored) { }
    }

    private void consider(double lat, double lon, String via) {
        if (!MoveTrigger.isUsable(lat, lon)) { Spike.log("location: ignored an unusable fix from " + via); return; }
        Where.save(ctx, lat, lon);
        if (!gate.accept(lat, lon)) return;
        Spike.log(String.format(java.util.Locale.US, "location: %s fix %.4f, %.4f: refreshing", via, lat, lon));
        listener.onMoved(lat, lon);
    }
}

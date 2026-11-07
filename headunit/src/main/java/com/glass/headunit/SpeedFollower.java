package com.glass.headunit;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

/**
 * The car's speed once a second, for the driving score. By your choice this asks the GPS itself (like the reference
 * project), so it only runs while the panel is open and the driving score setting is on. If the GPS is switched off
 * it listens to the passive provider instead, which carries fixes other apps request.
 */
final class SpeedFollower {

    interface Listener { void onSpeed(float kmh); }

    private final Context ctx;
    private final Listener listener;
    private LocationManager lm;
    private boolean started;

    private final LocationListener fix = new LocationListener() {
        @Override public void onLocationChanged(Location l) { listener.onSpeed(l.hasSpeed() ? l.getSpeed() * 3.6f : 0f); }
    };

    SpeedFollower(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (started) return;
        started = true;
        try {
            lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, fix);
                Spike.log("score: listening to the GPS once a second");
            } else {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, fix);
                Spike.log("score: GPS is off, listening to passive fixes instead");
            }
        } catch (SecurityException e) {
            Spike.log("score: no location permission");
        } catch (Exception e) {
            Spike.log("score: could not start (" + e.getClass().getSimpleName() + ")");
        }
    }

    void stop() {
        started = false;
        try { if (lm != null) lm.removeUpdates(fix); } catch (Exception ignored) { }
    }
}

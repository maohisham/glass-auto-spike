package com.glass.headunit;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;

/**
 * The phone's approximate position (a media card cannot read the car's GPS). Uses the newest last-known fix;
 * the app screen saves it so the card still has one when Android Auto starts it in the background.
 */
final class Where {

    private Where() { }

    static void save(Context ctx, double lat, double lon) {
        ctx.getSharedPreferences("where", Context.MODE_PRIVATE).edit()
                .putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
    }

    /** {lat, lon}, or null when the phone has never had a fix. */
    static double[] get(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("where", Context.MODE_PRIVATE);
        Location best = null;
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            for (String p : lm.getAllProviders()) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && MoveTrigger.isUsable(l.getLatitude(), l.getLongitude()) && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException e) {
            Spike.log("location: permission not granted, using the saved fix");
        }
        if (best != null) {
            sp.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
            return new double[]{best.getLatitude(), best.getLongitude()};
        }
        if (sp.contains("lat") && MoveTrigger.isUsable(sp.getFloat("lat", 0f), sp.getFloat("lon", 0f))) return new double[]{sp.getFloat("lat", 0f), sp.getFloat("lon", 0f)};
        return null;
    }
}

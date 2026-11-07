package com.glass.headunit;

/** Decides whether a new position is worth refreshing prayer times and weather for. */
final class MoveTrigger {

    /** About how far the car must have gone since the last refresh. */
    static final double REFRESH_METRES = 5000;

    private MoveTrigger() { }

    /** False for fixes that cannot be real: NaN, or exactly 0,0 (a provider with no fix). */
    static boolean isUsable(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
        return !(lat == 0 && lon == 0);
    }

    /** True when the two points are at least REFRESH_METRES apart (great-circle). Compare against the last position that caused a refresh. */
    static boolean shouldRefresh(double lastLat, double lastLon, double lat, double lon) {
        return distanceMetres(lastLat, lastLon, lat, lon) >= REFRESH_METRES;
    }

    /** Great-circle distance in metres. */
    static double distanceMetres(double lastLat, double lastLon, double lat, double lon) {
        double r = 6371000;
        double p1 = Math.toRadians(lastLat), p2 = Math.toRadians(lat);
        double dp = p2 - p1, dl = Math.toRadians(lon - lastLon);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}

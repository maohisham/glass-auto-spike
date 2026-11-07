package com.glass.headunit;

/**
 * Remembers the position that last caused a refresh and says whether a new fix should cause another. Comparing
 * against that point, not against the previous fix, is what stops a car parked near the 5 km line from refreshing in a loop.
 */
final class RefreshGate {

    private boolean have;
    private double lat, lon;

    boolean accept(double newLat, double newLon) {
        if (!MoveTrigger.isUsable(newLat, newLon)) return false;
        if (have && !MoveTrigger.shouldRefresh(lat, lon, newLat, newLon)) return false;
        have = true;
        lat = newLat;
        lon = newLon;
        return true;
    }
}

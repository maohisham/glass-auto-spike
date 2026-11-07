package com.glass.headunit;

/**
 * One refresh at a time, but a position that arrives while one is running is remembered (newest wins) and run as soon
 * as it ends, so a late real fix is not lost behind a fetch for an old position.
 */
final class FetchQueue {

    /** A request this close to the running fetch's position would learn nothing new. */
    private static final double SAME_SPOT_METRES = 1000;

    private boolean busy;
    private double[] pending;
    private double lastLat, lastLon;

    /** True when the caller should start fetching now; false when the position was queued behind a running fetch. */
    synchronized boolean request(double lat, double lon) {
        if (!busy) { busy = true; lastLat = lat; lastLon = lon; return true; }
        if (MoveTrigger.distanceMetres(lastLat, lastLon, lat, lon) < SAME_SPOT_METRES) return false;
        pending = new double[]{lat, lon};
        return false;
    }

    /** Call when a fetch ends: the queued position to fetch next (the queue stays busy), or null when idle again. */
    synchronized double[] finished() {
        if (pending != null) { double[] p = pending; pending = null; lastLat = p[0]; lastLon = p[1]; return p; }
        busy = false;
        return null;
    }
}

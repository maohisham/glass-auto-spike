package com.glass.headunit;

/** Which position the periodic refresh should use: a usable last-known fix, else the manual zone's centre, else none. */
final class FixChoice {

    private FixChoice() { }

    static double[] choose(double[] lastKnown, Zones.Zone manual) {
        if (lastKnown != null && MoveTrigger.isUsable(lastKnown[0], lastKnown[1])) return lastKnown;
        if (manual != null) return new double[]{manual.lat, manual.lon};
        return null;
    }
}

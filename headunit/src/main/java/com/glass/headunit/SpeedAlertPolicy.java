package com.glass.headunit;

/**
 * Decides when to sound the speed alert. It sounds once when the speed has been above the limit for two readings in a
 * row, stays quiet while the driver keeps going over, and is ready to sound again only after the speed has dropped at
 * least a few km/h below the limit. Pure.
 */
final class SpeedAlertPolicy {

    /** How far under the limit the speed must fall before the alert can sound again (so hovering at the limit stays quiet). */
    static final float REARM_MARGIN_KMH = 3f;

    private boolean armed = true;
    private int readingsAbove;

    /** Feed each speed reading; true means sound the alert now. A limit of zero or less turns it off. */
    boolean onSpeed(float kmh, int limitKmh) {
        if (limitKmh <= 0) { readingsAbove = 0; return false; }
        if (kmh > limitKmh) {
            readingsAbove++;
            if (armed && readingsAbove >= 2) { armed = false; return true; }
            return false;
        }
        readingsAbove = 0;                       // a spike that went away is forgotten
        if (kmh <= limitKmh - REARM_MARGIN_KMH) armed = true;
        return false;
    }
}

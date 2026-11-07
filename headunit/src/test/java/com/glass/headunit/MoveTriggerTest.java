package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoveTriggerTest {

    // one degree of latitude is about 111.2 km, so these offsets give known distances
    private static final double KM = 1.0 / 111.2;

    @Test
    public void kualaLumpurToSepangRefreshes() {
        assertTrue(MoveTrigger.shouldRefresh(3.1478, 101.6953, 2.7, 101.7));
    }

    @Test
    public void oneKilometreDoesNotRefresh() {
        assertFalse(MoveTrigger.shouldRefresh(3.0, 101.7, 3.0 + 1 * KM, 101.7));
    }

    @Test
    public void justUnderAndOverFiveKilometres() {
        assertFalse(MoveTrigger.shouldRefresh(3.0, 101.7, 3.0 + 4.9 * KM, 101.7));
        assertTrue(MoveTrigger.shouldRefresh(3.0, 101.7, 3.0 + 5.1 * KM, 101.7));
    }

    @Test
    public void zeroZeroAndNaNAreNotUsable() {
        assertFalse(MoveTrigger.isUsable(0, 0));
        assertFalse(MoveTrigger.isUsable(Double.NaN, 1));
        assertTrue(MoveTrigger.isUsable(3.1, 101.7));
    }
}

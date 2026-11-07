package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshGateTest {

    private static final double KM = 1.0 / 111.2;

    @Test
    public void firstUsableFixIsAccepted() {
        assertTrue(new RefreshGate().accept(3.0, 101.7));
    }

    @Test
    public void unusableFixesNeverCount() {
        RefreshGate g = new RefreshGate();
        assertFalse(g.accept(0, 0));
        assertFalse(g.accept(Double.NaN, 101.7));
        assertTrue(g.accept(3.0, 101.7)); // still the first usable one
    }

    @Test
    public void jitterNearTheLineDoesNotRefreshAgain() {
        RefreshGate g = new RefreshGate();
        assertTrue(g.accept(3.0, 101.7));
        assertTrue(g.accept(3.0 + 5.1 * KM, 101.7));   // moved past 5 km: refresh, new reference point
        assertFalse(g.accept(3.0 + 4.9 * KM, 101.7));  // wobbles back: 0.2 km from the new point
        assertFalse(g.accept(3.0 + 5.3 * KM, 101.7));
        assertTrue(g.accept(3.0 + 10.4 * KM, 101.7));  // really moved on another 5 km
    }
}

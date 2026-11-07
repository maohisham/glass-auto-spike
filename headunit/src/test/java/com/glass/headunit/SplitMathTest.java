package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitMathTest {

    @Test
    public void wideScreenKeepsSeventyThirty() {
        SplitMath.Result r = SplitMath.effective(0.7f, 1280, 1f);
        assertEquals(0.7f, r.mapShare, 0.001f);
        assertFalse(r.widened);
    }

    @Test
    public void narrowScreenWidensPanel() {
        SplitMath.Result r = SplitMath.effective(0.7f, 800, 1f);
        assertEquals(0.6f, r.mapShare, 0.001f);
        assertTrue(r.widened);
    }

    @Test
    public void tinyScreenFallsToFiftyFifty() {
        SplitMath.Result r = SplitMath.effective(0.7f, 600, 1f);
        assertEquals(0.5f, r.mapShare, 0.001f);
        assertTrue(r.widened);
    }

    @Test
    public void densityIsTakenIntoAccount() {
        // 1600 px at density 2 is 800 dp wide, same as the narrow case
        SplitMath.Result r = SplitMath.effective(0.7f, 1600, 2f);
        assertEquals(0.6f, r.mapShare, 0.001f);
    }

    @Test
    public void snapPicksNearestOffered() {
        assertEquals(0.667f, SplitMath.snap(0.68f, new float[]{0.5f, 0.667f, 0.8f}), 0.0001f);
    }
}

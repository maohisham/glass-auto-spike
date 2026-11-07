package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeedFormatTest {

    @Test
    public void speedIsRoundedToAWholeNumber() {
        assertEquals("62", SpeedFormat.text(62.4f, true));
        assertEquals("63", SpeedFormat.text(62.6f, true));
        assertEquals("110", SpeedFormat.text(109.5f, true));
    }

    @Test
    public void gpsJitterWhileStandingStillShowsZero() {
        assertEquals("0", SpeedFormat.text(0f, true));
        assertEquals("0", SpeedFormat.text(0.4f, true));
        assertEquals("0", SpeedFormat.text(0.9f, true));
        assertEquals("1", SpeedFormat.text(1.2f, true));
    }

    @Test
    public void noFixOrAnImpossibleReadingShowsDashes() {
        assertEquals("--", SpeedFormat.text(50f, false));
        assertEquals("--", SpeedFormat.text(Float.NaN, true));
        assertEquals("0", SpeedFormat.text(-3f, true));
    }

    @Test
    public void aReadingGoesStaleWhenFixesStopArriving() {
        assertFalse(SpeedFormat.isStale(10_000L, 12_000L));                       // 2 s old
        assertFalse(SpeedFormat.isStale(10_000L, 10_000L + SpeedFormat.STALE_MS)); // right at the limit
        assertTrue(SpeedFormat.isStale(10_000L, 10_000L + SpeedFormat.STALE_MS + 1));
        assertTrue(SpeedFormat.isStale(0L, 10_000L));                              // never had one
    }
}

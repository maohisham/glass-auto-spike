package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaProgressTest {

    @Test
    public void fractionIsPositionOverLengthClamped() {
        assertEquals(0.5f, MediaProgress.fraction(50_000, 100_000), 0.0001f);
        assertEquals(0f, MediaProgress.fraction(-5, 100_000), 0f);
        assertEquals(1f, MediaProgress.fraction(250_000, 100_000), 0f);
    }

    @Test
    public void noLengthMeansNoProgress() {
        assertEquals(0f, MediaProgress.fraction(10_000, 0), 0f);
        assertEquals(0f, MediaProgress.fraction(10_000, -1), 0f);
    }

    @Test
    public void formatsMinutesAndSeconds() {
        assertEquals("1:42", MediaProgress.format(102_000));
        assertEquals("3:36", MediaProgress.format(216_000));
        assertEquals("0:05", MediaProgress.format(5_400));
        assertEquals("0:00", MediaProgress.format(-3));
    }

    @Test
    public void formatsHoursForLongTracks() {
        assertEquals("1:02:05", MediaProgress.format(3_725_000));
    }

    @Test
    public void positionAdvancesWhilePlayingAndStopsAtTheEnd() {
        // received at t=1000 ms at position 42 s; now it is t=4000 ms
        assertEquals(45_000, MediaProgress.extrapolate(42_000, 1_000, 4_000, true, 216_000));
        assertEquals(216_000, MediaProgress.extrapolate(215_000, 1_000, 9_000, true, 216_000));
    }

    @Test
    public void positionStaysPutWhilePaused() {
        assertEquals(42_000, MediaProgress.extrapolate(42_000, 1_000, 9_000, false, 216_000));
    }

    @Test
    public void aSeekIsNoticedAsDrift() {
        // last update: 42 s at t=1 s, playing. At t=4 s we would expect 45 s.
        assertFalse(MediaProgress.drifted(42_000, 1_000, true, 45_300, 4_000));   // normal jitter
        assertTrue(MediaProgress.drifted(42_000, 1_000, true, 120_000, 4_000));   // jumped forward
        assertTrue(MediaProgress.drifted(42_000, 1_000, true, 10_000, 4_000));    // jumped back
        assertFalse(MediaProgress.drifted(42_000, 1_000, false, 42_200, 9_000));  // paused and unchanged
        assertTrue(MediaProgress.drifted(42_000, 1_000, false, 80_000, 9_000));   // paused then seeked
    }
}

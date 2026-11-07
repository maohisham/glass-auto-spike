package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EcoScoreTest {

    private static final long T0 = 1_000_000L;

    /** Feeds one speed per second for the given number of seconds, starting at second `from`. */
    private static void drive(EcoScore e, float kmh, int from, int seconds) {
        for (int i = 0; i < seconds; i++) e.onSpeed(kmh, T0 + (from + i) * 1000L);
    }

    @Test
    public void steadyHighwayDrivingScoresExcellent() {
        EcoScore e = new EcoScore();
        drive(e, 75f, 0, 600);
        EcoScore.Stats s = e.calculate(T0 + 600_000L);
        assertTrue("score " + s.score, s.score >= 95);
        assertEquals("Excellent", s.grade);
        assertEquals(12.5f, s.distanceKm, 0.3f);   // 75 km/h for 10 minutes
    }

    @Test
    public void speedingAbove130IsPenalised() {
        EcoScore e = new EcoScore();
        drive(e, 150f, 0, 300);
        EcoScore.Stats s = e.calculate(T0 + 300_000L);
        assertEquals(60f, s.bonusScore, 0.5f);      // 100 - (150 - 130) * 2
        assertTrue("score " + s.score, s.score < 60);
    }

    @Test
    public void stopAndGoSpeedDestroysSmoothness() {
        EcoScore e = new EcoScore();
        for (int i = 0; i < 300; i++) e.onSpeed(i % 2 == 0 ? 20f : 100f, T0 + i * 1000L);
        EcoScore.Stats s = e.calculate(T0 + 300_000L);
        assertTrue("smooth " + s.smoothScore, s.smoothScore < 5f);
    }

    @Test
    public void aLongStopCountsAsIdleTime() {
        EcoScore e = new EcoScore();
        drive(e, 60f, 0, 60);
        drive(e, 0f, 60, 60);     // a full minute standing still
        drive(e, 60f, 120, 60);
        EcoScore.Stats s = e.calculate(T0 + 180_000L);
        assertEquals(60f, s.idleSec, 2f);
        assertEquals(33.3f, s.idlePct, 2f);
    }

    @Test
    public void aShortStopIsNotIdleTime() {
        EcoScore e = new EcoScore();
        drive(e, 60f, 0, 60);
        drive(e, 0f, 60, 10);     // a red light
        drive(e, 60f, 70, 60);
        assertEquals(0L, e.calculate(T0 + 130_000L).idleSec);
    }

    @Test
    public void noTripUntilTheCarMoves() {
        EcoScore e = new EcoScore();
        drive(e, 0f, 0, 30);
        drive(e, 3f, 30, 10);
        assertFalse(e.isTripActive());
        assertEquals(0, e.calculate(T0 + 40_000L).score);
        e.onSpeed(10f, T0 + 41_000L);
        assertTrue(e.isTripActive());
    }

    @Test
    public void resetStartsOver() {
        EcoScore e = new EcoScore();
        drive(e, 75f, 0, 120);
        e.reset();
        assertFalse(e.isTripActive());
        assertEquals(0f, e.calculate(T0 + 130_000L).distanceKm, 0f);
    }

    @Test
    public void gradesFollowTheBands() {
        assertEquals("Excellent", EcoScore.grade(90));
        assertEquals("Good", EcoScore.grade(75));
        assertEquals("Fair", EcoScore.grade(60));
        assertEquals("Poor", EcoScore.grade(40));
        assertEquals("Very Poor", EcoScore.grade(39));
    }

    @Test
    public void theTipPicksTheMostUsefulAdvice() {
        EcoScore.Stats idle = new EcoScore.Stats();
        idle.idlePct = 30f; idle.smoothScore = 100; idle.bonusScore = 100; idle.avgSpeedKmh = 60;
        assertTrue(EcoScore.tip(idle).contains("idling"));
        EcoScore.Stats fast = new EcoScore.Stats();
        fast.idlePct = 0; fast.smoothScore = 100; fast.bonusScore = 100; fast.avgSpeedKmh = 110;
        assertTrue(EcoScore.tip(fast).contains("Slow down"));
        EcoScore.Stats fine = new EcoScore.Stats();
        fine.idlePct = 0; fine.smoothScore = 100; fine.bonusScore = 100; fine.avgSpeedKmh = 70;
        assertEquals("", EcoScore.tip(fine));
    }

    @Test
    public void durationsReadNaturally() {
        assertEquals("1h 02m", EcoScore.duration(3725));
        assertEquals("2m 05s", EcoScore.duration(125));
        assertEquals("42s", EcoScore.duration(42));
    }
}

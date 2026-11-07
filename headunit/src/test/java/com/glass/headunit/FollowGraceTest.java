package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FollowGraceTest {

    @Test
    public void aPlayerThatIsHereIsNeverMarkedLost() {
        assertEquals(0L, FollowGrace.mark(0L, true, 10_000L));
        assertEquals(0L, FollowGrace.mark(5_000L, true, 10_000L));   // it came back: the clock resets
    }

    @Test
    public void theFirstMissingCheckStartsTheClockAndLaterOnesKeepIt() {
        assertEquals(10_000L, FollowGrace.mark(0L, false, 10_000L));
        assertEquals(10_000L, FollowGrace.mark(10_000L, false, 12_000L));
    }

    @Test
    public void weKeepFollowingUntilTheGraceIsUp() {
        assertFalse(FollowGrace.shouldDetach(0L, 50_000L));                              // not lost at all
        assertFalse(FollowGrace.shouldDetach(10_000L, 12_000L));                         // 2 s: a track change
        assertFalse(FollowGrace.shouldDetach(10_000L, 10_000L + FollowGrace.GRACE_MS - 1));
        assertTrue(FollowGrace.shouldDetach(10_000L, 10_000L + FollowGrace.GRACE_MS));   // really gone
    }

    @Test
    public void theGraceCoversSeveralTwoSecondChecks() {
        assertTrue("grace is " + FollowGrace.GRACE_MS, FollowGrace.GRACE_MS >= 5_000L && FollowGrace.GRACE_MS <= 10_000L);
    }
}

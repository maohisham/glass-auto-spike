package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.session.PlaybackStateCompat;

import org.junit.Test;

public class RepeatModeTest {

    private static final int NONE = PlaybackStateCompat.REPEAT_MODE_NONE;
    private static final int ONE = PlaybackStateCompat.REPEAT_MODE_ONE;
    private static final int ALL = PlaybackStateCompat.REPEAT_MODE_ALL;
    private static final int GROUP = PlaybackStateCompat.REPEAT_MODE_GROUP;

    @Test
    public void tappingCyclesOffThenAllThenOneThenOff() {
        assertEquals(ALL, RepeatMode.next(NONE));
        assertEquals(ONE, RepeatMode.next(ALL));
        assertEquals(NONE, RepeatMode.next(ONE));
    }

    @Test
    public void groupCountsAsAllAndAnUnknownModeStartsTheCycle() {
        assertEquals(ONE, RepeatMode.next(GROUP));
        assertEquals(ALL, RepeatMode.next(PlaybackStateCompat.REPEAT_MODE_INVALID));
    }

    @Test
    public void anythingButOffCountsAsOn() {
        assertFalse(RepeatMode.isOn(NONE));
        assertFalse(RepeatMode.isOn(PlaybackStateCompat.REPEAT_MODE_INVALID));
        assertTrue(RepeatMode.isOn(ALL));
        assertTrue(RepeatMode.isOn(ONE));
        assertTrue(RepeatMode.isOn(GROUP));
    }

    @Test
    public void labelsSayWhatIsHappening() {
        assertEquals("Repeat off", RepeatMode.label(NONE));
        assertEquals("Repeat all", RepeatMode.label(ALL));
        assertEquals("Repeat all", RepeatMode.label(GROUP));
        assertEquals("Repeat one", RepeatMode.label(ONE));
    }
}

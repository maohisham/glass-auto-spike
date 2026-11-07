package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoPlayPolicyTest {

    @Test
    public void startsWhenEverythingLinesUp() {
        assertTrue(AutoPlayPolicy.shouldStart(true, "com.spotify.music", true, false, false));
    }

    @Test
    public void doesNothingWhenTheSettingIsOff() {
        assertFalse(AutoPlayPolicy.shouldStart(false, "com.spotify.music", true, false, false));
    }

    @Test
    public void doesNothingWithoutALastPlayerOrIfItIsGone() {
        assertFalse(AutoPlayPolicy.shouldStart(true, "", true, false, false));
        assertFalse(AutoPlayPolicy.shouldStart(true, null, true, false, false));
        assertFalse(AutoPlayPolicy.shouldStart(true, "com.spotify.music", false, false, false));
    }

    @Test
    public void neverInterruptsSomethingAlreadyPlaying() {
        assertFalse(AutoPlayPolicy.shouldStart(true, "com.spotify.music", true, true, false));
    }

    @Test
    public void triesOnlyOncePerStart() {
        assertFalse(AutoPlayPolicy.shouldStart(true, "com.spotify.music", true, false, true));
    }
}

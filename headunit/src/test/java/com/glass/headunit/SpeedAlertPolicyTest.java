package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeedAlertPolicyTest {

    private static final int LIMIT = 110;

    @Test
    public void soundsOnceAfterTwoReadingsInARowAboveTheLimit() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        assertFalse(p.onSpeed(115f, LIMIT));   // the first reading could be a GPS spike
        assertTrue(p.onSpeed(116f, LIMIT));    // the second confirms it
    }

    @Test
    public void staysQuietWhileYouKeepGoingOverTheLimit() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        p.onSpeed(115f, LIMIT);
        assertTrue(p.onSpeed(116f, LIMIT));
        for (int i = 0; i < 30; i++) assertFalse("reading " + i, p.onSpeed(120f, LIMIT));
    }

    @Test
    public void soundsAgainOnlyAfterDroppingBackUnderAndExceedingAgain() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        p.onSpeed(115f, LIMIT);
        assertTrue(p.onSpeed(116f, LIMIT));
        assertFalse(p.onSpeed(100f, LIMIT));   // back under by more than the margin: ready again
        assertFalse(p.onSpeed(118f, LIMIT));
        assertTrue(p.onSpeed(119f, LIMIT));    // exceeded again: sounds again
    }

    @Test
    public void hoveringJustUnderTheLimitDoesNotReadyItAgain() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        p.onSpeed(115f, LIMIT);
        assertTrue(p.onSpeed(116f, LIMIT));
        assertFalse(p.onSpeed(109f, LIMIT));   // within 3 km/h of the limit: still counts as the same stretch
        assertFalse(p.onSpeed(115f, LIMIT));
        assertFalse(p.onSpeed(116f, LIMIT));   // no second sound
    }

    @Test
    public void aSingleSpikeNeverSounds() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        assertFalse(p.onSpeed(140f, LIMIT));
        assertFalse(p.onSpeed(90f, LIMIT));    // the spike is forgotten
        assertFalse(p.onSpeed(140f, LIMIT));   // so one more reading still does not sound
        assertTrue(p.onSpeed(141f, LIMIT));
    }

    @Test
    public void exactlyAtTheLimitIsNotExceedingIt() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        assertFalse(p.onSpeed(110f, LIMIT));
        assertFalse(p.onSpeed(110f, LIMIT));
        assertFalse(p.onSpeed(110f, LIMIT));
    }

    @Test
    public void noLimitMeansNoAlert() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        assertFalse(p.onSpeed(200f, 0));
        assertFalse(p.onSpeed(200f, 0));
    }

    @Test
    public void changingTheLimitWhileDrivingTakesEffect() {
        SpeedAlertPolicy p = new SpeedAlertPolicy();
        assertFalse(p.onSpeed(100f, 120));
        assertFalse(p.onSpeed(100f, 120));
        assertFalse(p.onSpeed(100f, 90));      // now over a lower limit: first reading
        assertTrue(p.onSpeed(100f, 90));       // second reading: sounds
    }
}

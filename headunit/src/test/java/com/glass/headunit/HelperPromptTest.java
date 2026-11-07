package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HelperPromptTest {

    @Test
    public void asksForTheHelperWhenItIsOffAndTheSplitIsNotFiftyFifty() {
        assertTrue(HelperPrompt.shouldShow(false, 0.70f));
    }

    @Test
    public void staysQuietOnceTheHelperIsOn() {
        assertFalse(HelperPrompt.shouldShow(true, 0.70f));
    }

    @Test
    public void staysQuietWhenTheUserWantsFiftyFiftyBecauseTheUnitDoesThatByItself() {
        assertFalse(HelperPrompt.shouldShow(false, 0.50f));
    }
}

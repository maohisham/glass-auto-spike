package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitPlanTest {

    @Test
    public void mapOnTheLeftPutsTheDividerAtTheMapShare() {
        assertEquals(0.7f, SplitPlan.dividerFraction(0.7f, false), 0.001f);
    }

    @Test
    public void swappedSidesPutTheDividerAtThePanelShare() {
        assertEquals(0.3f, SplitPlan.dividerFraction(0.7f, true), 0.001f);
    }

    @Test
    public void theMapIsFirstWhenItSitsBeforeTheDivider() {
        assertTrue(SplitPlan.mapIsFirst(400f, 1280f * 0.5f));
        assertFalse(SplitPlan.mapIsFirst(900f, 1280f * 0.5f));
    }

    @Test
    public void theShareBelongsToWhicheverSideTheMapIsReallyOn() {
        // the unit put the map on the right: a 70% map needs the divider at 30% from the left
        assertEquals(0.3f, SplitPlan.dividerFraction(0.7f, !SplitPlan.mapIsFirst(900f, 640f)), 0.001f);
        assertEquals(0.7f, SplitPlan.dividerFraction(0.7f, !SplitPlan.mapIsFirst(300f, 640f)), 0.001f);
    }

    @Test
    public void aDividerStillAnimatingIsNotSettled() {
        // seen on the tablet: read at 28% while the split was still opening, it ended at 50%
        assertFalse(SplitPlan.isSettled(0.28f * 3008, 0.50f * 3008));
    }

    @Test
    public void aDividerThatStoppedMovingIsSettled() {
        assertTrue(SplitPlan.isSettled(1504f, 1505f));
        assertTrue(SplitPlan.isSettled(1504f, 1504f));
    }

    @Test
    public void mapFirstWhenThePanelIsOnTheRight() {
        assertEquals("maps", SplitPlan.firstApp(false));
        assertEquals("panel", SplitPlan.firstApp(true));
    }

    @Test
    public void gestureRunsAcrossTheDividerAxis() {
        // landscape: a vertical divider at x = 0.7 of 1280, drag along x at mid-height
        float[] g = SplitPlan.drag(1280, 720, 0.55f, 0.7f);   // fromFraction, toFraction
        assertEquals(1280 * 0.55f, g[0], 0.5f);
        assertEquals(360f, g[1], 0.5f);
        assertEquals(1280 * 0.7f, g[2], 0.5f);
        assertEquals(360f, g[3], 0.5f);
    }

    @Test
    public void portraitScreensDragAlongYBecauseTheDividerIsHorizontal() {
        float[] g = SplitPlan.drag(720, 1280, 0.5f, 0.7f);
        assertEquals(360f, g[0], 0.5f);
        assertEquals(1280 * 0.5f, g[1], 0.5f);
        assertEquals(360f, g[2], 0.5f);
        assertEquals(1280 * 0.7f, g[3], 0.5f);
    }

    @Test
    public void aDividerAlreadyNearTheTargetIsLeftAlone() {
        // the tablet snaps to 66.6%: asking for 70% again must not keep dragging
        assertFalse(SplitPlan.needsDrag(0.666f, 0.70f));
        assertFalse(SplitPlan.needsDrag(0.70f, 0.70f));
    }

    @Test
    public void aFiftyFiftySplitNeedsDraggingToSeventyThirty() {
        assertTrue(SplitPlan.needsDrag(0.50f, 0.70f));
        assertTrue(SplitPlan.needsDrag(0.50f, 0.30f));
    }
}

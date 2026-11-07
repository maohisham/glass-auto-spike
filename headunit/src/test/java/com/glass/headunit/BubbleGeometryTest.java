package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BubbleGeometryTest {

    @Test
    public void startsInsideThePanelHalfOnTheRight() {
        int[] xy = BubbleGeometry.start(1280, 720, 0.7f, false, 100, 16);
        assertTrue(xy[0] >= Math.round(1280 * 0.7f));
        assertTrue(xy[0] + 100 <= 1280);
        assertTrue(xy[1] >= 0 && xy[1] + 100 <= 720);
    }

    @Test
    public void startsInsideThePanelHalfWhenSidesAreSwapped() {
        int[] xy = BubbleGeometry.start(1280, 720, 0.7f, true, 100, 16);
        assertTrue(xy[0] >= 0);
        assertTrue(xy[0] + 100 <= Math.round(1280 * 0.3f));
    }

    @Test
    public void pickerCoversExactlyThePanelHalf() {
        int[] r = BubbleGeometry.picker(1280, 720, 0.7f, false);   // left, top, width, height
        assertEquals(Math.round(1280 * 0.7f), r[0]);
        assertEquals(1280 - r[0], r[2]);
        assertEquals(720, r[3]);
        int[] sw = BubbleGeometry.picker(1280, 720, 0.7f, true);
        assertEquals(0, sw[0]);
        assertEquals(Math.round(1280 * 0.3f), sw[2]);
    }

    @Test
    public void narrowScreenUsesTheWidenedPanelNotTheRequestedShare() {
        // 800 px at density 1: 70:30 would leave a 240 px panel, so the effective share is 0.6
        int[] r = BubbleGeometry.picker(800, 480, 0.6f, false);
        assertEquals(480, r[0]);
        assertEquals(320, r[2]);
    }
}

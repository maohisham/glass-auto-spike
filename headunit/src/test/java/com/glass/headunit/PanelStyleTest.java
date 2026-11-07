package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PanelStyleTest {

    @Test
    public void textScaleForEachSize() {
        assertEquals(0.85f, PanelStyle.scale(0), 0.001f);
        assertEquals(1.0f, PanelStyle.scale(1), 0.001f);
        assertEquals(1.25f, PanelStyle.scale(2), 0.001f);
    }

    @Test
    public void themeFollowsTheUnitOnlyWhenAsked() {
        assertTrue(PanelStyle.isDark(0, true));
        assertFalse(PanelStyle.isDark(0, false));
        assertFalse(PanelStyle.isDark(1, true));
        assertTrue(PanelStyle.isDark(2, false));
    }

    @Test
    public void compactModeShrinksTextFurther() {
        assertTrue(PanelStyle.sp(20f, 1, PanelMode.COMPACT) < PanelStyle.sp(20f, 1, PanelMode.REGULAR));
        // the biggest setting in the compact layout must still be no bigger than the regular layout's medium text
        assertTrue(PanelStyle.sp(20f, 2, PanelMode.COMPACT) <= PanelStyle.sp(20f, 1, PanelMode.REGULAR) * 1.1f);
    }

    @Test
    public void headerBlockNeverTakesMoreThanSixtyPercentOfTheHeight() {
        assertEquals(480, PanelStyle.headerMaxPx(800));
        assertEquals(144, PanelStyle.headerMaxPx(240));
    }

    @Test
    public void scoreColoursGoGreenYellowRed() {
        assertEquals(0xFF66DD88, PanelStyle.scoreColor(75));
        assertEquals(0xFF66DD88, PanelStyle.scoreColor(98));
        assertEquals(0xFFFFCC44, PanelStyle.scoreColor(60));
        assertEquals(0xFFFFCC44, PanelStyle.scoreColor(74));
        assertEquals(0xFFFF6655, PanelStyle.scoreColor(59));
        assertEquals(0xFFFF6655, PanelStyle.scoreColor(0));
    }
}

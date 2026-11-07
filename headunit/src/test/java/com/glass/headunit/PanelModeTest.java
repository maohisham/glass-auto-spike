package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PanelModeTest {

    @Test
    public void compactBelow400dp() {
        assertEquals(PanelMode.COMPACT, PanelMode.forWidthDp(399));
        assertEquals(PanelMode.REGULAR, PanelMode.forWidthDp(400));
    }
}

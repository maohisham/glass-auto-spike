package com.glass.headunit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CoverPickTest {

    @Test
    public void firstNonEmptyAddressWins() {
        assertEquals("content://art/1", CoverPick.firstUri(null, "", "content://art/1", "https://x/2"));
    }

    @Test
    public void noAddressMeansNull() {
        assertNull(CoverPick.firstUri(null, "", ""));
        assertNull(CoverPick.firstUri());
    }

    @Test
    public void bigCoversAreScaledDownKeepingTheirShape() {
        assertArrayEquals(new int[]{256, 128}, CoverPick.targetSize(1024, 512, 256));
        assertArrayEquals(new int[]{128, 256}, CoverPick.targetSize(500, 1000, 256));
    }

    @Test
    public void smallCoversAreLeftAlone() {
        assertArrayEquals(new int[]{120, 120}, CoverPick.targetSize(120, 120, 256));
    }
}

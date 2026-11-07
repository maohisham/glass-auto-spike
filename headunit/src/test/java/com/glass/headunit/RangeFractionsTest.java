package com.glass.headunit;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class RangeFractionsTest {

    @Test
    public void aDaysRangeIsPlacedWithinTheWeeksRange() {
        // 24 to 32 degrees in a week that ran 22 to 34
        assertArrayEquals(new float[]{2f / 12f, 10f / 12f}, Forecast.rangeFractions(24, 32, 22, 34), 0.001f);
    }

    @Test
    public void aFlatWeekGivesTheWholeBarInsteadOfDividingByZero() {
        assertArrayEquals(new float[]{0f, 1f}, Forecast.rangeFractions(28, 28, 28, 28), 0.001f);
    }

    @Test
    public void theWarmestAndColdestDaysTouchTheEnds() {
        assertArrayEquals(new float[]{0f, 0.5f}, Forecast.rangeFractions(22, 28, 22, 34), 0.001f);
        assertArrayEquals(new float[]{0.5f, 1f}, Forecast.rangeFractions(28, 34, 22, 34), 0.001f);
    }
}

package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CountdownTextTest {

    @Test
    public void overAnHourShowsHoursAndTwoDigitMinutes() {
        assertEquals("1h 06m", Prayer.formatMinutes(66));
        assertEquals("2h 00m", Prayer.formatMinutes(120));
    }

    @Test
    public void exactlyOneHourStillShowsTheHour() {
        assertEquals("1h 00m", Prayer.formatMinutes(60));
    }

    @Test
    public void underAnHourShowsOnlyMinutes() {
        assertEquals("59m", Prayer.formatMinutes(59));
        assertEquals("6m", Prayer.formatMinutes(6));
    }

    @Test
    public void zeroMinutesShowsZeroM() {
        assertEquals("0m", Prayer.formatMinutes(0));
    }

    @Test
    public void theStripBreaksOnlyBetweenThePrayerAndItsCountdown() {
        String s = StripText.build("Isyak", "8:27 PM", "1h 06m");
        int breaks = 0;
        for (char c : s.toCharArray()) if (c == ' ') breaks++;
        assertEquals(1, breaks);                         // one place the text may wrap
        assertTrue(s.startsWith("Isyak 8:27 PM "));
        assertTrue(s.endsWith("in 1h 06m"));
    }
}

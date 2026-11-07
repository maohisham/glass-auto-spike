package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class PrayerRolloverTest {

    private static Prayer.Data sample() {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48");
        d.times.put("Zohor", "13:10");
        d.times.put("Asar", "16:25");
        d.times.put("Maghrib", "19:15");
        d.times.put("Isyak", "20:27");
        d.syuruk = "07:00";
        d.hijri = "x"; // stops recompute from computing one
        return d;
    }

    private static long at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 7, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void afterIsyakNextIsTomorrowsSubuh() {
        Prayer.Data d = Prayer.recompute(sample(), at(22, 0));
        assertEquals("Subuh", d.nextName);
        assertTrue(d.nextMs > at(23, 59));
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(d.nextMs);
        assertEquals(8, next.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void beforeSubuhCurrentIsLastNightsIsyak() {
        Prayer.Data d = Prayer.recompute(sample(), at(4, 0));
        assertEquals("Isyak", d.currentName);
        assertEquals("Subuh", d.nextName);
        assertEquals(at(5, 48), d.nextMs);
    }

    @Test
    public void justStartedForThreeMinutes() {
        assertTrue(Prayer.recompute(sample(), at(13, 11)).justStarted);
        assertFalse(Prayer.recompute(sample(), at(13, 14)).justStarted);
    }
}

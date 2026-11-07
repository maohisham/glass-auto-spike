package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class PrayerProgressTest {

    private static Prayer.Data sample() {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48");
        d.times.put("Zohor", "13:00");
        d.times.put("Asar", "16:21");
        d.times.put("Maghrib", "18:58");
        d.times.put("Isyak", "20:10");
        d.syuruk = "07:00";
        d.hijri = "x";
        return d;
    }

    private static long at(int h, int m) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 7, h, m, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }


    @Test
    public void midAfternoonIsPartWayFromAsarToMaghrib() {
        Prayer.Data d = Prayer.recompute(sample(), at(17, 22));
        // Asar 16:21 to Maghrib 18:58 is 157 minutes; 61 have passed
        assertEquals(61f / 157f, PrayerProgress.fraction(d, at(17, 22)), 0.005f);
    }

    @Test
    public void justAfterAPrayerStartsTheBarIsNearlyEmpty() {
        Prayer.Data d = Prayer.recompute(sample(), at(16, 22));
        assertEquals(1f / 157f, PrayerProgress.fraction(d, at(16, 22)), 0.005f);
    }

    @Test
    public void beforeSubuhItCountsFromLastNightsIsyak() {
        Prayer.Data d = Prayer.recompute(sample(), at(4, 0));
        // Isyak 20:10 yesterday to Subuh 05:48 today is 578 minutes; 470 have passed
        assertEquals(470f / 578f, PrayerProgress.fraction(d, at(4, 0)), 0.005f);
    }

    @Test
    public void afterIsyakItCountsTowardTomorrowsSubuh() {
        Prayer.Data d = Prayer.recompute(sample(), at(22, 0));
        // Isyak 20:10 today to Subuh 05:48 tomorrow is 578 minutes; 110 have passed
        assertEquals(110f / 578f, PrayerProgress.fraction(d, at(22, 0)), 0.005f);
    }

    @Test
    public void theFractionStaysBetweenZeroAndOne() {
        // computed for 10:00 the current prayer is Subuh (05:48) and the next is Zohor (13:00): 252 of 432 minutes
        Prayer.Data morning = Prayer.recompute(sample(), at(10, 0));
        assertEquals(252f / 432f, PrayerProgress.fraction(morning, at(10, 0)), 0.005f);
        // asked well after the next prayer was due (before the next recompute), it stops at full
        Prayer.Data d = Prayer.recompute(sample(), at(17, 22));
        assertEquals(1f, PrayerProgress.fraction(d, d.nextMs + 3_600_000L), 0f);
    }
}

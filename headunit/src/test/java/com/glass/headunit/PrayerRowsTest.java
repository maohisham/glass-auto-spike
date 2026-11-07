package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

public class PrayerRowsTest {

    private static Prayer.Data sample(String syuruk) {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48");
        d.times.put("Zohor", "13:00");
        d.times.put("Asar", "16:21");
        d.times.put("Maghrib", "18:58");
        d.times.put("Isyak", "20:10");
        d.syuruk = syuruk;
        d.hijri = "x";
        return d;
    }

    private static long at(int h, int m) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 7, h, m, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String kinds(List<PrayerRows.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (PrayerRows.Row r : rows) sb.append(r.kind).append(' ');
        return sb.toString().trim();
    }

    @Test
    public void afternoonHighlightsAsarAndMarksMaghribNext() {
        Prayer.Data d = Prayer.recompute(sample("07:00"), at(17, 22));
        List<PrayerRows.Row> rows = PrayerRows.build(d, at(17, 22));
        assertEquals(6, rows.size());
        assertEquals("Subuh", rows.get(0).name);
        assertEquals("Syuruk", rows.get(1).name);
        assertEquals("5:48 AM", rows.get(0).time12);
        assertEquals("7:00 AM", rows.get(1).time12);
        assertEquals("4:21 PM", rows.get(3).time12);
        assertEquals("PAST SUNRISE PAST CURRENT NEXT LATER", kinds(rows));
    }

    @Test
    public void beforeSubuhLastNightsIsyakIsCurrentAndTodaysPrayersAreStillAhead() {
        Prayer.Data d = Prayer.recompute(sample("07:00"), at(4, 0));
        assertEquals("NEXT SUNRISE LATER LATER LATER CURRENT", kinds(PrayerRows.build(d, at(4, 0))));
    }

    @Test
    public void afterIsyakTomorrowsSubuhIsNextAndTodaysPrayersArePast() {
        Prayer.Data d = Prayer.recompute(sample("07:00"), at(22, 0));
        assertEquals("NEXT SUNRISE PAST PAST PAST CURRENT", kinds(PrayerRows.build(d, at(22, 0))));
    }

    @Test
    public void noSyurukMeansFiveRows() {
        Prayer.Data d = Prayer.recompute(sample(""), at(17, 22));
        List<PrayerRows.Row> rows = PrayerRows.build(d, at(17, 22));
        assertEquals(5, rows.size());
        assertEquals("PAST PAST CURRENT NEXT LATER", kinds(rows));
    }
}

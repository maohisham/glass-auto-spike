package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CacheTest {

    @Test
    public void prayerSurvivesAJsonRoundTrip() {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48");
        d.times.put("Zohor", "13:10");
        d.times.put("Asar", "16:25");
        d.times.put("Maghrib", "19:15");
        d.times.put("Isyak", "20:27");
        d.syuruk = "07:00";
        d.zoneCode = "SGR01";
        d.zoneName = "Gombak / Sepang";
        d.hijri = "26 Jamadil Awwal 1448";
        Prayer.Data back = Cache.prayerFromJson(Cache.toJson(d));
        assertEquals(d.times, back.times);
        assertEquals("07:00", back.syuruk);
        assertEquals("SGR01", back.zoneCode);
        assertEquals("Gombak / Sepang", back.zoneName);
        assertEquals("26 Jamadil Awwal 1448", back.hijri);
    }

    @Test
    public void weatherSurvivesAJsonRoundTrip() {
        Weather.Data w = new Weather.Data();
        w.tempC = 31f;
        w.code = 53;
        w.city = "Sepang";
        w.emoji = "🌦";
        w.desc = "Drizzle";
        Weather.Data back = Cache.weatherFromJson(Cache.toJson(w));
        assertEquals(31f, back.tempC, 0.001f);
        assertEquals(53, back.code);
        assertEquals("Sepang", back.city);
        assertEquals("Drizzle", back.desc);
    }

    @Test
    public void garbageJsonGivesNullNotACrash() {
        assertEquals(null, Cache.prayerFromJson("not json"));
        assertEquals(null, Cache.weatherFromJson(""));
    }
}

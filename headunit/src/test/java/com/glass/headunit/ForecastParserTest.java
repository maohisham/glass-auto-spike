package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForecastParserTest {

    /** A Saturday 7 Nov 2026, 18:15, with 48 hourly entries (7th and 8th) and 7 days. Hour h of day d has temperature 20 + h and rain 10 * d. */
    private static String forecastJson(boolean withRain) {
        StringBuilder times = new StringBuilder(), temps = new StringBuilder(), codes = new StringBuilder(), rain = new StringBuilder();
        for (int d = 0; d < 2; d++) {
            for (int h = 0; h < 24; h++) {
                String sep = times.length() == 0 ? "" : ",";
                times.append(sep).append(String.format("\"2026-11-%02dT%02d:00\"", 7 + d, h));
                temps.append(sep).append(20 + h);
                codes.append(sep).append(h < 12 ? 1 : 53);
                rain.append(sep).append(withRain ? String.valueOf(10 * (d + 1)) : "null");
            }
        }
        return "{\"utc_offset_seconds\":28800,"
                + "\"current\":{\"time\":\"2026-11-07T18:15\",\"temperature_2m\":26.1,\"relative_humidity_2m\":88,\"apparent_temperature\":30.2,\"weather_code\":53,\"wind_speed_10m\":9.4},"
                + "\"hourly\":{\"time\":[" + times + "],\"temperature_2m\":[" + temps + "],\"weather_code\":[" + codes + "],\"precipitation_probability\":[" + rain + "]},"
                + "\"daily\":{\"time\":[\"2026-11-07\",\"2026-11-08\",\"2026-11-09\",\"2026-11-10\",\"2026-11-11\",\"2026-11-12\",\"2026-11-13\"],"
                + "\"weather_code\":[53,3,61,95,1,0,2],\"temperature_2m_max\":[31,32,30,29,33,34,32],\"temperature_2m_min\":[24,25,24,23,24,25,24],"
                + "\"precipitation_probability_max\":[60,30,80,90,10,0,20]}}";
    }

    private static final String AIR = "{\"current\":{\"time\":\"2026-11-07T18:00\",\"us_aqi\":57,\"pm2_5\":18.2,\"pm10\":30.1}}";

    @Test
    public void currentConditionsAreRead() {
        Forecast f = ForecastParser.parse(forecastJson(true), AIR);
        assertNotNull(f);
        assertEquals(26.1f, f.tempC, 0.01f);
        assertEquals(30.2f, f.feelsC, 0.01f);
        assertEquals(88, f.humidity);
        assertEquals(9.4f, f.windKmh, 0.01f);
        assertEquals(53, f.code);
    }

    @Test
    public void next24HoursStartAtTheCurrentHour() {
        Forecast f = ForecastParser.parse(forecastJson(true), AIR);
        assertEquals(24, f.hours.size());
        assertEquals("6 PM", f.hours.get(0).label);       // 18:15 now, so the 18:00 entry is first
        assertEquals(38f, f.hours.get(0).tempC, 0.01f);   // 20 + 18
        assertEquals(53, f.hours.get(0).code);
        assertEquals(10, f.hours.get(0).rainPct);
        assertEquals("5 PM", f.hours.get(23).label);      // 17:00 the next day
        assertEquals(20, f.hours.get(23).rainPct);
    }

    @Test
    public void sevenDaysWithNamesAndRanges() {
        Forecast f = ForecastParser.parse(forecastJson(true), AIR);
        assertEquals(7, f.days.size());
        assertEquals("Today", f.days.get(0).name);
        assertEquals("Sun", f.days.get(1).name);          // 8 Nov 2026 is a Sunday
        assertEquals("Fri", f.days.get(6).name);
        assertEquals(31f, f.days.get(0).hiC, 0.01f);
        assertEquals(24f, f.days.get(0).loC, 0.01f);
        assertEquals(60, f.days.get(0).rainPct);
        assertEquals(95, f.days.get(3).code);
    }

    @Test
    public void airQualityIsReadWhenPresent() {
        Forecast f = ForecastParser.parse(forecastJson(true), AIR);
        assertTrue(f.air.valid);
        assertEquals(57, f.air.usAqi);
        assertEquals(18.2f, f.air.pm25, 0.01f);
        assertEquals(30.1f, f.air.pm10, 0.01f);
    }

    @Test
    public void missingAirQualityLeavesItInvalidButKeepsTheForecast() {
        Forecast f = ForecastParser.parse(forecastJson(true), null);
        assertNotNull(f);
        assertFalse(f.air.valid);
        assertEquals(7, f.days.size());
    }

    @Test
    public void nullRainChancesBecomeZero() {
        Forecast f = ForecastParser.parse(forecastJson(false), AIR);
        assertEquals(0, f.hours.get(0).rainPct);
    }

    @Test
    public void garbageGivesNullNotACrash() {
        assertNull(ForecastParser.parse("not json", AIR));
        assertNull(ForecastParser.parse("", null));
        assertNull(ForecastParser.parse(null, null));
    }

    @Test
    public void hourLabelsUseTwelveHourTime() {
        assertEquals("12 AM", ForecastParser.hourLabel(0));
        assertEquals("1 AM", ForecastParser.hourLabel(1));
        assertEquals("12 PM", ForecastParser.hourLabel(12));
        assertEquals("1 PM", ForecastParser.hourLabel(13));
        assertEquals("11 PM", ForecastParser.hourLabel(23));
    }

    @Test
    public void aCachedForecastStartsFromTheRealCurrentHour() {
        // fetched at 18:15 but read again at 20:30 local time (+08:00): the list must start at 8 PM
        long now = java.time.LocalDateTime.of(2026, 11, 7, 20, 30).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli();
        Forecast f = ForecastParser.parse(forecastJson(true), AIR, now);
        assertEquals("8 PM", f.hours.get(0).label);
        assertEquals(40f, f.hours.get(0).tempC, 0.01f);   // 20 + 20
        assertEquals(24, f.hours.size());
    }
}

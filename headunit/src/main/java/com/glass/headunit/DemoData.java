package com.glass.headunit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/** Made-up data for the demo mode, so the weather screen can be checked without a network. */
final class DemoData {

    private DemoData() { }

    static Forecast forecast() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        StringBuilder times = new StringBuilder(), temps = new StringBuilder(), codes = new StringBuilder(), rain = new StringBuilder();
        for (int d = 0; d < 2; d++) {
            for (int h = 0; h < 24; h++) {
                String sep = times.length() == 0 ? "" : ",";
                times.append(sep).append(String.format(Locale.US, "\"%sT%02d:00\"", today.plusDays(d), h));
                temps.append(sep).append(Math.round(26 + 6 * Math.sin((h - 9) / 24.0 * 2 * Math.PI)));
                boolean wet = h >= 14 && h <= 19;
                codes.append(sep).append(wet ? 80 : (h < 7 || h > 20 ? 1 : 2));
                rain.append(sep).append(wet ? 60 : (h > 19 ? 20 : 5));
            }
        }
        StringBuilder dates = new StringBuilder();
        for (int i = 0; i < 7; i++) dates.append(i == 0 ? "" : ",").append('"').append(today.plusDays(i)).append('"');
        String json = "{\"utc_offset_seconds\":28800,\"current\":{\"time\":\"" + now.toLocalDate() + "T" + String.format(Locale.US, "%02d:%02d", now.getHour(), now.getMinute())
                + "\",\"temperature_2m\":27.4,\"relative_humidity_2m\":84,\"apparent_temperature\":31.0,\"weather_code\":2,\"wind_speed_10m\":11.0},"
                + "\"hourly\":{\"time\":[" + times + "],\"temperature_2m\":[" + temps + "],\"weather_code\":[" + codes + "],\"precipitation_probability\":[" + rain + "]},"
                + "\"daily\":{\"time\":[" + dates + "],\"weather_code\":[80,2,61,95,2,1,3],\"temperature_2m_max\":[32,33,31,30,33,34,32],"
                + "\"temperature_2m_min\":[24,24,24,23,24,25,24],\"precipitation_probability_max\":[70,30,80,90,20,10,40]}}";
        String air = "{\"current\":{\"us_aqi\":78,\"pm2_5\":24.5,\"pm10\":41.2}}";
        return ForecastParser.parse(json, air, System.currentTimeMillis());
    }
}

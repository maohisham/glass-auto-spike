package com.glass.headunit;

import java.util.Locale;

/** Asks Open-Meteo (free, no key) for the forecast and the air quality at a position. Blocking: call off the main thread. */
final class ForecastFetcher {

    static final class Raw {
        String forecast, air;   // the answers as text; air can be null on its own
    }

    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
            + "&hourly=temperature_2m,weather_code,precipitation_probability"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            + "&forecast_days=7&timezone=auto";
    private static final String AIR = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%s&longitude=%s"
            + "&current=us_aqi,pm2_5,pm10&timezone=auto";

    private ForecastFetcher() { }

    /** Null when the forecast itself could not be fetched. */
    static Raw fetch(double lat, double lon) {
        String la = String.format(Locale.US, "%.4f", lat), lo = String.format(Locale.US, "%.4f", lon);
        Raw r = new Raw();
        r.forecast = Prayer.get(String.format(Locale.US, FORECAST, la, lo));
        if (r.forecast == null) { Spike.log("forecast: request failed"); return null; }
        r.air = Prayer.get(String.format(Locale.US, AIR, la, lo));
        Spike.log("forecast: fetched" + (r.air == null ? " (no air quality)" : " with air quality"));
        return r;
    }
}

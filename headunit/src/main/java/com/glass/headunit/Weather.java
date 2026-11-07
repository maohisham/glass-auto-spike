package com.glass.headunit;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Current weather from Open-Meteo plus a city name, ported from the launcher's WeatherManager. Blocking. */
final class Weather {

    static final class Data {
        float tempC;
        int code; // Open-Meteo weather code, drives the animation
        String emoji = "";
        String desc = "";
        String city = "";
    }

    private static final String API = "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code&latitude=%s&longitude=%s";

    private Weather() { }

    static Data fetch(Context ctx, double lat, double lon) {
        try {
            String body = Prayer.get(String.format(Locale.US, API, String.format(Locale.US, "%.4f", lat), String.format(Locale.US, "%.4f", lon)));
            if (body == null) { Spike.log("weather: request failed"); return null; }
            JSONObject cur = new JSONObject(body).getJSONObject("current");
            Data d = new Data();
            d.tempC = (float) cur.getDouble("temperature_2m");
            int code = cur.getInt("weather_code");
            d.code = code;
            d.emoji = emoji(code);
            d.desc = desc(code);
            d.city = city(ctx, lat, lon);
            Spike.log("weather: " + Math.round(d.tempC) + "°C " + d.desc + " in " + d.city);
            return d;
        } catch (Exception e) { Spike.log("weather: failed " + e.getMessage()); return null; }
    }

    private static String city(Context ctx, double lat, double lon) {
        try {
            if (Geocoder.isPresent()) {
                List<Address> r = new Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (r != null && !r.isEmpty()) {
                    Address a = r.get(0);
                    String n = a.getSubLocality();
                    if (n == null || n.isEmpty()) n = a.getLocality();
                    if (n == null || n.isEmpty()) n = a.getSubAdminArea();
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    static String emoji(int c) {
        if (c == 0) return "☀"; if (c <= 2) return "🌤"; if (c == 3) return "☁"; if (c <= 48) return "🌫";
        if (c <= 55) return "🌦"; if (c <= 65) return "🌧"; if (c <= 67) return "🌨"; if (c <= 77) return "❄";
        if (c <= 82) return "🌦"; if (c <= 86) return "🌨"; if (c <= 99) return "⛈"; return "🌡";
    }

    static String desc(int c) {
        if (c == 0) return "Clear"; if (c <= 2) return "Partly cloudy"; if (c == 3) return "Overcast"; if (c <= 48) return "Foggy";
        if (c <= 55) return "Drizzle"; if (c <= 65) return "Rain"; if (c <= 67) return "Freezing rain"; if (c <= 77) return "Snow";
        if (c <= 82) return "Showers"; if (c <= 86) return "Snow showers"; if (c <= 99) return "Thunderstorm"; return "";
    }
}

package com.glass.headunit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** The last prayer times and weather, kept so the panel has something to show at once at boot. */
final class Cache {

    private Cache() { }

    static String toJson(Prayer.Data d) {
        try {
            JSONObject times = new JSONObject();
            for (java.util.Map.Entry<String, String> e : d.times.entrySet()) times.put(e.getKey(), e.getValue());
            return new JSONObject().put("times", times).put("syuruk", d.syuruk).put("hijri", d.hijri)
                    .put("zoneName", d.zoneName).put("zoneCode", d.zoneCode).put("source", d.source).toString();
        } catch (Exception e) { return "{}"; }
    }

    /** Null when the text is not a saved prayer record. Call Prayer.recompute on the result. */
    static Prayer.Data prayerFromJson(String s) {
        try {
            JSONObject o = new JSONObject(s);
            JSONObject times = o.getJSONObject("times");
            Prayer.Data d = new Prayer.Data();
            for (String k : new String[]{"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"}) {
                if (times.has(k)) d.times.put(k, times.getString(k));
            }
            if (d.times.isEmpty()) return null;
            d.syuruk = o.optString("syuruk", "");
            d.hijri = o.optString("hijri", "");
            d.zoneName = o.optString("zoneName", "");
            d.zoneCode = o.optString("zoneCode", "");
            d.source = o.optString("source", "");
            return d;
        } catch (Exception e) { return null; }
    }

    static String toJson(Weather.Data w) {
        try {
            return new JSONObject().put("tempC", w.tempC).put("code", w.code).put("city", w.city)
                    .put("emoji", w.emoji).put("desc", w.desc).toString();
        } catch (Exception e) { return "{}"; }
    }

    static Weather.Data weatherFromJson(String s) {
        try {
            JSONObject o = new JSONObject(s);
            Weather.Data w = new Weather.Data();
            w.tempC = (float) o.getDouble("tempC");
            w.code = o.optInt("code", 0);
            w.city = o.optString("city", "");
            w.emoji = o.optString("emoji", "");
            w.desc = o.optString("desc", "");
            return w;
        } catch (Exception e) { return null; }
    }

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("cache", Context.MODE_PRIVATE); }

    private static String today() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()); }

    static void savePrayer(Context c, Prayer.Data d) {
        prefs(c).edit().putString("prayer", toJson(d)).putString("prayerDay", today()).apply();
    }

    static Prayer.Data loadPrayer(Context c) {
        String s = prefs(c).getString("prayer", null);
        return s == null ? null : prayerFromJson(s);
    }

    /** True when the saved prayer times were fetched today (yesterday's must not be shown as today's). */
    static boolean prayerIsFromToday(Context c) { return today().equals(prefs(c).getString("prayerDay", "")); }

    static void saveWeather(Context c, Weather.Data w) {
        prefs(c).edit().putString("weather", toJson(w)).putLong("weatherAt", System.currentTimeMillis()).apply();
    }

    static Weather.Data loadWeather(Context c) {
        String s = prefs(c).getString("weather", null);
        return s == null ? null : weatherFromJson(s);
    }

    /** When the saved weather was fetched, ms since the epoch, or 0. */
    static long weatherAt(Context c) { return prefs(c).getLong("weatherAt", 0); }

    /** Saves the forecast and air quality answers as received, so they can be read again after a restart. */
    static void saveForecast(Context c, ForecastFetcher.Raw raw) {
        prefs(c).edit().putString("forecastRaw", raw.forecast).putString("airRaw", raw.air == null ? "" : raw.air).apply();
    }

    /** The saved forecast read as of now (so its hour list starts at the current hour), or null. */
    static Forecast loadForecast(Context c) {
        String f = prefs(c).getString("forecastRaw", null);
        if (f == null) return null;
        String a = prefs(c).getString("airRaw", "");
        return ForecastParser.parse(f, a.isEmpty() ? null : a, System.currentTimeMillis());
    }
}

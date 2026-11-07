package com.glass.headunit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Real prayer times, ported from the launcher's PrayerTimesManager: JAKIM e-Solat for the zone nearest the
 * phone, then the waktusolat.app mirror, then Aladhan with the JAKIM method. Blocking: call off the main thread.
 */
final class Prayer {

    static final class Data {
        final Map<String, String> times = new LinkedHashMap<>(); // Subuh, Zohor, Asar, Maghrib, Isyak
        String syuruk = "";
        String hijri = "";
        String zoneName = "", zoneCode = "";
        String source = "";
        String currentName = "", currentTime = "";
        String nextName = "", nextTime = "";
        /** True for the first 3 minutes after a prayer time: the card says "now" and the alert fires. */
        boolean justStarted;
        long nextMs;
    }

    private static final String ESOLAT = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=%s";
    private static final String ZONE_GPS = "https://api.waktusolat.app/zones/%.4f/%.4f";
    private static final String WAKTU ="https://api.waktusolat.app/v2/solat/%s";
    private static final String ALADHAN = "https://api.aladhan.com/v1/timings/%d?latitude=%s&longitude=%s&method=17";
    private static final String[] HIJRI_MONTHS = {
        "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir", "Jamadil Awwal", "Jamadil Akhir",
        "Rajab", "Sha'ban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"
    };

    private Prayer() { }

    /** Asks waktusolat.app which JAKIM zone a coordinate is in; the centroid guess is only the fallback. */
    private static Zones.Zone zoneFor(double lat, double lon) {
        try {
            String body = get(String.format(Locale.US, ZONE_GPS, lat, lon));
            if (body != null) {
                JSONObject o = new JSONObject(body);
                String code = o.optString("zone", "");
                if (!code.isEmpty()) {
                    Zones.Zone z = Zones.byCode(code);
                    if (z == null) z = new Zones.Zone(code, o.optString("district", code), lat, lon);
                    Spike.log("prayer: zone " + code + " (" + o.optString("district", "") + ") from waktusolat.app");
                    return z;
                }
            }
        } catch (Exception e) { Spike.log("prayer: zone lookup failed " + e.getMessage()); }
        Zones.Zone z = Zones.nearest(lat, lon);
        Spike.log("prayer: zone lookup unavailable, nearest centroid " + z.code);
        return z;
    }

    static Data fetch(double lat, double lon) {
        return fetchFor(zoneFor(lat, lon), lat, lon);
    }

    /** Times for a zone chosen by hand (no GPS lookup). */
    static Data fetchForZone(Zones.Zone zone) {
        return fetchFor(zone, zone.lat, zone.lon);
    }

    private static Data fetchFor(Zones.Zone zone, double lat, double lon) {
        Data d = esolat(zone);
        if (d != null) d.source = "e-Solat";
        if (d == null) { d = waktu(zone); if (d != null) d.source = "waktusolat.app"; }
        if (d == null) { d = aladhan(lat, lon); if (d != null) d.source = "Aladhan (JAKIM method)"; }
        if (d == null) { Spike.log("prayer: every source failed"); return null; }
        d.zoneCode = zone.code;
        d.zoneName = zone.name;
        Spike.log("prayer: " + d.times + " zone " + zone.code + " via " + d.source);
        return recompute(d);
    }

    /** Works out the next prayer and its time from the clock. Cheap: call it every 30 s. */
    static Data recompute(Data d) { return recompute(d, System.currentTimeMillis()); }

    /** Same, as of nowMs (lets the clock be fixed in tests). */
    static Data recompute(Data d, long nowMs) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String[] names = d.times.keySet().toArray(new String[0]);
        String[] times = d.times.values().toArray(new String[0]);
        int[] mins = new int[times.length];
        for (int i = 0; i < times.length; i++) mins[i] = hhmm(times[i]);

        int current = -1;
        for (int i = 0; i < mins.length; i++) if (mins[i] <= nowMins) current = i;
        boolean tomorrow = current == mins.length - 1;
        int next = (current == -1 || tomorrow) ? 0 : current + 1;

        int cur = current == -1 ? mins.length - 1 : current; // before Subuh the current prayer is still last night's Isyak
        d.currentName = names[cur];
        d.currentTime = times[cur];
        d.justStarted = current != -1 && nowMins - mins[current] < 3;
        d.nextName = names[next];
        d.nextTime = times[next];
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        c.set(Calendar.HOUR_OF_DAY, mins[next] / 60);
        c.set(Calendar.MINUTE, mins[next] % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (tomorrow) c.add(Calendar.DAY_OF_YEAR, 1);
        d.nextMs = c.getTimeInMillis();
        if (d.hijri.isEmpty()) d.hijri = computeHijri();
        return d;
    }

    /** "13:10" -> "1:10 PM" */
    static String to12(String hhmm) {
        int m = hhmm(hhmm);
        int h = m / 60, mm = m % 60;
        return String.format(Locale.US, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, mm, h < 12 ? "AM" : "PM");
    }

    /** All times on one line for the collapsed card: "Sub 5:48 · Syu 7:00 · ▸Zoh 1:10 · ...". */
    static String compact(Data d) {
        String[][] names = {{"Subuh", "Sub"}, {"Syuruk", "Syu"}, {"Zohor", "Zoh"}, {"Asar", "Asr"}, {"Maghrib", "Mag"}, {"Isyak", "Isy"}};
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String[] n : names) {
            String t = n[0].equals("Syuruk") ? d.syuruk : d.times.get(n[0]);
            if (t == null || t.isEmpty()) continue;
            parts.add((n[0].equals(d.nextName) ? "▸" : "") + n[1] + " " + to12(t).replace(" AM", "").replace(" PM", ""));
        }
        return android.text.TextUtils.join(" · ", parts);
    }

    /** Collapsed-card top line: the current prayer, the countdown to the next, and the weather. */
    static String collapsed(Data d, Weather.Data w) {
        long mins = Math.max(0, (d.nextMs - System.currentTimeMillis()) / 60000);
        String s = d.justStarted
                ? "● " + d.currentName + " time now  ·  " + to12(d.currentTime)
                : d.currentName + " " + to12(d.currentTime) + "  ·  " + d.nextName + " in " + (mins / 60) + "h " + String.format(Locale.US, "%02d", mins % 60) + "m";
        if (w != null) s += "  ·  " + w.emoji + " " + Math.round(w.tempC) + "°C";
        return s;
    }

    /** "2h 15m" until the next prayer. */
    static String untilNext(Data d) {
        long mins = Math.max(0, (d.nextMs - System.currentTimeMillis()) / 60000);
        return formatMinutes(mins);
    }

    /** "1h 06m" from an hour up, just "6m" under an hour. */
    static String formatMinutes(long mins) {
        if (mins < 60) return mins + "m";
        return (mins / 60) + "h " + String.format(Locale.US, "%02d", mins % 60) + "m";
    }

    /** "Zohor in 0h 33m" */
    static String countdown(Data d) {
        long mins = Math.max(0, (d.nextMs - System.currentTimeMillis()) / 60000);
        return d.nextName + " in " + formatMinutes(mins);
    }

    private static Data esolat(Zones.Zone z) {
        try {
            String body = get(String.format(ESOLAT, z.code));
            if (body == null) return null;
            JSONObject root = new JSONObject(body);
            if (!root.optString("status", "").contains("OK")) return null;
            JSONArray arr = root.getJSONArray("prayerTime");
            if (arr.length() == 0) return null;
            JSONObject t = arr.getJSONObject(0);
            Data d = new Data();
            d.times.put("Subuh", tz(t.optString("fajr", "")));
            d.times.put("Zohor", tz(t.optString("dhuhr", "")));
            d.times.put("Asar", tz(t.optString("asr", "")));
            d.times.put("Maghrib", tz(t.optString("maghrib", "")));
            d.times.put("Isyak", tz(t.optString("isha", "")));
            d.syuruk = tz(t.optString("syuruk", ""));
            String h = t.optString("hijri", "");
            if (!h.isEmpty()) d.hijri = parseHijri(h);
            return valid(d) ? d : null;
        } catch (Exception e) { Spike.log("prayer: e-Solat failed " + e.getMessage()); return null; }
    }

    private static Data waktu(Zones.Zone z) {
        try {
            String body = get(String.format(WAKTU, z.code));
            if (body == null) return null;
            JSONArray arr = new JSONObject(body).optJSONArray("prayer_time");
            if (arr == null || arr.length() == 0) return null;
            int dom = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            JSONObject t = arr.getJSONObject(0);
            for (int i = 0; i < arr.length(); i++) if (arr.getJSONObject(i).optInt("day", -1) == dom) { t = arr.getJSONObject(i); break; }
            Data d = new Data();
            d.times.put("Subuh", tz(t.optString("subuh", "")));
            d.times.put("Zohor", tz(t.optString("zohor", "")));
            d.times.put("Asar", tz(t.optString("asar", "")));
            d.times.put("Maghrib", tz(t.optString("maghrib", "")));
            d.times.put("Isyak", tz(t.optString("isyak", "")));
            d.syuruk = tz(t.optString("syuruk", ""));
            return valid(d) ? d : null;
        } catch (Exception e) { Spike.log("prayer: waktusolat failed " + e.getMessage()); return null; }
    }

    private static Data aladhan(double lat, double lon) {
        try {
            String url = String.format(Locale.US, ALADHAN, System.currentTimeMillis() / 1000,
                    String.format(Locale.US, "%.4f", lat), String.format(Locale.US, "%.4f", lon));
            String body = get(url);
            if (body == null) return null;
            JSONObject tm = new JSONObject(body).getJSONObject("data").getJSONObject("timings");
            Data d = new Data();
            d.times.put("Subuh", tz(tm.optString("Fajr", "")));
            d.times.put("Zohor", tz(tm.optString("Dhuhr", "")));
            d.times.put("Asar", tz(tm.optString("Asr", "")));
            d.times.put("Maghrib", tz(tm.optString("Maghrib", "")));
            d.times.put("Isyak", tz(tm.optString("Isha", "")));
            d.syuruk = tz(tm.optString("Sunrise", ""));
            return valid(d) ? d : null;
        } catch (Exception e) { Spike.log("prayer: Aladhan failed " + e.getMessage()); return null; }
    }

    static String get(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "GlassPrayer/0.1");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) { c.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            c.disconnect();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private static String tz(String s) {
        if (s == null) return "";
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    private static boolean valid(Data d) {
        for (String t : d.times.values()) if (t == null || t.isEmpty() || !t.contains(":")) return false;
        return true;
    }

    static int hhmm(String s) {
        try { String[] p = s.split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); }
        catch (Exception e) { return 0; }
    }

    private static String computeHijri() {
        try {
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            return ic.get(android.icu.util.Calendar.DAY_OF_MONTH) + " " + HIJRI_MONTHS[ic.get(android.icu.util.Calendar.MONTH)]
                    + " " + ic.get(android.icu.util.Calendar.YEAR);
        } catch (Exception e) { return ""; }
    }

    /** JAKIM sends "DD-MonthName-YYYY" or "YYYY-MM-DD". */
    private static String parseHijri(String raw) {
        raw = raw.trim();
        int f = raw.indexOf('-'), l = raw.lastIndexOf('-');
        if (f <= 0 || l <= f) return raw.replace("-", " ").trim();
        String p1 = raw.substring(0, f).trim(), p2 = raw.substring(f + 1, l).trim(), p3 = raw.substring(l + 1).trim();
        try {
            if (p1.length() == 4) {
                int mo = Integer.parseInt(p2);
                if (mo >= 1 && mo <= 12) return Integer.parseInt(p3) + " " + HIJRI_MONTHS[mo - 1] + " " + p1;
            } else {
                int dy = Integer.parseInt(p1);
                try {
                    int mo = Integer.parseInt(p2);
                    if (mo >= 1 && mo <= 12) return dy + " " + HIJRI_MONTHS[mo - 1] + " " + p3;
                } catch (NumberFormatException ignore) { return dy + " " + p2 + " " + p3; }
            }
        } catch (NumberFormatException ignore) { }
        return raw.replace("-", " ").trim();
    }
}

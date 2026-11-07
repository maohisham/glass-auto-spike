package com.glass.aaspike;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics for one track, fetched from LRCLIB (free, no key). Blocking: call off the main thread.
 * Streaming apps label tracks like "Song (Official Video)" or "A, B", which LRCLIB does not match, so the
 * title and artist are cleaned first and the search endpoint is the fallback when the exact lookup misses.
 */
final class Lyrics {

    final List<Long> times = new ArrayList<>();
    final List<String> lines = new ArrayList<>();

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s*(.*)");
    private static final Pattern NOISE = Pattern.compile(
            "\\s*[\\(\\[][^\\)\\]]*(official|video|audio|lyric|lyrics|visuali[sz]er|remaster|live|mv|hd|4k)[^\\)\\]]*[\\)\\]]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEAT = Pattern.compile("\\s*[\\(\\[]?\\s*(feat\\.?|ft\\.?|featuring)\\s[^\\)\\]]*[\\)\\]]?", Pattern.CASE_INSENSITIVE);

    static String cleanTitle(String t) {
        String s = NOISE.matcher(t).replaceAll("");
        s = FEAT.matcher(s).replaceAll("");
        s = s.replaceAll("\\s+-\\s+(official|lyric|video|audio).*$", "");
        return s.trim();
    }

    static String firstArtist(String a) {
        String s = a.split(",|&| x | feat\\.? | ft\\.? ")[0];
        return s.replace(" - Topic", "").trim();
    }

    /** Null when the service has nothing synced for this track. */
    static Lyrics fetch(String title, String artist, long durationMs) {
        String t = cleanTitle(title), a = firstArtist(artist);
        try {
            long durS = durationMs / 1000;
            String body = Prayer.get("https://lrclib.net/api/get?track_name=" + enc(t) + "&artist_name=" + enc(a)
                    + (durS > 0 ? "&duration=" + durS : ""));
            Lyrics out = body == null ? null : parse(new JSONObject(body).optString("syncedLyrics", ""));
            if (out != null) { Spike.log("lyrics: exact match for '" + t + "' (" + out.lines.size() + " lines)"); return out; }

            // search fallbacks: with the artist, then a free-text query
            for (String url : new String[]{
                    "https://lrclib.net/api/search?track_name=" + enc(t) + "&artist_name=" + enc(a),
                    "https://lrclib.net/api/search?q=" + enc(t + " " + a)}) {
                String sb = Prayer.get(url);
                if (sb == null) continue;
                JSONArray arr = new JSONArray(sb);
                JSONObject best = null;
                double bestDiff = Double.MAX_VALUE;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    if (o.optString("syncedLyrics", "").isEmpty()) continue;
                    double diff = durS > 0 ? Math.abs(o.optDouble("duration", 0) - durS) : 0;
                    if (diff < bestDiff) { bestDiff = diff; best = o; }
                }
                if (best != null && (durS <= 0 || bestDiff <= 5)) {
                    out = parse(best.optString("syncedLyrics", ""));
                    if (out != null) {
                        Spike.log("lyrics: search match '" + best.optString("trackName") + "' by " + best.optString("artistName")
                                + " for '" + t + "' (length off by " + Math.round(bestDiff) + " s)");
                        return out;
                    }
                }
            }
            Spike.log("lyrics: nothing synced for '" + t + "' by " + a);
        } catch (Exception e) {
            Spike.log("lyrics: fetch failed for " + t + " (" + e.getClass().getSimpleName() + ")");
        }
        return null;
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static Lyrics parse(String synced) {
        if (synced == null || synced.isEmpty()) return null;
        Lyrics out = new Lyrics();
        for (String raw : synced.split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            out.times.add((long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000));
            out.lines.add(m.group(3));
        }
        return out.lines.isEmpty() ? null : out;
    }

    /** Index of the line being sung at posMs, or -1 before the first line. */
    int indexAt(long posMs) {
        int idx = -1;
        for (int i = 0; i < times.size(); i++) { if (times.get(i) <= posMs) idx = i; else break; }
        return idx;
    }
}

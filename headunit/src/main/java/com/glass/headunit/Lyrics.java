package com.glass.headunit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics for one track, tried in this order: LRCLIB (synced, free, no key), NetEase Cloud Music (synced, unofficial
 * web API), then lyrics.ovh (plain text with no timestamps, so its line times are only an even spread over the
 * track). Blocking: call off the main thread.
 *
 * Streaming apps label tracks like "Song (Official Video)" or "A, B", which the services do not match, so the
 * title and artist are cleaned first.
 */
final class Lyrics {

    final List<Long> times = new ArrayList<>();
    final List<String> lines = new ArrayList<>();
    /** Which service answered; "lyrics.ovh" means the times are estimated, not real. */
    String source = "";

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s*(.*)");
    private static final Pattern NOISE = Pattern.compile(
            "\\s*[\\(\\[][^\\)\\]]*(official|video|audio|lyric|lyrics|visuali[sz]er|remaster|live|mv|hd|4k)[^\\)\\]]*[\\)\\]]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEAT = Pattern.compile("\\s*[\\(\\[]?\\s*(feat\\.?|ft\\.?|featuring)\\s[^\\)\\]]*[\\)\\]]?", Pattern.CASE_INSENSITIVE);
    /** NetEase starts many lyrics with credit lines such as "作词 : Name" or "制作人 : Name"; they are not lyrics. */
    private static final Pattern CREDIT = Pattern.compile("^(作词|作曲|编曲|制作人|制作|混音|录音|母带|监制|出品|发行|词|曲|Lyrics|Lyricist|Composer|Producer|Arranger)\\s*[:：].*", Pattern.CASE_INSENSITIVE);
    /** Section markers in plain lyrics, such as "[Chorus]". */
    private static final Pattern SECTION = Pattern.compile("^\\[[^\\]]*\\]$");

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

    /** Null when no service has lyrics for this track. */
    static Lyrics fetch(String title, String artist, long durationMs) {
        String t = cleanTitle(title), a = firstArtist(artist);
        long durS = durationMs / 1000;
        Lyrics out = lrclib(t, a, durS);
        if (out == null) out = netease(t, a, durS);
        if (out == null) out = ovh(t, a, durationMs);
        if (out == null) Spike.log("lyrics: nothing found for '" + t + "' by " + a + " (LRCLIB, NetEase, lyrics.ovh)");
        return out;
    }

    // ---- LRCLIB ----

    static Lyrics lrclib(String t, String a, long durS) {
        try {
            String body = Prayer.get("https://lrclib.net/api/get?track_name=" + enc(t) + "&artist_name=" + enc(a)
                    + (durS > 0 ? "&duration=" + durS : ""));
            Lyrics out = body == null ? null : parse(new JSONObject(body).optString("syncedLyrics", ""));
            if (out != null) { out.source = "LRCLIB"; Spike.log("lyrics: LRCLIB exact match for '" + t + "' (" + out.lines.size() + " lines)"); return out; }

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
                        out.source = "LRCLIB";
                        Spike.log("lyrics: LRCLIB search match '" + best.optString("trackName") + "' by " + best.optString("artistName")
                                + " for '" + t + "' (length off by " + Math.round(bestDiff) + " s)");
                        return out;
                    }
                }
            }
        } catch (Exception e) {
            Spike.log("lyrics: LRCLIB failed for " + t + " (" + e.getClass().getSimpleName() + ")");
        }
        return null;
    }

    // ---- NetEase Cloud Music ----

    private static final String[] NETEASE_HEADERS = {"Referer", "https://music.163.com", "Cookie", "appver=2.0.2"};

    static Lyrics netease(String t, String a, long durS) {
        try {
            String body = http("https://music.163.com/api/cloudsearch/pc",
                    "s=" + enc(t + " " + a) + "&type=1&offset=0&limit=8", NETEASE_HEADERS);
            if (body == null) return null;
            JSONObject result = new JSONObject(body).optJSONObject("result");
            JSONArray songs = result == null ? null : result.optJSONArray("songs");
            if (songs == null) return null;

            // prefer a song whose artist matches and whose length is closest; with no length, the first artist match
            JSONObject best = null;
            double bestDiff = Double.MAX_VALUE;
            String wantArtist = a.toLowerCase();
            for (int i = 0; i < songs.length(); i++) {
                JSONObject s = songs.getJSONObject(i);
                boolean artistOk = false;
                JSONArray ar = s.optJSONArray("ar");
                for (int k = 0; ar != null && k < ar.length(); k++) {
                    String n = ar.getJSONObject(k).optString("name", "").toLowerCase();
                    if (!n.isEmpty() && (n.contains(wantArtist) || wantArtist.contains(n))) artistOk = true;
                }
                if (!artistOk) continue;
                double diff = durS > 0 ? Math.abs(s.optDouble("dt", 0) / 1000.0 - durS) : 0;
                if (diff < bestDiff) { bestDiff = diff; best = s; }
            }
            if (best == null || (durS > 0 && bestDiff > 6)) return null;

            String lbody = http("https://music.163.com/api/song/lyric?id=" + best.getLong("id") + "&lv=1&kv=1&tv=-1", null, NETEASE_HEADERS);
            if (lbody == null) return null;
            JSONObject lrc = new JSONObject(lbody).optJSONObject("lrc");
            Lyrics out = lrc == null ? null : parse(lrc.optString("lyric", ""));
            if (out == null || out.lines.size() < 4) return null; // a one-line result is the "instrumental" notice
            out.source = "NetEase";
            Spike.log("lyrics: NetEase match '" + best.optString("name") + "' for '" + t + "' (" + out.lines.size()
                    + " lines, length off by " + Math.round(bestDiff) + " s)");
            return out;
        } catch (Exception e) {
            Spike.log("lyrics: NetEase failed for " + t + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
    }

    // ---- lyrics.ovh ----

    /** Plain text only, so each line is given a time by spreading the lines evenly between a short intro and outro. */
    static Lyrics ovh(String t, String a, long durMs) {
        if (durMs <= 0 || a.isEmpty()) return null; // without the length there is nothing to spread over
        try {
            String body = http("https://api.lyrics.ovh/v1/" + encPath(a) + "/" + encPath(t), null);
            if (body == null) return null;
            String text = new JSONObject(body).optString("lyrics", "");
            List<String> raw = new ArrayList<>();
            for (String l : text.split("\\r?\\n")) {
                l = l.trim();
                if (!l.isEmpty() && !SECTION.matcher(l).matches()) raw.add(l);
            }
            if (raw.size() < 4) return null;
            Lyrics out = new Lyrics();
            long intro = Math.min(12_000L, durMs / 12);
            double step = (double) (durMs - 2 * intro) / raw.size();
            for (int i = 0; i < raw.size(); i++) {
                out.times.add(intro + (long) (i * step));
                out.lines.add(raw.get(i));
            }
            out.source = "lyrics.ovh";
            Spike.log("lyrics: lyrics.ovh text for '" + t + "' (" + raw.size() + " lines, times are estimated)");
            return out;
        } catch (Exception e) {
            Spike.log("lyrics: lyrics.ovh failed for " + t + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
    }

    // ---- helpers ----

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    /** A value inside a URL path: spaces must be %20, not +. */
    private static String encPath(String s) throws Exception { return enc(s).replace("+", "%20"); }

    /** GET, or POST when post is not null. Returns the body for a 200 answer, else null. */
    private static String http(String url, String post, String... headers) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
            if (post != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = c.getOutputStream()) { o.write(post.getBytes(StandardCharsets.UTF_8)); }
            }
            if (c.getResponseCode() != 200) { c.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l).append('\n');
            }
            c.disconnect();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private static Lyrics parse(String synced) {
        if (synced == null || synced.isEmpty()) return null;
        Lyrics out = new Lyrics();
        for (String raw : synced.split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            String text = m.group(3).trim();
            if (CREDIT.matcher(text).matches()) continue;
            out.times.add((long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000));
            out.lines.add(text);
        }
        return out.lines.isEmpty() ? null : out;
    }

    /** Index of the line being sung at posMs, or -1 before the first line. */
    int indexAt(long posMs) {
        int idx = -1;
        for (int i = 0; i < times.size(); i++) { if (times.get(i) <= posMs) idx = i; else break; }
        return idx;
    }

    /** Index of the first line at or after from that has text (instrumental gaps are empty lines), or -1. */
    int nextNonEmpty(int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) if (!lines.get(i).isEmpty()) return i;
        return -1;
    }

    /** Index of the last line with text at or before i (the sung line stays lit through an instrumental gap), or -1. */
    int lastNonEmptyAtOrBefore(int i) {
        for (int j = Math.min(i, lines.size() - 1); j >= 0; j--) if (!lines.get(j).isEmpty()) return j;
        return -1;
    }
}

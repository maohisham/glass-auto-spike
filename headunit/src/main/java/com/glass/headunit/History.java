package com.glass.headunit;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What has played through the card: song, artist, app, how often and when. Kept on the phone only. YouTube Music
 * and Spotify refuse to let other apps read their libraries, so this history is the only "recent" and "most
 * played" list the card can offer.
 */
final class History {

    static final class Entry {
        String pkg, title, artist;
        int count;
        long last;

        /** A browse-list media id for this entry. */
        String id() {
            String raw = pkg + "\u0001" + title + "\u0001" + artist;
            return "song:" + Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        }

        static Entry fromId(String id) {
            try {
                String raw = new String(Base64.decode(id.substring(5), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8);
                String[] p = raw.split("\u0001", -1);
                Entry e = new Entry();
                e.pkg = p[0]; e.title = p[1]; e.artist = p[2];
                return e;
            } catch (Exception ex) { return null; }
        }
    }

    private static final int MAX = 200;

    private History() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("history", Context.MODE_PRIVATE); }

    static synchronized List<Entry> all(Context ctx) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.pkg = o.getString("pkg"); e.title = o.getString("title"); e.artist = o.optString("artist", "");
                e.count = o.optInt("count", 1); e.last = o.optLong("last", 0);
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized void record(Context ctx, String pkg, String title, String artist) {
        List<Entry> list = all(ctx);
        Entry hit = null;
        for (Entry e : list) if (e.pkg.equals(pkg) && e.title.equals(title) && e.artist.equals(artist)) { hit = e; break; }
        if (hit == null) {
            hit = new Entry();
            hit.pkg = pkg; hit.title = title; hit.artist = artist;
            list.add(hit);
        }
        hit.count++;
        hit.last = System.currentTimeMillis();
        if (list.size() > MAX) {
            Collections.sort(list, (a, b) -> Long.compare(b.last, a.last));
            list = new ArrayList<>(list.subList(0, MAX));
        }
        try {
            JSONArray a = new JSONArray();
            for (Entry e : list) {
                a.put(new JSONObject().put("pkg", e.pkg).put("title", e.title).put("artist", e.artist)
                        .put("count", e.count).put("last", e.last));
            }
            prefs(ctx).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    static List<Entry> recent(Context ctx, int n) {
        List<Entry> l = all(ctx);
        Collections.sort(l, (a, b) -> Long.compare(b.last, a.last));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    static List<Entry> top(Context ctx, int n) {
        List<Entry> l = all(ctx);
        Collections.sort(l, (a, b) -> a.count != b.count ? Integer.compare(b.count, a.count) : Long.compare(b.last, a.last));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }
}

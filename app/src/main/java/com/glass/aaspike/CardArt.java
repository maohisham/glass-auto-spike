package com.glass.aaspike;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws the single card picture: prayer time countdown, weather, and the day's prayer table.
 * Lyrics are shown via the title/artist text fields in Android Auto.
 */
final class CardArt {

    private static final int BG = 0xFF0F2A1D, GREEN = 0xFF7BE3A6, WHITE = 0xFFFFFFFF, DIM = 0xFFC5D6CB;

    private CardArt() { }

    /**
     * The player's cover as the card picture. Without lyrics it is the cover cropped square with the song and artist
     * across the top. With lyric lines (the narrow card shows a large picture and leaves its top half clear of the
     * car's own text) the cover becomes a dimmed, blurred background and the lines are drawn on it: one before the
     * current line, the current line in bold, and the next few. cur is the index of the current line in lyric, or -1.
     */
    static Bitmap cover(int size, Bitmap src, String song, String artist, List<String> lyric, int cur) {
        src = soft(src);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (lyric == null || lyric.isEmpty()) {
            int w = src.getWidth(), h = src.getHeight(), side = Math.min(w, h);
            Rect from = new Rect((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
            c.drawBitmap(src, from, new Rect(0, 0, size, size), smooth);
            songStrip(c, size, song, artist, false);
            return bmp;
        }
        // a tiny copy stretched back up is a cheap blur
        Bitmap tiny = Bitmap.createScaledBitmap(src, 32, 32, true);
        c.drawBitmap(tiny, null, new Rect(0, 0, size, size), smooth);
        c.drawColor(0xB3000000);
        float y = songStrip(c, size, song, artist, true);
        lyricBlock(c, size, lyric, cur, y + 14 * (size / 640f));
        return bmp;
    }

    /** Shown when the player gives no cover: the card's green, with the lyrics on it when there are some. */
    static Bitmap placeholder(int size, String song, String artist, List<String> lyric, int cur) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(BG);
        if (lyric == null || lyric.isEmpty()) {
            TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(GREEN);
            p.setTextSize(size * 0.4f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("♪", size / 2f, size / 2f + size * 0.14f, p);
            songStrip(c, size, song, artist, false);
            return bmp;
        }
        float y = songStrip(c, size, song, artist, true);
        lyricBlock(c, size, lyric, cur, y + 14 * (size / 640f));
        return bmp;
    }

    /** A hardware bitmap cannot be drawn onto a software canvas, so copy it first. */
    private static Bitmap soft(Bitmap src) {
        if (src.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, false);
            if (copy != null) return copy;
        }
        return src;
    }

    /**
     * Song and artist across the top of the picture. The car draws its back arrow and app icon at the top-left and
     * crops about an eighth off each side in the narrow card, so the text starts below the icons and inside the side
     * crop. Returns the y just below the text. The compact form leaves room for lyric lines under it.
     */
    private static float songStrip(Canvas c, int size, String song, String artist, boolean compact) {
        float s = size / 640f;
        float y = 88 * s;
        if ((song == null || song.isEmpty()) && (artist == null || artist.isEmpty())) return y;
        if (!compact) {
            Paint fade = new Paint();
            fade.setShader(new LinearGradient(0, 0, 0, 210 * s, 0xB8000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, size, 210 * s, fade);
        }
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float x = 96 * s, w = size - 192 * s;
        if (song != null && !song.isEmpty()) {
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setColor(WHITE);
            p.setTextSize((compact ? 28 : 40) * s);
            y = drawWrapped(c, song, p, x, y, w, 1);
        }
        if (artist != null && !artist.isEmpty()) {
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(DIM);
            p.setTextSize((compact ? 22 : 30) * s);
            y = drawWrapped(c, artist, p, x, y + 2 * s, w, 1);
        }
        return y;
    }

    /** Lyric lines from top down to y 340 (below that the car draws its own text). The current line is bold and bright. */
    private static void lyricBlock(Canvas c, int size, List<String> lines, int cur, float top) {
        float s = size / 640f;
        float x = 96 * s, w = 448 * s, bottom = 340 * s, y = top;
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < lines.size(); i++) {
            boolean now = i == cur;
            p.setTypeface(now ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
            p.setTextSize((now ? 27 : 23) * s);
            p.setColor(now ? WHITE : 0xB3FFFFFF);
            String t = lines.get(i);
            StaticLayout l = StaticLayout.Builder.obtain(t, 0, t.length(), p, (int) w)
                    .setMaxLines(now ? 2 : 1).setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            if (y + l.getHeight() > bottom) break;
            c.save();
            c.translate(x, y);
            l.draw(c);
            c.restore();
            y += l.getHeight() + 5 * s;
        }
    }

    /** d and w may be null while the first fetch is running. */
    static Bitmap card(int size, Prayer.Data d, Weather.Data w) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = size / 640f;
        c.drawColor(BG);
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        // Android Auto draws the back arrow and app icon over the top-left corner, and the song text and
        // controls over everything below about 55% of the picture, so all content sits between y 76 and 340.
        float px = 36 * s, pw = size - 72 * s;

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(GREEN);
        p.setTextSize(56 * s);
        c.drawText(d == null ? "Loading…" : (d.justStarted ? d.currentName : d.nextName), px, 128 * s, p);

        p.setTextSize(40 * s);
        p.setColor(WHITE);
        String countdown = d == null ? "" : (d.justStarted ? "time now" : "in " + Prayer.untilNext(d));
        c.drawText(countdown, size - px - p.measureText(countdown), 128 * s, p);

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(26 * s);
        p.setColor(DIM);
        String weatherLine = w == null ? "" : w.emoji + "  " + Math.round(w.tempC) + "°C";
        if (w != null && !w.city.isEmpty()) weatherLine += "   " + w.city;
        if (!weatherLine.isEmpty()) drawWrapped(c, weatherLine, p, px, 142 * s, pw, 1);

        p.setColor(GREEN);
        c.drawRect(px, 184 * s, size - px, 187 * s, p);

        if (d != null) {
            List<String[]> rows = new ArrayList<>();
            boolean syurukDone = d.syuruk.isEmpty();
            for (Map.Entry<String, String> e : d.times.entrySet()) {
                rows.add(new String[]{e.getKey(), e.getValue()});
                if (!syurukDone) { rows.add(new String[]{"Syuruk", d.syuruk}); syurukDone = true; }
            }
            int perCol = (rows.size() + 1) / 2;
            float gap = 32 * s, cw = (pw - gap) / 2;
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                boolean real = !r[0].equals("Syuruk");
                row(c, p, s, px + (i / perCol) * (cw + gap), cw, (234 + (i % perCol) * 48) * s, r[0], r[1],
                        real && r[0].equals(d.nextName), real && r[0].equals(d.currentName));
            }
        }
        return bmp;
    }

    /** One table row: the next prayer in green and bold, the current one marked with a dot. */
    private static void row(Canvas c, Paint p, float s, float x, float w, float y, String name, String time24, boolean next, boolean current) {
        String time = Prayer.to12(time24);
        p.setColor(next ? GREEN : WHITE);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, next ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextSize(27 * s);
        c.drawText((current ? "● " : "") + name, x, y, p);
        c.drawText(time, x + w - p.measureText(time), y, p);
    }

    /** Test picture: a labelled grid, so a photo of the car screen shows what survives cropping, blur and dimming. */
    static Bitmap calibration(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = size / 640f;
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        String cols = "ABCDEFGH";
        for (int r = 0; r < 8; r++) {
            for (int k = 0; k < 8; k++) {
                p.setColor(((r + k) % 2 == 0) ? 0xFF1B3A2A : 0xFF2A1B3A);
                c.drawRect(k * 80 * s, r * 80 * s, (k + 1) * 80 * s, (r + 1) * 80 * s, p);
                p.setColor(WHITE);
                p.setTextSize(24 * s);
                p.setTypeface(Typeface.DEFAULT);
                c.drawText(cols.charAt(k) + "" + (r + 1), (k * 80 + 18) * s, (r * 80 + 48) * s, p);
            }
        }
        // frame, centre cross, inscribed circle
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8 * s);
        p.setColor(0xFFFF4040);
        c.drawRect(4 * s, 4 * s, size - 4 * s, size - 4 * s, p);
        p.setStrokeWidth(3 * s);
        p.setColor(GREEN);
        c.drawLine(size / 2f, 0, size / 2f, size, p);
        c.drawLine(0, size / 2f, size, size / 2f, p);
        c.drawCircle(size / 2f, size / 2f, size / 2f - 8 * s, p);
        // where a 2:1 and a 16:9 centre crop would cut
        p.setColor(0xFFFFD000);
        c.drawRect(0, 160 * s, size, 480 * s, p);
        p.setColor(0xFF40C0FF);
        c.drawRect(0, (320 - 180) * s, size, (320 + 180) * s, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(0xFFFFD000);
        p.setTextSize(30 * s);
        c.drawText("2:1 crop edge", 170 * s, 156 * s, p);
        p.setColor(0xFF40C0FF);
        c.drawText("16:9 crop edge", 170 * s, 136 * s, p);
        // text legibility samples, centre
        p.setColor(WHITE);
        int[] px = {18, 24, 32, 44};
        float y = 270 * s;
        for (int sz : px) { p.setTextSize(sz * s); c.drawText("Text " + sz + "px  Zohor 1:10 PM", 120 * s, y, p); y += (sz + 14) * s; }
        p.setTextSize(26 * s);
        p.setColor(0xFFFF4040);
        c.drawText("TOP", 290 * s, 36 * s, p);
        c.drawText("BOTTOM", 262 * s, (size - 14 * s) / s * s, p);
        return bmp;
    }


    /** Draws wrapped text with its top at y; returns the y below it. */
    private static float drawWrapped(Canvas c, String text, TextPaint p, float x, float y, float width, int maxLines) {
        StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), p, (int) width)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
        return y + l.getHeight();
    }
}

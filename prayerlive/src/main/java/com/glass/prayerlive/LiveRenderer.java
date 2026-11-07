package com.glass.prayerlive;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Draws the prayer screen: weather animation behind, the next prayer with a ticking countdown and progress bar on
 * the left, the day's prayer list on the right. All layout is in a 1220 x 630 design space that is scaled into the
 * area the car leaves free, so it fits whatever size the head unit gives.
 */
final class LiveRenderer {

    private static final float DW = 1220f, DH = 630f;
    private static final int GREEN = 0xFF19E3A5, WHITE = 0xFFFFFFFF, DIM = 0x99FFFFFF, FAINT = 0x66FFFFFF;

    private enum Sky { CLEAR, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM }

    // data, set from the main thread and read by the render thread
    private volatile Prayer.Data prayer;
    private volatile Weather.Data weather;
    private volatile String note = "Loading…";

    // animation state, touched only by the render thread
    private final Random rnd = new Random(7);
    private final int N = 220;
    private final float[] px = new float[N], py = new float[N], plen = new float[N], pspd = new float[N], pa = new float[N];
    private final float[] cx = new float[6], cy = new float[6], cr = new float[6], cspd = new float[6];
    private final float[] sx = new float[70], sy = new float[70], sph = new float[70];
    private float flash;
    private long lastMs;
    private float clock; // seconds since start, drives drifting and twinkling

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rr = new RectF();
    private final Paint unitGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader bgShader;
    private int bgKey;

    LiveRenderer() {
        for (int i = 0; i < N; i++) {
            px[i] = rnd.nextFloat();
            py[i] = rnd.nextFloat();
            plen[i] = 0.02f + rnd.nextFloat() * 0.04f;
            pspd[i] = 0.9f + rnd.nextFloat() * 0.9f;
            pa[i] = 0.25f + rnd.nextFloat() * 0.45f;
        }
        for (int i = 0; i < cx.length; i++) {
            cx[i] = rnd.nextFloat() * 1.4f - 0.2f;
            cy[i] = 0.05f + rnd.nextFloat() * 0.5f;
            cr[i] = 0.18f + rnd.nextFloat() * 0.18f;
            cspd[i] = 0.004f + rnd.nextFloat() * 0.01f;
        }
        for (int i = 0; i < sx.length; i++) { sx[i] = rnd.nextFloat(); sy[i] = rnd.nextFloat() * 0.6f; sph[i] = rnd.nextFloat() * 6.28f; }
        // a soft round glow of radius 1, scaled and moved with the canvas wherever it is needed
        unitGlow.setShader(new RadialGradient(0, 0, 1, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    void setData(Prayer.Data pr, Weather.Data w, String message) {
        if (pr != null) prayer = pr;
        if (w != null) weather = w;
        note = message;
    }

    // ---- one frame ----

    void draw(Canvas c, int w, int h, Rect area, long nowMs) {
        float dt = lastMs == 0 ? 0.033f : Math.min(0.1f, (nowMs - lastMs) / 1000f);
        lastMs = nowMs;
        clock += dt;

        Prayer.Data d = prayer;
        Weather.Data we = weather;
        if (d != null && nowMs >= d.nextMs) Prayer.recompute(d);
        Sky sky = skyFor(we);
        boolean night = isNight(d, nowMs);

        background(c, w, h, sky, night);
        weatherLayer(c, w, h, sky, night, dt);

        // dim the left side so the text stays readable over bright weather
        p.setShader(new LinearGradient(0, 0, w * 0.65f, 0, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        Rect a = (area == null || area.width() < 200 || area.height() < 150) ? new Rect(0, 0, w, h) : area;
        float s = Math.min(a.width() / DW, a.height() / DH);
        c.save();
        c.translate(a.left + (a.width() - DW * s) / 2f, a.top + (a.height() - DH * s) / 2f);
        c.scale(s, s);
        if (d == null) message(c);
        else content(c, d, we, nowMs);
        c.restore();
    }

    // ---- background and weather ----

    private Sky skyFor(Weather.Data w) {
        if (w == null) return Sky.CLEAR;
        int k = w.code;
        if (k == 0 || k == 1) return Sky.CLEAR;
        if (k == 2 || k == 3) return Sky.CLOUD;
        if (k == 45 || k == 48) return Sky.FOG;
        if (k >= 51 && k <= 57) return Sky.DRIZZLE;
        if ((k >= 61 && k <= 65) || (k >= 80 && k <= 82)) return Sky.RAIN;
        if ((k >= 66 && k <= 67)) return Sky.RAIN;
        if ((k >= 71 && k <= 77) || (k >= 85 && k <= 86)) return Sky.SNOW;
        if (k >= 95) return Sky.STORM;
        return Sky.CLOUD;
    }

    /** Night is before sunrise (Syuruk) or from Maghrib on; without prayer data it falls back to the clock. */
    private boolean isNight(Prayer.Data d, long nowMs) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (d != null && !d.syuruk.isEmpty() && d.times.containsKey("Maghrib")) {
            return mins < hhmm(d.syuruk) || mins >= hhmm(d.times.get("Maghrib"));
        }
        return mins < 7 * 60 || mins >= 19 * 60;
    }

    private void background(Canvas c, int w, int h, Sky sky, boolean night) {
        int top, bottom;
        if (night) { top = 0xFF0A1226; bottom = 0xFF04070E; }
        else switch (sky) {
            case CLEAR: top = 0xFF16455A; bottom = 0xFF0A1C26; break;
            case RAIN: case DRIZZLE: case STORM: top = 0xFF1C2A30; bottom = 0xFF0A1013; break;
            default: top = 0xFF1B3440; bottom = 0xFF0B161B;
        }
        int key = (h * 31 + top) * 31 + bottom;
        if (bgShader == null || key != bgKey) { bgShader = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP); bgKey = key; }
        p.setShader(bgShader);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void weatherLayer(Canvas c, int w, int h, Sky sky, boolean night, float dt) {
        if (sky == Sky.CLEAR) {
            if (night) stars(c, w, h);
            else glow(c, w * 0.82f, h * 0.14f, Math.min(w, h) * 0.55f, 0x66FFD58A);
        }
        if (sky != Sky.CLEAR) clouds(c, w, h, sky == Sky.CLOUD || sky == Sky.FOG ? 0x30DDE6EE : 0x40667788, dt, sky == Sky.FOG);
        if (sky == Sky.DRIZZLE) rain(c, w, h, 90, 0.55f, 1.6f, dt);
        if (sky == Sky.RAIN) rain(c, w, h, 170, 1.0f, 2.0f, dt);
        if (sky == Sky.STORM) {
            rain(c, w, h, 220, 1.2f, 2.4f, dt);
            if (flash <= 0.01f && rnd.nextFloat() < 0.006f) flash = 1f;
            if (flash > 0f) { p.setColor(0xFFFFFFFF); p.setAlpha((int) (flash * 70)); c.drawRect(0, 0, w, h, p); flash *= 0.86f; }
        }
        if (sky == Sky.SNOW) snow(c, w, h, dt);
    }

    private void glow(Canvas c, float x, float y, float r, int color) {
        unitGlow.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        c.drawCircle(0, 0, 1, unitGlow);
        c.restore();
    }

    private void clouds(Canvas c, int w, int h, int color, float dt, boolean wide) {
        for (int i = 0; i < cx.length; i++) {
            cx[i] += cspd[i] * dt;
            if (cx[i] > 1.35f) cx[i] = -0.35f;
            unitGlow.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
            float r = Math.max(w, h) * cr[i];
            c.save();
            c.translate(cx[i] * w, cy[i] * h);
            c.scale(wide ? r * 1.8f : r * 1.3f, wide ? r * 0.55f : r * 0.7f);
            c.drawCircle(0, 0, 1, unitGlow);
            c.restore();
        }
    }

    private void rain(Canvas c, int w, int h, int count, float speedScale, float slantScale, float dt) {
        float stroke = Math.max(1.5f, h / 360f);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        float slant = 0.12f * slantScale / 2f;
        for (int i = 0; i < Math.min(count, N); i++) {
            py[i] += pspd[i] * speedScale * dt;
            px[i] -= slant * pspd[i] * speedScale * dt * 0.5f;
            if (py[i] > 1.05f) { py[i] = -0.05f; px[i] = rnd.nextFloat() * 1.2f; }
            if (px[i] < -0.1f) px[i] += 1.2f;
            float len = plen[i] * h;
            float x = px[i] * w, y = py[i] * h;
            p.setColor(0xFFCFE4F2);
            p.setAlpha((int) (pa[i] * 255));
            c.drawLine(x, y, x + slant * len * 3f, y - len, p);
        }
        p.setAlpha(255);
    }

    private void snow(Canvas c, int w, int h, float dt) {
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 120; i++) {
            py[i] += (0.05f + pspd[i] * 0.08f) * dt;
            if (py[i] > 1.05f) { py[i] = -0.05f; px[i] = rnd.nextFloat(); }
            float x = (px[i] + 0.02f * (float) Math.sin(clock + i)) * w;
            p.setAlpha((int) (pa[i] * 255));
            c.drawCircle(x, py[i] * h, Math.max(1.5f, h / 300f) * (0.6f + plen[i] * 20f), p);
        }
        p.setAlpha(255);
    }

    private void stars(Canvas c, int w, int h) {
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < sx.length; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(clock * 1.5f + sph[i]);
            p.setAlpha((int) (60 + tw * 160));
            c.drawCircle(sx[i] * w, sy[i] * h, Math.max(1.2f, h / 400f), p);
        }
        p.setAlpha(255);
    }

    // ---- content, in the 1220 x 630 design space ----

    private void message(Canvas c) {
        text(c, note, 66, 300, 34, WHITE, true, Paint.Align.LEFT);
    }

    private void content(Canvas c, Prayer.Data d, Weather.Data we, long nowMs) {
        // weather pill, top right
        if (we != null) {
            String t = we.emoji + " " + Math.round(we.tempC) + "°  " + we.desc;
            float tw = measure(t, 20, false) + 36;
            rr.set(1130 - tw, 24, 1130, 62);
            fill(c, rr, 19, 0x66000000);
            text(c, t, 1130 - tw + 18, 50, 20, WHITE, false, Paint.Align.LEFT);
        }

        // header: city, date, Hijri
        float x = 66;
        String city = we != null && !we.city.isEmpty() ? we.city : d.zoneCode;
        x = textRun(c, city, x, 170, 18, WHITE, true) + 22;
        x = textRun(c, new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(new Date(nowMs)), x, 170, 17, DIM, false) + 22;
        if (!d.hijri.isEmpty()) textRun(c, d.hijri + " H", x, 170, 17, DIM, false);

        // next prayer
        p.setLetterSpacing(0.14f);
        text(c, d.justStarted ? "PRAYER TIME NOW" : "NEXT PRAYER", 66, 214, 17, GREEN, true, Paint.Align.LEFT);
        p.setLetterSpacing(0f);
        String name = d.justStarted ? d.currentName : d.nextName;
        String at = d.justStarted ? d.currentTime : d.nextTime;
        text(c, name, 62, 296, 92, WHITE, true, Paint.Align.LEFT);
        float ax = textRun(c, "at ", 66, 336, 21, DIM, false);
        textRun(c, Prayer.to12(at), ax, 336, 21, WHITE, true);

        // countdown with small units
        long left = Math.max(0, (d.nextMs - nowMs) / 1000);
        long hh = left / 3600, mm = (left % 3600) / 60, ss = left % 60;
        float cxp = 66;
        if (hh > 0) { cxp = textRun(c, String.valueOf(hh), cxp, 420, 72, WHITE, true); cxp = textRun(c, "h", cxp + 4, 420, 26, GREEN, true) + 14; }
        cxp = textRun(c, String.format(Locale.US, "%02d", mm), cxp, 420, 72, WHITE, true);
        cxp = textRun(c, "m", cxp + 4, 420, 26, GREEN, true) + 14;
        cxp = textRun(c, String.format(Locale.US, "%02d", ss), cxp, 420, 72, WHITE, true);
        textRun(c, "s", cxp + 4, 420, 26, GREEN, true);

        // progress between the last prayer and the next
        long prev = prevMs(d, nowMs);
        float frac = d.nextMs > prev ? Math.max(0f, Math.min(1f, (nowMs - prev) / (float) (d.nextMs - prev))) : 0f;
        rr.set(66, 452, 482, 460);
        fill(c, rr, 4, 0x33FFFFFF);
        rr.set(66, 452, 66 + 416 * frac, 460);
        if (rr.width() > 8) fill(c, rr, 4, GREEN);
        text(c, "Since " + d.currentName + "  ·  " + Math.round(frac * 100) + "% → " + d.nextName, 66, 490, 15, DIM, false, Paint.Align.LEFT);

        list(c, d, nowMs);
    }

    private void list(Canvas c, Prayer.Data d, long nowMs) {
        float px0 = 720, py0 = 112, pw = 445, ph = 406;
        rr.set(px0, py0, px0 + pw, py0 + ph);
        fill(c, rr, 26, 0xB30B1514);
        stroke(c, rr, 26, 0x22FFFFFF, 1.5f);

        List<String[]> rows = new ArrayList<>();
        boolean syurukDone = d.syuruk.isEmpty();
        for (Map.Entry<String, String> e : d.times.entrySet()) {
            rows.add(new String[]{e.getKey(), e.getValue()});
            if (!syurukDone) { rows.add(new String[]{"Syuruk", d.syuruk}); syurukDone = true; }
        }
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        float rowH = 60, top = py0 + 18;
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            boolean prayerRow = !r[0].equals("Syuruk");
            boolean isNow = prayerRow && r[0].equals(d.currentName);
            boolean isNext = prayerRow && r[0].equals(d.nextName) && !isNow;
            boolean passed = hhmm(r[1]) <= nowMins;
            float y = top + i * rowH, mid = y + rowH / 2f;

            if (isNow || isNext) {
                rr.set(px0 + 14, y + 4, px0 + pw - 14, y + rowH - 4);
                fill(c, rr, 16, isNow ? 0x2619E3A5 : 0x14FFFFFF);
                stroke(c, rr, 16, isNow ? GREEN : 0x33FFFFFF, 2f);
            }
            int color = (passed && !isNow) ? FAINT : WHITE;
            text(c, r[0], px0 + 34, mid + 8, 23, color, isNow || isNext, Paint.Align.LEFT);

            // time as "1:01" with a small PM, right-aligned
            String t12 = Prayer.to12(r[1]);
            int sp = t12.indexOf(' ');
            String clock = t12.substring(0, sp), ampm = t12.substring(sp + 1);
            float right = px0 + pw - 34;
            float aw = measure(ampm, 12, false);
            text(c, ampm, right, mid + 8, 12, color == WHITE ? DIM : FAINT, false, Paint.Align.RIGHT);
            text(c, clock, right - aw - 6, mid + 9, 26, color, true, Paint.Align.RIGHT);

            if (isNow || isNext) {
                String badge = isNow ? "NOW" : "NEXT";
                float bw = 62;
                float bx = right - aw - 6 - measure(clock, 26, true) - 18 - bw;
                rr.set(bx, mid - 12, bx + bw, mid + 12);
                fill(c, rr, 12, isNow ? GREEN : 0x40FFFFFF);
                p.setLetterSpacing(0.08f);
                text(c, badge, bx + bw / 2f, mid + 5, 13, isNow ? 0xFF05281D : WHITE, true, Paint.Align.CENTER);
                p.setLetterSpacing(0f);
            }
        }
    }

    // ---- helpers ----

    private long prevMs(Prayer.Data d, long nowMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        int m = hhmm(d.currentTime);
        c.set(Calendar.HOUR_OF_DAY, m / 60);
        c.set(Calendar.MINUTE, m % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > nowMs) c.add(Calendar.DAY_OF_YEAR, -1);
        return c.getTimeInMillis();
    }

    private static int hhmm(String s) {
        try { String[] q = s.split(":"); return Integer.parseInt(q[0]) * 60 + Integer.parseInt(q[1]); }
        catch (Exception e) { return 0; }
    }

    private void fill(Canvas c, RectF r, float radius, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(r, radius, radius, p);
    }

    private void stroke(Canvas c, RectF r, float radius, int color, float width) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void style(float size, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
    }

    private float measure(String t, float size, boolean bold) {
        style(size, bold);
        return p.measureText(t);
    }

    private void text(Canvas c, String t, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        style(size, bold);
        p.setColor(color);
        p.setTextAlign(align);
        c.drawText(t, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    /** Draws left-aligned text and returns the x where it ended, so the next piece can follow it. */
    private float textRun(Canvas c, String t, float x, float y, float size, int color, boolean bold) {
        text(c, t, x, y, size, color, bold, Paint.Align.LEFT);
        return x + measure(t, size, bold);
    }
}

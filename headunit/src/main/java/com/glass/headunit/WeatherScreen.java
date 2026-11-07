package com.glass.headunit;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** The weather in detail, opened by tapping the weather part of the top strip: now, air quality, the next 24 hours and the next 7 days. */
final class WeatherScreen extends LinearLayout {

    private static final int RAIN_BLUE = 0xFF6FB6FF;

    private final boolean dark;
    private final int textSize;
    private final PanelMode mode;
    private final String city;
    private final LinearLayout body;

    WeatherScreen(Context ctx, boolean dark, int textSize, PanelMode mode, String city, Forecast forecast, Runnable onClose) {
        super(ctx);
        this.dark = dark;
        this.textSize = textSize;
        this.mode = mode;
        this.city = city == null ? "" : city;
        setOrientation(VERTICAL);
        setBackground(new GlassBackground(dark));
        setClickable(true); // swallow touches so the panel below is not pressed through it
        int pad = Glass.dp(ctx, mode == PanelMode.COMPACT ? 14 : 20);
        setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new IconView(ctx, Icon.BACK, "Back", dark, 44, IconView.Style.NORMAL, onClose));
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(VERTICAL);
        words.addView(text("Weather", 20, true, PanelStyle.fg(dark)));
        if (!this.city.isEmpty()) words.addView(text(this.city, 13, false, PanelStyle.dim(dark)));
        LayoutParams wp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Glass.dp(ctx, 12);
        head.addView(words, wp);
        addView(head);

        body = new LinearLayout(ctx);
        body.setOrientation(VERTICAL);
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        setForecast(forecast);
    }

    private TextView text(String s, float baseSp, boolean bold, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, PanelStyle.sp(baseSp, textSize, mode));
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setTextColor(color);
        return t;
    }

    private LayoutParams gap(int topDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Glass.dp(getContext(), topDp);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(VERTICAL);
        int p = Glass.dp(getContext(), 14);
        c.setPadding(p, p, p, p);
        c.setBackground(Glass.button(getContext(), dark, false, false, 18));
        return c;
    }

    /** Rebuilds the screen for a new forecast (it can arrive while the screen is open). */
    void setForecast(Forecast f) {
        Context ctx = getContext();
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark);
        body.removeAllViews();
        if (f == null) {
            body.addView(text("Loading the forecast…", 17, true, fg), gap(18));
            body.addView(text("It needs the unit's internet connection and a location.", 14, false, dim), gap(6));
            return;
        }

        // now
        LinearLayout now = card();
        LinearLayout nowRow = new LinearLayout(ctx);
        nowRow.setOrientation(HORIZONTAL);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowRow.addView(text(Weather.emoji(f.code), 44, false, fg));
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Glass.dp(ctx, 10);
        nowRow.addView(text(Math.round(f.tempC) + "°", 52, true, fg), tp);
        LinearLayout side = new LinearLayout(ctx);
        side.setOrientation(VERTICAL);
        side.addView(text(Weather.desc(f.code), 16, true, fg));
        side.addView(text("Feels like " + Math.round(f.feelsC) + "°", 13, false, dim));
        side.addView(text("Humidity " + f.humidity + "%  ·  Wind " + Math.round(f.windKmh) + " km/h", 13, false, dim));
        LayoutParams sp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = Glass.dp(ctx, 14);
        nowRow.addView(side, sp);
        now.addView(nowRow);
        body.addView(now, gap(12));

        // air quality
        LinearLayout air = card();
        air.addView(text("Air quality", 13, false, dim));
        if (f.air.valid) {
            Aqi.Category cat = Aqi.category(f.air.usAqi);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.BOTTOM);
            row.addView(text(String.valueOf(f.air.usAqi), 40, true, cat.color));
            LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            cp.leftMargin = Glass.dp(ctx, 10);
            cp.bottomMargin = Glass.dp(ctx, 6);
            row.addView(text("US AQI  ·  " + cat.name, 15, true, fg), cp);
            air.addView(row);
            AqiBarView bar = new AqiBarView(ctx);
            bar.setAqi(f.air.usAqi);
            air.addView(bar, gap(2));
            air.addView(text(String.format(Locale.US, "PM2.5  %.0f µg/m³   ·   PM10  %.0f µg/m³", f.air.pm25, f.air.pm10), 13, false, dim), gap(6));
            air.addView(text(cat.advice, 13, false, dim), gap(6));
        } else {
            air.addView(text("Air quality is not available for this place right now.", 14, false, dim), gap(4));
        }
        body.addView(air, gap(12));

        // next 24 hours
        body.addView(text("Next 24 hours", 16, true, fg), gap(18));
        LinearLayout hours = new LinearLayout(ctx);
        hours.setOrientation(HORIZONTAL);
        for (Forecast.Hour h : f.hours) {
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = Glass.dp(ctx, 8);
            cell.setPadding(p, p, p, p);
            cell.setBackground(Glass.button(ctx, dark, false, false, 16));
            cell.addView(text(h.label, 12, false, dim));
            cell.addView(text(Weather.emoji(h.code), 22, false, fg));
            cell.addView(text(Math.round(h.tempC) + "°", 15, true, fg));
            cell.addView(text(h.rainPct > 0 ? h.rainPct + "%" : " ", 11, false, RAIN_BLUE));
            LayoutParams lp = new LayoutParams(Glass.dp(ctx, 64), LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Glass.dp(ctx, 8);
            hours.addView(cell, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(hours);
        body.addView(hs, gap(8));

        // next 7 days
        body.addView(text("Next 7 days", 16, true, fg), gap(18));
        float weekMin = Float.MAX_VALUE, weekMax = -Float.MAX_VALUE;
        for (Forecast.Day d : f.days) { weekMin = Math.min(weekMin, d.loC); weekMax = Math.max(weekMax, d.hiC); }
        LinearLayout days = card();
        for (int i = 0; i < f.days.size(); i++) {
            Forecast.Day d = f.days.get(i);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(d.name, 14, i == 0, fg), new LayoutParams(Glass.dp(ctx, 52), LayoutParams.WRAP_CONTENT));
            row.addView(text(Weather.emoji(d.code), 18, false, fg), new LayoutParams(Glass.dp(ctx, 32), LayoutParams.WRAP_CONTENT));
            TextView rain = text(d.rainPct > 0 ? d.rainPct + "%" : "", 12, false, RAIN_BLUE);
            row.addView(rain, new LayoutParams(Glass.dp(ctx, 38), LayoutParams.WRAP_CONTENT));
            row.addView(text(Math.round(d.loC) + "°", 14, false, dim), new LayoutParams(Glass.dp(ctx, 34), LayoutParams.WRAP_CONTENT));
            RangeBarView range = new RangeBarView(ctx, dark);
            range.setRange(Forecast.rangeFractions(d.loC, d.hiC, weekMin, weekMax));
            row.addView(range, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            TextView hi = text(Math.round(d.hiC) + "°", 14, true, fg);
            hi.setGravity(Gravity.END);
            LayoutParams hp = new LayoutParams(Glass.dp(ctx, 38), LayoutParams.WRAP_CONTENT);
            hp.leftMargin = Glass.dp(ctx, 6);
            row.addView(hi, hp);
            days.addView(row, i == 0 ? gap(0) : gap(10));
        }
        body.addView(days, gap(8));
    }
}

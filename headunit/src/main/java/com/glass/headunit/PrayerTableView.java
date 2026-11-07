package com.glass.headunit;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * All prayer times as a list: the current prayer lit with a NOW tag, Syuruk dim, and the next prayer with its countdown
 * and a bar that fills from the current prayer's start to the next one. The rows are rebuilt only when the current or
 * next prayer changes; every second only the countdown text and the bar move.
 */
final class PrayerTableView extends LinearLayout {

    private final boolean dark;
    private final int textSize;
    private final PanelMode mode;
    private String structure = "";
    private TextView countdown, percent;
    private BarView bar;

    PrayerTableView(Context ctx, boolean dark, int textSize, PanelMode mode) {
        super(ctx);
        this.dark = dark;
        this.textSize = textSize;
        this.mode = mode;
        setOrientation(VERTICAL);
    }

    /** Call about once a second. Recomputes the current and next prayer for nowMs, so d is updated in place. */
    void bind(Prayer.Data d, long nowMs) {
        Prayer.recompute(d, nowMs);
        List<PrayerRows.Row> rows = PrayerRows.build(d, nowMs);
        StringBuilder key = new StringBuilder(d.currentName).append('|').append(d.nextName);
        for (PrayerRows.Row r : rows) key.append('|').append(r.name).append(r.time12).append(r.kind);
        if (!key.toString().equals(structure)) {
            structure = key.toString();
            rebuild(rows, d);
        }
        if (bar != null) {
            float f = PrayerProgress.fraction(d, nowMs);
            bar.setFraction(f);
            countdown.setText("in " + Prayer.untilNext(d));
            percent.setText(Math.round(f * 100) + "%");
        }
    }

    private float sp(float base) { return PanelStyle.sp(base, textSize, mode); }

    private TextView text(String s, float baseSp, boolean bold, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(baseSp));
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setTextColor(color);
        t.setSingleLine();
        return t;
    }

    private void rebuild(List<PrayerRows.Row> rows, Prayer.Data d) {
        removeAllViews();
        bar = null;
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark), accent = PanelStyle.accent(dark);
        int padH = Glass.dp(getContext(), 14), padV = Glass.dp(getContext(), 9), gap = Glass.dp(getContext(), 6);
        for (PrayerRows.Row r : rows) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            boolean sunrise = r.kind == PrayerRows.Kind.SUNRISE;
            row.setPadding(padH, sunrise ? padV / 2 : padV, padH, sunrise ? padV / 2 : padV + (r.kind == PrayerRows.Kind.NEXT ? padV / 3 : 0));
            int color = r.kind == PrayerRows.Kind.PAST || sunrise ? dim : fg;

            LinearLayout head = new LinearLayout(getContext());
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(sunrise ? "Syuruk · sunrise" : r.name, sunrise ? 14 : 17, !sunrise, color);
            if (sunrise) name.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            head.addView(name);
            if (r.kind == PrayerRows.Kind.CURRENT) {
                TextView tag = text("NOW", 11, true, 0xFF04130E);
                tag.setLetterSpacing(0.1f);
                tag.setPadding(Glass.dp(getContext(), 8), Glass.dp(getContext(), 2), Glass.dp(getContext(), 8), Glass.dp(getContext(), 2));
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(Glass.dp(getContext(), 20));
                pill.setColor(accent);
                tag.setBackground(pill);
                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Glass.dp(getContext(), 10);
                head.addView(tag, lp);
            } else if (r.kind == PrayerRows.Kind.NEXT) {
                TextView tag = text("NEXT", 11, true, dim);
                tag.setLetterSpacing(0.1f);
                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Glass.dp(getContext(), 10);
                head.addView(tag, lp);
            }
            View spacer = new View(getContext());
            head.addView(spacer, new LayoutParams(0, 1, 1f));
            head.addView(text(r.time12, sunrise ? 14 : 17, !sunrise, color));
            row.addView(head);

            if (r.kind == PrayerRows.Kind.CURRENT) row.setBackground(Glass.button(getContext(), dark, false, true, 14));
            if (r.kind == PrayerRows.Kind.NEXT) {
                row.setBackground(Glass.button(getContext(), dark, false, false, 14));
                LinearLayout cd = new LinearLayout(getContext());
                cd.setOrientation(HORIZONTAL);
                cd.setPadding(0, Glass.dp(getContext(), 8), 0, Glass.dp(getContext(), 4));
                countdown = text("", 15, true, accent);
                cd.addView(countdown);
                View sp2 = new View(getContext());
                cd.addView(sp2, new LayoutParams(0, 1, 1f));
                percent = text("", 12, false, dim);
                cd.addView(percent);
                row.addView(cd);
                bar = new BarView(getContext(), dark, false);
                row.addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                LinearLayout ends = new LinearLayout(getContext());
                ends.setOrientation(HORIZONTAL);
                ends.addView(text(d.currentName + " " + Prayer.to12(d.currentTime), 12, false, dim));
                View sp3 = new View(getContext());
                ends.addView(sp3, new LayoutParams(0, 1, 1f));
                ends.addView(text(d.nextName + " " + Prayer.to12(d.nextTime), 12, false, dim));
                row.addView(ends);
            }
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = gap;
            addView(row, lp);
        }
    }
}

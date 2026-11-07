package com.glass.headunit;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** The driving score in detail, opened by tapping the score chip: the score, the trip so far, a bar for each part of the score, a tip, and Reset trip. */
final class DriveScreen extends LinearLayout {

    private final boolean dark;
    private final int textSize;
    private final PanelMode mode;
    private final LinearLayout body;

    DriveScreen(Context ctx, boolean dark, int textSize, PanelMode mode, Runnable onClose) {
        super(ctx);
        this.dark = dark;
        this.textSize = textSize;
        this.mode = mode;
        setOrientation(VERTICAL);
        setBackground(new GlassBackground(dark));
        setClickable(true); // swallow touches so the panel below is not pressed through it
        int pad = Glass.dp(ctx, mode == PanelMode.COMPACT ? 14 : 20);
        setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new IconView(ctx, Icon.BACK, "Back", dark, 44, IconView.Style.NORMAL, onClose));
        TextView title = text("Driving score", 20, true, PanelStyle.fg(dark));
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Glass.dp(ctx, 12);
        head.addView(title, tp);
        addView(head);

        body = new LinearLayout(ctx);
        body.setOrientation(VERTICAL);
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        refresh();
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

    /** Rebuilds the contents from the current trip. Call when opened, after a reset, and now and then while open. */
    void refresh() {
        Context ctx = getContext();
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark);
        EcoScore trip = DriveTrip.trip();
        body.removeAllViews();
        if (!trip.isTripActive()) {
            body.addView(text("--", 44, true, dim), gap(16));
            body.addView(text("Drive to start scoring", 17, true, fg), gap(4));
            body.addView(text("A trip starts when the car passes 5 km/h and the GPS has a fix. The score updates every few seconds.", 14, false, dim), gap(8));
            return;
        }
        EcoScore.Stats s = trip.calculate(System.currentTimeMillis());
        int color = PanelStyle.scoreColor(s.score);

        LinearLayout big = new LinearLayout(ctx);
        big.setOrientation(HORIZONTAL);
        big.setGravity(Gravity.BOTTOM);
        big.addView(text(String.valueOf(s.score), 56, true, color));
        LayoutParams gp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        gp.leftMargin = Glass.dp(ctx, 10);
        gp.bottomMargin = Glass.dp(ctx, 8);
        big.addView(text("/ 100  " + s.grade, 17, true, fg), gp);
        body.addView(big, gap(10));
        body.addView(text(s.grade + " driving efficiency", 14, false, dim), gap(0));

        body.addView(text("Trip", 16, true, fg), gap(18));
        body.addView(row("Distance", String.format(Locale.US, "%.1f km", s.distanceKm)), gap(6));
        body.addView(row("Duration", EcoScore.duration(s.durationSec)), gap(6));
        body.addView(row("Idle", EcoScore.duration(s.idleSec) + String.format(Locale.US, "  (%.0f%%)", s.idlePct)), gap(6));
        body.addView(row("Average speed", String.format(Locale.US, "%.0f km/h", s.avgSpeedKmh)), gap(6));
        body.addView(row("Top speed", String.format(Locale.US, "%.0f km/h", s.maxSpeedKmh)), gap(6));

        body.addView(text("Score breakdown", 16, true, fg), gap(18));
        body.addView(bar("Speed zone  35%", s.zoneScore, 0xFF66DD88), gap(8));
        body.addView(bar("Smoothness  25%", s.smoothScore, 0xFF66AAFF), gap(8));
        body.addView(bar("Average speed  20%", s.effScore, 0xFFFFCC44), gap(8));
        body.addView(bar("Idling  15%", s.idleScore, 0xFFFF9944), gap(8));
        body.addView(bar("Speed cap  5%", s.bonusScore, 0xFFCC88FF), gap(8));

        String tip = EcoScore.tip(s);
        if (!tip.isEmpty()) {
            body.addView(text("Tip", 16, true, fg), gap(18));
            body.addView(text(tip, 14, false, dim), gap(4));
        }
        body.addView(Glass.textButton(ctx, "Reset trip", dark, () -> { trip.reset(); refresh(); }), gap(18));
    }

    private LinearLayout row(String label, String value) {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(HORIZONTAL);
        r.addView(text(label, 14, false, PanelStyle.dim(dark)), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        r.addView(text(value, 14, true, PanelStyle.fg(dark)));
        return r;
    }

    private LinearLayout bar(String label, float score, int color) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(VERTICAL);
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(HORIZONTAL);
        top.addView(text(label, 13, false, PanelStyle.dim(dark)), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        top.addView(text(Math.round(score) + "", 13, true, PanelStyle.fg(dark)));
        c.addView(top);
        BarView b = new BarView(getContext(), dark, false, color);
        b.setFraction(score / 100f);
        c.addView(b, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return c;
    }
}

package com.glass.headunit;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The prayer table over the panel, opened by tapping the prayer strip. A round back button closes it. */
final class PrayerScreen extends LinearLayout {

    private final PrayerTableView table;
    private final Prayer.Data data;

    PrayerScreen(Context ctx, boolean dark, int textSize, PanelMode mode, Prayer.Data data, Weather.Data weather, Runnable onClose) {
        super(ctx);
        this.data = data;
        setOrientation(VERTICAL);
        setBackground(new GlassBackground(dark));
        setClickable(true); // swallow touches so the panel below is not pressed through it
        int pad = Glass.dp(ctx, mode == PanelMode.COMPACT ? 14 : 20);
        setPadding(pad, pad, pad, pad);
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new IconView(ctx, Icon.BACK, "Back", dark, 44, IconView.Style.NORMAL, onClose));
        TextView title = new TextView(ctx);
        title.setText("Prayer times");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, PanelStyle.sp(20, textSize, mode));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(fg);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Glass.dp(ctx, 12);
        head.addView(title, tp);
        addView(head);

        String city = weather != null && !weather.city.isEmpty() ? weather.city : data.zoneName.split(" / ")[0];
        TextView sub = new TextView(ctx);
        sub.setText(city + (data.zoneCode.isEmpty() ? "" : " · " + data.zoneCode) + (data.hijri.isEmpty() ? "" : " · " + data.hijri));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, PanelStyle.sp(13, textSize, mode));
        sub.setTextColor(dim);
        sub.setPadding(0, Glass.dp(ctx, 8), 0, Glass.dp(ctx, 4));
        addView(sub);

        table = new PrayerTableView(ctx, dark, textSize, mode);
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(VERTICAL);
        inner.addView(table);
        if (weather != null) {
            TextView foot = new TextView(ctx);
            foot.setText(weather.emoji + " " + Math.round(weather.tempC) + "°C  " + weather.desc);
            foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, PanelStyle.sp(14, textSize, mode));
            foot.setTextColor(dim);
            foot.setPadding(Glass.dp(ctx, 4), Glass.dp(ctx, 14), 0, 0);
            inner.addView(foot);
        }
        sv.addView(inner);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        tick();
    }

    /** Once a second while the screen is open. */
    void tick() { table.bind(data, System.currentTimeMillis()); }
}

package com.glass.headunit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A round glass button with a drawn icon. Normal, main (larger, tinted) or on (tinted, for a switch that is on). */
final class IconView extends View {

    enum Style { NORMAL, MAIN, ON }

    private final boolean dark;
    private final int sizePx;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Icon icon;
    private Style style = Style.NORMAL;

    /** description is what a screen reader says for the button. */
    IconView(Context ctx, Icon icon, String description, boolean dark, int sizeDp, Style style, Runnable onClick) {
        super(ctx);
        this.icon = icon;
        this.dark = dark;
        this.sizePx = Glass.dp(ctx, sizeDp);
        setContentDescription(description);
        setClickable(true);
        setFocusable(true);
        if (onClick != null) setOnClickListener(v -> onClick.run());
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);               // on the 24-unit icon grid
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setStyle(style);
    }

    void setIcon(Icon i) {
        if (i == icon) return;
        icon = i;
        invalidate();
    }

    void setStyle(Style s) {
        style = s;
        setBackground(Glass.button(getContext(), dark, true, s != Style.NORMAL, 0));
        int color = s == Style.ON ? PanelStyle.accent(dark) : PanelStyle.fg(dark);
        fill.setColor(color);
        stroke.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        float s = isPressed() ? 0.95f : 1f;
        setScaleX(s);
        setScaleY(s);
    }

    @Override
    protected void onDraw(Canvas c) {
        float icon = sizePx * (style == Style.MAIN ? 0.42f : 0.46f);
        c.save();
        c.translate((sizePx - icon) / 2f, (sizePx - icon) / 2f);
        Icons.draw(c, this.icon, icon, fill, stroke);
        c.restore();
    }
}

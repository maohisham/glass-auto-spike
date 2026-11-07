package com.glass.headunit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A small plain icon (no button background) for inline use, such as the mosque beside the prayer time. */
final class GlyphView extends View {

    private final Icon icon;
    private final int sizePx;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    GlyphView(Context ctx, Icon icon, int sizeDp, int color, String description) {
        super(ctx);
        this.icon = icon;
        this.sizePx = Glass.dp(ctx, sizeDp);
        fill.setColor(color);
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.8f);       // on the 24-unit icon grid
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription(description);
    }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override
    protected void onDraw(Canvas c) { Icons.draw(c, icon, sizePx, fill, stroke); }
}

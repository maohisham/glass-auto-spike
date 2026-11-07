package com.glass.headunit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** One day's low-to-high temperature as a segment within the week's range. */
final class RangeBarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float from, to = 1f;

    RangeBarView(Context ctx, boolean dark) {
        super(ctx);
        track.setColor(dark ? 0x30FFFFFF : 0x2E0C1626);
    }

    /** from and to are 0 to 1 across the week's range (see Forecast.rangeFractions). */
    void setRange(float[] f) {
        from = f[0];
        to = f[1];
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Glass.dp(getContext(), 60), w), Glass.dp(getContext(), 12));
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = Glass.dp(getContext(), 6), top = (getHeight() - h) / 2f, w = getWidth();
        r.set(0, top, w, top + h);
        c.drawRoundRect(r, h / 2, h / 2, track);
        float a = w * from, b = Math.max(w * to, a + h);
        seg.setShader(new LinearGradient(a, 0, b, 0, 0xFF7FB7FF, 0xFFFFB066, Shader.TileMode.CLAMP));
        r.set(a, top, Math.min(b, w), top + h);
        c.drawRoundRect(r, h / 2, h / 2, seg);
    }
}

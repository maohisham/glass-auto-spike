package com.glass.headunit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** The air quality scale as five coloured bands (good to very unhealthy) with a marker at the current value. */
final class AqiBarView extends View {

    private static final int[] COLORS = {0xFF66DD88, 0xFF66DD88, 0xFFFFCC44, 0xFFFFCC44, 0xFFFF9944, 0xFFFF9944, 0xFFFF6655, 0xFFFF6655, 0xFFCC88FF, 0xFFCC88FF};
    private static final float[] STOPS = {0f, 1f / 6, 1f / 6, 2f / 6, 2f / 6, 3f / 6, 3f / 6, 4f / 6, 4f / 6, 1f};

    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float fraction;

    AqiBarView(Context ctx) {
        super(ctx);
        knob.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Glass.dp(ctx, 2));
        ring.setColor(0x66000000);
    }

    void setAqi(int aqi) {
        fraction = Aqi.fraction(aqi);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Glass.dp(getContext(), 100), w), Glass.dp(getContext(), 22));
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = Glass.dp(getContext(), 8), h = Glass.dp(getContext(), 8), top = (getHeight() - h) / 2f, w = getWidth();
        band.setShader(new LinearGradient(pad, 0, w - pad, 0, COLORS, STOPS, Shader.TileMode.CLAMP));
        r.set(pad, top, w - pad, top + h);
        c.drawRoundRect(r, h / 2, h / 2, band);
        float x = pad + (w - 2 * pad) * fraction, rad = Glass.dp(getContext(), 8);
        c.drawCircle(x, getHeight() / 2f, rad, knob);
        c.drawCircle(x, getHeight() / 2f, rad, ring);
    }
}

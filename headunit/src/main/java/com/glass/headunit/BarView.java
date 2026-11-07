package com.glass.headunit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A thin rounded progress bar, optionally with a knob (the song's bar has one, the prayer bar does not). Set the fraction once a second. */
final class BarView extends View {

    private final boolean dark, knob;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fillP = new Paint(Paint.ANTI_ALIAS_FLAG), knobP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float fraction;
    private final int solid;   // 0 = the accent gradient

    BarView(Context ctx, boolean dark, boolean knob) { this(ctx, dark, knob, 0); }

    BarView(Context ctx, boolean dark, boolean knob, int solidColor) {
        super(ctx);
        this.solid = solidColor;
        this.dark = dark;
        this.knob = knob;
        track.setColor(dark ? 0x30FFFFFF : 0x2E0C1626);
        knobP.setColor(0xFFFFFFFF);
    }

    void setFraction(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (Math.abs(f - fraction) < 0.0005f) return;
        fraction = f;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Glass.dp(getContext(), 100), w), Glass.dp(getContext(), knob ? 14 : 10));
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = Glass.dp(getContext(), knob ? 6 : 8), w = getWidth(), top = (getHeight() - h) / 2f, rad = h / 2f;
        float pad = knob ? Glass.dp(getContext(), 7) : 0;
        r.set(pad, top, w - pad, top + h);
        c.drawRoundRect(r, rad, rad, track);
        float end = pad + (w - 2 * pad) * fraction;
        if (end > pad + 1) {
            if (solid != 0) { fillP.setShader(null); fillP.setColor(solid); }
            else fillP.setShader(new LinearGradient(pad, 0, w - pad, 0, PanelStyle.accent(dark), dark ? 0xFF7FF3CF : 0xFF3FD1A1, Shader.TileMode.CLAMP));
            r.set(pad, top, Math.max(end, pad + h), top + h);
            c.drawRoundRect(r, rad, rad, fillP);
        }
        if (knob) c.drawCircle(Math.max(end, pad), getHeight() / 2f, Glass.dp(getContext(), 6), knobP);
    }
}

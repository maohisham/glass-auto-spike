package com.glass.headunit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The panel's backdrop: a soft gradient with two gentle colour glows, so the glass buttons have something to show against. Drawn once per size. */
final class GlassBackground extends Drawable {

    private final boolean dark;
    private final Paint base = new Paint(), glowA = new Paint(), glowB = new Paint();

    GlassBackground(boolean dark) { this.dark = dark; }

    @Override
    protected void onBoundsChange(Rect b) {
        float w = b.width(), h = b.height();
        base.setShader(new LinearGradient(0, 0, 0, h, dark ? 0xFF0A1226 : 0xFFF4F7FC, dark ? 0xFF15264D : 0xFFD9E4F6, Shader.TileMode.CLAMP));
        glowA.setShader(new RadialGradient(w * 0.88f, h * -0.04f, Math.max(w, 1f) * 0.9f, dark ? 0x4219E3A5 : 0x4719E3A5, 0x0019E3A5, Shader.TileMode.CLAMP));
        glowB.setShader(new RadialGradient(w * -0.06f, h * 1.04f, Math.max(w, 1f) * 0.8f, dark ? 0x475A82FF : 0x3D5A82FF, 0x005A82FF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, base);
        c.drawRect(b, glowA);
        c.drawRect(b, glowB);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}

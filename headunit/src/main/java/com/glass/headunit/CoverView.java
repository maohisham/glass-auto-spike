package com.glass.headunit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** The small rounded album cover beside the title. With no cover it shows a glass tile with a note icon, so the layout never shifts. */
final class CoverView extends View {

    private final boolean dark;
    private final int sizePx;
    private final float radius;
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Matrix m = new Matrix();
    private Bitmap cover;

    CoverView(Context ctx, boolean dark, int sizeDp) {
        super(ctx);
        this.dark = dark;
        this.sizePx = Glass.dp(ctx, sizeDp);
        this.radius = Glass.dp(ctx, 12);
        setBackground(Glass.button(ctx, dark, false, false, 12));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        int c = PanelStyle.dim(dark);
        fill.setColor(c);
        stroke.setColor(c);
    }

    void setCover(Bitmap b) {
        if (b == cover) return;
        cover = b;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override
    protected void onDraw(Canvas c) {
        if (cover == null || cover.isRecycled()) {
            float icon = sizePx * 0.46f;
            c.save();
            c.translate((sizePx - icon) / 2f, (sizePx - icon) / 2f);
            Icons.draw(c, Icon.PLAYER, icon, fill, stroke);
            c.restore();
            return;
        }
        box.set(0, 0, sizePx, sizePx);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        // centre-crop the cover to a square
        float s = Math.max(sizePx / (float) cover.getWidth(), sizePx / (float) cover.getHeight());
        m.reset();
        m.postScale(s, s);
        m.postTranslate((sizePx - cover.getWidth() * s) / 2f, (sizePx - cover.getHeight() * s) / 2f);
        c.drawBitmap(cover, m, bmp);
        c.restore();
    }
}

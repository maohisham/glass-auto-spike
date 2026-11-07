package com.glass.headunit;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Draws each Icon on a 24 x 24 grid, scaled to the given size. fill and stroke paints carry the colour. */
final class Icons {

    private Icons() { }

    static void draw(Canvas c, Icon icon, float size, Paint fill, Paint stroke) {
        c.save();
        float s = size / 24f;
        c.scale(s, s);
        switch (icon) {
            case PREV:
                c.drawRect(6f, 5f, 8.4f, 19f, fill);
                c.drawPath(triangle(19, 6, 19, 18, 9.5f, 12), fill);
                break;
            case NEXT:
                c.drawRect(15.6f, 5f, 18f, 19f, fill);
                c.drawPath(triangle(5, 6, 5, 18, 14.5f, 12), fill);
                break;
            case PLAY:
                c.drawPath(triangle(8, 5, 8, 19, 19, 12), fill);
                break;
            case PAUSE:
                c.drawRect(7f, 5f, 10.6f, 19f, fill);
                c.drawRect(13.4f, 5f, 17f, 19f, fill);
                break;
            case PLAYER: {
                Path p = new Path();
                p.moveTo(9, 18); p.lineTo(9, 5.5f); p.lineTo(20, 3.5f); p.lineTo(20, 16);
                c.drawPath(p, stroke);
                c.drawCircle(6.5f, 18f, 2.6f, fill);
                c.drawCircle(17.5f, 16f, 2.6f, fill);
                break;
            }
            case SETTINGS: {
                Path p = new Path();
                p.moveTo(4, 7); p.lineTo(13, 7);
                p.moveTo(19, 7); p.lineTo(20, 7);
                p.moveTo(4, 17); p.lineTo(5, 17);
                p.moveTo(11, 17); p.lineTo(20, 17);
                c.drawPath(p, stroke);
                c.drawCircle(16f, 7f, 2.6f, stroke);
                c.drawCircle(8f, 17f, 2.6f, stroke);
                break;
            }
            case MAPS: {
                Path p = new Path();
                p.moveTo(12, 21);
                p.cubicTo(8, 17.4f, 5, 13.8f, 5, 9.8f);
                p.arcTo(new RectF(5, 2.8f, 19, 16.8f), 180, 180);
                p.cubicTo(19, 13.8f, 16, 17.4f, 12, 21);
                p.close();
                c.drawPath(p, stroke);
                c.drawCircle(12f, 9.8f, 2.4f, fill);
                break;
            }
            case BACK: {
                Path p = new Path();
                p.moveTo(15, 5); p.lineTo(8, 12); p.lineTo(15, 19);
                c.drawPath(p, stroke);
                break;
            }
            case SEARCH: {
                c.drawCircle(10.5f, 10.5f, 6.2f, stroke);
                Path p = new Path();
                p.moveTo(15.2f, 15.2f); p.lineTo(20, 20);
                c.drawPath(p, stroke);
                break;
            }
            case REPEAT:
            case REPEAT_ONE: {
                Path p = new Path();
                // top arrow pointing right, bottom arrow pointing left, joined by rounded corners (the usual repeat loop)
                p.moveTo(17, 3); p.lineTo(20.5f, 6.5f); p.lineTo(17, 10);
                p.moveTo(20.5f, 6.5f); p.lineTo(7, 6.5f); p.quadTo(3.5f, 6.5f, 3.5f, 10); p.lineTo(3.5f, 11);
                p.moveTo(7, 21); p.lineTo(3.5f, 17.5f); p.lineTo(7, 14);
                p.moveTo(3.5f, 17.5f); p.lineTo(17, 17.5f); p.quadTo(20.5f, 17.5f, 20.5f, 14); p.lineTo(20.5f, 13);
                c.drawPath(p, stroke);
                if (icon == Icon.REPEAT_ONE) {
                    Path one = new Path();   // a small "1" in the middle of the loop
                    one.moveTo(10.6f, 10.6f); one.lineTo(12.4f, 9.4f); one.lineTo(12.4f, 14.8f);
                    c.drawPath(one, stroke);
                }
                break;
            }
            case SHUFFLE: {
                Path p = new Path();
                p.moveTo(3, 7); p.lineTo(6, 7);
                p.cubicTo(9.4f, 7, 10.6f, 9.4f, 12, 12.3f);
                p.cubicTo(13.4f, 15.2f, 15, 17, 18, 17); p.lineTo(20, 17);
                p.moveTo(3, 17); p.lineTo(6, 17);
                p.cubicTo(7.3f, 17, 8.3f, 16.5f, 9.1f, 15.7f);
                p.moveTo(14.4f, 8.2f);
                p.cubicTo(15.2f, 7.5f, 16.2f, 7, 17.4f, 7); p.lineTo(20, 7);
                p.moveTo(17.5f, 4); p.lineTo(20.5f, 7); p.lineTo(17.5f, 10);
                p.moveTo(17.5f, 14); p.lineTo(20.5f, 17); p.lineTo(17.5f, 20);
                c.drawPath(p, stroke);
                break;
            }
        }
        c.restore();
    }

    private static Path triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path p = new Path();
        p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.close();
        return p;
    }
}

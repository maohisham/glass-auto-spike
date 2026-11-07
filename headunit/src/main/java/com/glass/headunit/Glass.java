package com.glass.headunit;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.Button;

/**
 * The frosted look: a translucent fill that is lighter at the top, a thin light edge, and a visibly brighter fill while
 * pressed. A real blur behind a view is not possible inside a split-screen window, so this is translucency and an edge.
 */
final class Glass {

    private Glass() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** A glass background: a circle or a rounded rectangle, plain or tinted with the accent (for the main or switched-on button). */
    static Drawable button(Context c, boolean dark, boolean circle, boolean accent, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, dark, circle, accent, true, radiusDp));
        s.addState(new int[]{}, shape(c, dark, circle, accent, false, radiusDp));
        return s;
    }

    private static Drawable shape(Context c, boolean dark, boolean circle, boolean accent, boolean pressed, float radiusDp) {
        int top, bottom, edge;
        if (accent) {
            top = dark ? (pressed ? 0x7019E3A5 : 0x4D19E3A5) : (pressed ? 0x6608906B : 0x4208906B);
            bottom = dark ? (pressed ? 0x3A19E3A5 : 0x2419E3A5) : (pressed ? 0x3308906B : 0x1F08906B);
            edge = dark ? 0x9919E3A5 : 0x8C08906B;
        } else {
            top = dark ? (pressed ? 0x57FFFFFF : 0x33FFFFFF) : (pressed ? 0xF2FFFFFF : 0xC7FFFFFF);
            bottom = dark ? (pressed ? 0x29FFFFFF : 0x12FFFFFF) : (pressed ? 0xB3FFFFFF : 0x6BFFFFFF);
            edge = dark ? 0x4DFFFFFF : 0xF2FFFFFF;
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        if (circle) g.setShape(GradientDrawable.OVAL); else g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), edge);
        return g;
    }

    /** A transparent area that lights up softly while pressed, for tappable parts inside a glass card. */
    static Drawable pressOverlay(Context c, boolean dark, float radiusDp) {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(c, radiusDp));
        pressed.setColor(dark ? 0x33FFFFFF : 0x2A0C1626);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        return s;
    }

    /** A rounded glass text button, for the few places that still need words. */
    static Button textButton(Context c, String label, boolean dark, Runnable onClick) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(PanelStyle.fg(dark));
        b.setBackground(button(c, dark, false, false, 14));
        b.setMinHeight(dp(c, 48));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }
}

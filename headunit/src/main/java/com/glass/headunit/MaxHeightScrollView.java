package com.glass.headunit;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

/** A scroll view that grows with its content but never past a limit, so what is below it always keeps some room. */
final class MaxHeightScrollView extends ScrollView {

    private int maxPx = Integer.MAX_VALUE;

    MaxHeightScrollView(Context c) { super(c); }

    /** Ignored when not positive (the view has not been measured yet). */
    void setMaxHeightPx(int px) {
        if (px <= 0 || px == maxPx) return;
        maxPx = px;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int mode = View.MeasureSpec.getMode(heightSpec);
        int size = View.MeasureSpec.getSize(heightSpec);
        int cap = mode == View.MeasureSpec.UNSPECIFIED ? maxPx : Math.min(maxPx, size);
        super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
    }
}

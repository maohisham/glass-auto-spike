package com.glass.headunit;

/** The decisions behind turning on split-screen: which app goes first, and where the divider should end up. Pure. */
final class SplitPlan {

    private SplitPlan() { }

    /** Where the divider belongs, as a fraction of the screen from the left (or top). mapShare is the effective share. */
    static float dividerFraction(float mapShare, boolean swapSides) { return swapSides ? 1f - mapShare : mapShare; }

    /** True when the divider (centre position in px) has stopped moving between two reads, so a drag will really start on it. */
    static boolean isSettled(float previousCenter, float currentCenter) { return Math.abs(previousCenter - currentCenter) <= 2f; }

    /** True when the map's window lies before the divider (left, or top), judged by where the unit really put it. */
    static boolean mapIsFirst(float mapCenter, float dividerCenter) { return mapCenter < dividerCenter; }

    /** Split-screen puts the app in front into the left (or top) slot, so that app goes first. */
    static String firstApp(boolean swapSides) { return swapSides ? "panel" : "maps"; }

    /** {x0, y0, x1, y1} of a drag from one divider position to another: along x in landscape, along y in portrait. */
    static float[] drag(int screenW, int screenH, float fromFraction, float toFraction) {
        if (screenW >= screenH) return new float[]{screenW * fromFraction, screenH / 2f, screenW * toFraction, screenH / 2f};
        return new float[]{screenW / 2f, screenH * fromFraction, screenW / 2f, screenH * toFraction};
    }

    /** Whether the divider is far enough from where it should be to be worth dragging (units snap to their own positions). */
    static boolean needsDrag(float currentFraction, float targetFraction) { return Math.abs(currentFraction - targetFraction) > 0.04f; }
}

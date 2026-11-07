package com.glass.headunit;

/** Works out how much of the screen each side gets, so the panel never ends up too narrow to read. */
final class SplitMath {

    /** The narrowest panel that still reads well, in dp. */
    static final float MIN_PANEL_DP = 320f;

    static final class Result {
        final float mapShare;
        final boolean widened;
        Result(float mapShare, boolean widened) { this.mapShare = mapShare; this.widened = widened; }
    }

    private SplitMath() { }

    /** The map's share of the width: the requested one, lowered when the panel would be under the readable minimum (never below 0.5). */
    static Result effective(float requestedMapShare, int screenWidthPx, float density) {
        float widthDp = screenWidthPx / density;
        float panelDp = widthDp * (1f - requestedMapShare);
        if (panelDp >= MIN_PANEL_DP) return new Result(requestedMapShare, false);
        float share = Math.max(0.5f, 1f - MIN_PANEL_DP / widthDp);
        return new Result(Math.min(share, requestedMapShare), true);
    }

    /** The divider position the unit offers that is closest to share. */
    static float snap(float share, float[] offered) {
        float best = offered[0];
        for (float o : offered) if (Math.abs(o - share) < Math.abs(best - share)) best = o;
        return best;
    }
}

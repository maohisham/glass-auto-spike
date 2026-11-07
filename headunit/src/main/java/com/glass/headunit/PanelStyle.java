package com.glass.headunit;

/** Sizes and colours for the panel, from the user's settings. Pure, so it can be tested. */
final class PanelStyle {

    static final int ACCENT_DARK = 0xFF19E3A5, ACCENT_LIGHT = 0xFF08906B;

    private PanelStyle() { }

    /** Small, medium and large text. */
    static float scale(int textSize) { return textSize == 0 ? 0.85f : textSize == 2 ? 1.25f : 1.0f; }

    /** theme: 0 follow the unit, 1 light, 2 dark. */
    static boolean isDark(int theme, boolean systemNight) { return theme == 0 ? systemNight : theme == 2; }

    /** A base size in sp, after the user's text size and the narrow layout's reduction. */
    static float sp(float base, int textSize, PanelMode mode) {
        return base * scale(textSize) * (mode == PanelMode.COMPACT ? 0.85f : 1f);
    }

    static int bg(boolean dark) { return dark ? 0xFF0A1226 : 0xFFF4F6FA; }
    static int fg(boolean dark) { return dark ? 0xFFFFFFFF : 0xFF0A1226; }
    static int dim(boolean dark) { return dark ? 0x99FFFFFF : 0x990A1226; }
    static int accent(boolean dark) { return dark ? ACCENT_DARK : ACCENT_LIGHT; }

    /** The most of the panel's height the controls block may take; the rest is kept for the lyrics. */
    static int headerMaxPx(int heightPx) { return Math.round(heightPx * 0.6f); }

    /** The driving score's colour: green from 75, yellow from 60, red below. */
    static int scoreColor(int score) { return score >= 75 ? 0xFF66DD88 : score >= 60 ? 0xFFFFCC44 : 0xFFFF6655; }

    /**
     * The lyrics' own text scale. -1 follows the panel's text size (so nothing changes by default); 0 to 3 are small,
     * medium, large and extra large whatever the panel's setting is.
     */
    static float lyricsScale(int lyricsSize, int panelTextSize) {
        if (lyricsSize < 0) return scale(panelTextSize);
        switch (Math.min(lyricsSize, 3)) {
            case 0: return 0.85f;
            case 1: return 1.0f;
            case 2: return 1.25f;
            default: return 1.5f;
        }
    }

    /** A lyrics size in sp, with the same narrow-layout shrink as every other size. */
    static float lyricSp(float base, int lyricsSize, int panelTextSize, PanelMode mode) {
        return base * lyricsScale(lyricsSize, panelTextSize) * (mode == PanelMode.COMPACT ? 0.85f : 1f);
    }
}

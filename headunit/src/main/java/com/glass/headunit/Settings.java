package com.glass.headunit;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the user can customize, kept in one place. */
final class Settings {

    private final SharedPreferences sp;

    private Settings(Context ctx) { sp = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE); }

    static Settings of(Context ctx) { return new Settings(ctx); }

    /** The map's share of the screen, 0.5 to 0.8. Default 70:30. */
    float mapShare() { return sp.getFloat("mapShare", 0.70f); }
    void setMapShare(float v) { sp.edit().putFloat("mapShare", Math.max(0.5f, Math.min(0.8f, v))).apply(); }

    /** 0 small, 1 medium, 2 large. */
    int textSize() { return sp.getInt("textSize", 1); }
    void setTextSize(int v) { sp.edit().putInt("textSize", Math.max(0, Math.min(2, v))).apply(); }

    /** 0 follow the unit, 1 light, 2 dark. */
    int theme() { return sp.getInt("theme", 0); }
    void setTheme(int v) { sp.edit().putInt("theme", Math.max(0, Math.min(2, v))).apply(); }

    /** True puts the panel on the left and the map on the right. */
    boolean swapSides() { return sp.getBoolean("swapSides", false); }
    void setSwapSides(boolean v) { sp.edit().putBoolean("swapSides", v).apply(); }

    /** A JAKIM zone code chosen by hand, or empty for "follow the GPS". */
    String manualZone() { return sp.getString("manualZone", ""); }
    void setManualZone(String code) { sp.edit().putString("manualZone", code == null ? "" : code).apply(); }

    /** The floating player bubble. Off by default. */
    boolean bubble() { return sp.getBoolean("bubble", false); }
    void setBubble(boolean v) { sp.edit().putBoolean("bubble", v).apply(); }

    /** Where the user left the bubble (top-left corner in pixels), or -1 when it has not been moved yet. */
    int bubbleX() { return sp.getInt("bubbleX", -1); }
    int bubbleY() { return sp.getInt("bubbleY", -1); }
    void setBubble(int x, int y) { sp.edit().putInt("bubbleX", x).putInt("bubbleY", y).apply(); }
    void resetBubble() { sp.edit().remove("bubbleX").remove("bubbleY").apply(); }
}

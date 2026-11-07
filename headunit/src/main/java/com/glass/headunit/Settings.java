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

    /** Resume the last player when the panel starts (off by default). */
    boolean autoPlayLast() { return sp.getBoolean("autoPlayLast", false); }
    void setAutoPlayLast(boolean v) { sp.edit().putBoolean("autoPlayLast", v).apply(); }

    /** The package of the last player that was actually playing, or empty. */
    String lastPlayer() { return sp.getString("lastPlayer", ""); }
    void setLastPlayer(String pkg) { sp.edit().putString("lastPlayer", pkg == null ? "" : pkg).apply(); }

    /** The driving score (uses the unit's GPS while the panel is open). On by default. */
    boolean driveScore() { return sp.getBoolean("driveScore", true); }
    void setDriveScore(boolean v) { sp.edit().putBoolean("driveScore", v).apply(); }

    /** Beep once when the speed goes over the limit below (off by default). */
    boolean speedAlert() { return sp.getBoolean("speedAlert", false); }
    void setSpeedAlert(boolean v) { sp.edit().putBoolean("speedAlert", v).apply(); }

    /** The speed limit for the alert in km/h, 40 to 180. */
    int speedLimit() { return sp.getInt("speedLimit", 110); }
    void setSpeedLimit(int kmh) { sp.edit().putInt("speedLimit", Math.max(40, Math.min(180, kmh))).apply(); }

    /** The lyrics' own text size: -1 follows the panel's text size, 0 small, 1 medium, 2 large, 3 extra large. */
    int lyricsSize() { return sp.getInt("lyricsSize", -1); }
    void setLyricsSize(int v) { sp.edit().putInt("lyricsSize", Math.max(-1, Math.min(3, v))).apply(); }

    /** The map app to open (a package name), or empty for automatic: Google Maps when it is installed, else the best navigation app. */
    String mapApp() { return sp.getString("mapApp", ""); }
    void setMapApp(String pkg) { sp.edit().putString("mapApp", pkg == null ? "" : pkg).apply(); }

    /** Open the map app beside the panel when the panel starts (on by default); off launches only the panel. */
    boolean autoOpenMaps() { return sp.getBoolean("autoOpenMaps", true); }
    void setAutoOpenMaps(boolean v) { sp.edit().putBoolean("autoOpenMaps", v).apply(); }
}

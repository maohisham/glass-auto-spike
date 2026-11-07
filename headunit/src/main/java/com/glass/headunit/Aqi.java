package com.glass.headunit;

/** The US air quality scale: names, colours and short advice. Pure. */
final class Aqi {

    static final class Category {
        final String name;
        final int color;
        final String advice;
        Category(String name, int color, String advice) { this.name = name; this.color = color; this.advice = advice; }
    }

    /** The scale on the bar runs 0 to this; anything above is shown at the end. */
    static final int SCALE_MAX = 300;

    private Aqi() { }

    static Category category(int aqi) {
        if (aqi <= 50) return new Category("Good", 0xFF66DD88, "Air quality is good. Nothing to worry about.");
        if (aqi <= 100) return new Category("Moderate", 0xFFFFCC44, "Acceptable. Unusually sensitive people may notice it.");
        if (aqi <= 150) return new Category("Unhealthy for sensitive groups", 0xFFFF9944, "If you have breathing or heart problems, keep the windows up and the air on recirculate.");
        if (aqi <= 200) return new Category("Unhealthy", 0xFFFF6655, "Keep the windows up and the air on recirculate. Limit time outdoors.");
        if (aqi <= 300) return new Category("Very unhealthy", 0xFFCC88FF, "Avoid being outdoors. Keep the windows up and the air on recirculate.");
        return new Category("Hazardous", 0xFFB04A6A, "Serious haze. Stay indoors if you can, and keep the car's air on recirculate.");
    }

    /** Where the marker sits on the bar, 0 to 1. */
    static float fraction(int aqi) { return Math.max(0f, Math.min(1f, aqi / (float) SCALE_MAX)); }
}

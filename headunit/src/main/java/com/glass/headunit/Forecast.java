package com.glass.headunit;

import java.util.ArrayList;
import java.util.List;

/** Current conditions, the next 24 hours, the next 7 days and the air quality, as the weather screen shows them. */
final class Forecast {

    static final class Hour {
        final String label;
        final float tempC;
        final int code, rainPct;
        Hour(String label, float tempC, int code, int rainPct) { this.label = label; this.tempC = tempC; this.code = code; this.rainPct = rainPct; }
    }

    static final class Day {
        final String name;
        final int code, rainPct;
        final float hiC, loC;
        Day(String name, int code, float hiC, float loC, int rainPct) { this.name = name; this.code = code; this.hiC = hiC; this.loC = loC; this.rainPct = rainPct; }
    }

    static final class Air {
        boolean valid;
        int usAqi;
        float pm25, pm10;
    }

    float tempC, feelsC, windKmh;
    int humidity, code;
    final List<Hour> hours = new ArrayList<>();
    final List<Day> days = new ArrayList<>();
    final Air air = new Air();

    /** Where a day's low-to-high range sits within the week's range, as {start, end} from 0 to 1 (a flat week fills the bar). */
    static float[] rangeFractions(float lo, float hi, float weekMin, float weekMax) {
        float span = weekMax - weekMin;
        if (span <= 0.001f) return new float[]{0f, 1f};
        return new float[]{(lo - weekMin) / span, (hi - weekMin) / span};
    }
}

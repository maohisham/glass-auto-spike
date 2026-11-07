package com.glass.headunit;

/** How the current speed is written, and when a reading is too old to show. Pure. */
final class SpeedFormat {

    /** A reading older than this (the GPS lost its fix, a tunnel) is replaced by dashes rather than left frozen on screen. */
    static final long STALE_MS = 5_000L;

    private SpeedFormat() { }

    /** The number for the chip: dashes without a fix, 0 below 1 km/h (GPS jitter while standing still), otherwise rounded. */
    static String text(float kmh, boolean hasFix) {
        if (!hasFix || Float.isNaN(kmh)) return "--";
        if (kmh < 1f) return "0";
        return String.valueOf(Math.round(kmh));
    }

    static boolean isStale(long lastFixAtMs, long nowMs) {
        return lastFixAtMs == 0L || nowMs - lastFixAtMs > STALE_MS;
    }
}

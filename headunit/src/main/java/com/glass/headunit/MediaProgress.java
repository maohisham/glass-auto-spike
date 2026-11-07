package com.glass.headunit;

import java.util.Locale;

/** The song's progress bar: the fraction and the m:ss text. Pure. */
final class MediaProgress {

    private MediaProgress() { }

    static float fraction(long positionMs, long durationMs) {
        if (durationMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
    }

    /** "1:42", or "1:02:05" for a track over an hour. */
    static String format(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** Where the song is now: the position we were told, plus the time since while it plays, never past the end. */
    static long extrapolate(long positionMs, long receivedAtMs, long nowMs, boolean playing, long durationMs) {
        long p = playing ? positionMs + Math.max(0, nowMs - receivedAtMs) : positionMs;
        if (durationMs > 0) p = Math.min(p, durationMs);
        return Math.max(0, p);
    }

    /** True when the real position is more than 1.5 s from where the last update predicts: the user seeked, so the panel needs a fresh update. */
    static boolean drifted(long lastPositionMs, long lastAtMs, boolean wasPlaying, long actualPositionMs, long nowMs) {
        long expected = wasPlaying ? lastPositionMs + Math.max(0, nowMs - lastAtMs) : lastPositionMs;
        return Math.abs(actualPositionMs - expected) > 1500;
    }
}

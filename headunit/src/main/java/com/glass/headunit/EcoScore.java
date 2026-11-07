package com.glass.headunit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A fuel-efficiency driving score from 0 to 100, from GPS speed. Ported from the glass-auto-launcher project's
 * EcoScoreManager, with the clock passed in so it can be tested.
 *
 * Weights: 35% time in the 60 to 90 km/h zone, 25% smooth speed, 20% average speed near 70 km/h, 15% idle time,
 * 5% staying under 130 km/h. A trip starts when the speed first reaches 5 km/h.
 */
final class EcoScore {

    private static final float IDLE_THRESHOLD_KMH = 5f;     // below this the car counts as standing still
    private static final long IDLE_MIN_MS = 15_000L;         // a stop must last this long to count as idling
    private static final float TRIP_START_MIN_KMH = 5f;
    private static final int MAX_SAMPLES = 36_000;           // 10 hours at one a second

    static final class Stats {
        int score;                 // 0 to 100
        String grade = "";
        float distanceKm;
        long durationSec, idleSec;
        float idlePct, avgSpeedKmh, maxSpeedKmh;
        float zoneScore, smoothScore, effScore, idleScore, bonusScore;   // each 0 to 100, before weighting
    }

    private final List<Float> samples = new ArrayList<>(3600);
    private float maxSpeed, distanceKm, lastSpeedKmh;
    private long lastSampleMs;
    private boolean isIdle, tripActive;
    private long idleStartMs, totalIdleMs, tripStartMs;

    /** One GPS speed reading in km/h at the given time (about once a second). */
    void onSpeed(float speedKmh, long nowMs) {
        if (!tripActive && speedKmh >= TRIP_START_MIN_KMH) {
            tripActive = true;
            tripStartMs = nowMs;
            lastSampleMs = nowMs;
        }
        if (!tripActive) return;

        if (lastSampleMs > 0) {
            float dtHours = (nowMs - lastSampleMs) / 3_600_000f;
            distanceKm += ((lastSpeedKmh + speedKmh) / 2f) * dtHours;   // trapezoid
        }
        lastSampleMs = nowMs;
        lastSpeedKmh = speedKmh;
        if (speedKmh > maxSpeed) maxSpeed = speedKmh;

        if (speedKmh < IDLE_THRESHOLD_KMH) {
            if (!isIdle) { isIdle = true; idleStartMs = nowMs; }
        } else if (isIdle) {
            long elapsed = nowMs - idleStartMs;
            if (elapsed >= IDLE_MIN_MS) totalIdleMs += elapsed;
            isIdle = false;
        }
        if (samples.size() < MAX_SAMPLES) samples.add(speedKmh);
    }

    Stats calculate(long nowMs) {
        Stats s = new Stats();
        if (!tripActive || samples.isEmpty()) return s;
        long tripMs = nowMs - tripStartMs;
        long idleMs = totalIdleMs + (isIdle && (nowMs - idleStartMs) >= IDLE_MIN_MS ? (nowMs - idleStartMs) : 0);

        s.distanceKm = distanceKm;
        s.durationSec = tripMs / 1000;
        s.idleSec = idleMs / 1000;
        s.idlePct = tripMs > 0 ? idleMs * 100f / tripMs : 0;
        s.maxSpeedKmh = maxSpeed;
        s.avgSpeedKmh = tripMs > 0 ? distanceKm / (tripMs / 3_600_000f) : 0;

        float zoneSum = 0;
        for (float v : samples) zoneSum += zonePoints(v);
        s.zoneScore = zoneSum / samples.size();

        float mean = s.avgSpeedKmh, varSum = 0;
        for (float v : samples) varSum += (v - mean) * (v - mean);
        float stdDev = (float) Math.sqrt(varSum / samples.size());
        s.smoothScore = Math.max(0, 100 - stdDev * 3f);

        s.effScore = Math.max(0, 100 - Math.abs(s.avgSpeedKmh - 70f) * 1.5f);
        s.idleScore = Math.max(0, 100 - s.idlePct * 2.5f);
        s.bonusScore = maxSpeed < 130 ? 100 : Math.max(0, 100 - (maxSpeed - 130) * 2f);

        float total = s.zoneScore * 0.35f + s.smoothScore * 0.25f + s.effScore * 0.20f + s.idleScore * 0.15f + s.bonusScore * 0.05f;
        s.score = Math.round(total);
        s.grade = grade(s.score);
        return s;
    }

    void reset() {
        samples.clear();
        maxSpeed = 0; distanceKm = 0; lastSpeedKmh = 0; lastSampleMs = 0;
        isIdle = false; idleStartMs = 0; totalIdleMs = 0;
        tripActive = false; tripStartMs = 0;
    }

    boolean isTripActive() { return tripActive; }

    private static float zonePoints(float speed) {
        if (speed >= 60 && speed <= 90) return 100f;
        if (speed >= 40 && speed <= 110) return 70f;
        if (speed >= 20 && speed <= 130) return 40f;
        return 10f;   // standing still or very fast
    }

    static String grade(int score) {
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good";
        if (score >= 60) return "Fair";
        if (score >= 40) return "Poor";
        return "Very Poor";
    }

    /** One line of advice for the weakest part of the trip, or empty when nothing stands out. */
    static String tip(Stats s) {
        if (s.idlePct > 20) return "Reduce engine idling: you idled " + String.format(Locale.US, "%.0f%%", s.idlePct) + " of your trip.";
        if (s.smoothScore < 50) return "Smoother acceleration and braking improves fuel economy.";
        if (s.avgSpeedKmh > 100) return "Slow down a little: fuel efficiency drops above 90 km/h.";
        if (s.avgSpeedKmh < 30 && s.distanceKm > 1) return "Short, slow trips use more fuel per km. Combine errands where possible.";
        if (s.bonusScore < 80) return "Avoid exceeding 130 km/h: fuel consumption rises sharply at high speed.";
        return "";
    }

    static String duration(long totalSec) {
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        if (h > 0) return String.format(Locale.US, "%dh %02dm", h, m);
        if (m > 0) return String.format(Locale.US, "%dm %02ds", m, s);
        return s + "s";
    }
}

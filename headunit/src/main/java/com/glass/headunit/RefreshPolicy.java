package com.glass.headunit;

/** When the once-a-minute check should refresh prayer times and weather. While offline nothing runs before the retry time. */
final class RefreshPolicy {

    static final long WEATHER_MS = 30 * 60_000L;

    private RefreshPolicy() { }

    static boolean shouldRefresh(long now, boolean haveData, boolean prayerFromToday, long weatherAt,
                                 boolean offline, long retryAt, boolean manualZoneChanged) {
        if (manualZoneChanged) return true;
        if (offline) return now >= retryAt;
        return !haveData || !prayerFromToday || now - weatherAt > WEATHER_MS;
    }
}

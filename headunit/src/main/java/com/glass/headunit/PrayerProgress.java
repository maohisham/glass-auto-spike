package com.glass.headunit;

import java.util.Calendar;

/** How far we are from the start of the current prayer to the next one, 0 to 1. Pure. */
final class PrayerProgress {

    private PrayerProgress() { }

    /** d must have been through Prayer.recompute for nowMs. Before Subuh the current prayer is last night's Isyak, which began yesterday. */
    static float fraction(Prayer.Data d, long nowMs) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMin = Prayer.hhmm(d.currentTime);
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(nowMs);
        start.set(Calendar.HOUR_OF_DAY, startMin / 60);
        start.set(Calendar.MINUTE, startMin % 60);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        if (startMin > nowMin) start.add(Calendar.DAY_OF_YEAR, -1);
        long s = start.getTimeInMillis(), e = d.nextMs;
        if (e <= s) return 0f;
        float f = (nowMs - s) / (float) (e - s);
        return Math.max(0f, Math.min(1f, f));
    }
}

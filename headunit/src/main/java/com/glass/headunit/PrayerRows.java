package com.glass.headunit;

import java.util.ArrayList;
import java.util.List;

/** The prayer table as rows: each time in 12-hour form with how the row should look. Pure. */
final class PrayerRows {

    enum Kind { PAST, SUNRISE, CURRENT, NEXT, LATER }

    static final class Row {
        final String name, time12;
        final Kind kind;
        Row(String name, String time12, Kind kind) { this.name = name; this.time12 = time12; this.kind = kind; }
    }

    private static final String[] ORDER = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};

    private PrayerRows() { }

    /** d must have been through Prayer.recompute for nowMs (it supplies currentName and nextName). Syuruk is left out when unknown. */
    static List<Row> build(Prayer.Data d, long nowMs) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        int nowMin = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        // When the current prayer's time is later in the day than now, it began yesterday (before Subuh): today's are all still ahead
        boolean currentIsYesterdays = d.currentTime != null && !d.currentTime.isEmpty() && Prayer.hhmm(d.currentTime) > nowMin;
        int curIdx = indexOf(d.currentName);

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ORDER.length; i++) {
            String name = ORDER[i];
            String t = d.times.get(name);
            if (t == null) continue;
            Kind kind;
            if (name.equals(d.currentName)) kind = Kind.CURRENT;
            else if (name.equals(d.nextName)) kind = Kind.NEXT;
            else if (currentIsYesterdays) kind = Kind.LATER;
            else kind = i < curIdx ? Kind.PAST : Kind.LATER;
            rows.add(new Row(name, Prayer.to12(t), kind));
            if (name.equals("Subuh") && d.syuruk != null && !d.syuruk.isEmpty()) {
                rows.add(new Row("Syuruk", Prayer.to12(d.syuruk), Kind.SUNRISE));
            }
        }
        return rows;
    }

    private static int indexOf(String name) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(name)) return i;
        return -1;
    }
}

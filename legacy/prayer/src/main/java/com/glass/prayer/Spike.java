package com.glass.prayer;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Throwaway event log shown on the phone screen, so Android Auto behaviour can be read without adb. */
final class Spike {
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private Spike() { }

    static synchronized void log(String line) {
        android.util.Log.d("AASpike", line);
        LOG.addLast(TIME.format(new Date()) + "  " + line);
        while (LOG.size() > 80) LOG.removeFirst();
    }

    static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : LOG) sb.append(l).append('\n');
        return sb.length() == 0 ? "(nothing yet)" : sb.toString();
    }
}

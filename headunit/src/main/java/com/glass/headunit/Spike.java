package com.glass.headunit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * The event log shown on screen, so a unit without adb (the Eonon) can still be debugged. It is also written to a
 * small file, so it survives a crash or the process being killed: the next run shows "earlier run" and its lines.
 */
final class Spike {
    private static final int MAX = 200;
    private static final long FILE_CAP = 32_000;
    private static final String MARKER = "--- earlier run ---";
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static File file;

    private Spike() { }

    /** Starts writing to this file and loads what an earlier run left in it. */
    static synchronized void attachFile(File f) {
        file = f;
        try {
            if (f.exists()) {
                int loaded = 0;
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        if (l.equals(MARKER)) continue;
                        LOG.addLast(l);
                        loaded++;
                        while (LOG.size() > MAX) LOG.removeFirst();
                    }
                }
                if (loaded > 0) LOG.addLast(MARKER);
            }
        } catch (Exception ignored) { }
    }

    /** For tests: forget the in-memory log and stop writing, as if the process had died. */
    static synchronized void detachForTest() {
        file = null;
        LOG.clear();
    }

    static synchronized void log(String line) {
        android.util.Log.d("GlassPanel", line);
        String entry = TIME.format(new Date()) + "  " + line;
        LOG.addLast(entry);
        while (LOG.size() > MAX) LOG.removeFirst();
        if (file == null) return;
        try {
            if (file.length() > FILE_CAP) {
                try (FileWriter w = new FileWriter(file, false)) { for (String l : LOG) w.write(l + "\n"); }
            } else {
                try (FileWriter w = new FileWriter(file, true)) { w.write(entry + "\n"); }
            }
        } catch (Exception ignored) { }
    }

    static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : LOG) sb.append(l).append('\n');
        return sb.length() == 0 ? "(nothing yet)" : sb.toString();
    }
}

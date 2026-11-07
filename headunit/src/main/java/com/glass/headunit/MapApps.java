package com.glass.headunit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Which installed apps count as map apps, in what order they are offered, and which one the panel opens. Pure. */
final class MapApps {

    static final String GOOGLE_MAPS = "com.google.android.apps.maps";

    /** Known navigation apps, best first. The Malaysian apps are matched by name (see isNavigation), not by package. */
    private static final String[] KNOWN = {
            GOOGLE_MAPS, "com.waze", "com.sygic.aura", "com.here.app.maps", "com.tomtom.gplay.navapp",
            "net.osmand", "net.osmand.plus", "cz.seznam.mapy", "app.organicmaps", "com.mapswithme.maps.pro",
            "ru.yandex.yandexnavi", "com.huawei.maps.app"
    };
    private static final String[] NAMES = {"teras", "tuju", "pintaz", "sygic", "waze", "navigation", "maps"};

    private MapApps() { }

    private static int rank(String pkg, String label) {
        for (int i = 0; i < KNOWN.length; i++) if (KNOWN[i].equals(pkg)) return i;
        String l = (label == null ? "" : label).toLowerCase(Locale.ROOT);
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String n : NAMES) if (l.contains(n) || p.contains(n)) return KNOWN.length;
        return -1;
    }

    static boolean isNavigation(String pkg, String label) { return rank(pkg, label) >= 0; }

    /** Input and output are {package, label} pairs: navigation apps first (best known first), the rest alphabetical. */
    static List<String[]> order(List<String[]> apps) {
        List<String[]> out = new ArrayList<>(apps);
        out.sort((a, b) -> {
            int ra = rank(a[0], a[1]), rb = rank(b[0], b[1]);
            if (ra >= 0 && rb >= 0 && ra != rb) return Integer.compare(ra, rb);
            if (ra >= 0 && rb < 0) return -1;
            if (rb >= 0 && ra < 0) return 1;
            return a[1].compareToIgnoreCase(b[1]);
        });
        return out;
    }

    static final class Split {
        final List<String[]> navigation = new ArrayList<>();
        final List<String[]> other = new ArrayList<>();
    }

    static Split split(List<String[]> apps) {
        Split s = new Split();
        for (String[] a : order(apps)) (isNavigation(a[0], a[1]) ? s.navigation : s.other).add(a);
        return s;
    }

    /**
     * The package the panel opens: the saved one if it is still installed, otherwise the best navigation app
     * (Google Maps first), otherwise null (the user has to pick one in Settings).
     */
    static String choose(String saved, List<String[]> installed) {
        if (saved != null && !saved.isEmpty()) {
            for (String[] a : installed) if (a[0].equals(saved)) return saved;
        }
        List<String[]> ordered = order(installed);
        if (!ordered.isEmpty() && isNavigation(ordered.get(0)[0], ordered.get(0)[1])) return ordered.get(0)[0];
        return null;
    }
}

package com.glass.headunit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Orders the installed media apps for the picker: the players the user named first, the rest alphabetical. */
final class PlayerList {

    private PlayerList() { }

    /** 0 Spotify, 1 YouTube Music, 2 Bitchord, 3 VRadio, or -1 for any other app. */
    private static int rank(String pkg, String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (pkg.equals("com.spotify.music")) return 0;
        if (pkg.contains("youtube.music")) return 1;
        if (l.contains("bitchord")) return 2;
        if (pkg.equals(Proxy.VRADIO)) return 3;
        return -1;
    }

    static boolean isKnown(String pkg, String label) { return rank(pkg, label) >= 0; }

    /** Input and output are {package, label} pairs, as Proxy.players returns them. */
    static List<String[]> order(List<String[]> players) {
        List<String[]> out = new ArrayList<>(players);
        out.sort((a, b) -> {
            int ra = rank(a[0], a[1]), rb = rank(b[0], b[1]);
            if (ra >= 0 && rb >= 0) return Integer.compare(ra, rb);
            if (ra >= 0) return -1;
            if (rb >= 0) return 1;
            return a[1].compareToIgnoreCase(b[1]);
        });
        return out;
    }

    /** The players to show up front, and everything else. */
    static final class Split {
        final List<String[]> main = new ArrayList<>();
        final List<String[]> more = new ArrayList<>();
    }

    /** Up front: the named players in their order, then any other app with a live media session. The rest, alphabetical, go under "More apps". */
    static Split split(List<String[]> all, java.util.Set<String> activePackages) {
        Split s = new Split();
        List<String[]> ordered = order(all);
        List<String[]> activeOthers = new ArrayList<>(), rest = new ArrayList<>();
        for (String[] p : ordered) {
            if (isKnown(p[0], p[1])) s.main.add(p);
            else if (activePackages != null && activePackages.contains(p[0])) activeOthers.add(p);
            else rest.add(p);
        }
        s.main.addAll(activeOthers);
        s.more.addAll(rest);
        return s;
    }
}

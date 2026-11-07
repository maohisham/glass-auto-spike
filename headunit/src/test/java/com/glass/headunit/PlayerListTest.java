package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PlayerListTest {

    private static String[] p(String pkg, String label) { return new String[]{pkg, label}; }

    @Test
    public void knownPlayersComeFirstInTheSpecifiedOrder() {
        List<String[]> in = new ArrayList<>();
        in.add(p("com.x.local", "Local Music"));
        in.add(p("com.ilv.vradio", "VRadio"));
        in.add(p("com.spotify.music", "Spotify"));
        in.add(p("app.revanced.android.apps.youtube.music", "YT Music"));
        in.add(p("org.b", "Bitchord"));
        in.add(p("com.a.alpha", "Alpha Player"));
        List<String[]> out = PlayerList.order(in);
        assertEquals("com.spotify.music", out.get(0)[0]);
        assertEquals("app.revanced.android.apps.youtube.music", out.get(1)[0]);
        assertEquals("org.b", out.get(2)[0]);
        assertEquals("com.ilv.vradio", out.get(3)[0]);
        assertEquals("Alpha Player", out.get(4)[1]);   // the rest, alphabetical
        assertEquals("Local Music", out.get(5)[1]);
    }

    @Test
    public void knownMeansTheFivePlayersTheUserNamed() {
        assertTrue(PlayerList.isKnown("com.spotify.music", "Spotify"));
        assertTrue(PlayerList.isKnown("com.google.android.apps.youtube.music", "YouTube Music"));
        assertTrue(PlayerList.isKnown("whatever.pkg", "bitchord"));
        assertTrue(PlayerList.isKnown("com.ilv.vradio", "VRadio"));
        assertFalse(PlayerList.isKnown("com.x.local", "Local Music"));
    }

    @Test
    public void emptyListGivesAnEmptyList() {
        assertTrue(PlayerList.order(new ArrayList<>()).isEmpty());
    }

    @Test
    public void splitPutsKnownAndActivePlayersFirstAndTheRestUnderMore() {
        List<String[]> in = new ArrayList<>();
        in.add(p("com.google.android.googlequicksearchbox", "Google"));
        in.add(p("com.spotify.music", "Spotify"));
        in.add(p("com.android.bluetooth", "Bluetooth"));
        in.add(p("org.local", "Local Music"));
        in.add(p("com.ilv.vradio", "VRadio"));
        java.util.Set<String> active = new java.util.HashSet<>();
        active.add("org.local");   // has a live media session right now
        PlayerList.Split s = PlayerList.split(in, active);
        assertEquals("com.spotify.music", s.main.get(0)[0]);
        assertEquals("com.ilv.vradio", s.main.get(1)[0]);
        assertEquals("org.local", s.main.get(2)[0]);        // active but not a named player: after the named ones
        assertEquals(3, s.main.size());
        assertEquals("Bluetooth", s.more.get(0)[1]);         // the rest, alphabetical
        assertEquals("Google", s.more.get(1)[1]);
        assertEquals(2, s.more.size());
    }
}

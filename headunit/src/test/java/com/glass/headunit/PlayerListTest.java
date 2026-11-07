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
}

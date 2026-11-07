package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class LyricLineTest {

    private static Lyrics lyrics(long[] times, String... lines) {
        Lyrics l = new Lyrics();
        for (long t : times) l.times.add(t);
        for (String s : lines) l.lines.add(s);
        return l;
    }

    @Test
    public void indexAtFollowsTheTimes() {
        Lyrics l = lyrics(new long[]{1000, 5000, 9000}, "a", "b", "c");
        assertEquals(-1, l.indexAt(0));
        assertEquals(0, l.indexAt(1000));
        assertEquals(1, l.indexAt(8999));
        assertEquals(2, l.indexAt(60000));
    }

    @Test
    public void nextNonEmptySkipsInstrumentalGaps() {
        Lyrics l = lyrics(new long[]{0, 1, 2}, "a", "", "c");
        assertEquals(2, l.nextNonEmpty(1));
        assertEquals(0, l.nextNonEmpty(0));
        assertEquals(-1, l.nextNonEmpty(3));
    }

    @Test
    public void highlightedLineIsTheLastRealLineDuringAGap() {
        Lyrics l = lyrics(new long[]{0, 5000, 9000}, "a", "", "c");
        assertEquals(0, l.lastNonEmptyAtOrBefore(1));   // sung line "a" stays lit through the instrumental gap
        assertEquals(2, l.lastNonEmptyAtOrBefore(2));
        assertEquals(-1, l.lastNonEmptyAtOrBefore(-1)); // before the first line: nothing lit
    }

    @Test
    public void idleHasNoPlayerNoLyricsAndNoLine() {
        NowPlaying n = NowPlaying.idle();
        assertNull(n.pkg);
        assertNull(n.lyrics);
        assertEquals(-1, n.line);
        assertFalse(n.playing);
        assertFalse(n.lyricsLoading);
    }
}

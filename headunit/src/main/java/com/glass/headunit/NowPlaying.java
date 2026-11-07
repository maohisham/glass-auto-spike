package com.glass.headunit;

/** One immutable picture of what is playing, handed to the panel each time something visible changes. */
final class NowPlaying {

    final String pkg;          // null when nothing is playing
    final String playerLabel;
    final String title, artist;
    final boolean playing;
    final long positionMs, durationMs;
    final Lyrics lyrics;       // null when none was found (or none applies)
    final boolean lyricsLoading;
    final int line;            // index into lyrics.lines, -1 before the first line
    final boolean shuffleAvailable, shuffleOn;

    NowPlaying(String pkg, String playerLabel, String title, String artist, boolean playing,
               long positionMs, long durationMs, Lyrics lyrics, boolean lyricsLoading, int line,
               boolean shuffleAvailable, boolean shuffleOn) {
        this.pkg = pkg;
        this.playerLabel = playerLabel;
        this.title = title;
        this.artist = artist;
        this.playing = playing;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.lyrics = lyrics;
        this.lyricsLoading = lyricsLoading;
        this.line = line;
        this.shuffleAvailable = shuffleAvailable;
        this.shuffleOn = shuffleOn;
    }

    static NowPlaying idle() { return new NowPlaying(null, "", "", "", false, 0, 0, null, false, -1, false, false); }

    /** What the panel would draw differently: used to avoid redrawing when nothing visible changed. */
    String key() {
        return pkg + "|" + title + "|" + artist + "|" + playing + "|" + lyricsLoading + "|" + (lyrics != null) + "|" + line + "|" + shuffleAvailable + "|" + shuffleOn;
    }
}

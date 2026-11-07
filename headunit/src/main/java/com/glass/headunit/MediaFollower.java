package com.glass.headunit;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationManagerCompat;

/**
 * Follows whichever app is playing and reports it as NowPlaying. Every 2 s it re-picks the player; every 500 ms it
 * moves to the lyric line that matches the playing position; it calls the listener only when something visible changed.
 */
final class MediaFollower {

    interface Listener { void onChanged(NowPlaying n); }

    private final Context ctx;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController target;
    private MediaControllerCompat compat;   // the support-library view of the same player: the platform one has no shuffle calls
    private String trackKey = "";
    private Lyrics lyrics;
    private boolean lyricsLoading;
    private int generation;     // bumped on every track change so a slow lyrics answer for an old track is dropped
    private int shownLine = -1;
    private String lastKey = "";
    private long lastPos, lastPosAt;      // the position we last published and when, to notice a seek
    private boolean lastPlaying;
    private boolean started;
    private long switchLockUntil;   // while set, do not jump back to the player we are switching away from

    private final MediaController.Callback mirror = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { trackMaybeChanged(); publish(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { publish(); }
        @Override public void onSessionDestroyed() { detach(); publish(); }
    };

    private final Runnable pickTick = new Runnable() {
        @Override public void run() {
            if (SystemClock.elapsedRealtime() < switchLockUntil) { publish(); handler.postDelayed(this, 2000); return; }
            MediaController best = Proxy.findActive(ctx, target);
            if (best == null) {
                if (target != null) detach();
            } else if (target == null || !best.getSessionToken().equals(target.getSessionToken())) {
                if (target == null || (isPlaying(best) && !isPlaying(target))) attach(best);
            }
            publish();
            handler.postDelayed(this, 2000);
        }
    };

    private final Runnable lineTick = new Runnable() {
        @Override public void run() {
            publish();
            handler.postDelayed(this, 500);
        }
    };

    MediaFollower(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    /** Whether the user has granted this app notification access, which reading other apps' media sessions needs. */
    static boolean hasMediaAccess(Context ctx) {
        return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.getPackageName());
    }

    void start() {
        if (started) return;
        started = true;
        handler.post(pickTick);
        handler.post(lineTick);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(pickTick);
        handler.removeCallbacks(lineTick);
        detach();
    }

    void play() { if (target != null) target.getTransportControls().play(); }
    void pause() { if (target != null) target.getTransportControls().pause(); }
    void next() { if (target != null) target.getTransportControls().skipToNext(); }
    void previous() { if (target != null) target.getTransportControls().skipToPrevious(); }
    /** Turns shuffle on if it is off and off if it is on. Does nothing for a player without shuffle. */
    void toggleShuffle() {
        if (compat == null) return;
        int mode = compat.getShuffleMode();
        compat.getTransportControls().setShuffleMode(mode > 0 ? PlaybackStateCompat.SHUFFLE_MODE_NONE : PlaybackStateCompat.SHUFFLE_MODE_ALL);
    }

    void seekTo(long ms) { if (target != null) target.getTransportControls().seekTo(ms); }

    /** Stops the 2-second re-pick for a while, so the player we just left (still reporting playing) is not re-attached. */
    void lockSwitch(long ms) { switchLockUntil = SystemClock.elapsedRealtime() + ms; }

    /** The player we follow, or null. */
    MediaController controller() { return target; }

    /** Follows this player now (used right after the user switches player). */
    void follow(MediaController c) { if (c != null) { attach(c); publish(); } }

    private void attach(MediaController c) {
        detach();
        target = c;
        c.registerCallback(mirror, handler);
        try { compat = new MediaControllerCompat(ctx, MediaSessionCompat.Token.fromToken(c.getSessionToken())); }
        catch (Exception e) { compat = null; Spike.log("shuffle: cannot reach " + c.getPackageName() + " (" + e.getClass().getSimpleName() + ")"); }
        Spike.log("following " + c.getPackageName());
        trackMaybeChanged();
    }

    private void detach() {
        if (target != null) {
            try { target.unregisterCallback(mirror); } catch (Exception ignored) { }
            Spike.log("no longer following " + target.getPackageName());
        }
        target = null;
        compat = null;
        trackKey = "";
        lyrics = null;
        lyricsLoading = false;
        shownLine = -1;
        generation++;
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState s = c == null ? null : c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private long position() {
        PlaybackState s = target == null ? null : target.getPlaybackState();
        if (s == null) return 0;
        if (s.getState() != PlaybackState.STATE_PLAYING) return s.getPosition();
        return s.getPosition() + (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
    }

    private static String str(String s) { return s == null ? "" : s; }

    /** A different title or artist means a new track: drop the old lyrics at once and fetch the new ones. */
    private void trackMaybeChanged() {
        if (target == null) return;
        MediaMetadata m = target.getMetadata();
        String title = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String key = target.getPackageName() + "|" + title + "|" + artist;
        if (key.equals(trackKey)) return;
        trackKey = key;
        lyrics = null;
        shownLine = -1;
        final int gen = ++generation;
        if (title.isEmpty() || Proxy.VRADIO.equals(target.getPackageName())) { lyricsLoading = false; return; }
        History.record(ctx, target.getPackageName(), title, artist);
        lyricsLoading = true;
        final long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        final String t = title, a = artist;
        new Thread(() -> {
            Lyrics found = null;
            try { found = Lyrics.fetch(t, a, dur); } catch (Exception e) { Spike.log("lyrics: failed " + e.getMessage()); }
            final Lyrics result = found;
            handler.post(() -> {
                if (gen != generation) return; // the track changed while we waited
                lyrics = result;
                lyricsLoading = false;
                publish();
            });
        }, "lyrics").start();
    }

    private void publish() {
        NowPlaying n = snapshot();
        String key = n.key();
        long at = SystemClock.elapsedRealtime();
        // nothing visible changed and the position is where the last update predicts: no need to redraw
        if (key.equals(lastKey) && !MediaProgress.drifted(lastPos, lastPosAt, lastPlaying, n.positionMs, at)) return;
        lastKey = key;
        lastPos = n.positionMs;
        lastPosAt = at;
        lastPlaying = n.playing;
        listener.onChanged(n);
    }

    private NowPlaying snapshot() {
        if (target == null) return NowPlaying.idle();
        MediaMetadata m = target.getMetadata();
        String title = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = position();
        int line = lyrics == null ? -1 : lyrics.indexAt(pos);
        shownLine = line;
        PlaybackState ps = target.getPlaybackState();
        boolean shuffleAvailable = ControlsPolicy.showShuffle(target.getPackageName(), ps == null ? 0 : ps.getActions());
        boolean shuffleOn = shuffleAvailable && compat != null && compat.getShuffleMode() > 0;
        return new NowPlaying(target.getPackageName(), Proxy.label(ctx, target.getPackageName()), title, artist,
                isPlaying(target), pos, dur, lyrics, lyricsLoading, line, shuffleAvailable, shuffleOn);
    }
}

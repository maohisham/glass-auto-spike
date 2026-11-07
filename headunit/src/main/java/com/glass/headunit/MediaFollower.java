package com.glass.headunit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
    private long lostAt;   // when the followed player first went missing (0 = it is here); see FollowGrace
    private Bitmap cover;
    private String coverId = "", coverLoading = "";
    private String lastRecorded = "";
    private NowPlaying lastDelivered;
    private long switchLockUntil;   // while set, do not jump back to the player we are switching away from

    private final MediaController.Callback mirror = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) {
            trackMaybeChanged();
            if (cover == null && target != null) refreshCover(target.getMetadata()); // art often arrives a moment after the title
            publish();
        }
        @Override public void onPlaybackStateChanged(PlaybackState s) { publish(); }
        @Override public void onSessionDestroyed() {
            // players drop and re-create their session at a track change: wait before treating it as gone
            if (lostAt == 0) { lostAt = SystemClock.elapsedRealtime(); Spike.log("session ended: waiting up to " + FollowGrace.GRACE_MS / 1000 + " s for the player to come back"); }
        }
    };

    private final Runnable pickTick = new Runnable() {
        @Override public void run() {
            if (SystemClock.elapsedRealtime() < switchLockUntil) { publish(); handler.postDelayed(this, 2000); return; }
            MediaController best = Proxy.findActive(ctx, target);
            long now = SystemClock.elapsedRealtime();
            if (target == null) {
                if (best != null) attach(best);
            } else {
                MediaController same = Proxy.find(ctx, target.getPackageName());
                boolean present = same != null && same.getSessionToken().equals(target.getSessionToken());
                if (!present && same != null) { rebind(same); present = true; }   // same app, new session: follow it with no gap
                boolean wasLost = lostAt != 0;
                lostAt = FollowGrace.mark(lostAt, present, now);
                if (!wasLost && lostAt != 0) Spike.log("lost the player's session: waiting up to " + FollowGrace.GRACE_MS / 1000 + " s for it to come back");
                if (!present) {
                    if (best != null && isPlaying(best)) attach(best);          // another app took over
                    else if (FollowGrace.shouldDetach(lostAt, now)) { Spike.log("the player did not come back: showing the idle screen"); detach(); }
                } else if (best != null && !best.getSessionToken().equals(target.getSessionToken()) && isPlaying(best) && !isPlaying(target)) {
                    attach(best);                                               // another app is playing while ours is not
                }
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

    /** Steps repeat through off, all and one. Does nothing for a player without repeat. */
    void toggleRepeat() {
        if (compat == null) return;
        compat.getTransportControls().setRepeatMode(RepeatMode.next(compat.getRepeatMode()));
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

    /** The same app gave us a new session (it re-creates it at a track change): follow that one, keeping the lyrics and cover on screen. */
    private void rebind(MediaController c) {
        try { if (target != null) target.unregisterCallback(mirror); } catch (Exception ignored) { }
        target = c;
        c.registerCallback(mirror, handler);
        try { compat = new MediaControllerCompat(ctx, MediaSessionCompat.Token.fromToken(c.getSessionToken())); }
        catch (Exception e) { compat = null; }
        lostAt = 0;
        Spike.log("followed " + c.getPackageName() + "'s new session");
        trackMaybeChanged();
    }

    private void detach() {
        if (target != null) {
            try { target.unregisterCallback(mirror); } catch (Exception ignored) { }
            Spike.log("no longer following " + target.getPackageName());
        }
        target = null;
        lostAt = 0;
        compat = null;
        cover = null;
        coverId = "";
        coverLoading = "";
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
        cover = null;
        coverId = "";
        coverLoading = "";
        final int gen = ++generation;
        refreshCover(m);
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

    /** True when what we last told the panel says something is playing. */
    boolean anyPlaying() { return lastDelivered != null && lastDelivered.playing; }

    /** The small cover for the current track: a bitmap the player gave us, else one fetched from its picture address off the main thread. */
    private void refreshCover(MediaMetadata m) {
        if (m == null) return;
        Bitmap b = null;
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            b = m.getBitmap(k);
            if (b != null) break;
        }
        if (b != null) {
            cover = scaled(b);
            coverId = "bmp:" + System.identityHashCode(b);
            return;
        }
        final String uri = CoverPick.firstUri(m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                m.getString(MediaMetadata.METADATA_KEY_ART_URI), m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI));
        if (uri == null || uri.equals(coverId) || uri.equals(coverLoading)) return;
        coverLoading = uri;
        final int gen = generation;
        new Thread(() -> {
            Bitmap bm = null;
            try {
                java.io.InputStream in;
                if (uri.startsWith("http")) {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(uri).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    in = c.getInputStream();
                } else {
                    in = ctx.getContentResolver().openInputStream(Uri.parse(uri));
                }
                if (in != null) { try { bm = BitmapFactory.decodeStream(in); } finally { in.close(); } }
            } catch (Exception e) { Spike.log("cover: could not load (" + e.getClass().getSimpleName() + ")"); }
            final Bitmap result = bm == null ? null : scaled(bm);
            handler.post(() -> {
                if (gen != generation) return;       // the track changed while we waited
                coverLoading = "";
                if (result != null) { cover = result; coverId = uri; publish(); }
            });
        }, "cover").start();
    }

    private static Bitmap scaled(Bitmap b) {
        Bitmap src = b.getConfig() == Bitmap.Config.HARDWARE ? b.copy(Bitmap.Config.ARGB_8888, false) : b;
        if (src == null) return null;
        int[] sz = CoverPick.targetSize(src.getWidth(), src.getHeight(), 256);
        return sz[0] == src.getWidth() && sz[1] == src.getHeight() ? src : Bitmap.createScaledBitmap(src, sz[0], sz[1], true);
    }

    private void publish() {
        if (lostAt != 0 && target != null) return;   // the player is briefly missing: keep what the panel shows
        NowPlaying n = snapshot();
        String key = n.key();
        long at = SystemClock.elapsedRealtime();
        // nothing visible changed and the position is where the last update predicts: no need to redraw
        if (key.equals(lastKey) && !MediaProgress.drifted(lastPos, lastPosAt, lastPlaying, n.positionMs, at)) return;
        lastKey = key;
        lastPos = n.positionMs;
        lastPosAt = at;
        lastPlaying = n.playing;
        lastDelivered = n;
        // remember which player was actually playing, for auto-play next time
        if (n.pkg != null && n.playing && !n.pkg.equals(lastRecorded)) {
            lastRecorded = n.pkg;
            Settings.of(ctx).setLastPlayer(n.pkg);
        }
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
        boolean repeatAvailable = ControlsPolicy.showRepeat(target.getPackageName(), ps == null ? 0 : ps.getActions());
        int repeatMode = repeatAvailable && compat != null ? compat.getRepeatMode() : 0;
        return new NowPlaying(target.getPackageName(), Proxy.label(ctx, target.getPackageName()), title, artist,
                isPlaying(target), pos, dur, lyrics, lyricsLoading, line, shuffleAvailable, shuffleOn, repeatAvailable, repeatMode, cover, coverId);
    }
}

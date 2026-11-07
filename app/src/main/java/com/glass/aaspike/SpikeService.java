package com.glass.aaspike;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * The Glass Auto lyrics card. The card picture is the playing track's album cover; the text beside it shows
 * time-synced lyrics. It follows whichever app is playing (any app with a media session), forwards play, pause,
 * next, previous and seek to it, and fetches lyrics for the current track. Prayer times have their own app
 * (the prayer module); this service still posts the prayer-time alert until that moves there too.
 *
 * The title line carries the lyric being sung, the artist line the next two. Song and artist names are drawn
 * across the top of the cover, because the car shows no other text field.
 */
public class SpikeService extends MediaBrowserServiceCompat {

    private static final String ROOT = "root";
    /** The line being sung plus the next two; the car shows at most 2 + 2 lines (2 + 1 in the narrow card). */
    private static final int LYRIC_LINES = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionCompat session;

    private MediaController target;
    private String trackKey = "";
    private Lyrics lyrics;
    private boolean lyricsLoading;
    private int shownLine = -2;

    // players we already asked to shuffle, so turning it off by hand is not undone on the next track
    private final java.util.Set<String> shuffleDone = new java.util.HashSet<>();

    private boolean loggedPictureSize;

    private Prayer.Data prayer;
    private Weather.Data weather;

    // held in a field because SharedPreferences keeps only a weak reference to its listeners
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener calListener = (sp, key) -> publishMetadata();

    private final MediaController.Callback mirror = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { onTrackMaybeChanged(); maybeRecord(); reassertSoon(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { publishState(); maybeRecord(); }
        @Override public void onSessionDestroyed() { Spike.log("player session ended"); detach(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSessionCompat(this, "GlassAuto");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { Spike.log("steering/card: play"); if (target != null) target.getTransportControls().play(); reassertSoon(); }
            @Override public void onPause() { Spike.log("steering/card: pause"); if (target != null) target.getTransportControls().pause(); reassertSoon(); }
            @Override public void onSkipToNext() { Spike.log("steering/card: next"); if (target != null) target.getTransportControls().skipToNext(); reassertSoon(); }
            @Override public void onSkipToPrevious() { Spike.log("steering/card: previous"); if (target != null) target.getTransportControls().skipToPrevious(); reassertSoon(); }
            @Override public void onSetShuffleMode(int mode) {
                Spike.log("card: shuffle " + (mode == PlaybackStateCompat.SHUFFLE_MODE_NONE ? "off" : "on"));
                if (target != null) compat(target).setShuffleMode(mode);
                session.setShuffleMode(mode);
            }
            @Override public void onSetRepeatMode(int mode) {
                Spike.log("card: repeat mode " + mode);
                if (target != null) compat(target).setRepeatMode(mode);
                session.setRepeatMode(mode);
            }
            @Override public void onPlayFromMediaId(String id, Bundle extras) {
                Spike.log("card: picked " + id);
                if (id == null) return;
                if (id.startsWith("player:")) switchTo(id.substring(7));
                else if (id.startsWith("q:")) {
                    if (target != null) target.getTransportControls().skipToQueueItem(Long.parseLong(id.substring(2)));
                } else if (id.startsWith("song:")) {
                    History.Entry e = History.Entry.fromId(id);
                    if (e != null) playSong(e.pkg, e.title, e.artist);
                }
            }
            @Override public void onSeekTo(long pos) { if (target != null) target.getTransportControls().seekTo(pos); }
            /** Tapping a line in the lyrics list jumps the song to that line. */
            @Override public void onSkipToQueueItem(long id) {
                Spike.log("lyrics list: jump to line " + id);
                if (target != null && lyrics != null && id >= 0 && id < lyrics.times.size()) {
                    target.getTransportControls().seekTo(lyrics.times.get((int) id));
                }
            }
            @Override public void onPlayFromSearch(String q, Bundle extras) {
                Spike.log("voice: play '" + q + "'");
                if (target != null) target.getTransportControls().playFromSearch(q, extras);
            }
        });
        getSharedPreferences("cal", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(calListener);
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        publishState();
        publishMetadata();
        handler.post(pickTick);
        handler.post(lineTick);
        handler.post(loadReal);
        watchLocation();
        handler.postDelayed(refreshTick, 30000);
        Spike.log("Glass Auto card created, session active");
    }

    // ---- keeping the card on screen ----

    /**
     * When the player changes track, Android Auto tends to jump to that player's own card. Re-announcing our
     * session as freshly playing a few times right after a change is the best lever an app has to stay in front.
     */
    private void reassertSoon() {
        for (long delay : new long[]{300, 1200, 3000}) {
            handler.postDelayed(() -> { session.setActive(true); publishState(); }, delay);
        }
    }

    // ---- switching player from the card's list ----

    private String switchingTo;
    /** While a switch is in progress the old player may still report "playing"; do not jump back to it. */
    private long switchLockUntil;

    /**
     * The user picked a player in the card's list: pause the one that is playing, then start the chosen one.
     * If it has no session yet, send it a media Play button, and after a few seconds open its screen on the phone.
     */
    private void switchTo(String pkg) {
        if (target != null && !pkg.equals(target.getPackageName())) target.getTransportControls().pause();
        switchingTo = pkg;
        switchLockUntil = SystemClock.elapsedRealtime() + 16000;
        MediaController c = Proxy.find(this, pkg);
        if (c != null) {
            attach(c);
            startOrSearch(c);
            switchLockUntil = SystemClock.elapsedRealtime() + 4000;
            Spike.log("switch: now " + pkg);
            return;
        }
        Proxy.wake(this, pkg, false);
        waitForPlayer(pkg, 0);
    }

    // a song picked from the history list, waiting for its app to be the one we follow
    private String pendingTitle, pendingArtist;

    /** Plays a song by searching for it in its app (the only way in: that app's library is closed to us). */
    private void playSong(String pkg, String title, String artist) {
        if (target != null && pkg.equals(target.getPackageName())) { searchNow(target, title, artist); return; }
        pendingTitle = title;
        pendingArtist = artist;
        switchTo(pkg);
    }

    /** After switching: either play the pending song, or just press play. */
    private void startOrSearch(MediaController c) {
        if (pendingTitle == null) { c.getTransportControls().play(); return; }
        final String t = pendingTitle, a = pendingArtist;
        pendingTitle = null;
        handler.postDelayed(() -> searchNow(c, t, a), 1500);
    }

    private void searchNow(MediaController c, String title, String artist) {
        Bundle extras = new Bundle();
        extras.putString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio");
        extras.putString(android.provider.MediaStore.EXTRA_MEDIA_TITLE, title);
        if (!artist.isEmpty()) extras.putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, artist);
        c.getTransportControls().playFromSearch((title + " " + artist).trim(), extras);
        Spike.log("history: asked " + c.getPackageName() + " to play '" + title + "' " + artist);
    }

    private void waitForPlayer(String pkg, int seconds) {
        if (!pkg.equals(switchingTo)) return;
        MediaController c = Proxy.find(this, pkg);
        if (c != null) {
            attach(c);
            startOrSearch(c);
            switchingTo = null;
            switchLockUntil = SystemClock.elapsedRealtime() + 4000;
            Spike.log("switch: " + pkg + " started after " + seconds + " s");
            return;
        }
        if (seconds == 3) Proxy.wake(this, pkg, true);
        if (seconds >= 15) { Spike.log("switch: " + pkg + " never started, open it once on the phone"); switchingTo = null; return; }
        handler.postDelayed(() -> waitForPlayer(pkg, seconds + 1), 1000);
    }

    // ---- following whichever app is playing ----

    /** Every 2 s: attach to whichever app is playing, and drop it when its session is gone. */
    private final Runnable pickTick = new Runnable() {
        @Override public void run() {
            if (SystemClock.elapsedRealtime() < switchLockUntil) { handler.postDelayed(this, 2000); return; }
            MediaController best = Proxy.findActive(SpikeService.this, target);
            if (best == null) {
                if (target != null) detach();
            } else if (target == null || !best.getSessionToken().equals(target.getSessionToken())) {
                boolean bestPlaying = isPlaying(best);
                if (target == null || (bestPlaying && !isPlaying(target))) attach(best);
            }
            handler.postDelayed(this, 2000);
        }
    };

    private static boolean isPlaying(MediaController c) {
        PlaybackState s = c == null ? null : c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Every 500 ms: move to the lyric line that matches the playback position. */
    private final Runnable lineTick = new Runnable() {
        @Override public void run() {
            if (target != null && lyrics != null) {
                int idx = lyrics.indexAt(position());
                if (idx != shownLine) { shownLine = idx; publishMetadata(); }
            }
            handler.postDelayed(this, 500);
        }
    };

    private long position() {
        PlaybackState s = target == null ? null : target.getPlaybackState();
        if (s == null) return 0;
        if (s.getState() != PlaybackState.STATE_PLAYING) return s.getPosition();
        return s.getPosition() + (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
    }

    private void attach(MediaController c) {
        detach();
        target = c;
        c.registerCallback(mirror, handler);
        Spike.log("following " + c.getPackageName());
        notifyChildrenChanged(ROOT);
        onTrackMaybeChanged();
        publishState();
        if (!Proxy.VRADIO.equals(c.getPackageName()) && shuffleDone.add(c.getPackageName())) {
            handler.postDelayed(() -> enableShuffle(c), 1500);
        }
    }

    /** The platform controller has no shuffle or repeat calls, the support-library one does. */
    private android.support.v4.media.session.MediaControllerCompat.TransportControls compat(MediaController c) {
        try {
            return new android.support.v4.media.session.MediaControllerCompat(this,
                    MediaSessionCompat.Token.fromToken(c.getSessionToken())).getTransportControls();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Shuffle is on by default: ask the player once, after its session has settled, if it offers the standard shuffle action. */
    private void enableShuffle(MediaController c) {
        if (target == null || !c.getSessionToken().equals(target.getSessionToken())) return;
        PlaybackState s = c.getPlaybackState();
        boolean offers = s != null && (s.getActions() & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0;
        if (offers) {
            compat(c).setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);
            session.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);
            Spike.log("shuffle: turned on in " + c.getPackageName());
        } else {
            Spike.log("shuffle: " + c.getPackageName() + " does not offer the standard shuffle action, left as it is");
        }
    }

    private void detach() {
        if (target != null) { try { target.unregisterCallback(mirror); } catch (Exception ignored) { } }
        target = null;
        lyrics = null;
        lyricsLoading = false;
        trackKey = "";
        shownLine = -2;
        publishState();
        publishMetadata();
    }

    private void onTrackMaybeChanged() {
        MediaMetadata m = target == null ? null : target.getMetadata();
        String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String key = title + "|" + artist;
        if (title != null && !key.equals(trackKey)) {
            trackKey = key;
            lyrics = null;
            shownLine = -2;
            final long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            final String t = title, a = artist == null ? "" : artist;
            if (!Proxy.VRADIO.equals(target.getPackageName())) {
                lyricsLoading = true;
                new Thread(() -> {
                    Lyrics l = Lyrics.fetch(t, a, dur);
                    handler.post(() -> {
                        if (key.equals(trackKey)) { lyrics = l; lyricsLoading = false; shownLine = -2; publishMetadata(); }
                    });
                }).start();
            } else {
                lyricsLoading = false;
            }
        }
        publishMetadata();
    }

    // ---- prayer and weather: real data ----

    /** Every 20 min: fetch prayer times for the phone's JAKIM zone and the weather there. */
    private final Runnable loadReal = new Runnable() {
        @Override public void run() {
            reload(Where.get(getApplicationContext()), "timer");
            handler.postDelayed(this, 20 * 60_000L);
        }
    };

    private double[] lastFetchAt;
    private long lastReloadMs;
    private boolean reloading;

    /** at is {lat, lon}, or null when there is no fix yet (Kuala Lumpur then). One fetch at a time; a failed fetch retries in 2 min. */
    private void reload(double[] at, String why) {
        if (reloading) return;
        reloading = true;
        lastReloadMs = SystemClock.elapsedRealtime();
        final android.content.Context ctx = getApplicationContext();
        final double[] where;
        if (at == null) {
            Spike.log("location: no fix yet, using Kuala Lumpur. Open the app once to save one");
            where = new double[]{3.1478, 101.6953};
        } else {
            where = at;
            Spike.log("location (" + why + "): " + String.format(java.util.Locale.US, "%.3f, %.3f", at[0], at[1]));
        }
        lastFetchAt = where;
        new Thread(() -> {
            final Prayer.Data p = Prayer.fetch(where[0], where[1]);
            final Weather.Data w = Weather.fetch(ctx, where[0], where[1]);
            handler.post(() -> {
                reloading = false;
                if (p != null) prayer = p;
                if (w != null) weather = w;
                publishMetadata();
                scheduleExact();
                if (p == null || w == null) handler.postDelayed(() -> reload(Where.get(ctx), "retry"), 120_000L);
            });
        }).start();
    }

    // ---- travel: refresh when the phone has moved far enough to change the weather or the prayer zone ----

    private android.location.LocationManager lm;

    private final android.location.LocationListener moved = new android.location.LocationListener() {
        @Override public void onLocationChanged(android.location.Location loc) {
            Where.save(SpikeService.this, loc.getLatitude(), loc.getLongitude());
            if (lastFetchAt == null) return;
            float[] d = new float[1];
            android.location.Location.distanceBetween(lastFetchAt[0], lastFetchAt[1], loc.getLatitude(), loc.getLongitude(), d);
            if (d[0] > 5000 && SystemClock.elapsedRealtime() - lastReloadMs > 120_000L) {
                Spike.log("moved " + Math.round(d[0] / 1000) + " km: refreshing prayer times and weather");
                reload(new double[]{loc.getLatitude(), loc.getLongitude()}, "moved");
            }
        }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    private void watchLocation() {
        try {
            lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            for (String provider : new String[]{android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER}) {
                if (lm.getAllProviders().contains(provider)) lm.requestLocationUpdates(provider, 60_000L, 1000f, moved);
            }
            Spike.log("watching the phone's location for travel");
        } catch (SecurityException e) {
            Spike.log("location: no permission, travel updates are off");
        }
    }

    /** Every 30 s: the countdown moves, so recompute and redraw. */
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            if (prayer != null) Prayer.recompute(prayer);
            maybeAlert();
            publishMetadata();
            scheduleExact();
            handler.postDelayed(this, 30000);
        }
    };

    // ---- prayer alert ----

    /** Fires at the prayer time itself (the 30 s tick alone could be up to 30 s late). */
    private final Runnable exactCheck = new Runnable() {
        @Override public void run() {
            if (prayer != null) { Prayer.recompute(prayer); maybeAlert(); publishMetadata(); }
            scheduleExact();
        }
    };

    private void scheduleExact() {
        handler.removeCallbacks(exactCheck);
        if (prayer == null) return;
        long delay = Math.max(1000L, prayer.nextMs - System.currentTimeMillis() + 1000L);
        handler.postDelayed(exactCheck, Math.min(delay, 30 * 60_000L));
    }

    /** Posts the alert once per prayer per day, within 3 minutes after the prayer time. */
    private void maybeAlert() {
        if (prayer == null || !prayer.justStarted) return;
        String key = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()) + prayer.currentName;
        android.content.SharedPreferences sp = getSharedPreferences("alerts", MODE_PRIVATE);
        if (key.equals(sp.getString("last", ""))) return;
        sp.edit().putString("last", key).apply();
        String place = weather != null && !weather.city.isEmpty() ? weather.city : prayer.zoneName.split(" / ")[0];
        PrayerAlert.post(this, prayer.currentName, Prayer.to12(prayer.currentTime), place);
    }

    // ---- the card ----

    private static final long ACTIONS = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
            | PlaybackStateCompat.ACTION_SET_REPEAT_MODE | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;

    private void publishState() {
        PlaybackState src = target == null ? null : target.getPlaybackState();
        if (src != null) {
            // built fresh (not copied) so the update time is now, which keeps this session the most recent one
            session.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(ACTIONS)
                    .setActiveQueueItemId(activeQueueId)
                    .setState(src.getState(), position(), src.getPlaybackSpeed())
                    .build());
            return;
        }
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(0)
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                .build());
    }

    private void publishMetadata() {
        if (getSharedPreferences("cal", MODE_PRIVATE).getBoolean("on", false)) { publishCalibration(); return; }
        MediaMetadata m = target == null ? null : target.getMetadata();

        String song = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_TITLE), "");
        String artist = m == null ? "" : str(m.getString(MediaMetadata.METADATA_KEY_ARTIST), "");
        String header = song.isEmpty() ? "" : song + (artist.isEmpty() ? "" : "  —  " + artist);

        String top, bottom;
        if (m == null) {
            top = "Nothing playing";
            bottom = "Play something to see lyrics";
        } else if (Proxy.VRADIO.equals(target.getPackageName())) {
            top = header.isEmpty() ? "Radio" : header;
            bottom = "Radio: no lyrics";
        } else if (lyricsLoading) {
            top = header;
            bottom = "Finding lyrics…";
        } else if (lyrics == null) {
            top = header;
            bottom = "No synced lyrics for this track";
        } else {
            // The car shows the title in 2 lines and the artist in 2 (1 in the narrow card), wrapping at spaces and
            // cutting the rest with "…". So the line being sung goes in the title, where it can wrap, and the next
            // lines go in the artist. Instrumental gaps (empty lines) are skipped.
            List<String> next = new ArrayList<>();
            for (int i = Math.max(0, shownLine); i < lyrics.lines.size() && next.size() < LYRIC_LINES; i++) {
                if (!lyrics.lines.get(i).isEmpty()) next.add(lyrics.lines.get(i));
            }
            top = next.isEmpty() ? "♪  …" : next.get(0);
            bottom = next.size() > 1 ? android.text.TextUtils.join("\n", next.subList(1, next.size())) : "";
        }

        computeView();
        Bitmap art = cover(m);
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "glass-auto")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, top)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, bottom)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, header)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art);
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (dur > 0) b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur);
        session.setMetadata(b.build());
        updateLyricQueue();
        if (!loggedPictureSize) {
            // Android may shrink metadata pictures; log what the system actually kept
            MediaMetadataCompat kept = session.getController().getMetadata();
            Bitmap k = kept == null ? null : kept.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            if (k != null) { loggedPictureSize = true; Spike.log("picture: drew " + art.getWidth() + " px, system kept " + k.getWidth() + " px"); }
        }
    }

    // ---- lyrics on the picture and as a list ----

    private List<String> viewLines;   // lines drawn on the card picture: one before the current line, it, and the next few
    private int viewCur = -1;
    private Lyrics queuedLyrics;      // the lyrics currently loaded into the session's queue
    private long activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID;

    private String viewKey() {
        return viewLines == null ? "" : android.text.TextUtils.join("\n", viewLines) + "#" + viewCur;
    }

    /** Picks the lines for the picture: the one before the current line, the current line, and the three after it. */
    private void computeView() {
        viewLines = null;
        viewCur = -1;
        if (lyrics == null) return;
        List<String> ne = new ArrayList<>();
        int k = -1;
        for (int i = 0; i < lyrics.lines.size(); i++) {
            String l = lyrics.lines.get(i);
            if (l.isEmpty()) continue; // instrumental gap
            ne.add(l);
            if (i <= shownLine) k = ne.size() - 1;
        }
        if (ne.isEmpty()) return;
        int from = k < 0 ? 0 : Math.max(0, k - 1);
        int to = Math.min(ne.size(), (k < 0 ? 0 : k) + 4);
        viewLines = new ArrayList<>(ne.subList(from, to));
        viewCur = k < 0 ? -1 : k - from;
    }

    /**
     * The lyrics as the session's queue, so the car's queue list in the now-playing view shows every line with the
     * current one highlighted. The queue id of a line is its index in the lyrics, which is also what a tap seeks to.
     */
    private void updateLyricQueue() {
        if (lyrics == null) {
            if (queuedLyrics != null) {
                session.setQueue(null);
                queuedLyrics = null;
                activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
                publishState();
            }
            return;
        }
        if (lyrics != queuedLyrics) {
            List<MediaSessionCompat.QueueItem> items = new ArrayList<>();
            for (int i = 0; i < lyrics.lines.size(); i++) {
                String l = lyrics.lines.get(i);
                if (l.isEmpty()) continue;
                items.add(new MediaSessionCompat.QueueItem(
                        new MediaDescriptionCompat.Builder().setMediaId("lyric:" + i).setTitle(l).build(), i));
            }
            session.setQueue(items);
            session.setQueueTitle("Lyrics");
            queuedLyrics = lyrics;
            activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
        }
        long id = MediaSessionCompat.QueueItem.UNKNOWN_ID;
        for (int i = Math.min(shownLine, lyrics.lines.size() - 1); i >= 0; i--) {
            if (!lyrics.lines.get(i).isEmpty()) { id = i; break; }
        }
        if (id != activeQueueId) { activeQueueId = id; publishState(); }
    }

    // ---- album cover ----

    private Bitmap coverSrc, coverArt;          // the player's bitmap and the square we made from it
    private String uriLoading, uriCoverKey;     // a cover that comes as a link is fetched once, off the main thread
    private Bitmap uriCoverBmp;

    /** The playing track's cover as a square card picture, or the placeholder when the player gives none. */
    private Bitmap cover(MediaMetadata m) {
        if (m == null) return placeholder("", "");
        final String song = str(m.getString(MediaMetadata.METADATA_KEY_TITLE), "");
        final String by = str(m.getString(MediaMetadata.METADATA_KEY_ARTIST), "");
        Bitmap src = null;
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            src = m.getBitmap(k);
            if (src != null) break;
        }
        if (src == null) {
            String uri = null;
            for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                uri = m.getString(k);
                if (uri != null && !uri.isEmpty()) break;
                uri = null;
            }
            if (uri != null) {
                if (uri.equals(uriCoverKey)) src = uriCoverBmp;
                else if (!uri.equals(uriLoading)) loadCoverUri(uri);
            }
        }
        if (src == null) return placeholder(song, by);
        String key = song + "|" + by + "|" + viewKey();
        if (src != coverSrc || coverArt == null || !key.equals(coverKey)) {
            boolean newSrc = src != coverSrc;
            coverSrc = src;
            coverKey = key;
            coverArt = CardArt.cover(640, src, song, by, viewLines, viewCur);
            if (newSrc) Spike.log("cover: using the player's picture, " + src.getWidth() + "x" + src.getHeight());
        }
        return coverArt;
    }

    private Bitmap placeholderArt;
    private String placeholderKey = "", coverKey = "";

    private Bitmap placeholder(String song, String artist) {
        String key = song + "|" + artist + "|" + viewKey();
        if (placeholderArt == null || !key.equals(placeholderKey)) {
            placeholderArt = CardArt.placeholder(640, song, artist, viewLines, viewCur);
            placeholderKey = key;
        }
        return placeholderArt;
    }

    private void loadCoverUri(final String uri) {
        uriLoading = uri;
        new Thread(() -> {
            Bitmap b = null;
            try {
                java.io.InputStream in;
                if (uri.startsWith("http")) {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(uri).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    in = c.getInputStream();
                } else {
                    in = getContentResolver().openInputStream(android.net.Uri.parse(uri));
                }
                if (in != null) { try { b = android.graphics.BitmapFactory.decodeStream(in); } finally { in.close(); } }
            } catch (Exception e) { Spike.log("cover: could not load the link: " + e.getMessage()); }
            final Bitmap loaded = b;
            handler.post(() -> {
                uriCoverKey = uri;
                uriCoverBmp = loaded;
                uriLoading = null;
                if (loaded != null) publishMetadata();
            });
        }, "cover-load").start();
    }

    /** Test card: a labelled picture and ruler strings, to read off the cropping and text room in the car. */
    private void publishCalibration() {
        StringBuilder ruler = new StringBuilder();
        for (char tens = 'A'; tens < 'A' + 10; tens++) ruler.append("123456789").append(tens);
        // numbered short lines: the last number visible on screen is how many lines each field really shows
        StringBuilder titleLines = new StringBuilder(), artistLines = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            if (i > 1) { titleLines.append('\n'); artistLines.append('\n'); }
            titleLines.append("Title line ").append(i);
            artistLines.append("Artist line ").append(i);
        }
        Bitmap art = CardArt.calibration(640);
        session.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "glass-auto-cal")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleLines.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistLines.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ALBUM " + ruler)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "SUBTITLE " + ruler)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "DESCRIPTION " + ruler)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
                .build());
    }

    private static String str(String s, String fallback) {
        return s == null || s.isEmpty() ? fallback : s;
    }

    // ---- browse list: nothing to browse, but Android Auto needs a root ----

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Spike.log("browse: onGetRoot from " + clientPackageName);
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> list = new ArrayList<>();
        String current = target == null ? null : target.getPackageName();
        switch (parentId) {
            case "folder:recent":
                for (History.Entry e : History.recent(this, 30)) {
                    list.add(entry("song:" + e.id().substring(5), e.title, e.artist + (e.artist.isEmpty() ? "" : "  ·  ") + Proxy.label(this, e.pkg), false));
                }
                if (list.isEmpty()) list.add(entry("none", "Nothing played yet", "Songs you play show up here", false));
                break;
            case "folder:top":
                for (History.Entry e : History.top(this, 30)) {
                    list.add(entry("song:" + e.id().substring(5), e.title, e.artist + (e.artist.isEmpty() ? "" : "  ·  ") + e.count + "×  ·  " + Proxy.label(this, e.pkg), false));
                }
                if (list.isEmpty()) list.add(entry("none", "Nothing played yet", "Your most played songs show up here", false));
                break;
            case "folder:queue": {
                List<android.media.session.MediaSession.QueueItem> q = target == null ? null : target.getQueue();
                if (q != null) {
                    for (android.media.session.MediaSession.QueueItem qi : q) {
                        CharSequence t = qi.getDescription().getTitle(), s = qi.getDescription().getSubtitle();
                        list.add(entry("q:" + qi.getQueueId(), String.valueOf(t), s == null ? "" : s.toString(), false));
                    }
                }
                if (list.isEmpty()) list.add(entry("none", target == null ? "Play something first" : "This player shares no queue", "", false));
                break;
            }
            case "folder:players":
                // tapping a player starts it and the card follows it
                for (String[] p : Proxy.players(this)) list.add(entry("player:" + p[0], p[1], p[0].equals(current) ? "Playing now" : "Tap to switch", false));
                if (list.isEmpty()) list.add(entry("none", "No music apps found", "", false));
                break;
            default:
                list.add(entry("folder:recent", "Recent songs", "What you played lately", true));
                list.add(entry("folder:top", "Most played", "Your top songs", true));
                list.add(entry("folder:queue", "Up next", current == null ? "Play something first" : "From " + Proxy.label(this, current), true));
                list.add(entry("folder:players", "Players", "Switch between music apps", true));
        }
        Spike.log("browse: " + parentId + " -> " + list.size() + " items");
        result.sendResult(list);
    }

    private static MediaBrowserCompat.MediaItem entry(String id, String title, String subtitle, boolean folder) {
        MediaDescriptionCompat d = new MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build();
        return new MediaBrowserCompat.MediaItem(d, folder ? MediaBrowserCompat.MediaItem.FLAG_BROWSABLE : MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    // ---- history: remember what has played ----

    private String lastRecordedKey = "";

    /** Records the current song once it is really playing, so skipped or paused tracks do not pile up. */
    private void maybeRecord() {
        MediaMetadata m = target == null ? null : target.getMetadata();
        String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty() || !isPlaying(target)) return;
        String artist = str(m.getString(MediaMetadata.METADATA_KEY_ARTIST), "");
        String key = target.getPackageName() + "|" + title + "|" + artist;
        if (key.equals(lastRecordedKey)) return;
        lastRecordedKey = key;
        History.record(this, target.getPackageName(), title, artist);
        notifyChildrenChanged("folder:recent");
        notifyChildrenChanged("folder:top");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (lm != null) { try { lm.removeUpdates(moved); } catch (Exception ignored) { } }
        if (target != null) { try { target.unregisterCallback(mirror); } catch (Exception ignored) { } }
        session.release();
        Spike.log("service destroyed");
        super.onDestroy();
    }
}

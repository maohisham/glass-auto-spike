package com.glass.headunit;

import android.content.Context;
import android.media.session.MediaController;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.function.Consumer;

/**
 * Changes the player: pauses the one that is playing, starts the chosen one (waking it with a media Play button, and
 * opening its screen if that does not work), then optionally plays a song or item in it. The follower is optional:
 * the bubble has none and relies on the panel's own follower noticing the new player within 2 s.
 */
final class PlayerSwitcher {

    private final Context ctx;
    private final MediaFollower follower;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String switchingTo;

    PlayerSwitcher(Context ctx, MediaFollower followerOrNull) {
        this.ctx = ctx.getApplicationContext();
        this.follower = followerOrNull;
    }

    void switchTo(String pkg) { switchTo(pkg, null, true); }

    /** Presses Play in that app so it carries on where it stopped. Never opens the app's own screen (that could cover Maps or the panel). */
    void resume(String pkg) { switchTo(pkg, null, false); }

    /** Plays a song by asking that app to search for it: the only way in where its library is closed to us. */
    void playSong(String pkg, String title, String artist) {
        switchTo(pkg, true, c -> handler.postDelayed(() -> {
            Bundle extras = new Bundle();
            extras.putString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio");
            extras.putString(android.provider.MediaStore.EXTRA_MEDIA_TITLE, title);
            if (!artist.isEmpty()) extras.putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, artist);
            c.getTransportControls().playFromSearch((title + " " + artist).trim(), extras);
            Spike.log("asked " + pkg + " to play '" + title + "' " + artist);
        }, 1500));
    }

    /** Plays an item from that player's own browse list. */
    void playItem(String pkg, String mediaId) {
        switchTo(pkg, true, c -> {
            c.getTransportControls().playFromMediaId(mediaId, null);
            Spike.log("asked " + pkg + " to play item " + mediaId);
        });
    }

    private void switchTo(String pkg, boolean openScreen, Consumer<MediaController> then) { switchTo(pkg, then, openScreen); }

    private void switchTo(String pkg, Consumer<MediaController> then, boolean openScreen) {
        MediaController current = follower != null ? follower.controller() : Proxy.findActive(ctx, null);
        if (current != null && !pkg.equals(current.getPackageName())) current.getTransportControls().pause();
        switchingTo = pkg;
        if (follower != null) follower.lockSwitch(16_000);
        MediaController c = Proxy.find(ctx, pkg);
        if (c != null) { arrived(c, then); return; }
        Proxy.wake(ctx, pkg, false);
        waitFor(pkg, 0, then, openScreen);
    }

    private void arrived(MediaController c, Consumer<MediaController> then) {
        switchingTo = null;
        if (follower != null) { follower.follow(c); follower.lockSwitch(4_000); }
        if (then == null) c.getTransportControls().play(); else then.accept(c);
        Spike.log("switch: now " + c.getPackageName());
    }

    private void waitFor(String pkg, int seconds, Consumer<MediaController> then, boolean openScreen) {
        if (!pkg.equals(switchingTo)) return; // another switch started
        MediaController c = Proxy.find(ctx, pkg);
        if (c != null) { arrived(c, then); return; }
        if (openScreen && seconds == 3) Proxy.wake(ctx, pkg, true);
        if (seconds >= 15) {
            Spike.log("switch: " + pkg + " never started, open it once by hand");
            switchingTo = null;
            return;
        }
        handler.postDelayed(() -> waitFor(pkg, seconds + 1, then, openScreen), 1000);
    }
}

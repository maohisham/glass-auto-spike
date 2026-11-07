package com.glass.headunit;

import android.support.v4.media.session.PlaybackStateCompat;

/** The repeat button's cycle and wording. Pure. */
final class RepeatMode {

    private RepeatMode() { }

    /** Off, then repeat all, then repeat one, then off. Group counts as all; an unknown mode starts the cycle. */
    static int next(int current) {
        if (current == PlaybackStateCompat.REPEAT_MODE_ALL || current == PlaybackStateCompat.REPEAT_MODE_GROUP) return PlaybackStateCompat.REPEAT_MODE_ONE;
        if (current == PlaybackStateCompat.REPEAT_MODE_ONE) return PlaybackStateCompat.REPEAT_MODE_NONE;
        return PlaybackStateCompat.REPEAT_MODE_ALL;
    }

    static boolean isOn(int mode) {
        return mode == PlaybackStateCompat.REPEAT_MODE_ONE || mode == PlaybackStateCompat.REPEAT_MODE_ALL || mode == PlaybackStateCompat.REPEAT_MODE_GROUP;
    }

    /** What a screen reader says for the button. */
    static String label(int mode) {
        if (mode == PlaybackStateCompat.REPEAT_MODE_ONE) return "Repeat one";
        if (mode == PlaybackStateCompat.REPEAT_MODE_ALL || mode == PlaybackStateCompat.REPEAT_MODE_GROUP) return "Repeat all";
        return "Repeat off";
    }
}

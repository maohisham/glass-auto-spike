package com.glass.headunit;

import android.support.v4.media.session.PlaybackStateCompat;

/** Which controls make sense for the playing app: radio has no shuffle and no track length. Pure. */
final class ControlsPolicy {

    private ControlsPolicy() { }

    /** Radio apps have no lyrics: the panel shows a station card instead. Only VRadio is known as radio. */
    static boolean isRadio(String pkg) { return Proxy.VRADIO.equals(pkg); }

    /** Shuffle only for a music player that offers it; never for VRadio or when nothing is playing. */
    static boolean showShuffle(String pkg, long actions) {
        if (pkg == null || Proxy.VRADIO.equals(pkg)) return false;
        return (actions & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0;
    }

    /** Repeat follows the same rule as shuffle: a music player that offers it; never VRadio or when nothing is playing. */
    static boolean showRepeat(String pkg, long actions) {
        if (pkg == null || Proxy.VRADIO.equals(pkg)) return false;
        return (actions & PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0;
    }

    /** The progress bar needs a track length; a live stream has none. */
    static boolean showProgress(String pkg, long durationMs) {
        if (pkg == null || Proxy.VRADIO.equals(pkg)) return false;
        return durationMs > 0;
    }
}

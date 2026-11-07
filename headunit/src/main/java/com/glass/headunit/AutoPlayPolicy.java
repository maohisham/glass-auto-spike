package com.glass.headunit;

/** Whether to resume the last player when the panel starts. Pure. */
final class AutoPlayPolicy {

    private AutoPlayPolicy() { }

    /** Only when the setting is on, a last player is known and still installed, nothing is playing, and we have not tried yet this start. */
    static boolean shouldStart(boolean enabled, String lastPlayer, boolean installed, boolean anythingPlaying, boolean alreadyTried) {
        if (!enabled || alreadyTried || anythingPlaying || !installed) return false;
        return lastPlayer != null && !lastPlayer.isEmpty();
    }
}

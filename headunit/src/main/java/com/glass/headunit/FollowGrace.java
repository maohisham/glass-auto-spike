package com.glass.headunit;

/**
 * Players briefly drop their media session when a track changes. The panel waits this long before deciding the player is
 * really gone (and showing the idle screen), so a song change does not make the screen flash. Pure.
 */
final class FollowGrace {

    /** Long enough for a few of the 2-second checks, short enough that a closed player clears soon. */
    static final long GRACE_MS = 6_000L;

    private FollowGrace() { }

    /** The time the player first went missing (0 = it is here). Call at every check. */
    static long mark(long lostSinceMs, boolean playerPresent, long nowMs) {
        if (playerPresent) return 0L;
        return lostSinceMs == 0L ? nowMs : lostSinceMs;
    }

    static boolean shouldDetach(long lostSinceMs, long nowMs) {
        return lostSinceMs != 0L && nowMs - lostSinceMs >= GRACE_MS;
    }
}

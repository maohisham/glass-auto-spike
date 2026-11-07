package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshPolicyTest {

    private static final long MIN = 60_000L;
    private static final long NOW = 100 * MIN;

    @Test
    public void staleDataRefreshesWhenOnline() {
        assertTrue(RefreshPolicy.shouldRefresh(NOW, true, false, NOW - 5 * MIN, false, 0, false));   // yesterday's prayer
        assertTrue(RefreshPolicy.shouldRefresh(NOW, true, true, NOW - 31 * MIN, false, 0, false));   // weather older than 30 min
        assertTrue(RefreshPolicy.shouldRefresh(NOW, false, true, NOW, false, 0, false));             // no prayer data at all
    }

    @Test
    public void freshDataDoesNotRefresh() {
        assertFalse(RefreshPolicy.shouldRefresh(NOW, true, true, NOW - 5 * MIN, false, 0, false));
    }

    @Test
    public void whileOfflineNothingRunsBeforeTheRetryTimeEvenIfStale() {
        // the bug: stale data bypassed the 120 s retry, so a failing source was hit every 60 s
        assertFalse(RefreshPolicy.shouldRefresh(NOW, true, false, NOW - 90 * MIN, true, NOW + 60_000L, false));
        assertFalse(RefreshPolicy.shouldRefresh(NOW, false, true, 0, true, NOW + 1, false));
    }

    @Test
    public void whileOfflineItRetriesOnceTheRetryTimeHasPassed() {
        assertTrue(RefreshPolicy.shouldRefresh(NOW, true, true, NOW - 5 * MIN, true, NOW, false));
    }

    @Test
    public void changingTheManualZoneRefreshesAtOnce() {
        assertTrue(RefreshPolicy.shouldRefresh(NOW, true, true, NOW - MIN, false, 0, true));
    }
}

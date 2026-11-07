package com.glass.headunit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FetchQueueTest {

    @Test
    public void firstRequestStartsRightAway() {
        assertTrue(new FetchQueue().request(3.0, 101.7));
    }

    @Test
    public void aRequestDuringAFetchIsRememberedNotDropped() {
        FetchQueue q = new FetchQueue();
        assertTrue(q.request(3.0, 101.7));                 // yesterday's parking spot, fetch starts
        assertFalse(q.request(2.7, 101.7));                // the real fix arrives while it runs
        assertArrayEquals(new double[]{2.7, 101.7}, q.finished(), 0.0001);   // run it as soon as the first ends
        assertNull(q.finished());                          // and only once
    }

    @Test
    public void onlyTheNewestWaitingRequestIsKept() {
        FetchQueue q = new FetchQueue();
        q.request(1, 1);
        q.request(2, 2);
        q.request(3, 3);
        assertArrayEquals(new double[]{3, 3}, q.finished(), 0.0001);
    }

    @Test
    public void afterEverythingFinishedANewRequestStartsAgain() {
        FetchQueue q = new FetchQueue();
        q.request(1, 1);
        assertNull(q.finished());
        assertTrue(q.request(2, 2));
    }

    @Test
    public void aSecondRequestForTheSameSpotIsNotQueued() {
        // seen on the phone: the first fix and the periodic check asked for the same place a second apart
        FetchQueue q = new FetchQueue();
        assertTrue(q.request(2.7000, 101.7000));
        assertFalse(q.request(2.7001, 101.7000));   // about 11 m away: nothing new to learn
        assertNull(q.finished());
    }
}

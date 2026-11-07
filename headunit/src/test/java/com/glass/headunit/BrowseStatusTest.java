package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BrowseStatusTest {

    @Test
    public void onlyARealRefusalIsRemembered() {
        assertEquals(BrowseStatus.REFUSED, BrowseStatus.fromError("connection REFUSED"));
        assertEquals(BrowseStatus.REFUSED, BrowseStatus.fromError("no media browser service (not installed?)"));
    }

    @Test
    public void aTimeoutOrABrowseErrorIsNotAVerdict() {
        assertEquals(BrowseStatus.UNKNOWN, BrowseStatus.fromError("timed out after 8 s (app may not have started)"));
        assertEquals(BrowseStatus.UNKNOWN, BrowseStatus.fromError("browse error for abc"));
        assertEquals(BrowseStatus.UNKNOWN, BrowseStatus.fromError("connection suspended"));
    }

    @Test
    public void noErrorMeansAllowed() {
        assertEquals(BrowseStatus.ALLOWED, BrowseStatus.fromError(null));
    }
}

package com.glass.headunit;

/** The one trip shared by the whole app, so closing and reopening the panel does not lose it. */
final class DriveTrip {

    private static final EcoScore TRIP = new EcoScore();

    private DriveTrip() { }

    static EcoScore trip() { return TRIP; }
}

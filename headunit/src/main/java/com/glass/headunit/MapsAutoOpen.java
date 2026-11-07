package com.glass.headunit;

/** Whether the panel should open the map app beside itself as it starts. Pure. */
final class MapsAutoOpen {

    private MapsAutoOpen() { }

    /** Only on a fresh start (not when returning from Settings), with the setting on and a map app installed, and never in the demo. */
    static boolean shouldOpen(boolean settingOn, boolean mapsInstalled, boolean freshStart, boolean demo) {
        return settingOn && mapsInstalled && freshStart && !demo;
    }
}

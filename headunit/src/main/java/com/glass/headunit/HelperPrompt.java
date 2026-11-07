package com.glass.headunit;

/** When the panel should ask the user to turn on the window helper. Pure. */
final class HelperPrompt {

    private HelperPrompt() { }

    /** Only the helper can move the divider, so ask when it is off and the user wants something other than the unit's own 50:50. */
    static boolean shouldShow(boolean helperOn, float mapShare) { return !helperOn && mapShare > 0.505f; }
}

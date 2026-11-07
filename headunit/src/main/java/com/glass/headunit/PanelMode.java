package com.glass.headunit;

/** Which panel layout fits the width it was given. */
enum PanelMode {
    COMPACT, REGULAR;

    static PanelMode forWidthDp(int dp) { return dp < 400 ? COMPACT : REGULAR; }
}

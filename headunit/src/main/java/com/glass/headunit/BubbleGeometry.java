package com.glass.headunit;

/** Where the floating bubble starts and how big its picker is, from the screen and the map/panel split. Pure. */
final class BubbleGeometry {

    private BubbleGeometry() { }

    /** {x, y} of the bubble's top-left corner: inside the panel's half so it does not cover Google Maps. mapShare is the effective share. */
    static int[] start(int screenW, int screenH, float mapShare, boolean swapSides, int size, int margin) {
        int x = swapSides ? margin : screenW - size - margin;
        int y = Math.round(screenH * 0.55f);
        return new int[]{x, Math.min(y, screenH - size - margin)};
    }

    /** {left, top, width, height} of the picker overlay: exactly the panel's half. */
    static int[] picker(int screenW, int screenH, float mapShare, boolean swapSides) {
        int mapW = Math.round(screenW * mapShare);
        int panelW = screenW - mapW;
        return swapSides ? new int[]{0, 0, Math.round(screenW * (1f - mapShare)), screenH}
                         : new int[]{mapW, 0, panelW, screenH};
    }
}

package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MapAppsTest {

    private static String[] app(String pkg, String label) { return new String[]{pkg, label}; }

    private static List<String[]> installed() {
        return Arrays.asList(
                app("com.calculator", "Calculator"),
                app("com.waze", "Waze"),
                app("com.google.android.apps.maps", "Maps"),
                app("com.camera", "Camera"),
                app("com.sygic.aura", "Sygic GPS Navigation"));
    }

    @Test
    public void googleMapsIsFirstThenWazeThenSygic() {
        List<String[]> out = MapApps.order(installed());
        assertEquals("com.google.android.apps.maps", out.get(0)[0]);
        assertEquals("com.waze", out.get(1)[0]);
        assertEquals("com.sygic.aura", out.get(2)[0]);
    }

    @Test
    public void otherAppsFollowAlphabetically() {
        List<String[]> out = MapApps.order(installed());
        assertEquals("Calculator", out.get(3)[1]);
        assertEquals("Camera", out.get(4)[1]);
    }

    @Test
    public void malaysianAppsAreRecognisedByTheirName() {
        assertTrue(MapApps.isNavigation("my.gov.x", "Teras Navigation"));
        assertTrue(MapApps.isNavigation("com.y", "TUJU"));
        assertTrue(MapApps.isNavigation("com.z", "Pintaz Maps"));
        assertFalse(MapApps.isNavigation("com.calculator", "Calculator"));
    }

    @Test
    public void splitPutsNavigationAppsUpFrontAndTheRestUnderOther() {
        MapApps.Split s = MapApps.split(installed());
        assertEquals(3, s.navigation.size());
        assertEquals(2, s.other.size());
        assertEquals("Calculator", s.other.get(0)[1]);
    }

    @Test
    public void aSavedAppThatIsInstalledIsUsed() {
        assertEquals("com.waze", MapApps.choose("com.waze", installed()));
    }

    @Test
    public void nothingSavedMeansGoogleMapsWhenItIsInstalled() {
        assertEquals("com.google.android.apps.maps", MapApps.choose("", installed()));
    }

    @Test
    public void aSavedAppThatWasUninstalledFallsBackToTheBestNavigationApp() {
        assertEquals("com.google.android.apps.maps", MapApps.choose("com.gone", installed()));
    }

    @Test
    public void withoutGoogleMapsTheNextNavigationAppIsUsed() {
        List<String[]> noGoogle = Arrays.asList(app("com.sygic.aura", "Sygic"), app("com.waze", "Waze"));
        assertEquals("com.waze", MapApps.choose("", noGoogle));
    }

    @Test
    public void noNavigationAppAndNothingSavedMeansNoMapApp() {
        List<String[]> only = java.util.Collections.singletonList(app("com.calculator", "Calculator"));
        assertNull(MapApps.choose("", only));
    }

    @Test
    public void anyInstalledAppCanBeChosenEvenIfNotKnownAsNavigation() {
        assertEquals("com.calculator", MapApps.choose("com.calculator", installed()));
    }
}

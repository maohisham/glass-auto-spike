package com.glass.headunit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FixChoiceTest {

    private static final Zones.Zone SGR01 = new Zones.Zone("SGR01", "Sepang", 3.3, 101.5);

    @Test
    public void aRealLastKnownFixIsUsed() {
        assertArrayEquals(new double[]{2.7, 101.7}, FixChoice.choose(new double[]{2.7, 101.7}, null), 0.0001);
    }

    @Test
    public void aZeroZeroFixIsNeverUsedEvenWhenItIsTheNewest() {
        assertNull(FixChoice.choose(new double[]{0, 0}, null));
        assertArrayEquals(new double[]{3.3, 101.5}, FixChoice.choose(new double[]{0, 0}, SGR01), 0.0001);
    }

    @Test
    public void noFixFallsBackToTheManualZoneCentreOrNothing() {
        assertArrayEquals(new double[]{3.3, 101.5}, FixChoice.choose(null, SGR01), 0.0001);
        assertNull(FixChoice.choose(null, null));
    }

    @Test
    public void aManualZoneDoesNotOverrideARealFix() {
        assertArrayEquals(new double[]{2.7, 101.7}, FixChoice.choose(new double[]{2.7, 101.7}, SGR01), 0.0001);
    }
}

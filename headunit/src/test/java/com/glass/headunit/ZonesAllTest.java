package com.glass.headunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ZonesAllTest {

    @Test
    public void listsEveryZoneSortedByCode() {
        List<Zones.Zone> all = Zones.all();
        assertTrue(all.size() > 50);
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).code.compareTo(all.get(i).code) < 0);
        }
        assertNotNull(Zones.byCode("SGR01"));
        assertEquals("SGR01", find(all, "SGR01").code);
        assertEquals("WLY01", find(all, "WLY01").code);
    }

    private static Zones.Zone find(List<Zones.Zone> all, String code) {
        for (Zones.Zone z : all) if (z.code.equals(code)) return z;
        throw new AssertionError("missing " + code);
    }
}

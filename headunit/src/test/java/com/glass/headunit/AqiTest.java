package com.glass.headunit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AqiTest {

    @Test
    public void categoriesFollowTheUsScale() {
        assertEquals("Good", Aqi.category(0).name);
        assertEquals("Good", Aqi.category(50).name);
        assertEquals("Moderate", Aqi.category(51).name);
        assertEquals("Moderate", Aqi.category(100).name);
        assertEquals("Unhealthy for sensitive groups", Aqi.category(101).name);
        assertEquals("Unhealthy for sensitive groups", Aqi.category(150).name);
        assertEquals("Unhealthy", Aqi.category(151).name);
        assertEquals("Unhealthy", Aqi.category(200).name);
        assertEquals("Very unhealthy", Aqi.category(201).name);
        assertEquals("Very unhealthy", Aqi.category(300).name);
        assertEquals("Hazardous", Aqi.category(301).name);
    }

    @Test
    public void eachCategoryHasItsOwnColourAndAdvice() {
        assertEquals(0xFF66DD88, Aqi.category(20).color);
        assertEquals(0xFFFFCC44, Aqi.category(80).color);
        assertEquals(0xFFFF6655, Aqi.category(180).color);
        for (int v : new int[]{10, 80, 120, 180, 250, 400}) assertEquals(false, Aqi.category(v).advice.isEmpty());
    }

    @Test
    public void markerPositionIsAFractionOfTheScale() {
        assertEquals(0f, Aqi.fraction(0), 0.001f);
        assertEquals(0.5f, Aqi.fraction(150), 0.001f);
        assertEquals(1f, Aqi.fraction(300), 0.001f);
        assertEquals(1f, Aqi.fraction(450), 0.001f);   // clamped
        assertEquals(0f, Aqi.fraction(-5), 0.001f);
    }
}

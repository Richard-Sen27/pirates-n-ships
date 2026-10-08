package com.richardsenger.piratesnships.world.treasure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasureBearingTest {

    @Test
    void eightPointsWithNorthAsMinusZ() {
        assertEquals(TreasureBearing.Point.N, TreasureBearing.of(0, 0, 0, -100).point());
        assertEquals(TreasureBearing.Point.E, TreasureBearing.of(0, 0, 100, 0).point());
        assertEquals(TreasureBearing.Point.S, TreasureBearing.of(0, 0, 0, 100).point());
        assertEquals(TreasureBearing.Point.W, TreasureBearing.of(0, 0, -100, 0).point());
        assertEquals(TreasureBearing.Point.NE, TreasureBearing.of(0, 0, 100, -100).point());
        assertEquals(TreasureBearing.Point.SE, TreasureBearing.of(0, 0, 100, 100).point());
        assertEquals(TreasureBearing.Point.SW, TreasureBearing.of(0, 0, -100, 100).point());
        assertEquals(TreasureBearing.Point.NW, TreasureBearing.of(0, 0, -100, -100).point());
    }

    @Test
    void sectorsAreFortyFiveDegreesWide() {
        // 22 degrees east of north is still north, 23 is north-east
        double r = 1000;
        assertEquals(TreasureBearing.Point.N, TreasureBearing.of(0, 0, r * Math.sin(Math.toRadians(22)), -r * Math.cos(Math.toRadians(22))).point());
        assertEquals(TreasureBearing.Point.NE, TreasureBearing.of(0, 0, r * Math.sin(Math.toRadians(23)), -r * Math.cos(Math.toRadians(23))).point());
        assertEquals(TreasureBearing.Point.N, TreasureBearing.of(0, 0, -r * Math.sin(Math.toRadians(22)), -r * Math.cos(Math.toRadians(22))).point());
    }

    @Test
    void distanceIsHorizontalAndRounded() {
        TreasureBearing b = TreasureBearing.of(10.5, 20.5, 10.5 - 240.4, 20.5 - 240.4);
        assertEquals(TreasureBearing.Point.NW, b.point());
        assertEquals(340, b.distance());
        assertEquals("NW, 340 blocks", b.english());
    }

    @Test
    void closeByItSaysDigHere() {
        TreasureBearing b = TreasureBearing.of(0, 0, 1.5, 1.5);
        assertTrue(b.here());
        assertEquals("X marks the spot: dig here!", b.english());
        assertEquals(TreasureBearing.Point.E, TreasureBearing.of(0, 0, 3, 0).point(), "three blocks away it points again");
    }
}

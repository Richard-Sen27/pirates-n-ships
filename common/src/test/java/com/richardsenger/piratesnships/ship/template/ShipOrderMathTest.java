package com.richardsenger.piratesnships.ship.template;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SW1: price, build time, materials, finish day and the free-berth rule. */
class ShipOrderMathTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void priceIsTheTemplatePriceTimesTheFactor() {
        assertEquals(400, ShipOrderMath.price(400, 1.0));
        assertEquals(300, ShipOrderMath.price(300, 1.0));
        assertEquals(600, ShipOrderMath.price(400, 1.5));
        assertEquals(133, ShipOrderMath.price(400, 1.0 / 3.0));
        assertEquals(0, ShipOrderMath.price(400, 0.0));
        assertEquals(0, ShipOrderMath.price(400, -2.0));
    }

    @Test
    void buildDaysScaleWithTheBlockCount() {
        assertEquals(1.0, ShipOrderMath.buildDays(1.0, 500), 1e-9);
        assertEquals(1.36, ShipOrderMath.buildDays(1.0, 680), 1e-9);
        assertEquals(2.72, ShipOrderMath.buildDays(2.0, 680), 1e-9);
        assertEquals(0.0, ShipOrderMath.buildDays(0.0, 680), 1e-9);
        assertEquals(0.0, ShipOrderMath.buildDays(-1.0, 680), 1e-9);
    }

    @Test
    void materialsAreALogPerTwentyBlocksAndAWoolPerYard() {
        assertEquals(34, ShipOrderMath.logs(680, 1.0));
        assertEquals(35, ShipOrderMath.logs(682, 1.0)); // 34.1 rounds up
        assertEquals(17, ShipOrderMath.logs(680, 0.5));
        assertEquals(68, ShipOrderMath.logs(680, 2.0));
        assertEquals(0, ShipOrderMath.logs(680, 0.0));
        assertEquals(16, ShipOrderMath.wool(16, 1.0));
        assertEquals(8, ShipOrderMath.wool(16, 0.5));
        assertEquals(3, ShipOrderMath.wool(5, 0.5)); // 2.5 rounds up
        assertEquals(0, ShipOrderMath.wool(0, 3.0));
    }

    @Test
    void finishDayAndReadiness() {
        assertEquals(0.5, ShipOrderMath.day(12000), 1e-9);
        assertEquals(3.0, ShipOrderMath.day(72000), 1e-9);
        double finish = ShipOrderMath.finishDay(2.25, 1.36);
        assertEquals(3.61, finish, 1e-9);
        assertFalse(ShipOrderMath.ready(finish, 3.0));
        assertTrue(ShipOrderMath.ready(finish, 3.61));
        assertTrue(ShipOrderMath.ready(finish, 10.0));
        assertEquals(0.61, ShipOrderMath.remaining(finish, 3.0), 1e-9);
        assertEquals(0.0, ShipOrderMath.remaining(finish, 5.0), 1e-9);
        assertEquals(2.25, ShipOrderMath.finishDay(2.25, -1.0), 1e-9);
    }

    @Test
    void daysAreShownRoundedUpToATenth() {
        assertEquals("1.5", ShipOrderMath.formatDays(1.5));
        assertEquals("1.4", ShipOrderMath.formatDays(1.36));
        assertEquals("0.8", ShipOrderMath.formatDays(0.71));
        assertEquals("0.1", ShipOrderMath.formatDays(0.001));
        assertEquals("0.0", ShipOrderMath.formatDays(0.0));
        assertEquals("2.0", ShipOrderMath.formatDays(2.0));
    }

    @Test
    void orderLimit() {
        assertTrue(ShipOrderMath.takesOrder(0, 3));
        assertTrue(ShipOrderMath.takesOrder(2, 3));
        assertFalse(ShipOrderMath.takesOrder(3, 3));
        assertFalse(ShipOrderMath.takesOrder(0, 0));
    }

    @Test
    void aBerthIsTakenWhenAShipCoversItOrTheFootprint() {
        BlockPos berth = new BlockPos(100, 62, 200);
        BoundingBox footprint = new BoundingBox(101, 60, 186, 109, 80, 214);
        // no ships
        assertFalse(ShipOrderMath.occupied(berth, footprint, List.of()));
        // a ship far away
        assertFalse(ShipOrderMath.occupied(berth, footprint, List.of(new AABB(0, 60, 0, 10, 80, 30))));
        // a ship over the berth's centre block only
        assertTrue(ShipOrderMath.occupied(berth, footprint, List.of(new AABB(99.5, 61, 199.5, 100.5, 63, 200.5))));
        // a ship overlapping the footprint's last block
        assertTrue(ShipOrderMath.occupied(berth, footprint, List.of(new AABB(109.5, 70, 214.2, 120, 75, 230))));
        // touching the footprint's outer face is not an overlap
        assertFalse(ShipOrderMath.occupied(berth, footprint, List.of(new AABB(110, 60, 186, 120, 80, 214))));
        // a ship on the other side of the pier
        assertFalse(ShipOrderMath.occupied(berth, footprint, List.of(new AABB(90, 60, 186, 99, 80, 214))));
    }
}

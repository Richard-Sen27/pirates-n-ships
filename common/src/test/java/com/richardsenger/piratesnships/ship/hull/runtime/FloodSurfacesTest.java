package com.richardsenger.piratesnships.ship.hull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** FLD1: which compartments show a water surface, at what relative level, and when a payload is due. */
class FloodSurfacesTest {

    /** A closed 5×4×5 box at plot origin x {@code ox}: one 3×2×3 compartment at x ox+2..4, y 2..3, z 2..4. */
    private static HullAnalysis box(int ox) {
        HullGrid.Builder b = HullGrid.builder(7, 6, 7).origin(ox, 0, 0);
        b.fill(0, 0, 0, 6, 5, 6, CellKind.AIR);
        b.fill(1, 1, 1, 5, 4, 5, CellKind.SOLID);
        b.fill(2, 2, 2, 4, 3, 4, CellKind.AIR);
        return HullAnalyzer.analyze(b.build());
    }

    @Test
    void halfFullBottomLayerIsHalfABlockAboveTheFloorEvenFarOut() {
        // plot coordinates are in the millions; the relative level must stay exact
        FloodSimulation sim = new FloodSimulation(box(29_000_000), FloodParams.DEFAULTS);
        sim.setVolume(0, 4.5);
        List<FloodSurfacePayload.Surface> s = new FloodSurfaces().build(sim);
        assertEquals(1, s.size());
        assertEquals(18, s.get(0).cells().count(), "the surface carries the whole compartment");
        assertEquals(29_000_002, s.get(0).cells().minX());
        assertEquals(2, s.get(0).cells().minY());
        assertEquals(0.5f, s.get(0).level(), 1e-4f);

        sim.setVolume(0, 13.5); // full bottom layer and half the top one
        assertEquals(1.5f, new FloodSurfaces().build(sim).get(0).level(), 1e-4f);
    }

    @Test
    void dryAndFullCompartmentsShowNoSurface() {
        FloodSimulation sim = new FloodSimulation(box(0), FloodParams.DEFAULTS);
        FloodSurfaces f = new FloodSurfaces();
        assertTrue(f.build(sim).isEmpty(), "dry");
        sim.setVolume(0, FloodSurfaces.MIN_VOLUME / 2);
        assertTrue(f.build(sim).isEmpty(), "a film below the minimum");
        sim.setVolume(0, 18);
        assertTrue(f.build(sim).isEmpty(), "full: the surface would lie on the ceiling");
        sim.setVolume(0, 17.9);
        assertEquals(1, f.build(sim).size());
    }

    @Test
    void cellsAreCachedPerAnalysis() {
        HullAnalysis a = box(0);
        FloodSurfaces f = new FloodSurfaces();
        assertTrue(f.cellsOf(a) == f.cellsOf(a));
        assertFalse(f.cellsOf(a) == f.cellsOf(box(0)));
    }

    @Test
    void dueOnFirstCallThenOnlyOnChangeAndNotTooOften() {
        FloodSimulation sim = new FloodSimulation(box(0), FloodParams.DEFAULTS);
        FloodSurfaces f = new FloodSurfaces();
        List<FloodSurfacePayload.Surface> empty = f.build(sim);
        assertTrue(f.due(0, 10, empty), "the first state is always sent");
        f.markSent(0, empty);
        assertFalse(f.due(50, 10, empty), "nothing changed");

        sim.setVolume(0, 4.5);
        List<FloodSurfacePayload.Surface> half = f.build(sim);
        assertFalse(f.due(5, 10, half), "the last payload was only 5 ticks ago");
        assertTrue(f.due(60, 10, half));
        f.markSent(60, half);

        sim.setVolume(0, 4.5 + 9 * FloodSurfaces.LEVEL_EPSILON / 2); // half the epsilon
        assertFalse(f.due(100, 10, f.build(sim)), "a level change below the epsilon");
        sim.setVolume(0, 4.5 + 9 * FloodSurfaces.LEVEL_EPSILON * 3);
        assertFalse(f.due(65, 10, f.build(sim)), "within the interval");
        assertTrue(f.due(70, 10, f.build(sim)));

        f.markSent(70, f.build(sim));
        sim.setVolume(0, 0);
        assertTrue(f.due(80, 10, f.build(sim)), "drained: the clearing payload is due");
    }

    @Test
    void payloadRoundTrips() {
        FloodSimulation sim = new FloodSimulation(box(1_000_000), FloodParams.DEFAULTS);
        sim.setVolume(0, 6);
        FloodSurfacePayload p = new FloodSurfacePayload(UUID.randomUUID(), 0f, 1f, 0f, 10, new FloodSurfaces().build(sim));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FloodSurfacePayload.CODEC.encode(buf, p);
        int bytes = buf.readableBytes();
        FloodSurfacePayload back = FloodSurfacePayload.CODEC.decode(buf);
        assertEquals(p, back);
        assertTrue(bytes < 64, "one 3×2×3 compartment should be a few dozen bytes, was " + bytes);
    }
}

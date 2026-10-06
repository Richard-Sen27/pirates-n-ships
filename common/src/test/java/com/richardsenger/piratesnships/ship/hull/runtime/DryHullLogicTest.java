package com.richardsenger.piratesnships.ship.hull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.CellFaces;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.PartialCellRule;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

class DryHullLogicTest {

    private static final double EPS = 1e-9;

    // ---------------------------------------------------------------- sea level

    @Test
    void surfaceIsMedianAndNeedsEnoughColumns() {
        assertEquals(62.9, SeaLevel.surface(new double[] {62.9, 62.9, 70, Double.NaN, 62.9}, 3), EPS);
        assertEquals(63.0, SeaLevel.surface(new double[] {62, 64, Double.NaN}, 2), EPS);
        assertTrue(Double.isNaN(SeaLevel.surface(new double[] {62, Double.NaN, Double.NaN}, 3)));
        assertTrue(Double.isNaN(SeaLevel.surface(new double[0], 1)));
    }

    @Test
    void seaInShipFrameLevelAndTilted() {
        // level ship whose plot point (100, 10, 100) sits at world y 60; sea at 62.5 -> 2.5 above along up
        assertEquals(12.5, SeaLevel.inShipFrame(HullVec.UP, new HullVec(100, 10, 100), 60, 62.5), EPS);
        // tilted 30° about z: up = (sin, cos, 0); height of the reference is up·ref, sea adds the world offset
        double a = Math.toRadians(30);
        HullVec up = SeaLevel.up(Math.sin(a), Math.cos(a), 0);
        HullVec ref = new HullVec(4, 2, 0);
        assertEquals(up.dot(ref) + 1.0, SeaLevel.inShipFrame(up, ref, 50, 51), EPS);
        assertEquals(SeaLevel.NO_WATER, SeaLevel.inShipFrame(up, ref, 50, Double.NaN));
        assertEquals(HullVec.UP, SeaLevel.up(0, 0, 0));
        assertEquals(1.0, SeaLevel.up(0, 3, 4).length(), EPS);
    }

    // ---------------------------------------------------------------- buoyancy

    @Test
    void buoyancyForcesPointAndScale() {
        HullVec dryC = new HullVec(1, 2, 3), floodC = new HullVec(1, 0.5, 3);
        FloodReport r = new FloodReport(new double[] {4}, new double[] {1}, 4, floodC, 10, dryC, 0, 0, 0);
        List<HullBuoyancy.PointForce> f = HullBuoyancy.forces(r, HullVec.UP, HullBuoyancy.Params.DEFAULTS);
        assertEquals(2, f.size());
        assertEquals(dryC, f.get(0).point());
        assertEquals(10 * 10.5, f.get(0).force().y(), EPS);
        assertEquals(floodC, f.get(1).point());
        assertEquals(-4 * 10.5, f.get(1).force().y(), EPS);

        // tilted frame: forces follow the local up vector
        HullVec up = SeaLevel.up(0, 1, 1);
        HullBuoyancy.PointForce d = HullBuoyancy.forces(r, up, new HullBuoyancy.Params(true, 2, false, 1, 10)).get(0);
        assertEquals(200 * up.z(), d.force().z(), EPS);
        assertEquals(1, HullBuoyancy.forces(r, up, new HullBuoyancy.Params(true, 2, false, 1, 10)).size());
    }

    @Test
    void noForcesWithoutVolume() {
        FloodReport r = new FloodReport(new double[] {0}, new double[] {0}, 0, null, 0, null, 0, 0, 0);
        assertTrue(HullBuoyancy.forces(r, HullVec.UP, HullBuoyancy.Params.DEFAULTS).isEmpty());
    }

    // ---------------------------------------------------------------- breaches

    @Test
    void breachBookkeeping() {
        BreachSet b = new BreachSet();
        BlockPos p = new BlockPos(5, 6, 7);
        assertTrue(b.onBlockChanged(p, CellKind.SOLID, CellKind.AIR), "removed hull block is a breach");
        assertFalse(b.onBlockChanged(p, CellKind.SOLID, CellKind.AIR), "already a breach");
        assertFalse(b.onBlockChanged(new BlockPos(1, 1, 1), CellKind.AIR, CellKind.AIR), "air stays air");
        assertFalse(b.onBlockChanged(new BlockPos(2, 1, 1), CellKind.SOLID, CellKind.OPENING), "a door in a wall is no breach");
        assertFalse(b.onBlockChanged(new BlockPos(3, 1, 1), null, CellKind.AIR), "outside the grid");
        assertTrue(b.contains(p));
        BreachSet copy = new BreachSet();
        copy.load(b.toArray());
        assertTrue(copy.contains(p) && copy.size() == 1);
        assertTrue(b.onBlockChanged(p, CellKind.AIR, CellKind.SOLID), "patched");
        assertTrue(b.isEmpty());
    }

    // ---------------------------------------------------------------- cell sets and payload

    @Test
    void cellSetCropsAndRoundTrips() {
        HullGrid g = HullGrid.builder(6, 5, 7).origin(1000, 64, -2000).build();
        BitSet cells = new BitSet();
        cells.set(g.index(1, 2, 3));
        cells.set(g.index(4, 2, 5));
        cells.set(g.index(2, 4, 3));
        CellSet s = CellSet.fromGrid(g, cells);
        assertEquals(3, s.count());
        assertEquals(1001, s.minX());
        assertEquals(66, s.minY());
        assertEquals(-1997, s.minZ());
        assertEquals(4, s.sizeX());
        assertEquals(3, s.sizeY());
        assertEquals(3, s.sizeZ());
        assertTrue(s.contains(1004, 66, -1995) && s.contains(1002, 68, -1997));
        assertFalse(s.contains(1001, 66, -1996) || s.contains(0, 0, 0));

        ByteBuf buf = Unpooled.buffer();
        CellSet.CODEC.encode(buf, s);
        CellSet back = CellSet.CODEC.decode(buf);
        assertEquals(s, back);

        ByteBuf list = Unpooled.buffer();
        var codec = CellSet.CODEC.apply(ByteBufCodecs.list(HullRegionsPayload.MAX_REGIONS));
        codec.encode(list, List.of(s, CellSet.fromGrid(g, new BitSet())));
        List<CellSet> decoded = codec.decode(list);
        assertEquals(s, decoded.get(0));
        assertTrue(decoded.get(1).isEmpty());
        assertTrue(new HullRegionsPayload(UUID.randomUUID(), List.of(s)).regions().contains(s));
    }

    @Test
    void regionWithPartialCellsRoundTripsThroughThePayload() {
        // closed shell 1..5 in a 7³ grid with a bottom-slab floor cell under the room and a top slab as hull bottom
        HullGrid.Builder b = HullGrid.builder(7, 7, 7).origin(5000, 70, -300);
        b.fill(1, 1, 1, 5, 5, 5, CellKind.SOLID).fill(2, 2, 2, 4, 4, 4, CellKind.AIR);
        int sides = CellFaces.NORTH | CellFaces.SOUTH | CellFaces.WEST | CellFaces.EAST;
        b.partial(3, 1, 3, CellFaces.UP | sides).partial(2, 1, 2, CellFaces.DOWN | sides);
        HullAnalysis a = HullAnalyzer.analyze(b.build());
        BitSet dry = (BitSet) a.compartments().get(0).cells().clone();
        dry.or(PartialCellRule.of(a).additions(List.of(dry))[0]);
        CellSet s = CellSet.fromGrid(a.grid(), dry);
        assertEquals(28, s.count(), "27 room cells and the bottom slab");
        assertTrue(s.contains(5003, 71, -297), "the bottom slab's cell");
        assertFalse(s.contains(5002, 71, -298), "the top slab over the sea");
        assertEquals(4, s.sizeY());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        HullRegionsPayload p = new HullRegionsPayload(UUID.randomUUID(), List.of(s));
        HullRegionsPayload.CODEC.encode(buf, p);
        HullRegionsPayload back = HullRegionsPayload.CODEC.decode(buf);
        assertEquals(p.ship(), back.ship());
        assertEquals(p.regions(), back.regions());
    }

    @Test
    void cellSetRejectsHugeBoxes() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(0).writeVarInt(0).writeVarInt(0).writeVarInt(4096).writeVarInt(4096).writeVarInt(4096);
        buf.writeLongArray(new long[0]);
        assertThrows(IllegalArgumentException.class, () -> CellSet.CODEC.decode(buf));
    }

    // ---------------------------------------------------------------- persistence

    /** A closed 5×4×5 box: one 3×2×3 compartment. */
    private static HullAnalysis box(int ox) {
        HullGrid.Builder b = HullGrid.builder(7, 6, 7).origin(ox, 0, 0);
        b.fill(0, 0, 0, 6, 5, 6, CellKind.AIR);
        b.fill(1, 1, 1, 5, 4, 5, CellKind.SOLID);
        b.fill(2, 2, 2, 4, 3, 4, CellKind.AIR);
        return HullAnalyzer.analyze(b.build());
    }

    @Test
    void floodStateRoundTripsAndRebindsByPosition() {
        HullAnalysis a = box(0);
        assertEquals(1, a.compartments().size());
        FloodSimulation sim = new FloodSimulation(a, FloodParams.DEFAULTS);
        sim.setVolume(0, 7.25);
        BreachSet breaches = new BreachSet();
        breaches.onBlockChanged(new BlockPos(1, 2, 3), CellKind.SOLID, CellKind.AIR);

        CompoundTag tag = FloodState.capture(breaches, sim).toTag();
        FloodState back = FloodState.fromTag(tag.copy());
        assertEquals(1, back.water().size());
        assertEquals(7.25, back.water().get(0).volume(), EPS);
        assertEquals(1, back.breaches().length);

        FloodSimulation fresh = new FloodSimulation(box(0), FloodParams.DEFAULTS);
        assertEquals(0, back.applyWater(fresh), EPS);
        assertEquals(7.25, fresh.totalVolume(), EPS);

        // a hull that no longer has a compartment at the saved cell loses that water
        FloodSimulation moved = new FloodSimulation(box(50), FloodParams.DEFAULTS);
        assertEquals(7.25, back.applyWater(moved), EPS);
        assertEquals(0, moved.totalVolume(), EPS);
        assertTrue(FloodState.fromTag(new CompoundTag()).isEmpty());
    }

    // ---------------------------------------------------------------- sea at the hull

    @Test
    void hullSurfaceNeedsAThirdOfTheProbesInWater() {
        double n = Double.NaN;
        // dry dock / cliff: no probe touches water, whatever is around
        assertTrue(Double.isNaN(SeaLevel.hullSurface(new double[] {n, n, n, n, n, n, n, n, n})));
        assertTrue(Double.isNaN(SeaLevel.hullSurface(new double[0])));
        // half on a beach: 3 of 9 probes wet is enough, the wet median is the sea
        assertEquals(62.9, SeaLevel.hullSurface(new double[] {62.9, 62.8, 63.0, n, n, n, n, n, n}), 1e-9);
        // 2 of 9 is aground
        assertTrue(Double.isNaN(SeaLevel.hullSurface(new double[] {62.9, 62.9, n, n, n, n, n, n, n})));
        // a one-column mast-like hull: its single probe decides
        assertEquals(10.5, SeaLevel.hullSurface(new double[] {10.5}), 1e-9);
    }

    @Test
    void probesAreTheLowestSolidCellsOfTheFootprint() {
        // a 5x5 floor at y=1 with one keel block below its center; empty columns around it
        HullGrid.Builder b = HullGrid.builder(7, 4, 7);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                b.set(x, 1, z, CellKind.SOLID);
            }
        }
        b.set(3, 0, 3, CellKind.SOLID);
        java.util.List<int[]> probes = SeaLevel.probes(b.build());
        assertEquals(9, probes.size());
        for (int[] p : probes) {
            assertTrue(p[0] == 1 || p[0] == 3 || p[0] == 5, "x " + p[0]);
            assertEquals(p[0] == 3 && p[2] == 3 ? 0 : 1, p[1], "lowest solid cell of column " + p[0] + "," + p[2]);
        }
        // a single solid column is probed once, an empty grid not at all
        assertEquals(1, SeaLevel.probes(HullGrid.builder(3, 3, 3).set(1, 1, 1, CellKind.SOLID).build()).size());
        assertTrue(SeaLevel.probes(HullGrid.builder(3, 3, 3).build()).isEmpty());
    }
}

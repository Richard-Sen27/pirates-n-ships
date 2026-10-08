package com.richardsenger.piratesnships.worldsim.lane;

import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanePathfinderTest {

    private static final int CELL = 32;
    private static final LanePathfinder.Params P = new LanePathfinder.Params(4.0, 20_000, 8);

    /** Every point along every leg (every block) lies in a sea cell. */
    private static void assertAllAtSea(SeaGrid grid, List<Lane.Point> points) {
        for (int i = 1; i < points.size(); i++) {
            Lane.Point a = points.get(i - 1), b = points.get(i);
            double len = a.distanceTo(b);
            for (double t = 0; t <= len; t += 1.0) {
                double x = a.x() + (b.x() - a.x()) * t / Math.max(1e-9, len);
                double z = a.z() + (b.z() - a.z()) * t / Math.max(1e-9, len);
                int cx = grid.cellOf((int) Math.floor(x)), cz = grid.cellOf((int) Math.floor(z));
                assertTrue(grid.isSea(cx, cz), "leg " + i + " crosses land at " + x + " " + z);
            }
        }
    }

    @Test
    void openSeaIsAStraightLine() {
        SeaGrid sea = SeaGrid.allSea(CELL);
        LanePathfinder.Result r = LanePathfinder.find(sea, 10, 10, 1000, 10, P);
        assertTrue(r.found());
        assertEquals(List.of(new Lane.Point(10, 10), new Lane.Point(1000, 10)), r.waypoints());
        assertEquals(990.0, Lane.length(r.waypoints()), 1e-9);
    }

    @Test
    void diagonalRunsAreMerged() {
        LanePathfinder.Result r = LanePathfinder.find(SeaGrid.allSea(CELL), 16, 16, 16 + 10 * CELL, 16 + 10 * CELL, P);
        assertTrue(r.found());
        assertEquals(2, r.waypoints().size(), "one diagonal leg: " + r.waypoints());
    }

    @Test
    void laneBendsAroundALandBar() {
        // A wall of land at cell x = 5 for cell z in [-6, 6]
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> !(cx == 5 && cz >= -6 && cz <= 6));
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 16 + 10 * CELL, 16, P);
        assertTrue(r.found(), r.status().name());
        assertTrue(r.waypoints().size() > 2, "bends: " + r.waypoints());
        assertTrue(Lane.length(r.waypoints()) > 10 * CELL, "longer than the straight line");
        assertAllAtSea(grid, r.waypoints());
        assertTrue(r.waypoints().stream().anyMatch(p -> Math.abs(grid.cellOf(p.z())) >= 7), "passes beyond the bar's end");
    }

    @Test
    void sealedPortHasNoLane() {
        // A pond of sea cells (|c| <= 2) inside a land ring (3..4), open sea beyond
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> {
            int r = Math.max(Math.abs(cx), Math.abs(cz));
            return r <= 2 || r >= 5;
        });
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 16 + 20 * CELL, 16, P);
        assertEquals(LanePathfinder.Status.NO_PATH, r.status());
        assertTrue(r.waypoints().isEmpty());
        assertTrue(r.expanded() <= 25, "only the pond was searched: " + r.expanded());
    }

    @Test
    void landlockedEndpointBeyondRadiusFails() {
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cx < 100);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 120 * CELL, 16, P);
        assertEquals(LanePathfinder.Status.NO_END, r.status());
    }

    @Test
    void endpointOnLandMovesToTheNearestSeaCell() {
        // Land for cell x >= 10; the destination is two cells inland
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cx < 10);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 11 * CELL + 16, 16, P);
        assertTrue(r.found());
        Lane.Point last = r.waypoints().get(r.waypoints().size() - 1);
        assertEquals(new Lane.Point(9 * CELL + 16, 16), last, "the sea cell beside the coast");
    }

    @Test
    void budgetStopsTheSearch() {
        // A long detour around a wall forces many expansions
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> !(cx == 5 && cz >= -400 && cz <= 400));
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 16 + 10 * CELL, 16, new LanePathfinder.Params(4.0, 200, 8));
        assertEquals(LanePathfinder.Status.BUDGET, r.status());
        assertEquals(200, r.expanded());
    }

    @Test
    void landMarginKeepsLanesOffTheCoast() {
        // Land north of cell z = 0 (cz < 0); both ports on the coast row cz = 0, 30 cells apart
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cz >= 0);
        LanePathfinder.Result hug = LanePathfinder.find(grid, 16, 16, 30 * CELL + 16, 16, new LanePathfinder.Params(1.0, 20_000, 8));
        assertEquals(2, hug.waypoints().size(), "without a margin cost the lane follows the coast");
        LanePathfinder.Result off = LanePathfinder.find(grid, 16, 16, 30 * CELL + 16, 16, P);
        assertTrue(off.found());
        assertTrue(off.waypoints().size() >= 4, "steps off the coast and back: " + off.waypoints());
        for (int i = 1; i < off.waypoints().size() - 1; i++) {
            Lane.Point p = off.waypoints().get(i);
            assertTrue(grid.cellOf(p.z()) >= 1, "interior waypoint off the coast row: " + p);
        }
    }

    @Test
    void diagonalsDoNotCutLandCorners() {
        // Land at (1, 0) and (0, 1): from cell (0,0) to (1,1) the diagonal would squeeze between them
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> !((cx == 1 && cz == 0) || (cx == 0 && cz == 1)));
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, CELL + 16, CELL + 16, P);
        assertTrue(r.found());
        assertTrue(r.waypoints().size() > 2, "goes around: " + r.waypoints());
        assertAllAtSea(grid, r.waypoints());
    }

    @Test
    void positionAlongWalksTheLegs() {
        List<Lane.Point> pts = List.of(new Lane.Point(0, 0), new Lane.Point(100, 0), new Lane.Point(100, 50));
        Lane.Position a = Lane.positionAlong(pts, 40);
        assertEquals(40, a.x(), 1e-9);
        assertEquals(0, a.leg());
        assertEquals(90.0, a.headingDegrees(), 1e-9, "east");
        Lane.Position b = Lane.positionAlong(pts, 120);
        assertEquals(100, b.x(), 1e-9);
        assertEquals(20, b.z(), 1e-9);
        assertEquals(1, b.leg());
        assertEquals(180.0, b.headingDegrees(), 1e-9, "south (+z)");
        Lane.Position end = Lane.positionAlong(pts, 1e6);
        assertEquals(50, end.z(), 1e-9);
        assertEquals(0.0, Lane.heading(new Lane.Point(0, 0), new Lane.Point(0, -5)), 1e-9, "north (−z)");
    }

    @Test
    void riskCountsPirateIslandsNearTheLane() {
        List<Lane.Point> route = List.of(new Lane.Point(0, 0), new Lane.Point(1000, 0));
        assertEquals(0.1, LaneRisk.risk(route, List.of(), 300, 0.1, 0.4), 1e-9);
        double one = LaneRisk.risk(route, List.of(new Lane.Point(500, 200), new Lane.Point(500, 900)), 300, 0.1, 0.4);
        assertEquals(1 - 0.9 * 0.6, one, 1e-9);
        double two = LaneRisk.risk(route, List.of(new Lane.Point(500, 200), new Lane.Point(1100, -100)), 300, 0.1, 0.4);
        assertEquals(1 - 0.9 * 0.36, two, 1e-9);
    }

    @Test
    void laneAndCacheSurviveTheCodec() {
        Lane lane = Lane.of(ResourceLocation.parse("pirates_n_ships:a"), ResourceLocation.parse("pirates_n_ships:b"),
                List.of(new Lane.Point(1, 2), new Lane.Point(-30, 400), new Lane.Point(5, 6)), 1234L);
        Tag tag = Lane.CODEC.encodeStart(NbtOps.INSTANCE, lane).getOrThrow();
        assertEquals(lane, Lane.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        assertEquals(lane, Lane.CODEC.parse(JsonOps.INSTANCE, Lane.CODEC.encodeStart(JsonOps.INSTANCE, lane).getOrThrow()).getOrThrow());

        LaneData.Snapshot snap = new LaneData.Snapshot(3, List.of(lane),
                List.of(new LaneData.Failure(ResourceLocation.parse("pirates_n_ships:c"), ResourceLocation.parse("pirates_n_ships:d"), 99L)));
        Tag st = LaneData.Snapshot.CODEC.encodeStart(NbtOps.INSTANCE, snap).getOrThrow();
        assertEquals(snap, LaneData.Snapshot.CODEC.parse(NbtOps.INSTANCE, st).getOrThrow());
    }

    @Test
    void cacheReturnsTheLaneInEitherDirection() {
        LaneData data = new LaneData();
        ResourceLocation a = ResourceLocation.parse("pirates_n_ships:a"), b = ResourceLocation.parse("pirates_n_ships:b");
        data.checkVersion(1);
        data.putLane(Lane.of(a, b, List.of(new Lane.Point(0, 0), new Lane.Point(10, 0)), 0));
        assertEquals(new Lane.Point(0, 0), data.lane(a, b).orElseThrow().waypoints().get(0));
        assertEquals(new Lane.Point(10, 0), data.lane(b, a).orElseThrow().waypoints().get(0));
        assertEquals(b, data.lane(b, a).orElseThrow().from());
        data.checkVersion(2);
        assertFalse(data.lane(a, b).isPresent(), "a new version drops the cache");
    }
}

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
    void steppedSearchEqualsOneGo() {
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> !(cx == 5 && cz >= -20 && cz <= 20));
        LanePathfinder.Result once = LanePathfinder.find(grid, 16, 16, 16 + 12 * CELL, 16, P);
        LanePathfinder.Search search = new LanePathfinder.Search(grid, 16, 16, 16 + 12 * CELL, 16, P);
        int calls = 0;
        while (!search.done()) {
            search.step(System.nanoTime() - 1); // deadline passed: 8 cells per call
            calls++;
        }
        assertTrue(calls > 1, "spread over several calls: " + calls);
        assertEquals(once, search.result());
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

        LaneData.Snapshot snap = new LaneData.Snapshot(3, Lanes.RULES, List.of(lane),
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
        data.putLane(Lane.of(a, b, List.of(new Lane.Point(0, 0), new Lane.Point(10, 0)), 0));
        data.checkVersion(2, Lanes.RULES + 1);
        assertFalse(data.lane(a, b).isPresent(), "new search rules drop the cache");
    }

    @Test
    void cacheSavedBeforeLane1CarriesTheOldRules() {
        // A WS2 save has no "rules" field: it reads as rules 1 and is dropped by the current rules
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("version", 2);
        LaneData.Snapshot old = LaneData.Snapshot.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(1, old.rules());
        assertTrue(Lanes.RULES > 1, "LANE1 raised the rules");
    }

    // ------------------------------------------------------------------ LANE1: start cell, shores, budget

    @Test
    void berthWithNoSeaWithinTheRadiusReportsNoStart() {
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cx > 20);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 40 * CELL, 16, P);
        assertEquals(LanePathfinder.Status.NO_START, r.status());
        assertEquals(0, r.expanded());
        assertTrue(r.start() == null && r.end() == null);
    }

    @Test
    void berthOnLandStartsInTheNearestWaterCellWithinTheRadius() {
        // The berth's cell (0,0) and its ring are land; the sea starts 3 cells east
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cx >= 3);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 30 * CELL + 16, 16, P);
        assertTrue(r.found(), r.status().name());
        assertEquals(new LanePathfinder.Cell(3, 0), r.start(), "the nearest water cell");
        assertEquals(new Lane.Point(3 * CELL + 16, 16), r.waypoints().get(0), "the lane starts at that cell's centre");
    }

    /**
     * The LANE1 case (seed 2718281828, the seafarer village): the berth's own cell is beach and its nearest ocean cell a
     * small pocket ringed by beach; the open sea lies beyond the beach. Beaches as land: the search dies in the pocket
     * (no_path after a handful of cells). Beaches as shore: the lane crosses the beach and reaches the far port.
     */
    @Test
    void bayRingedByBeachReachesTheSeaOverTheShore() {
        // ocean pocket (cx 1..2, cz -1..1), beach ring around it and the berth cell, open sea from cx >= 6
        SeaGrid.CellPredicate ocean = (cx, cz) -> cx >= 6 || (cx >= 1 && cx <= 2 && Math.abs(cz) <= 1);
        SeaGrid.CellPredicate beach = (cx, cz) -> cx >= -1 && cx <= 5 && Math.abs(cz) <= 3 && !ocean.test(cx, cz);
        SeaGrid landBeaches = SeaGrid.of(CELL, ocean);
        SeaGrid shores = SeaGrid.of(CELL, ocean, beach);
        int toX = 60 * CELL + 16;
        LanePathfinder.Result blocked = LanePathfinder.find(landBeaches, 16, 16, toX, 16, P);
        assertEquals(LanePathfinder.Status.NO_PATH, blocked.status());
        assertTrue(blocked.expanded() <= 6, "only the pocket: " + blocked.expanded());
        LanePathfinder.Result open = LanePathfinder.find(shores, 16, 16, toX, 16, P);
        assertTrue(open.found(), open.status().name());
        assertEquals(new LanePathfinder.Cell(0, 0), open.start(), "the berth's own beach cell");
        assertAllAtSea(shores, open.waypoints());
    }

    @Test
    void lanesPreferOpenSeaToShore() {
        // A beach strip along cz = 0 (the straight line) and open sea everywhere else
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cz != 0 || cx <= 0 || cx >= 40, (cx, cz) -> cz == 0);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 40 * CELL + 16, 16, P);
        assertTrue(r.found());
        long onShore = 0;
        List<Lane.Point> pts = r.waypoints();
        for (int i = 1; i < pts.size() - 1; i++) if (grid.isShore(grid.cellOf(pts.get(i).x()), grid.cellOf(pts.get(i).z()))) onShore++;
        assertEquals(0, onShore, "no interior waypoint on the shore: " + pts);
        assertTrue(pts.size() > 2, "steps off the beach strip: " + pts);
    }

    @Test
    void budgetGrowsWithTheStraightDistance() {
        LanePathfinder.Params p = new LanePathfinder.Params(4.0, 200_000, 8, 6.0, 20_000, 400);
        assertEquals(20_000, p.budget(0), "the floor for neighbours");
        assertEquals(20_000, p.budget(50), "400 x 50 = the floor");
        assertEquals(63_200, p.budget(158), "a 5050-block pair at 32-block cells");
        assertEquals(200_000, p.budget(10_000), "capped");
        assertEquals(200_000, new LanePathfinder.Params(4.0, 200_000, 8, 6.0, 20_000, 0).budget(10), "0 per cell: always the cap");
        assertEquals(500, new LanePathfinder.Params(4.0, 500, 8).budget(1_000), "the fixed-budget constructor");
        assertEquals(1_000, new LanePathfinder.Params(4.0, 1_000, 8, 4.0, 5_000, 400).budget(1), "min above max: max wins");
    }

    @Test
    void searchUsesTheDistanceBudget() {
        // A closed sea of 141 x 141 = 19881 cells: with the old fixed 20000 it is searched to the end (NO_PATH); a budget
        // of 400 per straight cell for 30 cells (12000) gives up first, and is reported as BUDGET with that number
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> Math.abs(cx) <= 70 && Math.abs(cz) <= 70);
        LanePathfinder.Params scaled = new LanePathfinder.Params(4.0, 200_000, 8, 6.0, 1_000, 400);
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 16 + 30 * CELL, 16 + 75 * CELL, scaled);
        // the end (cell 30, 75) is land; its nearest sea cell within 8 is (30, 70): straight octile distance 30 + 0.414 * 70
        assertEquals(LanePathfinder.Status.FOUND, r.status(), "inside the closed sea the end cell is reachable");
        LanePathfinder.Result cut = LanePathfinder.find(grid, 16, 16, 16 + 30 * CELL, 16 + 200 * CELL, scaled);
        assertEquals(LanePathfinder.Status.NO_END, cut.status());
        SeaGrid split = SeaGrid.of(CELL, (cx, cz) -> (Math.abs(cx) <= 70 && Math.abs(cz) <= 70) || cx >= 100);
        LanePathfinder.Result closed = LanePathfinder.find(split, 16, 16, 101 * CELL + 16, 16, new LanePathfinder.Params(4.0, 200_000, 8, 6.0, 20_000, 400));
        assertEquals(LanePathfinder.Status.NO_PATH, closed.status(), "separate seas: the whole closed sea searched");
        assertEquals(141 * 141, closed.expanded());
        assertEquals(40_400, closed.budget(), "400 x 101 cells");
        LanePathfinder.Result tight = LanePathfinder.find(split, 16, 16, 101 * CELL + 16, 16, new LanePathfinder.Params(4.0, 200_000, 8, 6.0, 1_000, 100));
        assertEquals(LanePathfinder.Status.BUDGET, tight.status());
        assertEquals(10_100, tight.expanded());
        assertEquals(10_100, tight.budget());
    }

    @Test
    void laneCrossesA400CellSeaAroundAnIsland() {
        // 400 cells of sea (12800 blocks at 32), an island of 41 x 81 cells across the straight line in the middle
        SeaGrid grid = SeaGrid.of(CELL, (cx, cz) -> cx >= -5 && cx <= 405 && Math.abs(cz) <= 200
                && !(cx >= 180 && cx <= 220 && Math.abs(cz) <= 40));
        LanePathfinder.Result r = LanePathfinder.find(grid, 16, 16, 400 * CELL + 16, 16, LanePathfinder.Params.DEFAULT);
        assertTrue(r.found(), r.status() + " after " + r.expanded() + " of " + r.budget());
        assertAllAtSea(grid, r.waypoints());
        assertTrue(r.waypoints().stream().anyMatch(p -> Math.abs(grid.cellOf(p.z())) > 40), "goes round the island: " + r.waypoints());
        double straight = 400.0 * CELL;
        assertTrue(Lane.length(r.waypoints()) < straight * 1.15, "not a long detour: " + Lane.length(r.waypoints()));
        assertTrue(r.expanded() < r.budget(), "within the budget: " + r.expanded() + " of " + r.budget());
    }
}

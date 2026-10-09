package com.richardsenger.piratesnships.core.perf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfStatsTest {

    private static final String MOD = "com.richardsenger.piratesnships.";

    @Test
    void summaryUsesNearestRankPercentiles() {
        double[] v = new double[20];
        for (int i = 0; i < 20; i++) v[i] = 20 - i; // 20..1, unsorted on purpose
        PerfStats.Summary s = PerfStats.summarize(v);
        assertEquals(1, s.min());
        assertEquals(10, s.median());
        assertEquals(19, s.p95());
        assertEquals(20, s.max());
        assertEquals(10.5, s.mean(), 1e-12);
        assertEquals(20, s.count());
        assertEquals(20, v[0], "the input is not sorted in place");
    }

    @Test
    void emptySeriesIsZero() {
        PerfStats.Summary s = PerfStats.summarize(new double[0]);
        assertEquals(0, s.count());
        assertEquals(0, s.p95());
    }

    @Test
    void ticksSinceReadsTheRingInOrder() {
        long[] ring = new long[100];
        for (int t = 150; t <= 249; t++) ring[t % 100] = t * 1_000_000L; // tick t took t ms
        double[] last = PerfStats.ticksSince(ring, 229, 249);
        assertEquals(20, last.length);
        assertEquals(230, last[0], 1e-9);
        assertEquals(249, last[19], 1e-9);
        assertEquals(100, PerfStats.ticksSince(ring, 0, 249).length, "at most the ring");
        assertEquals(0, PerfStats.ticksSince(ring, 249, 249).length);
    }

    @Test
    void perShipCostIsTheSlope() {
        assertEquals(1.5, PerfStats.perShip(22.5, 8, 12.0, 1), 1e-12);
        assertEquals(0, PerfStats.perShip(5, 3, 5, 3));
    }

    @Test
    void waitingSamplesAreNotBusy() {
        PerfStats.Samples s = new PerfStats.Samples();
        s.add(PerfStats.frames(List.of("jdk.internal.misc.Unsafe.park", "java.util.concurrent.locks.LockSupport.parkNanos",
                "net.minecraft.server.MinecraftServer.waitUntilNextTick", "net.minecraft.server.MinecraftServer.runServer")));
        s.add(PerfStats.frames(List.of("java.lang.Thread.run")));
        assertEquals(2, s.total());
        assertEquals(0, s.busy());
        assertTrue(s.topInclusive(5).isEmpty());
    }

    @Test
    void busySamplesAreAttributedInclusiveSelfAndByArea() {
        PerfStats.Samples s = new PerfStats.Samples();
        // 1: lean scan under the crew goal
        s.add(PerfStats.frames(List.of("net.minecraft.world.level.Level.getBlockState",
                MOD + "crew.lean.RailLean.candidates", MOD + "crew.lean.RailLean.claim", MOD + "crew.lean.RailLeanGoal.canUse",
                "net.minecraft.server.level.ServerLevel.tick", "net.minecraft.server.MinecraftServer.tickServer")));
        // 2: hull runtime, recursion counts once
        s.add(PerfStats.frames(List.of(MOD + "ship.hull.HullAnalyzer.fill", MOD + "ship.hull.HullAnalyzer.fill",
                MOD + "ship.hull.runtime.HullRuntime.tick", "dev.ryanhcode.sable.Foo.bar",
                "net.minecraft.server.MinecraftServer.tickServer")));
        // 3: vanilla only
        s.add(PerfStats.frames(List.of("net.minecraft.world.entity.Entity.move", "net.minecraft.server.MinecraftServer.tickServer")));
        // 4: the test's own frames do not count
        s.add(PerfStats.frames(List.of(MOD + "core.perf.PerformanceGameTests.sample", "net.minecraft.server.MinecraftServer.tickServer")));
        assertEquals(4, s.busy());
        assertEquals(0.5, s.modShare(), 1e-12);
        assertEquals(0.25, s.sableShare(), 1e-12);
        assertEquals(List.of("dev.ryanhcode.sable.Foo.bar"), s.topSable(5).stream().map(PerfStats.Entry::name).toList());
        List<PerfStats.Entry> inc = s.topInclusive(10);
        assertEquals(5, inc.size()); // candidates, claim, canUse, fill (once), HullRuntime.tick
        assertTrue(inc.stream().allMatch(e -> e.samples() == 1));
        assertEquals(MOD + "crew.lean.RailLean.candidates", s.topSelf(1).get(0).name());
        assertEquals(2, s.topSelf(10).size());
        List<PerfStats.Entry> areas = s.topAreas(10);
        assertEquals(List.of("crew.lean", "ship.hull"), areas.stream().map(PerfStats.Entry::name).toList());
        assertEquals(0.25, areas.get(0).share(), 1e-12);
        assertFalse(inc.stream().anyMatch(e -> e.name().contains("core.perf")));
        // what the deepest mod frame calls: only the lean scan calls out (the hull sample's deepest frame is the top)
        assertEquals(List.of(MOD + "crew.lean.RailLean.candidates > net.minecraft.world.level.Level.getBlockState"),
                s.topCallees(10).stream().map(PerfStats.Entry::name).toList());
    }

    @Test
    void windowsMergeByName() {
        List<PerfStats.Entry> a = List.of(new PerfStats.Entry("x", 10, 0.2), new PerfStats.Entry("y", 5, 0.1));
        List<PerfStats.Entry> b = List.of(new PerfStats.Entry("x", 20, 0.4));
        List<PerfStats.Entry> m = PerfStats.mergeWindows(List.of(a, b), 5);
        assertEquals("x", m.get(0).name());
        assertEquals(0.3, m.get(0).share(), 1e-12);
        assertEquals(30, m.get(0).samples());
        assertEquals(0.05, m.get(1).share(), 1e-12);
    }
}

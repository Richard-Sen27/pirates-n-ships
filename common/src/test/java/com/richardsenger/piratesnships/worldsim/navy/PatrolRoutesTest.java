package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolRoutesTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("pirates_n_ships", path);
    }

    private static PatrolRoutes.Site site(String path, String dim, int x, int z) {
        return new PatrolRoutes.Site(id(path), dim, x, z);
    }

    private static Lane.Point p(int x, int z) {
        return new Lane.Point(x, z);
    }

    @Test
    void anOutpostSailsToAnotherOutpostInRange() {
        List<PatrolRoutes.Site> outposts = List.of(site("a", "o", 0, 0), site("b", "o", 1000, 0));
        for (int seed = 0; seed < 20; seed++) {
            PatrolRoutes.Plan plan = PatrolRoutes.choose(outposts, List.of(site("i", "o", 50, 0)), 3000, RandomSource.create(seed)).orElseThrow();
            assertFalse(plan.outAndBack());
            assertFalse(plan.outpost().equals(plan.other()));
        }
    }

    @Test
    void aLoneOutpostSailsTowardTheNearestPirateIslandOfItsDimension() {
        List<PatrolRoutes.Site> outposts = List.of(site("a", "o", 0, 0), site("far", "o", 9000, 0), site("nether", "n", 10, 0));
        List<PatrolRoutes.Site> islands = List.of(site("i1", "o", 800, 0), site("i2", "o", 300, 0), site("i3", "n", 1, 0));
        // the first seed whose pick is outpost "a"
        Optional<PatrolRoutes.Plan> plan = Optional.empty();
        for (int seed = 0; seed < 50 && (plan.isEmpty() || !plan.get().outpost().equals(id("a"))); seed++) {
            plan = PatrolRoutes.choose(outposts, islands, 3000, RandomSource.create(seed));
        }
        assertEquals(Optional.of(new PatrolRoutes.Plan(id("a"), id("i2"), true)), plan);
    }

    @Test
    void noOutpostsNoPatrol() {
        assertEquals(Optional.empty(), PatrolRoutes.choose(List.of(), List.of(site("i", "o", 0, 0)), 3000, RandomSource.create(0)));
        assertEquals(Optional.empty(), PatrolRoutes.choose(List.of(site("a", "o", 0, 0)), List.of(), 3000, RandomSource.create(0)));
    }

    @Test
    void outAndBackTurnsAtTheRadius() {
        List<Lane.Point> lane = List.of(p(0, 0), p(300, 0), p(300, 300));
        List<Lane.Point> r = PatrolRoutes.outAndBack(lane, 400);
        assertEquals(List.of(p(0, 0), p(300, 0), p(300, 100), p(300, 0), p(0, 0)), r);
        assertEquals(800, Lane.length(r), 1e-6);
    }

    @Test
    void outAndBackOnAShortLaneGoesToItsEnd() {
        List<Lane.Point> lane = List.of(p(0, 0), p(100, 0));
        assertEquals(List.of(p(0, 0), p(100, 0), p(0, 0)), PatrolRoutes.outAndBack(lane, 400));
    }

    @Test
    void standoffStopsShortOfTheTarget() {
        double[] s = PatrolRoutes.standoffPoint(100, 0, 0, 0, 20);
        assertEquals(20, s[0], 1e-9);
        assertEquals(0, s[1], 1e-9);
        double[] close = PatrolRoutes.standoffPoint(10, 0, 0, 0, 20);
        assertEquals(10, close[0], 1e-9, "closer than the standoff: stay");
    }

    @Test
    void pursuitIsCappedAtTheFirstLandCellAfterTheSea() {
        // cells of 32: x cells 0..2 sea, cell 3 land, cell 4 and on sea again
        SeaGrid grid = SeaGrid.of(32, (cx, cz) -> cx != 3);
        Lane.Point end = PatrolRoutes.capToSea(grid, 0, 0, 200, 0);
        assertTrue(end.x() < 96 && end.x() >= 64, "stopped at " + end);
    }

    @Test
    void landAtTheStartDoesNotStopThePursuit() {
        SeaGrid grid = SeaGrid.of(32, (cx, cz) -> cx >= 1);
        assertEquals(p(200, 0), PatrolRoutes.capToSea(grid, 0, 0, 200, 0));
        assertEquals(p(200, 0), PatrolRoutes.capToSea(SeaGrid.allSea(32), 0, 0, 200, 0));
    }

    @Test
    void pursuitRouteRunsFromThePatrolToTheStandoffPoint() {
        List<Lane.Point> r = PatrolRoutes.pursuit(SeaGrid.allSea(32), 100, 0, 0, 0, 20);
        assertEquals(List.of(p(100, 0), p(20, 0)), r);
    }

    @Test
    void ringStartsNearestThePatrolAndKeepsTheRadius() {
        List<Vec3> ring = PatrolRoutes.ring(0, 0, 20, 100, 0, 4, 63);
        assertEquals(4, ring.size());
        assertEquals(20, ring.get(0).x, 1e-9);
        assertEquals(0, ring.get(0).z, 1e-9);
        for (Vec3 v : ring) {
            assertEquals(20, Math.hypot(v.x, v.z), 1e-9);
            assertEquals(63, v.y, 1e-9);
        }
        // counter-clockwise seen from above: from east (+x) toward north (−z)
        assertEquals(-20, ring.get(1).z, 1e-9);
    }

    @Test
    void rejoinLeadsToTheNearestRoutePointThenOnward() {
        List<Lane.Point> home = List.of(p(0, 0), p(100, 0), p(200, 0));
        List<Lane.Point> r = PatrolRoutes.rejoin(home, 50, 150, 40);
        assertEquals(List.of(p(150, 40), p(150, 0), p(200, 0)), r);
    }

    @Test
    void rejoinOnAnOutAndBackRouteKeepsTheLegNearTheProgress() {
        List<Lane.Point> home = List.of(p(0, 0), p(100, 0), p(0, 0));
        // left on the way back (progress 150): rejoin at 50 on the return leg, then home
        List<Lane.Point> r = PatrolRoutes.rejoin(home, 150, 50, 30);
        assertEquals(List.of(p(50, 30), p(50, 0), p(0, 0)), r);
    }
}

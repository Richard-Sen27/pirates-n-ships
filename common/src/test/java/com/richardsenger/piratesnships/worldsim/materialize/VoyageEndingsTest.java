package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure ending rules, the route projection, the radius clamp and the saved link (WS3b). */
class VoyageEndingsTest {

    private static final EndingRules.Params P = EndingRules.Params.DEFAULTS;

    @Test
    void sinksWhenFloodedWreckedOrUnderwaterLongEnough() {
        assertFalse(EndingRules.sunk(0.79, false, 99, P));
        assertTrue(EndingRules.sunk(0.8, false, 0, P));
        assertTrue(EndingRules.sunk(0.0, true, 0, P));
        assertTrue(EndingRules.sunk(0.0, false, 100, P));
        assertEquals(0.5, EndingRules.floodFraction(50, 100));
        assertEquals(0.0, EndingRules.floodFraction(50, 0));
        assertEquals(1.0, EndingRules.floodFraction(150, 100));
    }

    @Test
    void submergedTicksResetWhenTheShipComesUp() {
        int t = EndingRules.submergedTicks(EndingRules.NOT_HELD, true, 20);
        assertEquals(0, t, "the time before the first check that sees it does not count");
        t = EndingRules.submergedTicks(t, true, 20);
        t = EndingRules.submergedTicks(t, true, 20);
        assertEquals(40, t);
        assertEquals(EndingRules.NOT_HELD, EndingRules.submergedTicks(t, false, 20));
        assertFalse(EndingRules.sunk(0.0, false, EndingRules.NOT_HELD, P));
    }

    @Test
    void captureNeedsNoFightersAndAPlayerWithoutABreak() {
        int t = EndingRules.NOT_HELD;
        for (int i = 0; i < 5; i++) t = EndingRules.captureTicks(t, 0, true, 20);
        assertEquals(80, t);
        assertFalse(EndingRules.captured(t, P));
        assertTrue(EndingRules.captured(EndingRules.captureTicks(t, 0, true, 20), P));
        assertEquals(EndingRules.NOT_HELD, EndingRules.captureTicks(80, 1, true, 20), "a fighter still alive");
        assertEquals(EndingRules.NOT_HELD, EndingRules.captureTicks(80, 0, false, 20), "the player left");
        assertFalse(EndingRules.captured(EndingRules.NOT_HELD, P));
    }

    @Test
    void plunderCountsOncePerVoyageAndOnlyWithAPlayerAboard() {
        assertEquals(EndingRules.CargoChange.NONE, EndingRules.cargoChange(96, 96, true, false));
        assertEquals(EndingRules.CargoChange.PLUNDERED, EndingRules.cargoChange(96, 86, true, false));
        assertEquals(EndingRules.CargoChange.CHANGED, EndingRules.cargoChange(96, 86, true, true));
        assertEquals(EndingRules.CargoChange.CHANGED, EndingRules.cargoChange(96, 86, false, false));
        assertEquals(EndingRules.CargoChange.CHANGED, EndingRules.cargoChange(86, 96, true, false));
    }

    @Test
    void blameLastsForTheShooterMemory() {
        UUID p = UUID.randomUUID();
        EndingRules.Hit hit = new EndingRules.Hit(p, 1000);
        assertEquals(Optional.of(p), EndingRules.blame(hit, 2200, P));
        assertEquals(Optional.empty(), EndingRules.blame(hit, 2201, P));
        assertEquals(Optional.empty(), EndingRules.blame(null, 1000, P));
    }

    @Test
    void deedsAndEventsByFaction() {
        assertEquals(VoyageDeed.SINK_NAVY, VoyageDeed.sink(Faction.NAVY));
        assertEquals(VoyageDeed.CAPTURE_PIRATE, VoyageDeed.capture(Faction.PIRATES));
        assertEquals(FactionEvent.CONVOY_SUNK, VoyageDeed.SINK_MERCHANT.factionEvent());
        assertEquals(FactionEvent.PATROL_LOST, VoyageDeed.CAPTURE_NAVY.factionEvent());
        assertEquals(FactionEvent.PIRATE_SHIP_LOST, EndingRules.lostEvent(Faction.PIRATES));
        assertEquals(FactionEvent.MERCHANT_PLUNDERED, VoyageDeed.PLUNDER_MERCHANT.factionEvent());
        assertEquals("sink_merchant", VoyageDeed.SINK_MERCHANT.id());
    }

    @Test
    void routeProjectionFollowsTheLegs() {
        List<Lane.Point> route = List.of(new Lane.Point(0, 0), new Lane.Point(100, 0), new Lane.Point(100, 100));
        assertEquals(40.0, RouteMath.project(route, 40, 3, 0), 1e-9);
        assertEquals(150.0, RouteMath.project(route, 104, 50, 0), 1e-9);
        assertEquals(0.0, RouteMath.project(route, -20, 0, 0), 1e-9);
        assertEquals(100.0, RouteMath.progressAt(route, 1), 1e-9);
        assertEquals(200.0, RouteMath.progressAt(route, 7), 1e-9);
        assertEquals(List.of(new Lane.Point(100, 0), new Lane.Point(100, 100)), RouteMath.ahead(route, 40));
        assertEquals(List.of(new Lane.Point(100, 100)), RouteMath.ahead(route, 150));
    }

    @Test
    void routeProjectionPrefersTheLegNearTheRecordOnARouteThatDoublesBack() {
        List<Lane.Point> route = List.of(new Lane.Point(0, 0), new Lane.Point(100, 0), new Lane.Point(0, 0));
        assertEquals(40.0, RouteMath.project(route, 40, 0, 30), 1e-9);
        assertEquals(160.0, RouteMath.project(route, 40, 0, 150), 1e-9);
    }

    @Test
    void radiusIsClampedToTheLoadedArea() {
        assertEquals(144, MaterializeConfig.clampRadius(192, 10));
        assertEquals(192, MaterializeConfig.clampRadius(192, 32));
        assertEquals(32, MaterializeConfig.clampRadius(192, 2));
    }

    @Test
    void linkSurvivesTheUserDataRoundTrip() {
        VoyageLink link = new VoyageLink(UUID.randomUUID(), true, Map.of(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "sugar"), 12));
        CompoundTag tag = link.toTag();
        assertEquals(Optional.of(link), VoyageLink.fromTag(tag.copy()));
        assertEquals(Optional.empty(), VoyageLink.fromTag(new CompoundTag()));
        VoyageLink bare = new VoyageLink(UUID.randomUUID(), false, Map.of());
        assertEquals(Optional.of(bare), VoyageLink.fromTag(bare.toTag()));
    }
}

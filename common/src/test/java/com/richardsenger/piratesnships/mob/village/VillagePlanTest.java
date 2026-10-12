package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import com.richardsenger.piratesnships.trade.stall.StallKind;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LIFE1: a village's pieces to its people, their stations, beds and seats. */
class VillagePlanTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlacedPiece piece(String name, int x, int z, Rotation r) {
        return new PlacedPiece(name, new BlockPos(x, 64, z), r);
    }

    private static ResidentPlan only(List<ResidentPlan> plans, VillageRole role) {
        List<ResidentPlan> of = plans.stream().filter(p -> p.role() == role).toList();
        assertEquals(1, of.size(), role + " plans: " + of);
        return of.get(0);
    }

    @Test
    void theFishwifeWorksHerRackThenTheFishMarketAndSleepsAtHome() {
        List<PlacedPiece> pieces = List.of(piece("dock_head", 0, 0, Rotation.NONE), piece("house_fisher", 20, 0, Rotation.NONE),
                piece("fish_market", 40, 0, Rotation.NONE), piece("street", 10, 10, Rotation.NONE));
        List<ResidentPlan> plans = VillagePlan.plan(pieces);
        assertEquals(2, plans.size(), "the fishwife and the fish market's vendor: " + plans);
        ResidentPlan fishwife = only(plans, VillageRole.FISHWIFE);
        assertEquals(new Post(new BlockPos(29, 65, 4), Direction.EAST), fishwife.station(0), "the drying rack");
        assertEquals(new Post(new BlockPos(42, 65, 3), Direction.NORTH), fishwife.station(1), "the fish market's counter");
        assertEquals(new BlockPos(21, 65, 4), fishwife.bed().orElseThrow().foot(), "her own bed");
        assertEquals(Direction.SOUTH, fishwife.bed().orElseThrow().facing());
        assertEquals(new Post(new BlockPos(22, 65, 3), Direction.WEST), fishwife.seats().get(0), "her chair at home");
        ResidentPlan vendor = only(plans, VillageRole.VENDOR);
        assertEquals(Optional.of(StallKind.FISH), vendor.stall());
        assertEquals(new BlockPos(44, 65, 3), vendor.home().pos());
        assertEquals("house_fisher@20,64,0#0", fishwife.slot());
    }

    @Test
    void withoutAFishMarketTheFishwifeStaysAtHerRack() {
        ResidentPlan f = VillagePlan.plan(List.of(piece("house_fisher", 0, 0, Rotation.NONE))).get(0);
        assertEquals(f.station(0), f.station(1));
        assertEquals(f.station(0), f.station(7), "an index past the last means the last");
    }

    @Test
    void postsTurnWithTheirPiece() {
        // turned clockwise 90: local (x, z) -> (-z, x); north -> east
        ResidentPlan v = VillagePlan.plan(List.of(piece("house_small", 100, 100, Rotation.CLOCKWISE_90))).get(0);
        assertEquals(new Post(new BlockPos(98, 65, 102), Direction.NORTH), v.home(), "the bench post, west turned to north");
        assertEquals(new BlockPos(95, 65, 105), v.bed().orElseThrow().foot());
        assertEquals(Direction.WEST, v.bed().orElseThrow().facing(), "south turned clockwise is west");
    }

    @Test
    void residentsWithoutABedOfTheirOwnTakeTheNearestFreeOne() {
        List<PlacedPiece> pieces = List.of(piece("house_small", 0, 0, Rotation.NONE), piece("house_tall", 30, 0, Rotation.NONE),
                piece("net_loft", 40, 0, Rotation.NONE), piece("warehouse", 200, 0, Rotation.NONE), piece("lighthouse", 300, 0, Rotation.NONE));
        List<ResidentPlan> plans = VillagePlan.plan(pieces);
        ResidentPlan small = only(plans, VillageRole.NET_MENDER);
        // house_tall's second bed (5, 5, 5) is free: the net mender, nearest, takes it
        assertEquals(new BlockPos(35, 69, 5), small.bed().orElseThrow().foot());
        // the dockhand finds the last free bed (house_small's is taken by its villager)
        Set<BlockPos> beds = new HashSet<>();
        for (ResidentPlan p : plans) p.bed().ifPresent(b -> assertTrue(beds.add(b.foot()), "one sleeper per bed: " + b));
        assertEquals(3, beds.size(), "three beds, three sleepers");
        assertTrue(only(plans, VillageRole.LAMPLIGHTER).bed().isEmpty() || only(plans, VillageRole.DOCKHAND).bed().isEmpty(),
                "four sleepers for three beds: one stays at home");
    }

    @Test
    void theDockhandWorksTheWarehouseThenThePier() {
        List<ResidentPlan> plans = VillagePlan.plan(List.of(piece("warehouse", 0, 0, Rotation.NONE), piece("pier", 0, -40, Rotation.NONE)));
        ResidentPlan d = only(plans, VillageRole.DOCKHAND);
        assertEquals(new BlockPos(3, 65, 2), d.station(0).pos(), "the crates");
        assertTrue(d.station(1).pos().getZ() < -20 && d.station(1).pos().getY() == 70, "on the pier's deck: " + d.station(1));
    }

    @Test
    void theMarketSquareHasTwoVendorsAndTheirStalls() {
        List<ResidentPlan> plans = VillagePlan.plan(List.of(piece("street_square_market", 0, 0, Rotation.NONE)));
        assertEquals(2, plans.size());
        assertEquals(Optional.of(StallKind.PROVISIONS), plans.get(0).stall());
        assertEquals(Optional.of(StallKind.CHANDLERY), plans.get(1).stall());
        // the notice board's free spot (NB2) is clear of both stalls' posts
        for (ResidentPlan p : plans) {
            BlockPos at = p.home().pos();
            assertTrue(at.getX() < 3 || at.getX() > 4 || at.getZ() < 11 || at.getZ() > 12, "a vendor on the notice board spot");
        }
    }

    @Test
    void theTavernsSeatsComeFirstAtDusk() {
        List<ResidentPlan> plans = VillagePlan.plan(List.of(piece("house_small", 0, 0, Rotation.NONE), piece("tavern", 16, 0, Rotation.NONE)));
        ResidentPlan v = only(plans, VillageRole.VILLAGER);
        assertEquals(VillagePlan.SEATS_PER_RESIDENT, v.seats().size());
        assertTrue(v.seats().get(0).pos().getX() >= 16, "a tavern chair before its own: " + v.seats());
    }

    @Test
    void thePlanSurvivesTheCodec() {
        for (ResidentPlan p : VillagePlan.plan(List.of(piece("house_fisher", 0, 0, Rotation.NONE), piece("shipwright", 30, 0, Rotation.NONE)))) {
            var tag = ResidentPlan.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
            assertEquals(p, ResidentPlan.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        }
        ResidentPlan s = ResidentPlan.standalone(VillageRole.SHIPWRIGHT, new BlockPos(1, 2, 3), Direction.EAST);
        assertEquals(Optional.of(StallKind.SHIPWRIGHT), s.stall());
        assertTrue(s.bed().isEmpty() && s.seats().isEmpty());
    }

    @Test
    void lampsAreWalkedNearestFirst() {
        BlockPos start = BlockPos.ZERO;
        List<BlockPos> lamps = List.of(new BlockPos(30, 0, 0), new BlockPos(5, 0, 0), new BlockPos(10, 0, 0), new BlockPos(-40, 0, 0));
        assertEquals(List.of(new BlockPos(5, 0, 0), new BlockPos(10, 0, 0), new BlockPos(30, 0, 0)), LampRoute.order(start, lamps, 3));
        assertEquals(4, LampRoute.order(start, lamps, 10).size());
        assertTrue(LampRoute.order(start, List.of(), 5).isEmpty());
    }
}

package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spike 4 GameTests: a crew member at the sail winch of an assembled ship (docs/design.md §6, milestone 4). All tests
 * pin {@code ticks_per_trim_step = 10} (so hoisting from furled takes 20 ticks) and therefore run in config batches.
 */
public final class StationGameTests {

    private static final int STEP = 10;
    /** Checks of "not before" and "after" keep this margin to the expected completion tick (tick order within a tick). */
    private static final int MARGIN = 3;

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(StationGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void pin(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        ConfigOverrides.during(h, StationConfig.SEAT_CHECK_INTERVAL, 5);
    }

    /** 40×40 basin: water up to y=7, or stone up to y=4 (a ship resting on land). */
    private static void basin(GameTestHelper h, boolean water) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    boolean solid = wall || !water && y <= 4;
                    h.setBlock(new BlockPos(x, y, z), solid ? Blocks.STONE : y <= 7 && water ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    /** 5×4×5 plank hull at (x0, z0), deck top y=9, helm at the stern, mast + furled sail amidships, winch on deck. */
    private static BlockPos hull(GameTestHelper h, int x0, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(x0 + 2, 9, z0 + 2), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(x0 + 2, 10, z0 + 2), SailingBlocks.SMALL_SQUARE_SAIL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH).setValue(SailBlock.TRIM, SailTrim.FURLED));
        h.setBlock(new BlockPos(x0 + 1, 9, z0 + 3), SailingBlocks.SAIL_WINCH.get());
        return helm;
    }

    private record Fixture(ShipBody ship, BlockPos winch, BlockPos sail, BlockPos helm) { }

    private static Fixture ship(GameTestHelper h, boolean water) {
        basin(h, water);
        BlockPos helm = hull(h, 17, 17);
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) throw new AssertionError("no ship after assembly");
        return new Fixture(ship, find(h, ship, SailingBlocks.SAIL_WINCH.get()), find(h, ship, SailingBlocks.SMALL_SQUARE_SAIL.get()),
                find(h, ship, AssemblyContent.HELM.get()));
    }

    private static BlockPos find(GameTestHelper h, ShipBody ship, Block block) {
        return ship.plotBlocks().stream().filter(p -> h.getLevel().getBlockState(p).is(block)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + block + " on the ship"));
    }

    private static CrewMember crew(GameTestHelper h, int x, int z) {
        return h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(x, 9, z));
    }

    private static CrewMember seated(GameTestHelper h, Fixture f, CrewMember c) {
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, f.winch());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        h.assertTrue(c.isAtStation(), "crew member does not ride the station seat");
        StationSeat seat = (StationSeat) c.getVehicle();
        ShipBody in = ShipEntities.containing(seat);
        h.assertTrue(in != null && in.id().equals(f.ship().id()), "the seat is not inside the ship's plot: " + seat.position());
        h.assertTrue(ShipEntities.retainedInPlot(seat), "Sable does not retain the seat type (tag missing)");
        return c;
    }

    private static SailTrim trim(GameTestHelper h, Fixture f) {
        return h.getLevel().getBlockState(f.sail()).getValue(SailBlock.TRIM);
    }

    private static List<StationSeat> seats(GameTestHelper h, Fixture f) {
        return h.getLevel().getEntitiesOfClass(StationSeat.class, new AABB(f.winch()).inflate(4), s -> !s.isRemoved());
    }

    /** Crew position relative to the ship: its plot-space offset from the seat's spot (0 when it stands exactly there). */
    private static double offsetFromSeat(Fixture f, CrewMember c, Vec3 seatPlot) {
        return f.ship().toPlot(c.position()).distanceTo(seatPlot);
    }

    /**
     * Sable places a rider of a plot vehicle by transforming its <em>eye</em> point and hanging the body straight down
     * in world space ({@code mixinhelpers/entity/entity_riding_sub_level_vehicle/EntityRidingSubLevelVehicleHelper}
     * l.22-27), so on a tilted ship the feet swing off the seat by about 2·eyeHeight·sin(tilt/2) while the eyes stay
     * put. This is the offset that must stay small.
     */
    private static double eyeOffsetFromSeat(Fixture f, CrewMember c, Vec3 seatPlot) {
        Vec3 eyeAtSeat = seatPlot.add(0, c.getEyeHeight() + 0.01, 0);
        return f.ship().toPlot(c.getEyePosition()).distanceTo(eyeAtSeat);
    }

    // ------------------------------------------------------------------ tests

    /** Seated at the winch; hoist takes 2 steps × 10 ticks: furled at +17, full at +23; furl brings them down; one occupant. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_station_winch")
    public static void crewHoistsAndFurlsAfterTheWorkTime(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, true);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        CrewMember other = crew(h, 3, 2);
        h.assertTrue(CrewStations.assign(h.getLevel(), other, f.winch()) == CrewStations.AssignResult.TAKEN,
                "a second crew member occupied the winch");
        h.assertTrue(other.assignment() == null && !other.isPassenger(), "the second crew member was seated anyway");
        h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == Stations.OrderResult.STARTED, "hoist not started");
        h.runAfterDelay(2 * STEP - MARGIN, () -> {
            h.assertTrue(trim(h, f) == SailTrim.FURLED, "sails changed before the work was done: " + trim(h, f));
            h.assertTrue(Stations.state(c.assignment()).phase() == StationState.Phase.OPERATING, "station not operating");
        });
        h.runAfterDelay(2 * STEP + MARGIN, () -> {
            h.assertTrue(trim(h, f) == SailTrim.FULL, "sails not full after the work time: " + trim(h, f));
            h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.FURL) == Stations.OrderResult.STARTED, "furl not started");
        });
        h.runAfterDelay(4 * STEP + 2 * MARGIN, () -> {
            h.assertTrue(trim(h, f) == SailTrim.FURLED, "sails not furled: " + trim(h, f));
            h.assertTrue(c.isAtStation(), "crew member left the station");
            c.discard();
            other.discard();
            h.succeed();
        });
    }

    /**
     * Milestone 4: hoist while the ship drifts, then sail downwind (6 blocks/s from astern, as the sailing tests).
     * The crew member stays at its seat measured in the ship's frame: its eye point within 0.5 blocks (riders are
     * re-placed once per tick, at under 1 m/s that lags by 0.05 blocks; see {@link #eyeOffsetFromSeat} for why the
     * eyes and not the feet). The ship must have moved at least 0.3 blocks (measured
     * mean speed is about 0.4 m/s, sable-notes §9.0d).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_station_sailing")
    public static void crewStaysAtStationWhileSailing(GameTestHelper h) {
        pin(h);
        ConfigOverrides.during(h, SailingConfig.KEEL_LATERAL_DRAG, 8.0);
        ConfigOverrides.during(h, SailingConfig.SAIL_HEEL_FACTOR, 0.25);
        String dim = h.getLevel().dimension().location().toString();
        WindOverride.set(dim, 0.0, 6.0, h.getLevel().getGameTime() + 400);
        Fixture f = ship(h, true);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        Vec3 seatPlot = c.getVehicle().position();
        Vec3 start = f.ship().toWorld(Vec3.atCenterOf(f.helm()));
        double[] worst = {0, 0, 0};
        h.runAfterDelay(10, () -> h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == Stations.OrderResult.STARTED, "hoist"));
        h.onEachTick(() -> {
            if (h.getTick() > 12 && !f.ship().isRemoved() && c.isAtStation()) {
                worst[0] = Math.max(worst[0], eyeOffsetFromSeat(f, c, seatPlot));
                worst[1] = Math.max(worst[1], offsetFromSeat(f, c, seatPlot));
                worst[2] = Math.max(worst[2], com.richardsenger.piratesnships.ship.assembly.DisassemblyMath.tiltDegrees(f.ship().orientation()));
            }
        });
        h.runAfterDelay(200, () -> {
            WindOverride.clear(dim);
            double moved = f.ship().toWorld(Vec3.atCenterOf(f.helm())).distanceTo(start);
            Constants.LOG.info("[station test] sailing: ship moved {} blocks, worst eye offset {} blocks, worst feet offset {} blocks, max tilt {}°",
                    String.format("%.2f", moved), String.format("%.3f", worst[0]), String.format("%.3f", worst[1]), String.format("%.1f", worst[2]));
            h.assertTrue(trim(h, f) == SailTrim.FULL, "sails not hoisted");
            h.assertTrue(c.isAtStation(), "crew member left the station while sailing");
            h.assertTrue(moved > 0.3, "the ship did not sail: moved " + moved);
            h.assertTrue(worst[0] < 0.5, "crew member's eyes drifted from their seat point by " + worst[0]);
            c.discard();
            h.succeed();
        });
    }

    /** Releasing removes the seat and leaves the crew member on deck (inside the ship's world bounds, standing). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_station_winch")
    public static void releaseLeavesCrewOnDeck(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        h.runAfterDelay(20, () -> {
            CrewStations.release(h.getLevel(), c);
            h.assertTrue(!c.isPassenger() && c.assignment() == null, "still seated or assigned");
            h.assertTrue(seats(h, f).isEmpty(), "seat left over");
            h.assertTrue(Stations.state(new StationRef(f.ship().id(), f.winch())) == null, "station still occupied");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(f.ship().worldBounds().inflate(0.5, 2, 0.5).contains(c.position()), "crew member not on deck: " + c.position()
                    + " ship " + f.ship().worldBounds());
            h.assertTrue(c.onGround(), "crew member not standing on the deck");
            c.discard();
            h.succeed();
        });
    }

    /** Disassembly: the crew member stands at the seat's world spot (within 0.75 blocks horizontally, 0.6 vertically). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_station_winch")
    public static void disassemblyLeavesCrewAtTheStation(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationSeat seat = (StationSeat) c.getVehicle();
        boolean[] done = {false};
        Vec3[] expected = new Vec3[1];
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 20 || h.getTick() % 5 != 0) return;
            Vec3 spot = f.ship().toWorld(seat.position());
            AssemblyResult r = ShipAssembler.disassemble(f.ship(), f.helm(), null);
            if (r.outcome() == AssemblyResult.Outcome.DISASSEMBLED) {
                done[0] = true;
                expected[0] = spot;
                h.assertTrue(seat.isRemoved(), "seat survived disassembly");
                h.assertTrue(!c.isPassenger(), "crew member still riding");
                h.runAfterDelay(15, () -> {
                    Vec3 p = c.position();
                    double dx = Math.hypot(p.x - expected[0].x, p.z - expected[0].z), dy = Math.abs(p.y - expected[0].y);
                    h.assertTrue(dx < 0.75 && dy < 0.6, "crew member at " + p + ", expected " + expected[0]);
                    h.assertTrue(c.isAlive() && c.assignment() == null, "crew member still assigned to the gone ship");
                    // near the crew member (world) and at the seat's old plot position; other tests' seats are farther away
                    List<StationSeat> left = h.getLevel().getEntitiesOfClass(StationSeat.class, new AABB(p, p).inflate(6), s -> true);
                    left.addAll(h.getLevel().getEntitiesOfClass(StationSeat.class, seat.getBoundingBox().inflate(6), s -> true));
                    h.assertTrue(left.isEmpty(), "seat entities left over: " + left.size());
                    c.discard();
                    h.succeed();
                });
            }
        });
    }

    /** Breaking the winch frees the station, removes the seat and releases the crew member. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_station_winch")
    public static void breakingTheWinchReleasesTheCrew(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationSeat seat = (StationSeat) c.getVehicle();
        h.runAfterDelay(10, () -> {
            h.getLevel().destroyBlock(f.winch(), false);
            h.assertTrue(seat.isRemoved() && !c.isPassenger(), "seat not removed with the winch");
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.assignment() == null, "crew member still assigned to the broken winch");
            h.assertTrue(seats(h, f).isEmpty(), "seat left over");
            c.discard();
            h.succeed();
        });
    }

    /** The assignment is saved with the crew member, and the seat saves the crew member as its passenger. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_station_winch")
    public static void assignmentSurvivesSaveAndLoad(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        CompoundTag tag = new CompoundTag();
        c.saveWithoutId(tag);
        CrewMember loaded = StationContent.CREW_MEMBER.get().create(h.getLevel());
        loaded.load(tag);
        h.assertTrue(c.assignment().equals(loaded.assignment()), "assignment lost: " + loaded.assignment());
        CompoundTag seatTag = new CompoundTag();
        h.assertTrue(c.getVehicle().save(seatTag), "seat not saveable");
        h.assertTrue(seatTag.getList("Passengers", 10).size() == 1, "seat does not save its passenger");
        c.discard();
        h.succeed();
    }

    /** Removing the ship for good kills the seat (Sable, #sable:destroy_with_sub_level) and the crew is released. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_station_winch")
    public static void removingTheShipLeavesNoSeats(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationSeat seat = (StationSeat) c.getVehicle();
        h.runAfterDelay(10, () -> {
            SableShips.remove(f.ship());
            h.assertTrue(seat.isRemoved(), "seat survived the ship's removal");
            h.assertTrue(!c.isPassenger(), "crew member still riding");
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.assignment() == null, "crew member still assigned to the removed ship");
            c.discard();
            h.succeed();
        });
    }
}

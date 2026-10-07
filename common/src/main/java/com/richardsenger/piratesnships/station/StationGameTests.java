package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrderPayload;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
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
 * pin {@code ticks_per_trim_step = 10} (so hoisting from furled takes 20 ticks) and therefore run in config batches,
 * one per test: tests of one batch start a few ticks apart (each waits for its chunks), and a test that ends early
 * would restore the value it found, mid-way through a test that overrode it after it (the Q3 failure of
 * {@code CrewPoseGameTests}).
 */
public final class StationGameTests {

    private static final int STEP = 10;
    /** Checks of "not before" and "after" keep this margin to the expected completion tick (tick order within a tick). */
    private static final int MARGIN = 3;
    /** Prefix of the per-test config batches (see the class comment). */
    private static final String BATCH = "pirates_n_ships_config_station_winch_";

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
        SailingGameTestsShips.openSky(h, 40); // the two-yard rig reaches the GameTest barrier ceiling (y=12)
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
        SailingGameTestsShips.rig(h, x0 + 2, z0 + 2, 1, 3, SailTrim.FURLED); // a square sail between two yards (F5a)
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
        SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
        if (rt == null || rt.sailCount() != 1) throw new AssertionError("expected one sail on the ship");
        return new Fixture(ship, find(h, ship, SailingBlocks.SAIL_WINCH.get()), rt.sailPositions().get(0),
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
        return h.getLevel().getBlockState(f.sail()).getValue(YardBlock.TRIM);
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
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "crewhoistsandfurlsaftertheworktime")
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
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "releaseleavescrewondeck")
    public static void releaseLeavesCrewOnDeck(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        h.runAfterDelay(20, () -> {
            CrewStations.release(h.getLevel(), c);
            h.assertTrue(!c.isPassenger() && c.assignment() == null, "still seated or assigned");
            h.assertTrue(seats(h, f).isEmpty(), "seat left over");
            h.assertTrue(Stations.state(new StationRef(f.ship().id(), f.winch())) == null, "station still occupied");
            // checked at once: released, the crew member strolls about the 5×5 deck and may jump or step off it
            h.assertTrue(f.ship().worldBounds().inflate(0.5, 2, 0.5).contains(c.position()), "crew member not on deck: " + c.position()
                    + " ship " + f.ship().worldBounds());
            // on the deck top (the helm's bottom face): Level#noCollision does not see plot blocks, so compare heights
            double deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm())).y;
            h.assertTrue(Math.abs(c.getY() - deck) < 0.3, "crew member not standing on the deck: " + c.position() + ", deck top " + deck);
            c.discard();
            h.succeed();
        });
    }

    /**
     * Disassembly: the crew member stands at the seat's world spot (within 0.75 blocks horizontally, 0.6 vertically) on
     * the deck, is released, and no seat entity is left.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "disassemblyleavescrewatthestation")
    public static void disassemblyLeavesCrewAtTheStation(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationSeat seat = (StationSeat) c.getVehicle();
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 20 || h.getTick() % 5 != 0) return;
            Vec3 spot = f.ship().toWorld(seat.position());
            AssemblyResult r = ShipAssembler.disassemble(f.ship(), f.helm(), null);
            if (r.outcome() == AssemblyResult.Outcome.DISASSEMBLED) {
                done[0] = true;
                h.assertTrue(seat.isRemoved(), "seat survived disassembly");
                h.assertTrue(!c.isPassenger(), "crew member still riding");
                // checked in the tick of the disassembly: once released, the crew member strolls and may jump
                Vec3 p = c.position();
                double dx = Math.hypot(p.x - spot.x, p.z - spot.z), dy = Math.abs(p.y - spot.y);
                h.assertTrue(dx < 0.75 && dy < 0.6, "crew member at " + p + ", expected " + spot);
                h.assertTrue(h.getLevel().noCollision(c) && !h.getLevel().noCollision(c, c.getBoundingBox().move(0, -0.1, 0)),
                        "crew member not standing on the deck: " + p);
                h.runAfterDelay(15, () -> {
                    h.assertTrue(c.isAlive() && c.assignment() == null, "crew member still assigned to the gone ship");
                    // near the crew member (world) and at the seat's old plot position
                    List<StationSeat> left = h.getLevel().getEntitiesOfClass(StationSeat.class, c.getBoundingBox().inflate(6), s -> orphan(h, s));
                    left.addAll(h.getLevel().getEntitiesOfClass(StationSeat.class, seat.getBoundingBox().inflate(6), s -> orphan(h, s)));
                    h.assertTrue(left.isEmpty(), "seat entities left over: " + left.size() + " at " + left.stream().map(StationSeat::position).toList());
                    c.discard();
                    h.succeed();
                });
            }
        });
    }

    /**
     * A seat no live ship holds. Seats live in a plot, and the disassembled ship is gone; but Sable hands its freed
     * plot to the next ship at once, and a station test running at the same time assembles the same hull there and
     * seats its crew at the very same plot position (seen when these tests still shared a batch: the whistle test's
     * seat, 8 ticks after its ship took over this test's plot). That seat is not ours.
     */
    private static boolean orphan(GameTestHelper h, StationSeat s) {
        return SableShips.containing(h.getLevel(), s.blockPosition()) == null;
    }

    /** A seat without passenger removes itself after MAX_IDLE_TICKS (20): seats in a plot tick like any entity. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "idleseatremovesitself")
    public static void idleSeatRemovesItself(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        StationSeat seat = StationSeat.spawn(h.getLevel(), f.winch(), f.winch().north());
        h.assertTrue(!seat.isRemoved() && ShipEntities.containing(seat) != null, "seat not spawned in the ship's plot");
        h.runAfterDelay(10, () -> h.assertTrue(!seat.isRemoved(), "idle seat removed too early"));
        h.runAfterDelay(30, () -> {
            h.assertTrue(seat.isRemoved(), "idle seat still there after 30 ticks");
            h.succeed();
        });
    }

    /** Breaking the winch frees the station, removes the seat and releases the crew member. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "breakingthewinchreleasesthecrew")
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
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "assignmentsurvivessaveandload")
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

    /**
     * The captain's whistle with a mock player: use on a crew member selects it, use on the winch assigns it,
     * using it in the air gives no order on the server, the radial menu's "hoist" (its server handler) issues the
     * order to the ship's crew, use on the crew member again releases it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "whistleassignsordersandreleases")
    public static void whistleAssignsOrdersAndReleases(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = crew(h, 2, 2);
        net.minecraft.world.entity.player.Player p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        net.minecraft.world.item.ItemStack whistle = new net.minecraft.world.item.ItemStack(StationContent.CAPTAINS_WHISTLE.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, whistle);
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z);
        whistle.interactLivingEntity(p, c, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(c.assignment() == null, "selecting assigned the crew member");
        whistle.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(f.winch()), Direction.UP, f.winch(), false)));
        h.assertTrue(c.isAtStation(), "whistle on the winch did not seat the crew member");
        ShipBody on = com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem.shipOf(h.getLevel(), p);
        h.assertTrue(on != null && on.id().equals(f.ship().id()), "player on deck is not on the ship: " + p.position() + " " + f.ship().worldBounds());
        h.assertTrue(CrewStations.crewOf(h.getLevel(), f.ship().id()).contains(c), "crew member not found on its ship at " + c.position());
        whistle.use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(Stations.state(c.assignment()).order() == null, "using the whistle in the air gave an order on the server");
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "menu order hoist: " + r);
        h.assertTrue(Stations.state(c.assignment()).order() == SailOrder.HOIST, "the menu's hoist did not reach the crew");
        h.runAfterDelay(2 * STEP + MARGIN, () -> {
            h.assertTrue(trim(h, f) == SailTrim.FULL, "sails not hoisted by the whistle's order");
            whistle.interactLivingEntity(p, c, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(c.assignment() == null && !c.isPassenger(), "whistle on the assigned crew member did not release it");
            c.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ radial menu (order payload handler)

    /** A mock player standing on the fixture's deck, holding a whistle or (with {@code whistle == false}) nothing. */
    private static net.minecraft.world.entity.player.Player captain(GameTestHelper h, Fixture f, boolean whistle) {
        net.minecraft.world.entity.player.Player p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        if (whistle) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        }
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    /** The menu's "reef" reaches the seated crew and is remembered on the whistle; "release crew" frees the station. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "menuorderreachesseatedcrew")
    public static void menuOrderReachesSeatedCrew(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationRef ref = c.assignment();
        net.minecraft.world.entity.player.Player p = captain(h, f, true);
        WhistleOrders.Result r = WhistleOrders.handle(p, new WhistleOrderPayload(WhistleOrder.REEF).order());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "reef: " + r);
        h.assertTrue(Stations.state(ref).order() == SailOrder.REEF, "the crew does not reef");
        h.assertTrue(WhistleOrders.heldWhistle(p).get(StationContent.WHISTLE_ORDER.get()) == SailOrder.REEF, "the whistle does not remember reef");
        h.runAfterDelay(STEP + MARGIN, () -> {
            h.assertTrue(trim(h, f) == SailTrim.HALF, "sails not reefed by the menu's order: " + trim(h, f));
            WhistleOrders.Result rel = WhistleOrders.handle(p, WhistleOrder.RELEASE.id());
            h.assertTrue(rel.outcome() == WhistleOrders.Outcome.ISSUED && rel.crew() == 1, "release: " + rel);
            h.assertTrue(c.assignment() == null && !c.isPassenger(), "release crew left the crew member at its station");
            h.assertTrue(Stations.state(ref) == null || Stations.state(ref).occupant() == null, "the station is still occupied");
            c.discard();
            h.succeed();
        });
    }

    /** A player without a whistle cannot order through the payload. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "menuorderrefusedwithoutwhistle")
    public static void menuOrderRefusedWithoutWhistle(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        WhistleOrders.Result r = WhistleOrders.handle(captain(h, f, false), WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.NO_WHISTLE, "no whistle: " + r);
        h.assertTrue(Stations.state(c.assignment()).order() == null, "an order without a whistle reached the crew");
        h.assertTrue(c.isAtStation(), "the crew member left its station");
        c.discard();
        h.succeed();
    }

    /** An unknown order id (newer or modified client) is ignored: nothing changes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "menuignoresunknownorder")
    public static void menuIgnoresUnknownOrder(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        net.minecraft.world.entity.player.Player p = captain(h, f, true);
        WhistleOrders.Result r = WhistleOrders.handle(p, "fire_at_will");
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.UNKNOWN_ORDER, "unknown order: " + r);
        h.assertTrue(Stations.state(c.assignment()).order() == null, "an unknown order changed the crew's work");
        h.assertTrue(c.isAtStation(), "an unknown order released the crew member");
        h.assertTrue(trim(h, f) == SailTrim.FURLED, "an unknown order changed the sails");
        h.assertTrue(WhistleOrders.heldWhistle(p).get(StationContent.WHISTLE_ORDER.get()) == null, "an unknown order was remembered");
        c.discard();
        h.succeed();
    }

    /** A crew member that dies frees its station at once, and its seat goes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "deathfreesthestation")
    public static void deathFreesTheStation(GameTestHelper h) {
        pin(h);
        Fixture f = ship(h, false);
        CrewMember c = seated(h, f, crew(h, 2, 2));
        StationRef ref = c.assignment();
        h.runAfterDelay(5, () -> {
            c.kill();
            h.assertTrue(Stations.state(ref) == null, "station still occupied by the dead crew member");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(seats(h, f).isEmpty(), "seat left over after death");
            h.succeed();
        });
    }

    /** Removing the ship for good kills the seat (Sable, #sable:destroy_with_sub_level) and the crew is released. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "removingtheshipleavesnoseats")
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

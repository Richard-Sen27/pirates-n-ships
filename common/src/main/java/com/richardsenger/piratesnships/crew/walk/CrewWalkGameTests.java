package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.core.gametest.ShipTrack;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.galley.MealConfig;
import com.richardsenger.piratesnships.crew.galley.MealSeat;
import com.richardsenger.piratesnships.crew.galley.MealVisits;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.HammockSeat;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * WALK1 (docs/design.md §6): crew walk over the deck to their station, meal spot and hammock and are seated on
 * arrival; an unreachable spot seats them after {@code crew.walk.timeout_ticks}; {@code crew.walk.enabled} off seats
 * them at once. The ship is a 7×17 plank hull with the helm at the stern and the winch 12 blocks forward; the crew
 * member stands on the deck 7 blocks short of the winch, with AI (a mob without AI cannot walk and is seated at once).
 * Every test runs in a batch of its own: most change config or the clock, and the meal and nightfall rules reach every
 * ship of the level.
 */
public final class CrewWalkGameTests {

    private static final String BATCH = "pirates_n_ships_config_crew_walk_";

    static final BlockPos HELM = new BlockPos(18, 9, 6);
    static final BlockPos WINCH = new BlockPos(18, 9, 18);
    static final BlockPos START = new BlockPos(18, 9, 11);
    /** Ticks the crew member gets to land on the deck (and track the ship) before it is sent anywhere. */
    static final int SETTLE = 20;
    /** Ticks between two position samples. */
    static final int SAMPLE = 5;

    private CrewWalkGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CrewWalkGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    record Ship(ShipBody body, BlockPos helm) {
        /** Plot cell of the relative position {@code rel} the fixture built: assembly only translates. */
        BlockPos plot(BlockPos rel) {
            return helm.offset(rel.subtract(HELM));
        }
    }

    /** The basin (water or land) and the 7×17 hull with helm and winch on deck, plus {@code extra}, assembled. */
    static Ship ship(GameTestHelper h, boolean water, Consumer<GameTestHelper> extra) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        StationGameTests.basin(h, water);
        for (int x = 15; x <= 21; x++) {
            for (int z = 5; z <= 21; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 15 || x == 21 || z == 5 || z == 21;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(WINCH, SailingBlocks.SAIL_WINCH.get());
        extra.accept(h);
        AssemblyResult r = ShipTestCleanup.assemble(h, HELM);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody body = SableShips.byId(h.getLevel(), r.shipId());
        if (body == null) throw new AssertionError("no ship after assembly");
        return new Ship(body, StationGameTests.find(h, body, AssemblyContent.HELM.get()));
    }

    /** A crew member with AI on the deck at relative {@code rel}; stationary, so it does not stroll off before its walk. */
    static CrewMember crew(GameTestHelper h, Ship s, BlockPos rel) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = s.body().toWorld(Vec3.atBottomCenterOf(s.plot(rel)).add(0, 0.1, 0));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setStationary(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    static void assertOnShip(GameTestHelper h, Ship s, CrewMember c) {
        ShipBody on = ShipEntities.standingOrRiding(c);
        h.assertTrue(on != null && on.id().equals(s.body().id()), "the crew member does not stand on the ship (Sable tracking): " + on);
    }

    static void cleanup(CrewMember c) {
        if (c.getVehicle() != null) c.getVehicle().discard();
        c.discard();
    }

    /** What a walk looked like: its start distance and the samples on the way. */
    static final class Walk {
        double startDistance;
        long startTick = -1;
        Vec3 last;
        final List<Double> between = new ArrayList<>();
        boolean done;
        int offShip;

        /**
         * One sample at ship-space position {@code local}, {@code distance} from the spot: counted as on the way when
         * it moved since the last sample and lies between the start and the arrival distance.
         */
        void sample(Vec3 local, double distance) {
            if (last != null && local.distanceTo(last) > 0.2 && distance < startDistance - 0.5
                    && distance > WalkConfig.ARRIVE_DISTANCE.get()) {
                between.add(distance);
            }
            last = local;
        }
    }

    /**
     * The common station walk: at {@link #SETTLE} the crew member is ordered to the winch and must walk (not seated at
     * once, the station's work held); samples every {@link #SAMPLE} ticks; seated within {@code within} ticks with at
     * least two positions seen on the way. {@code onTick} runs every tick first (e.g. to drive the ship).
     */
    static Walk walkToWinch(GameTestHelper h, Ship s, CrewMember c, int within, Runnable onTick) {
        Walk w = new Walk();
        BlockPos winch = s.plot(WINCH);
        h.onEachTick(() -> {
            onTick.run();
            long t = h.getTick();
            if (w.done) {
                return;
            }
            if (t == SETTLE) {
                assertOnShip(h, s, c);
                CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, winch);
                h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
                h.assertTrue(!c.isAtStation() && c.walk() != null && c.walk().purpose() == WalkTarget.Purpose.STATION,
                        "not walking to the winch: at station " + c.isAtStation() + ", walk " + c.walk());
                StationRef ref = c.assignment();
                h.assertTrue(!CrewWalk.present(h.getLevel(), ref, c.getUUID()), "the station's work does not wait for the walker");
                w.startDistance = CrewWalk.distance(s.body(), c, c.walk().spot());
                h.assertTrue(w.startDistance >= 5, "the walk is too short to test: " + w.startDistance);
                w.startTick = t;
                w.last = s.body().toPlot(c.position());
                return;
            }
            if (w.startTick < 0) {
                return;
            }
            if (c.isAtStation()) {
                w.done = true;
                h.assertTrue(w.between.size() >= 2, "seated without being seen on the way (teleported?): " + w.between
                        + " from " + w.startDistance);
                h.assertTrue(c.walk() == null, "still walking when seated: " + c.walk());
                h.assertTrue(CrewWalk.present(h.getLevel(), c.assignment(), c.getUUID()), "seated but not present for the work");
                h.assertTrue(w.offShip <= 2, "left the deck on " + w.offShip + " samples");
                return;
            }
            h.assertTrue(t - w.startTick <= within, "not seated within " + within + " ticks; on the way at " + w.between
                    + ", now " + CrewWalk.distance(s.body(), c, s.plot(WINCH)) + " from the winch, walk " + c.walk());
            if ((t - w.startTick) % SAMPLE == 0) {
                h.assertTrue(c.walk() != null, "the walk ended without a seat: " + c.getVehicle());
                ShipBody on = ShipEntities.standingOrRiding(c);
                if (on == null || !on.id().equals(s.body().id())) w.offShip++;
                w.sample(s.body().toPlot(c.position()), CrewWalk.distance(s.body(), c, c.walk().spot()));
            }
        });
        return w;
    }

    // ------------------------------------------------------------------ stations

    /** On a ship resting on land, a crew member ordered to the winch 7 blocks off walks there and is seated. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "resting")
    public static void walksToItsStationOnARestingShip(GameTestHelper h) {
        Ship s = ship(h, false, x -> { });
        CrewMember c = crew(h, s, START);
        Walk w = walkToWinch(h, s, c, 100, () -> { });
        h.succeedWhen(() -> {
            h.assertTrue(w.done, "still walking");
            cleanup(c);
        });
    }

    /**
     * On a ship under way at about 2 blocks/s (driven every tick along its keel), the walk is measured in ship space
     * and ends at the winch; the ship's travel is integrated from its velocity ({@code ShipTrack}, sable-notes §9.0l).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240, batch = BATCH + "underway")
    public static void walksToItsStationOnAShipUnderWay(GameTestHelper h) {
        Ship s = ship(h, true, x -> { });
        CrewMember c = crew(h, s, START);
        ShipTrack track = ShipTrack.follow(h, s.body(), null, SETTLE);
        Walk w = walkToWinch(h, s, c, 120, () -> {
            if (h.getTick() >= 2 && !s.body().isRemoved()) {
                Vector3d v = s.body().linearVelocity();
                double dz = Math.max(-0.2, Math.min(0.2, 2.0 - v.z)); // ease up to 2 blocks/s along +z
                s.body().addVelocity(new Vector3d(-v.x * 0.5, 0, dz), new Vector3d());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.done, "still walking");
            long ticks = h.getTick() - w.startTick;
            track.stop();
            h.assertTrue(track.travel() >= 1.5 * ticks / 20.0, "the ship was not under way: " + track + " in " + ticks + " ticks");
            cleanup(c);
        });
    }

    /** A winch walled off by planks two high: the walker cannot reach it and is seated by the fallback at the timeout. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "walled")
    public static void walledOffStationSeatsAfterTheTimeout(GameTestHelper h) {
        int timeout = 60;
        ConfigOverrides.during(h, WalkConfig.TIMEOUT_TICKS, timeout);
        Ship s = ship(h, false, x -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                    x.setBlock(WINCH.offset(dx, 0, dz), Blocks.OAK_PLANKS);
                    x.setBlock(WINCH.offset(dx, 1, dz), Blocks.OAK_PLANKS);
                }
            }
        });
        CrewMember c = crew(h, s, START);
        h.runAtTickTime(SETTLE, () -> {
            assertOnShip(h, s, c);
            CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.plot(WINCH));
            h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
            h.assertTrue(!c.isAtStation() && c.walk() != null, "not walking: at station " + c.isAtStation());
        });
        h.runAtTickTime(SETTLE + timeout - 10, () -> {
            h.assertTrue(!c.isAtStation(), "seated before the timeout");
            h.assertTrue(c.walk() != null, "the walk ended before the timeout");
            h.assertTrue(CrewWalk.distance(s.body(), c, c.walk().spot()) > WalkConfig.ARRIVE_DISTANCE.get(),
                    "it got through the wall");
        });
        h.runAtTickTime(SETTLE + timeout + 10, () -> {
            h.assertTrue(c.isAtStation(), "not seated by the fallback after the timeout: " + c.walk());
            h.assertTrue(c.walk() == null, "still walking after the fallback");
            cleanup(c);
            h.succeed();
        });
    }

    /** {@code crew.walk.enabled = false}: the old behaviour, seated at once. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "off")
    public static void walkingOffSeatsAtOnce(GameTestHelper h) {
        ConfigOverrides.during(h, WalkConfig.ENABLED, false);
        Ship s = ship(h, false, x -> { });
        CrewMember c = crew(h, s, START);
        h.runAtTickTime(SETTLE, () -> {
            assertOnShip(h, s, c);
            CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.plot(WINCH));
            h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
            h.assertTrue(c.isAtStation() && c.walk() == null, "not seated at once: walk " + c.walk());
            cleanup(c);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ meal and hammock

    /** A meal: the free crew member walks to the pantry on the deck and sits down there on arrival. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "meal")
    public static void walksToItsMeal(GameTestHelper h) {
        ConfigOverrides.during(h, MealConfig.ENABLED, true);
        ConfigOverrides.during(h, MealConfig.MEAL_TICKS, 400);
        BlockPos pantryRel = new BlockPos(18, 9, 19);
        Ship s = ship(h, false, x -> x.setBlock(pantryRel, CrewContent.PANTRY.get()));
        CrewMember c = crew(h, s, START);
        Walk w = new Walk();
        h.runAtTickTime(SETTLE, () -> {
            assertOnShip(h, s, c);
            List<CrewMember> seated = MealVisits.serve(h.getLevel(), s.body());
            h.assertTrue(seated.contains(c), "not sent to the meal: " + seated);
            h.assertTrue(c.walk() != null && c.walk().purpose() == WalkTarget.Purpose.MEAL && !c.isPassenger(),
                    "not walking to the meal: walk " + c.walk() + ", vehicle " + c.getVehicle());
            h.assertTrue(!MealVisits.isFree(c), "a diner on its way counts as free");
            w.startDistance = CrewWalk.distance(s.body(), c, c.walk().spot());
            w.startTick = SETTLE;
            w.last = s.body().toPlot(c.position());
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (w.done || w.startTick < 0 || t <= w.startTick) return;
            if (c.getVehicle() instanceof MealSeat seat) {
                w.done = true;
                h.assertTrue(seat.provisions().equals(s.plot(pantryRel)), "the meal is not at the pantry: " + seat.provisions());
                h.assertTrue(!w.between.isEmpty(), "seated without being seen on the way: from " + w.startDistance);
                return;
            }
            h.assertTrue(t - w.startTick <= 100, "not at the meal within 100 ticks: walk " + c.walk() + ", on the way " + w.between);
            if ((t - w.startTick) % SAMPLE == 0 && c.walk() != null) {
                w.sample(s.body().toPlot(c.position()), CrewWalk.distance(s.body(), c, c.walk().spot()));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.done, "still walking");
            cleanup(c);
        });
    }

    /** Nightfall: the free crew member walks to the hammock hung on the deck and lies down in it on arrival. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "hammock")
    public static void walksToItsHammockAtNightfall(GameTestHelper h) {
        BlockPos footRel = new BlockPos(17, 9, 17);
        Ship s = ship(h, false, x -> {
            BlockState foot = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.EAST);
            x.setBlock(footRel.west(), Blocks.OAK_FENCE); // both posts first: a hammock without its supports falls
            x.setBlock(footRel.east(2), Blocks.OAK_FENCE);
            x.setBlock(footRel, foot.setValue(HammockBlock.PART, BedPart.FOOT));
            x.setBlock(footRel.east(), foot.setValue(HammockBlock.PART, BedPart.HEAD));
        });
        CrewMember c = crew(h, s, START);
        Walk w = new Walk();
        h.runAtTickTime(SETTLE, () -> {
            assertOnShip(h, s, c);
            h.assertTrue(com.richardsenger.piratesnships.crew.hammock.ShipBunks.hammocks(h.getLevel(), s.body()).equals(List.of(s.plot(footRel))),
                    "the hammock does not hang: " + h.getLevel().getBlockState(s.plot(footRel)));
            long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
            h.getLevel().setDayTime(today + 18000L); // midnight
            CrewRest.turnIn(h.getLevel(), s.body());
            h.assertTrue(c.walk() != null && c.walk().purpose() == WalkTarget.Purpose.HAMMOCK && c.rest() == null,
                    "not walking to the hammock: walk " + c.walk() + ", rest " + c.rest());
            h.assertTrue(c.nightOutcome() == NightOutcome.SLEPT, "night outcome " + c.nightOutcome());
            w.startDistance = CrewWalk.distance(s.body(), c, c.walk().spot());
            w.startTick = SETTLE;
            w.last = s.body().toPlot(c.position());
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (w.done || w.startTick < 0 || t <= w.startTick) return;
            if (c.rest() != null) {
                w.done = true;
                h.assertTrue(c.getVehicle() instanceof HammockSeat seat && seat.foot().equals(s.plot(footRel)),
                        "not in the hammock: " + c.getVehicle());
                h.assertTrue(!w.between.isEmpty(), "in the hammock without being seen on the way: from " + w.startDistance);
                return;
            }
            h.assertTrue(t - w.startTick <= 100, "not in the hammock within 100 ticks: walk " + c.walk() + ", on the way " + w.between);
            if ((t - w.startTick) % SAMPLE == 0 && c.walk() != null) {
                w.sample(s.body().toPlot(c.position()), CrewWalk.distance(s.body(), c, c.walk().spot()));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.done, "still walking");
            CrewRest.getUp(c, false);
            cleanup(c);
        });
    }
}

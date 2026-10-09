package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The crow's nest and its lookout (CN1, docs/design.md §6, §7). The ship is a ballasted 5×4×5 plank hull afloat in a
 * stone basin (water to y = 7) with its helm at the stern facing north (bow +z, heading about 180°) and a fence mast
 * amidships carrying the nest at y = 10. Scans are called directly ({@link Lookouts#scan}) rather than waited for, and
 * the other tests of a batch (their ships and sharks lie within the lookout's range) are told apart by memory key.
 * Tests that change config have batches of their own.
 */
public final class LookoutGameTests {

    private static final String BATCH = "pirates_n_ships_station_lookout_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_station_lookout_";

    private LookoutGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(LookoutGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Rig(Fixture f, BlockPos nest) {
        ShipBody ship() {
            return f.ship();
        }

        StationRef ref() {
            return new StationRef(f.ship().id(), nest);
        }

        /** World position of the nest's floor centre. */
        Vec3 floor() {
            return f.ship().toWorld(Vec3.atBottomCenterOf(nest).add(0, CrowsNestBlock.FLOOR_HEIGHT / 16.0, 0));
        }
    }

    /** The hull at x in [x0, x0+4], z in [z0, z0+4] with a fence mast and the nest on it, assembled. */
    private static Rig rig(GameTestHelper h, int x0, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        SailingGameTestsShips.ballast(h, x0, z0);
        BlockPos helm = new BlockPos(x0 + 2, 9, z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(x0 + 2, 9, z0 + 3), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(x0 + 2, 10, z0 + 3), LookoutContent.CROWS_NEST.get());
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        for (BlockPos p : f.ship().plotBlocks()) {
            if (h.getLevel().getBlockState(p).is(LookoutContent.CROWS_NEST.get())) {
                return new Rig(f, p);
            }
        }
        throw new AssertionError("no crow's nest on the ship");
    }

    /** The 40×40 basin of the sailing tests with the rig in its north-west corner. */
    private static Rig rig(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        return rig(h, 3, 3);
    }

    /** A crew member without AI on the deck, seated in the nest. */
    private static CrewMember lookout(GameTestHelper h, Rig r) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = r.ship().toWorld(Vec3.atBottomCenterOf(r.nest().below(2).east()));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        CrewStations.AssignResult a = CrewStations.assign(h.getLevel(), c, r.nest());
        h.assertTrue(a == CrewStations.AssignResult.ASSIGNED, "assign: " + a);
        return c;
    }

    /** A mock player standing on the nest's floor (not added to the level). */
    private static Player inTheNest(GameTestHelper h, Rig r) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = r.floor();
        p.moveTo(at.x, at.y, at.z);
        return p;
    }

    /** A shark without AI in the basin's water, {@code dx}, {@code dz} blocks from the nest (world axes). */
    private static Shark shark(GameTestHelper h, Rig r, double dx, double dz) {
        Shark s = MobContent.SHARK.get().create(h.getLevel());
        if (s == null) throw new AssertionError("no shark");
        Vec3 n = r.floor();
        s.moveTo(n.x + dx, h.absolutePos(new BlockPos(0, 4, 0)).getY(), n.z + dz, 0, 0);
        s.setNoAi(true);
        h.getLevel().addFreshEntity(s);
        return s;
    }

    private static List<Lookouts.Report> scan(GameTestHelper h, Rig r, List<Player> players, long now) {
        List<Lookouts.Observer> obs = Lookouts.observers(h.getLevel(), players).getOrDefault(r.ship().id(), List.of());
        if (obs.isEmpty()) {
            StringBuilder crew = new StringBuilder();
            for (CrewMember m : h.getLevel().getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(r.ship(), 8))) {
                crew.append(m.getUUID().toString(), 0, 8).append(" at ").append(m.position()).append(" assigned ").append(m.assignment())
                        .append(" seated ").append(m.isAtStation()).append(" vehicle ").append(m.getVehicle()).append("; ");
            }
            h.fail("no lookout on the ship " + r.ship().id() + " (nest " + r.nest() + " " + h.getLevel().getBlockState(r.nest())
                    + ", crew near: " + crew + ")");
        }
        return Lookouts.scan(h.getLevel(), r.ship(), obs, players, now);
    }

    /**
     * The game time for a test's first scan. The level tick scans every manned nest by itself every
     * {@code scan_interval_ticks}; one of those between the setup and the test's scan would already have remembered
     * (and called) what the test looks for, so the ship's memory is cleared first.
     */
    private static long firstScan(GameTestHelper h, Rig r) {
        Lookouts.forget(r.ship().id());
        return h.getLevel().getGameTime();
    }

    private static List<Lookouts.Report> about(List<Lookouts.Report> reports, String key) {
        return reports.stream().filter(rep -> rep.sighting().key().equals(key)).toList();
    }

    private static String key(Shark s) {
        return "entity:" + s.getUUID();
    }

    // ------------------------------------------------------------------ the block

    /**
     * The nest stands on a fence, a log and a solid top, not on air or beside a block; it breaks and drops itself when
     * the fence under it goes.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "block")
    public static void nestSitsOnAMastAndDropsWithoutIt(GameTestHelper h) {
        BlockState nest = LookoutContent.CROWS_NEST.get().defaultBlockState();
        h.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(4, 1, 2), Blocks.SPRUCE_LOG);
        h.setBlock(new BlockPos(6, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(2, 2, 6), Blocks.STONE);
        for (int x : new int[] {2, 4, 6}) {
            h.assertTrue(nest.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(x, 2, 2))), "no nest on the support at x " + x);
        }
        h.assertFalse(nest.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(2, 3, 4))), "a nest in mid air");
        h.assertFalse(nest.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 6))), "a nest beside a block");
        h.setBlock(new BlockPos(2, 2, 2), nest);
        h.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        h.runAtTickTime(2, () -> {
            h.assertBlockNotPresent(LookoutContent.CROWS_NEST.get(), new BlockPos(2, 2, 2));
            Item item = LookoutContent.CROWS_NEST.get().asItem();
            h.assertItemEntityPresent(item, new BlockPos(2, 2, 2), 1.5);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the station

    /**
     * A crew member assigned with the whistle's call takes the lookout station: it rides the station's seat inside the
     * nest's block and stands on the nest's floor (world position within half a block), and the lookout finds it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "station")
    public static void crewMemberKeepsWatchInTheNest(GameTestHelper h) {
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        h.runAtTickTime(5, () -> {
            h.assertTrue(c.isAtStation(), "the crew member does not ride the nest's seat");
            h.assertTrue(c.getVehicle() instanceof StationSeat seat && seat.blockPosition().equals(r.nest()),
                    "the seat is not in the nest: " + c.getVehicle());
            Vec3 floor = r.ship().toWorld(Vec3.atBottomCenterOf(r.nest()));
            h.assertTrue(c.position().distanceTo(floor) < 0.5, "the lookout stands at " + c.position() + ", the nest at " + floor);
            h.assertTrue(Stations.isManned(r.ref()), "the station is not manned");
            List<Lookouts.Observer> obs = Lookouts.observers(h.getLevel(), List.of()).get(r.ship().id());
            h.assertTrue(obs != null && obs.size() == 1 && obs.get(0).crew() == c, "observers " + obs);
            h.assertTrue(Stations.order(h.getLevel(), r.ref(), LookoutStation.Order.KEEP_WATCH) == Stations.OrderResult.NOTHING_TO_DO,
                    "the watch is an order with work to time");
            CrewStations.release(h.getLevel(), c);
            c.discard();
            h.succeed();
        });
    }

    /** A crew member at the lookout calls a shark in the water once, with its bearing, and not on the next scan. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "shark")
    public static void sharkIsCalledOnce(GameTestHelper h) {
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        // 12 blocks east of the nest: abeam to port of a ship heading south
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            long now = firstScan(h, r);
            List<Lookouts.Report> first = about(scan(h, r, List.of(), now), key(s));
            h.assertTrue(first.size() == 1, "shark calls on the first scan: " + first + " (shark alive " + s.isAlive() + " at "
                    + s.position() + ", nest " + r.floor() + ", lookout seated " + c.isAtStation() + ", observers "
                    + Lookouts.observers(h.getLevel(), List.of()).get(r.ship().id()) + ")");
            Lookouts.Report rep = first.get(0);
            h.assertTrue(rep.sighting().kind() == Lookouts.Kind.SHARK, "kind " + rep.sighting().kind());
            // east of a ship heading south is to port, abeam (8 points)
            double heading = Lookouts.heading(r.ship());
            h.assertTrue(Math.abs(Bearings.relative(180, heading)) < 20, "the test ship heads " + heading);
            Bearings.Relative b = rep.sighting().bearing();
            h.assertTrue(b.side() == Bearings.Side.PORT && Math.abs(b.points() - 8) <= 2, "bearing " + b);
            h.assertTrue(Math.abs(rep.sighting().distance() - 12) <= 1, "distance " + rep.sighting().distance());
            h.assertTrue(rep.line().getString().contains(c.getDisplayName().getString()), "speaker: " + rep.line().getString());
            h.assertTrue(about(scan(h, r, List.of(), now + 60), key(s)).isEmpty(), "the shark was called twice");
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /** A player standing in an unmanned nest is a lookout too, and is told what he sees. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "player")
    public static void playerInTheNestIsTold(GameTestHelper h) {
        Rig r = rig(h);
        Shark s = shark(h, r, 10, 6);
        h.runAtTickTime(5, () -> {
            Player p = inTheNest(h, r);
            Lookouts.Nest n = Lookouts.nestOf(h.getLevel(), p);
            h.assertTrue(n != null && n.pos().equals(r.nest()) && n.ship().id().equals(r.ship().id()), "nest of the player " + n);
            Player ashore = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 far = r.floor().add(20, 0, 20);
            ashore.moveTo(far.x, far.y, far.z);
            h.assertTrue(Lookouts.nestOf(h.getLevel(), ashore) == null, "a player off the ship is in the nest");
            List<Lookouts.Report> reps = about(scan(h, r, List.of(p, ashore), firstScan(h, r)), key(s));
            h.assertTrue(reps.size() == 1, "calls " + reps);
            List<UUID> to = reps.get(0).recipients();
            h.assertTrue(to.contains(p.getUUID()) && !to.contains(ashore.getUUID()), "recipients " + to);
            h.assertTrue(reps.get(0).line().getString().contains("Crow's nest") || reps.get(0).line().getString().contains(LookoutLang.KEY_NEST),
                    "speaker: " + reps.get(0).line().getString());
            s.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ an NPC ship

    private static final int SURFACE = 7;

    /** A 48×48 stone basin, water to y = 7, its ceiling removed up to y = 18 (the materialisation tests' basin). */
    private static void basin48(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 9; y <= 18; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /** A test convoy (origin {@code gametest/…}, left alone by the scheduler) whose position is the basin's middle. */
    private static Voyage convoy(GameTestHelper h) {
        BlockPos c = h.absolutePos(new BlockPos(28, SURFACE, 30));
        Vec3 m = new Vec3(c.getX(), c.getY(), c.getZ());
        double heading = 90.0; // sailing east, across the lookout's line of sight
        double dx = Math.sin(Math.toRadians(heading));
        double dz = -Math.cos(Math.toRadians(heading));
        List<Lane.Point> route = List.of(point(m, dx, dz, -100), point(m, dx, dz, 60), point(m, dx, dz, 200));
        ResourceLocation from = Constants.id("gametest/cn1_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.CONVOY, Faction.MERCHANTS, ShipTemplates.STARTER_SLOOP_ID, from, from,
                route, Map.of(), h.getLevel().getGameTime());
        v = v.withProgress(RouteMath.project(route, m.x, m.z, 100));
        VoyageData.get(h.getLevel().getServer()).put(v);
        return v;
    }

    private static Lane.Point point(Vec3 m, double dx, double dz, double d) {
        return new Lane.Point((int) Math.round(m.x + dx * d), (int) Math.round(m.z + dz * d));
    }

    /**
     * A materialised merchant within range is called once as "a merchant" with its bearing (south-east of a ship
     * heading south: about four points off the port bow) and its distance, and not on the next scan; the lookout's
     * own ship is never called.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "npc")
    public static void npcMerchantIsCalledOnceWithItsBearing(GameTestHelper h) {
        basin48(h);
        Rig r = rig(h, 3, 3);
        CrewMember c = lookout(h, r);
        Voyage v = convoy(h);
        ShipBody[] npc = new ShipBody[1];
        h.runAtTickTime(5, () -> {
            MinecraftServer server = h.getLevel().getServer();
            Materializer.Outcome o = Materializer.materialize(server, v.id());
            Voyage m = Voyages.get(server, v.id()).orElseThrow();
            m.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
            h.assertTrue(o == Materializer.Outcome.SPAWNED, "materialize: " + o);
            npc[0] = SableShips.byId(h.getLevel(), m.shipId().orElseThrow());
            h.assertTrue(npc[0] != null, "no NPC ship");
        });
        h.runAtTickTime(15, () -> {
            String key = "ship:" + npc[0].id();
            long now = firstScan(h, r);
            List<Lookouts.Report> all = scan(h, r, List.of(), now);
            h.assertTrue(about(all, "ship:" + r.ship().id()).isEmpty(), "the lookout called its own ship");
            List<Lookouts.Report> first = about(all, key);
            h.assertTrue(first.size() == 1, "merchant calls on the first scan: " + first + " of " + all);
            Lookouts.Sighting s = first.get(0).sighting();
            h.assertTrue(s.whatKey().equals(LookoutLang.KEY_WHAT_MERCHANT), "what " + s.whatKey());
            Vec3 eye = r.ship().toWorld(Vec3.atCenterOf(r.nest()));
            Vec3 there = CrewStations.worldBox(npc[0], 0).getCenter();
            double dx = there.x - eye.x;
            double dz = there.z - eye.z;
            h.assertTrue(dx > 10 && dz > 10, "the merchant is not south-east of the lookout: " + dx + ", " + dz);
            Bearings.Relative b = s.bearing();
            h.assertTrue(b.sector() == Bearings.Sector.BOW && b.side() == Bearings.Side.PORT && b.points() >= 3 && b.points() <= 5,
                    "bearing " + b + " (heading " + Lookouts.heading(r.ship()) + ")");
            h.assertTrue(Math.abs(s.distance() - Math.hypot(dx, dz)) <= 6, "distance " + s.distance() + " / " + Math.hypot(dx, dz));
            h.assertTrue(about(scan(h, r, List.of(), now + 60), key).isEmpty(), "the merchant was called twice");
            Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
            VoyageCrew.discard(h.getLevel(), v.id());
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /**
     * LAW4: {@link Lookouts#isManned} is false for an empty nest, true while a crew lookout is seated in it, false
     * again once he is released, true for a player (in the level) standing in it and false for one on the deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "manned")
    public static void isMannedByASeatedLookoutOrAPlayerInTheNest(GameTestHelper h) {
        Rig r = rig(h);
        h.assertFalse(Lookouts.isManned(h.getLevel(), r.ship()), "an empty nest is manned");
        CrewMember c = lookout(h, r);
        h.runAtTickTime(5, () -> {
            h.assertTrue(c.isAtStation(), "the lookout is not seated");
            h.assertTrue(Lookouts.isManned(h.getLevel(), r.ship()), "a seated lookout does not man the nest");
            CrewStations.release(h.getLevel(), c);
            c.discard();
        });
        h.runAtTickTime(7, () -> {
            h.assertFalse(Lookouts.isManned(h.getLevel(), r.ship()), "the nest is manned after the lookout left");
            Player p = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 deck = r.ship().toWorld(Vec3.atBottomCenterOf(r.nest().below(2).east()));
            p.moveTo(deck.x, deck.y, deck.z);
            h.getLevel().addFreshEntity(p);
            try {
                h.assertFalse(Lookouts.isManned(h.getLevel(), r.ship()), "a player on the deck mans the nest");
                Vec3 at = r.floor();
                p.setPos(at.x, at.y, at.z);
                h.assertTrue(Lookouts.isManned(h.getLevel(), r.ship()), "a player standing in the nest does not man it");
            } finally {
                p.discard();
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ config

    /** lookout.enabled off: nothing is scanned, the shark is not called. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "enabled")
    public static void switchedOffLookoutCallsNothing(GameTestHelper h) {
        ConfigOverrides.during(h, LookoutConfig.ENABLED, false);
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            long now = firstScan(h, r);
            h.assertTrue(about(scan(h, r, List.of(), now), key(s)).isEmpty(), "a switched-off lookout called the shark");
            h.assertTrue(Lookouts.scanAll(h.getLevel(), List.of(), now).isEmpty(), "a switched-off lookout scanned");
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /** lookout.announce off: the shark is seen and remembered, but nobody is told. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "announce")
    public static void silentLookoutTellsNobody(GameTestHelper h) {
        ConfigOverrides.during(h, LookoutConfig.ANNOUNCE, false);
        Rig r = rig(h);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            Player p = inTheNest(h, r);
            long now = firstScan(h, r);
            List<Lookouts.Report> reps = about(scan(h, r, List.of(p), now), key(s));
            h.assertTrue(reps.size() == 1 && reps.get(0).recipients().isEmpty(), "silent calls " + reps);
            h.assertTrue(about(scan(h, r, List.of(p), now + 60), key(s)).isEmpty(), "not remembered while silent");
            s.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /** lookout.range: a shark beyond it is not seen. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "range")
    public static void sharkBeyondTheRangeIsNotCalled(GameTestHelper h) {
        ConfigOverrides.during(h, LookoutConfig.RANGE, 8);
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        Shark far = shark(h, r, 14, 0);
        Shark near = shark(h, r, 5, 3);
        h.runAtTickTime(5, () -> {
            List<Lookouts.Report> reps = scan(h, r, List.of(), firstScan(h, r));
            h.assertTrue(about(reps, key(far)).isEmpty(), "a shark 14 blocks off was seen with range 8");
            h.assertTrue(about(reps, key(near)).size() == 1, "the near shark was not called: " + reps);
            far.discard();
            near.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /** lookout.memory_ticks: a sighting not seen for longer is called again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "memory")
    public static void forgottenSharkIsCalledAgain(GameTestHelper h) {
        ConfigOverrides.during(h, LookoutConfig.MEMORY_TICKS, 20);
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            long now = firstScan(h, r);
            h.assertTrue(about(scan(h, r, List.of(), now), key(s)).size() == 1, "first call");
            h.assertTrue(about(scan(h, r, List.of(), now + 10), key(s)).isEmpty(), "called again within the memory");
            h.assertTrue(about(scan(h, r, List.of(), now + 40), key(s)).size() == 1, "not called again after 30 ticks unseen");
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }
}

package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
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
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.FlagConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpolePart;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleRun;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
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
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
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
        return rig(h, x0, z0, 0);
    }

    /** The rig with {@code poleBlocks} flagpole blocks standing on the nest (FLG3). */
    private static Rig rig(GameTestHelper h, int x0, int z0, int poleBlocks) {
        return rig(h, x0, z0, poleBlocks, LookoutContent.CROWS_NEST.get());
    }

    /** The rig with the crow's nest {@code nestBlock} (WOOD3: any wood's). */
    private static Rig rig(GameTestHelper h, int x0, int z0, int poleBlocks, CrowsNestBlock nestBlock) {
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
        h.setBlock(new BlockPos(x0 + 2, 10, z0 + 3), nestBlock);
        for (int i = 1; i <= poleBlocks; i++) h.setBlock(new BlockPos(x0 + 2, 10 + i, z0 + 3), ShipDecor.FLAGPOLE.get());
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        for (BlockPos p : f.ship().plotBlocks()) {
            if (h.getLevel().getBlockState(p).is(nestBlock)) {
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

    // ------------------------------------------------------------------ FLG3: a flagpole on the nest

    /** A player places a flagpole on the top face of the block at {@code onto}, as vanilla does. */
    private static boolean placePole(GameTestHelper h, Player player, BlockPos onto) {
        ItemStack held = new ItemStack(ShipDecor.FLAGPOLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(onto);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    /**
     * A flagpole placed on the nest rises from its centre: the nest shows the socketed model (pole stub and cleat), the
     * pole block above it is a bare top (no cleat) with no collision, so a player standing on the nest's floor is not
     * pushed; a second block makes it a two-block pole whose head flies the flag above the
     * nest. Breaking the nest brings the pole and its flag down; the nest's own support rule is unchanged.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "pole")
    public static void poleOnTheNestFliesAboveIt(GameTestHelper h) {
        ConfigOverrides.during(h, FlagConfig.STACKED_POLES, true);
        ConfigOverrides.during(h, FlagConfig.MAX_POLE_HEIGHT, 4);
        Player builder = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos nest = new BlockPos(2, 2, 2), low = nest.above(), head = low.above();
        h.setBlock(nest.below(), Blocks.SPRUCE_LOG); // a log mast: a fence's tall collision would reach into the nest
        h.setBlock(nest, LookoutContent.CROWS_NEST.get());
        h.assertBlockProperty(nest, FlagpoleFooting.POLE, false);
        h.assertTrue(placePole(h, builder, nest), "no pole placed on the nest");
        h.assertBlockProperty(nest, FlagpoleFooting.POLE, true);
        h.assertBlockProperty(low, FlagpoleBlock.PART, FlagpolePart.TOP);
        BlockPos lowAbs = h.absolutePos(low);
        h.assertTrue(h.getLevel().getBlockState(lowAbs).getCollisionShape(h.getLevel(), lowAbs).isEmpty(),
                "the pole above the nest blocks the lookout");
        h.assertFalse(h.getLevel().getBlockState(lowAbs).getShape(h.getLevel(), lowAbs).isEmpty(), "the pole has no outline");
        h.assertTrue(placePole(h, builder, low), "no second pole block");
        BlockPos headAbs = h.absolutePos(head);
        h.assertBlockProperty(low, FlagpoleBlock.PART, FlagpolePart.MIDDLE);
        h.assertFalse(h.getLevel().getBlockState(headAbs).getCollisionShape(h.getLevel(), headAbs).isEmpty(),
                "the upper pole block lost its collision");
        FlagpoleFoot.Pole pole = FlagpoleRun.pole(h.getLevel(), headAbs);
        h.assertTrue(pole.height() == 2 && pole.cleat() == -2 && pole.base() == FlagpoleFoot.Support.CROWS_NEST, "pole " + pole);
        h.assertTrue(FlagpoleRun.owner(h.getLevel(), lowAbs) == h.getBlockEntity(head), "the head does not own the flag");
        if (!(h.getBlockEntity(head) instanceof FlagpoleBlockEntity be)) throw new AssertionError("no head");
        be.commandSet(FlagKind.NAVY, false, null);
        h.assertBlockProperty(head, FlagpoleBlock.FLAG, FlagKind.NAVY);
        // a player on the nest's floor stands free of the pole and is in the nest
        Player lookout = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 floor = Vec3.atBottomCenterOf(h.absolutePos(nest)).add(0, CrowsNestBlock.FLOOR_HEIGHT / 16.0, 0);
        lookout.moveTo(floor.x, floor.y, floor.z);
        h.assertTrue(lookout.getBoundingBox().maxY > headAbs.getY() - 1 + 0.5, "the test player does not reach into the pole block");
        h.assertTrue(h.getLevel().noCollision(lookout, lookout.getBoundingBox()), "the lookout collides with the pole");
        h.getLevel().destroyBlock(h.absolutePos(nest), true);
        h.runAtTickTime(2, () -> {
            h.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), low);
            h.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), head);
            h.assertItemEntityCountIs(ShipDecor.FLAGPOLE.get().asItem(), low, 3.0, 2);
            h.assertItemEntityPresent(Flags.NAVY_FLAG.get(), head, 2.0);
            h.assertItemEntityPresent(LookoutContent.CROWS_NEST.get().asItem(), nest, 2.0);
            h.succeed();
        });
    }

    /**
     * With a two-block flagpole on the nest, through assembly: the nest still shows the pole, a crew member assigned
     * with the whistle sits in the nest, the watch finds it and it calls a shark; a player standing in the nest is a
     * lookout too.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "pole_watch")
    public static void lookoutKeepsWatchUnderAPole(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Rig r = rig(h, 3, 3, 2);
        h.assertTrue(h.getLevel().getBlockState(r.nest()).getValue(FlagpoleFooting.POLE), "the nest lost its pole in assembly");
        h.assertTrue(FlagpoleRun.isPole(h.getLevel(), r.nest().above()) && FlagpoleRun.isPole(h.getLevel(), r.nest().above(2)),
                "the pole did not come along");
        CrewMember c = lookout(h, r);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            h.assertTrue(c.isAtStation() && c.getVehicle() instanceof StationSeat seat && seat.blockPosition().equals(r.nest()),
                    "the lookout is not seated in the nest: " + c.getVehicle());
            List<Lookouts.Observer> obs = Lookouts.observers(h.getLevel(), List.of()).get(r.ship().id());
            h.assertTrue(obs != null && obs.size() == 1 && obs.get(0).crew() == c, "observers " + obs);
            List<Lookouts.Report> calls = about(scan(h, r, List.of(), firstScan(h, r)), key(s));
            h.assertTrue(calls.size() == 1 && calls.get(0).sighting().kind() == Lookouts.Kind.SHARK, "calls " + calls);
            Player p = inTheNest(h, r);
            Lookouts.Nest n = Lookouts.nestOf(h.getLevel(), p);
            h.assertTrue(n != null && n.pos().equals(r.nest()), "nest of the player " + n);
            h.assertTrue(Lookouts.isManned(h.getLevel(), r.ship()), "the nest with a pole is not manned");
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
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
            SightingPayload call = rep.payload();
            h.assertTrue(call.what() == SightingPayload.What.SHARK && call.relative() < -60 && call.relative() > -120
                    && call.distance() == rep.sighting().distance() && call.ship().equals(r.ship().id()), "call " + call);
            h.assertTrue(about(scan(h, r, List.of(), now + 60), key(s)).isEmpty(), "the shark was called twice");
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /**
     * WOOD3: an acacia crow's nest is a lookout station like the spruce one: a crew member seated in it calls a shark
     * once; the nest anchors ratlines (the family is in {@code ratlines_anchors}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "acacia")
    public static void anAcaciaNestSightsAShark(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        CrowsNestBlock acacia = LookoutContent.CROWS_NESTS.get(ShipWood.ACACIA);
        Rig r = rig(h, 3, 3, 0, acacia);
        h.assertTrue(h.getLevel().getBlockState(r.nest()).is(RiggingContent.RATLINES_ANCHORS), "the acacia nest anchors no ratlines");
        CrewMember c = lookout(h, r);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            long now = firstScan(h, r);
            List<Lookouts.Report> first = about(scan(h, r, List.of(), now), key(s));
            h.assertTrue(first.size() == 1 && first.get(0).sighting().kind() == Lookouts.Kind.SHARK,
                    "shark calls from the acacia nest: " + first);
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
            Vec3 nest = r.ship().toWorld(Vec3.atCenterOf(r.nest()));
            SightingPayload call = reps.get(0).payload();
            h.assertTrue(new Vec3(call.nestX(), call.nestY(), call.nestZ()).distanceTo(nest) < 0.01, "hailed from " + call);
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
            h.assertTrue(s.what() == SightingPayload.What.MERCHANT && first.get(0).payload().what() == SightingPayload.What.MERCHANT,
                    "what " + s.what());
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

    /** TPL2: ships the tests make renowned (the source is registered once, it answers only for these ids). */
    private static final Map<UUID, String> RENOWNED = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        Lookouts.renown((level, ship) -> java.util.Optional.ofNullable(RENOWNED.get(ship)));
    }

    /**
     * TPL2: a ship the lookout knows by sight ({@link Lookouts#renown}, a pirate captain's) is called by its name
     * ("Sail ho! ... it's &lt;name&gt;!"); the same merchant unnamed is called as before.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "renowned")
    public static void renownedShipIsCalledByName(GameTestHelper h) {
        basin48(h);
        Rig r = rig(h, 3, 3);
        CrewMember c = lookout(h, r);
        Voyage v = convoy(h);
        ShipBody[] npc = new ShipBody[1];
        String name = "Mad Silas' Night Shark";
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
            List<Lookouts.Report> plain = about(scan(h, r, List.of(), now), key);
            h.assertTrue(plain.size() == 1 && plain.get(0).sighting().name().isEmpty() && plain.get(0).payload().name().isEmpty(),
                    "an unnamed call: " + plain);
            String plainCue = SightingCue.text(plain.get(0).payload(), english());
            h.assertTrue(plainCue.startsWith("Merchant "), "the plain call: " + plainCue);
            RENOWNED.put(npc[0].id(), name);
            try {
                Lookouts.forget(r.ship().id());
                List<Lookouts.Report> named = about(scan(h, r, List.of(), now + 1), key);
                h.assertTrue(named.size() == 1 && named.get(0).sighting().name().equals(name), "a named call: " + named);
                h.assertTrue(named.get(0).payload().name().equals(name), "the call does not carry the name: " + named.get(0).payload());
                String cue = SightingCue.text(named.get(0).payload(), english());
                h.assertTrue(cue.startsWith(name + " ") && cue.length() <= SightingCue.MAX, "the cue does not name the ship: " + cue);
            } finally {
                RENOWNED.remove(npc[0].id());
            }
            Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
            VoyageCrew.discard(h.getLevel(), v.id());
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    private static java.util.function.UnaryOperator<String> english() {
        return k -> LookoutLang.ENGLISH.getOrDefault(k, k);
    }

    // ------------------------------------------------------------------ LKO2: the call

    /** A mock player that keeps every chat line and action-bar message it is shown. */
    private static final class Listener extends Player {
        final List<Component> chat = new ArrayList<>();
        final List<SightingPayload> calls = new ArrayList<>();

        Listener(GameTestHelper h, Vec3 at) {
            super(h.getLevel(), BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "test-sailor"));
            moveTo(at.x, at.y, at.z);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            chat.add(message);
        }

        @Override
        public void sendSystemMessage(Component message) {
            chat.add(message);
        }
    }

    /** Scans and delivers like the level tick does, into the listeners' own lists. */
    private static List<Lookouts.Report> scanAndDeliver(GameTestHelper h, Rig r, List<Player> players, long now) {
        List<Lookouts.Report> reps = scan(h, r, players, now);
        for (Lookouts.Report rep : reps) {
            Lookouts.deliver(rep, players, (p, payload) -> {
                if (p instanceof Listener l) l.calls.add(payload);
            });
        }
        return reps;
    }

    /**
     * LKO2: a shark east of a ship heading south is called to the player in the nest and to a player on the deck (not
     * to one ashore) as a payload: a shark, about 90° to port, its distance, hailed from the nest, its mark kept
     * {@code mark_seconds}; the cue reads "Shark abeam to port, 12 blocks" (at most 40 characters). Nobody gets a chat
     * line.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "call")
    public static void callReachesNestAndDeckWithoutChat(GameTestHelper h) {
        Rig r = rig(h);
        Shark s = shark(h, r, 12, 0);
        h.runAtTickTime(5, () -> {
            Listener nest = new Listener(h, r.floor());
            Listener deck = new Listener(h, r.ship().toWorld(Vec3.atBottomCenterOf(r.nest().below(1).east())));
            Listener ashore = new Listener(h, r.floor().add(30, -4, 30));
            h.assertTrue(Lookouts.nestOf(h.getLevel(), nest) != null, "the nest listener is not in the nest");
            List<Player> players = List.of(nest, deck, ashore);
            List<Lookouts.Report> reps = about(scanAndDeliver(h, r, players, firstScan(h, r)), key(s));
            h.assertTrue(reps.size() == 1, "calls " + reps);
            SightingPayload sent = reps.get(0).payload();
            for (Listener l : List.of(nest, deck)) {
                List<SightingPayload> got = l.calls.stream().filter(c -> c.key() == sent.key()).toList();
                h.assertTrue(got.size() == 1, (l == nest ? "nest" : "deck") + " calls " + l.calls);
                SightingPayload c = got.get(0);
                h.assertTrue(c.what() == SightingPayload.What.SHARK, "what " + c.what());
                h.assertTrue(c.relative() < -70 && c.relative() > -110, "relative bearing " + c.relative());
                h.assertTrue(Math.abs(Bearings.relative(c.compass(), 90)) < 20, "compass bearing " + c.compass());
                h.assertTrue(Math.abs(c.distance() - 12) <= 1, "distance " + c.distance());
                h.assertTrue(c.freshTicks() == LookoutConfig.MARK_SECONDS.get() * 20, "fresh " + c.freshTicks());
                h.assertTrue(c.ship().equals(r.ship().id()), "ship " + c.ship());
            }
            h.assertTrue(ashore.calls.isEmpty(), "a player ashore was called: " + ashore.calls);
            String cue = SightingCue.text(sent, english());
            h.assertTrue(cue.startsWith("Shark ") && cue.contains("port") && cue.length() <= SightingCue.MAX, "cue " + cue);
            for (Listener l : List.of(nest, deck, ashore)) {
                h.assertTrue(l.chat.isEmpty(), "a chat line: " + l.chat);
            }
            s.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /**
     * LKO2: a crew lookout's call reaches the players aboard (no one in the nest), and a pirate ship is called as one,
     * through the same payload.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "crew_call")
    public static void crewLookoutCallsThePlayersAboard(GameTestHelper h) {
        Rig r = rig(h);
        CrewMember c = lookout(h, r);
        Shark s = shark(h, r, 0, 14);
        h.runAtTickTime(5, () -> {
            h.assertTrue(c.isAtStation(), "the lookout is not seated");
            Listener deck = new Listener(h, r.ship().toWorld(Vec3.atBottomCenterOf(r.nest().below(1).east())));
            List<Lookouts.Report> reps = about(scanAndDeliver(h, r, List.of(deck), firstScan(h, r)), key(s));
            h.assertTrue(reps.size() == 1, "calls " + reps);
            List<SightingPayload> got = deck.calls.stream().filter(x -> x.key() == reps.get(0).payload().key()).toList();
            h.assertTrue(got.size() == 1, "the deck's calls " + deck.calls);
            // 14 blocks south of a ship heading south: about dead ahead
            h.assertTrue(Math.abs(got.get(0).relative()) < 20, "relative " + got.get(0).relative());
            String cue = SightingCue.text(got.get(0), english());
            h.assertTrue(cue.startsWith("Shark ") && cue.endsWith(", " + got.get(0).distance() + " blocks") && cue.length() <= SightingCue.MAX,
                    "cue " + cue);
            h.assertTrue(deck.chat.isEmpty(), "a chat line: " + deck.chat);
            s.discard();
            CrewStations.release(h.getLevel(), c);
            c.discard();
            Lookouts.forget(r.ship().id());
            h.succeed();
        });
    }

    /**
     * LKO2's repeat rule: a shark still in view is not called again on the next scans, is called again once it came
     * {@code recall_blocks} closer, and again {@code recall_seconds} after the last call.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "recall")
    public static void sightingIsCalledAgainCloserOrLater(GameTestHelper h) {
        ConfigOverrides.during(h, LookoutConfig.RECALL_BLOCKS, 6);
        ConfigOverrides.during(h, LookoutConfig.RECALL_SECONDS, 10);
        Rig r = rig(h);
        Shark s = shark(h, r, 16, 0);
        h.runAtTickTime(5, () -> {
            Player p = inTheNest(h, r);
            long now = firstScan(h, r);
            h.assertTrue(about(scan(h, r, List.of(p), now), key(s)).size() == 1, "first call");
            h.assertTrue(about(scan(h, r, List.of(p), now + 60), key(s)).isEmpty(), "called again in view, not closer");
            Vec3 n = r.floor();
            s.moveTo(n.x + 12, s.getY(), n.z);
            h.assertTrue(about(scan(h, r, List.of(p), now + 120), key(s)).isEmpty(), "called again 4 blocks closer");
            s.moveTo(n.x + 8, s.getY(), n.z);
            List<Lookouts.Report> closer = about(scan(h, r, List.of(p), now + 140), key(s));
            h.assertTrue(closer.size() == 1, "not called again 8 blocks closer: " + closer);
            h.assertTrue(about(scan(h, r, List.of(p), now + 200), key(s)).isEmpty(), "called again right after");
            h.assertTrue(about(scan(h, r, List.of(p), now + 140 + 200), key(s)).size() == 1, "not called again 10 s later");
            s.discard();
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

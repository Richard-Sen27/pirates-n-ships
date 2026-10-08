package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryConfig;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.worldsim.faction.FactionConfig;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Navy patrols and the hunt (WS4b) in a 40×40 dry dock with open sky (the WS4a gunnery layout): the quarry is a 5×5
 * plank hull at x 27..31, z 17..21 (deck y 8, helm at (29, 9, 19), flagpole at (28, 9, 18)), owned by a made-up player;
 * for the gun tests the patrol is a second hull at x 3..7 with a cannon at (6, 9, 18) facing east, 24 blocks from the
 * quarry, linked to a MATERIALISED test voyage. Abstract patrols are SAILING test voyages ({@code gametest/ws4b_…}
 * origins, which the automatic checks leave to the tests) placed 100 blocks east of the quarry. Every test calls
 * {@link Hunting#updateAbstract} / {@link Hunting#updateMaterialised} itself and runs in a batch of its own: patrols
 * sight ships within 256 blocks, and the gun tests change cannon timers.
 */
public final class NavyGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_navy_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_worldsim_navy_";
    private static final int SIZE = 40;
    private static final int PATROL_X = 3, TARGET_X = 27, Z = 17;
    /** Blocks east of the quarry an abstract patrol starts. */
    private static final int ABSTRACT_DISTANCE = 100;

    private static final Map<UUID, List<CannonShipHits.ShipHit>> HITS = new ConcurrentHashMap<>();
    private static volatile boolean listening;

    private NavyGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NavyGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        CannonShipHits.register(hit -> HITS.computeIfAbsent(hit.hitShip(), id -> new CopyOnWriteArrayList<>()).add(hit));
    }

    private static int hits(UUID ship) {
        List<CannonShipHits.ShipHit> l = HITS.get(ship);
        return l == null ? 0 : l.size();
    }

    /** Stone up to y 4 over the whole template (both hulls rest on it and keep their heading), walls, open sky. */
    private static void basin(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall || y <= 4 ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        SailingGameTestsShips.openSky(h, SIZE);
    }

    /** A closed 5×5×4 plank hull at x x0..x0+4, z Z..Z+4, bottom y 5; returns the helm on its deck. */
    private static BlockPos hull(GameTestHelper h, int x0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = Z; z <= Z + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == Z || z == Z + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, Z + 2);
        h.setBlock(helm, AssemblyContent.HELM.get());
        return helm;
    }

    /** The quarry: its pole and helm before assembly, the owner it belongs to. */
    private record Quarry(Fixture ship, UUID owner) {
        UUID id() {
            return ship.ship().id();
        }

        Vec3 centre() {
            return Hunting.centre(ship.ship());
        }

        BlockPos polePlot() {
            return ship.helmPlot().offset(-1, 0, -1);
        }
    }

    /** The quarry hull flying {@code flag}, assembled and registered as the ship of a made-up player. */
    private static Quarry quarry(GameTestHelper h, FlagKind flag) {
        BlockPos helm = hull(h, TARGET_X);
        BlockPos pole = helm.offset(-1, 0, -1);
        h.setBlock(pole, ShipDecor.FLAGPOLE.get());
        if (!(h.getBlockEntity(pole) instanceof FlagpoleBlockEntity be)) throw new GameTestAssertException("no flagpole at " + pole);
        be.commandSet(flag, false, null);
        Fixture f = DryHullGameTests.assemble(h, helm);
        UUID owner = UUID.randomUUID();
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        ShipData data = registry.find(f.ship().id()).orElseThrow(() -> new GameTestAssertException("the quarry is not registered"));
        registry.put(data.withOwner(Optional.of(owner)));
        h.assertTrue(ShipAllegiance.of(f.ship()).kind() == flag, "the quarry flies " + ShipAllegiance.of(f.ship()));
        return new Quarry(f, owner);
    }

    /** A SAILING test patrol {@value #ABSTRACT_DISTANCE} blocks east of the quarry, on a route running north–south. */
    private static Voyage abstractPatrol(GameTestHelper h, Quarry q) {
        Vec3 c = q.centre();
        int x = (int) Math.round(c.x) + ABSTRACT_DISTANCE;
        int z = (int) Math.round(c.z);
        List<Lane.Point> route = List.of(new Lane.Point(x, z - 100), new Lane.Point(x, z + 100));
        ResourceLocation from = Constants.id("gametest/ws4b_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.PATROL, Faction.NAVY, ShipTemplates.STARTER_SLOOP_ID, from, from,
                route, Map.of(), h.getLevel().getGameTime()).withProgress(100);
        VoyageData.get(h.getLevel().getServer()).put(v);
        return v;
    }

    /** One hunting check of an abstract patrol, stored like the scheduler stores it. */
    private static Voyage check(GameTestHelper h, Voyage v) {
        MinecraftServer server = h.getLevel().getServer();
        Voyage now = Voyages.get(server, v.id()).orElseThrow(() -> new GameTestAssertException("the patrol is gone"));
        Voyage next = Hunting.updateAbstract(server, now);
        Voyages.update(server, next);
        return next;
    }

    /** What a patrol sees of the quarry now (test messages). */
    private static String seen(GameTestHelper h, Quarry q) {
        return String.valueOf(Hunting.candidate(h.getLevel(), q.id())) + ", data " + ShipRegistry.get(h.getLevel().getServer()).find(q.id());
    }

    private static void end(GameTestHelper h, Voyage v) {
        Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
    }

    /** The patrol hull with an east-facing cannon and a chest, linked to a MATERIALISED test patrol; the gun crewed. */
    private record Engagement(Fixture patrol, Quarry quarry, Voyage voyage, BlockPos cannon, BlockPos chest, CrewMember gunner) {
    }

    private static Engagement engagement(GameTestHelper h, FlagKind flag) {
        listen();
        basin(h);
        BlockPos helm = hull(h, PATROL_X);
        BlockPos master = helm.offset(1, 0, -1);
        BlockState state = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.EAST);
        h.setBlock(master, state);
        h.setBlock(CannonRules.rearOf(master, Direction.EAST), CannonContent.CANNON.get().rearState(state));
        h.setBlock(helm.offset(-1, 0, -1), Blocks.CHEST);
        Quarry q = quarry(h, flag);
        Fixture patrol = DryHullGameTests.assemble(h, helm);
        BlockPos cannon = patrol.helmPlot().offset(1, 0, -1);
        BlockPos chest = patrol.helmPlot().offset(-1, 0, -1);
        h.assertTrue(CannonBlock.isMaster(h.getLevel().getBlockState(cannon)), "the cannon is not in the plot at " + cannon);

        Vec3 c = Hunting.centre(patrol.ship());
        List<Lane.Point> route = List.of(new Lane.Point((int) c.x, (int) c.z - 100), new Lane.Point((int) c.x, (int) c.z + 100));
        ResourceLocation from = Constants.id("gametest/ws4b_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.PATROL, Faction.NAVY, ShipTemplates.STARTER_SLOOP_ID, from, from,
                route, Map.of(), h.getLevel().getGameTime()).withProgress(100)
                .withState(Voyage.State.MATERIALISED, Optional.of(patrol.ship().id()));
        VoyageData.get(h.getLevel().getServer()).put(v);

        Vec3 deck = patrol.ship().toWorld(Vec3.atBottomCenterOf(patrol.helmPlot().offset(1, 0, 1)));
        CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), h.relativeVec(deck));
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), crew, cannon);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        return new Engagement(patrol, q, v, cannon, chest, crew);
    }

    private static void loadByHand(GameTestHelper h, Engagement e) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonService.load(level, e.cannon(), creative, new ItemStack(Items.GUNPOWDER)).outcome()
                == CannonService.Outcome.POWDER_IN, "powder was refused");
        h.assertTrue(CannonService.load(level, e.cannon(), creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the ball was refused");
    }

    private static CannonBlockEntity cannon(GameTestHelper h, Engagement e) {
        if (h.getLevel().getBlockEntity(e.cannon()) instanceof CannonBlockEntity be) return be;
        throw new GameTestAssertException("no cannon at " + e.cannon());
    }

    /** Runs a materialised hunting check every {@code interval} ticks for the whole test. */
    private static void checkEvery(GameTestHelper h, Engagement e, int interval) {
        long start = h.getLevel().getGameTime();
        h.onEachTick(() -> {
            if ((h.getLevel().getGameTime() - start) % interval == 0) Hunting.updateMaterialised(h.getLevel().getServer(), e.voyage().id());
        });
    }

    private static Voyage voyage(GameTestHelper h, Voyage v) {
        return Voyages.get(h.getLevel().getServer(), v.id()).orElseThrow(() -> new GameTestAssertException("the patrol is gone"));
    }

    private static void cleanup(GameTestHelper h, Engagement e) {
        Gunnery.clear(e.patrol().ship());
        h.getEntities(CannonContent.CANNONBALL.get()).forEach(CannonballEntity::discard);
        e.gunner().discard();
        HITS.remove(e.quarry().id());
        end(h, e.voyage());
    }

    // ------------------------------------------------------------------ abstract patrols

    /**
     * A player's ship under the Jolly Roger 100 blocks from an abstract patrol: one check gives the record the quarry as
     * its pursuit, a straight route toward it that stops short of it, and saves the route it left.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "jolly_roger")
    public static void aJollyRogerShipIsChasedAfterOneCheck(GameTestHelper h) {
        basin(h);
        Quarry q = quarry(h, FlagKind.JOLLY_ROGER);
        Voyage v = abstractPatrol(h, q);
        Lane.Position before = v.position();
        Voyage after = check(h, v);
        h.assertTrue(after.pursuit().equals(Optional.of(q.id())), "pursuit " + after.pursuit());
        Lane.Point end = after.waypoints().get(after.waypoints().size() - 1);
        Vec3 c = q.centre();
        double startDistance = Math.hypot(before.x() - c.x, before.z() - c.z);
        double endDistance = Math.hypot(end.x() - c.x, end.z() - c.z);
        h.assertTrue(endDistance < startDistance, "the pursuit route does not close in: " + after.waypoints());
        h.assertTrue(endDistance >= NavyConfig.STANDOFF_DISTANCE.get() - 2, "the pursuit route runs onto the quarry: " + endDistance);
        Optional<NavyData.Pursuit> p = NavyData.get(h.getLevel().getServer()).get(v.id());
        h.assertTrue(p.isPresent() && p.get().home().equals(v.waypoints()) && Math.abs(p.get().homeProgress() - 100) < 1e-6,
                "the route left is not saved: " + p);
        end(h, v);
        h.assertTrue(NavyData.get(h.getLevel().getServer()).get(v.id()).isEmpty(), "the chase outlived its voyage");
        h.succeed();
    }

    /** A player's ship under the merchant flag whose owner is clean is left alone. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "merchant")
    public static void aCleanMerchantShipIsIgnored(GameTestHelper h) {
        basin(h);
        Quarry q = quarry(h, FlagKind.MERCHANT);
        Voyage v = abstractPatrol(h, q);
        Voyage after = check(h, v);
        h.assertFalse(after.pursuit().map(q.id()::equals).orElse(false), "the patrol chases a clean merchant");
        end(h, v);
        h.succeed();
    }

    /**
     * A merchant-flag ship whose owner has a bounty: below {@code hunt_bounty_minimum} (30 of 50) it is left alone,
     * at 60 it is hunted.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "bounty")
    public static void aBountiedOwnersShipIsHunted(GameTestHelper h) {
        basin(h);
        MinecraftServer server = h.getLevel().getServer();
        Quarry q = quarry(h, FlagKind.MERCHANT);
        BountyTarget owner = BountyTarget.player(q.owner(), "Captain Test");
        try {
            long minimum = NavyConfig.HUNT_BOUNTY_MINIMUM.get();
            LawService.placeStandingBounty(server, owner, (int) (minimum * 3 / 5));
            Voyage v = abstractPatrol(h, q);
            Voyage after = check(h, v);
            h.assertFalse(after.pursuit().map(q.id()::equals).orElse(false),
                    "hunted at a bounty of " + LawService.bountyTotal(server, q.owner()) + " (minimum " + minimum + ")");
            LawService.placeStandingBounty(server, owner, (int) (minimum * 3 / 5));
            after = check(h, after);
            h.assertTrue(after.pursuit().equals(Optional.of(q.id())),
                    "not hunted at a bounty of " + LawService.bountyTotal(server, q.owner()) + ": " + after.pursuit());
            end(h, v);
        } finally {
            LawService.withdrawBounties(server, q.owner());
        }
        h.succeed();
    }

    /**
     * Give-up: with {@code give_up_ticks} 40 and the quarry 100 blocks off (beyond {@code contact_distance} 64), the
     * chase ends after 40 ticks; the record turns back to the route it left (from where it is, to the nearest route
     * point) and the saved chase is gone.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = CONFIG_BATCH + "give_up")
    public static void aPatrolWithoutContactGivesUp(GameTestHelper h) {
        ConfigOverrides.during(h, NavyConfig.GIVE_UP_TICKS, 40);
        basin(h);
        Quarry q = quarry(h, FlagKind.JOLLY_ROGER);
        Voyage v = abstractPatrol(h, q);
        Voyage chasing = check(h, v);
        h.assertTrue(chasing.pursuit().isPresent(), "no chase");
        h.runAfterDelay(20, () -> h.assertTrue(check(h, chasing).pursuit().isPresent(), "gave up before give_up_ticks: " + seen(h, q)));
        h.runAfterDelay(45, () -> {
            Voyage after = check(h, chasing);
            h.assertTrue(after.pursuit().isEmpty(), "still chasing after give_up_ticks");
            h.assertTrue(NavyData.get(h.getLevel().getServer()).get(v.id()).isEmpty(), "the chase is still saved");
            Lane.Point last = after.waypoints().get(after.waypoints().size() - 1);
            h.assertTrue(last.equals(v.waypoints().get(v.waypoints().size() - 1)), "the route does not lead back to the patrol's: "
                    + after.waypoints());
            h.assertTrue(after.progress() == 0.0, "progress " + after.progress());
            end(h, v);
            h.succeed();
        });
    }

    /** {@code world_simulation.navy.enabled} off: a patrol sights nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "disabled")
    public static void disabledPatrolsDoNotHunt(GameTestHelper h) {
        ConfigOverrides.during(h, NavyConfig.ENABLED, false);
        basin(h);
        Quarry q = quarry(h, FlagKind.JOLLY_ROGER);
        Voyage v = abstractPatrol(h, q);
        Voyage after = check(h, v);
        h.assertTrue(after.pursuit().isEmpty(), "a disabled patrol chases " + after.pursuit());
        h.assertTrue(NavyModule.PATROLS.spawnChance(h.getLevel().getServer(), 20) == 0.0, "patrols still set out");
        end(h, v);
        h.succeed();
    }

    // ------------------------------------------------------------------ materialised patrols

    /**
     * A materialised patrol with a loaded, crewed gun facing a player's Jolly Roger hull 24 blocks ahead: the first
     * check sets the gun crews on it (WS4a TARGET) and a looping course around it; the gun fires and hits.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "fires")
    public static void aMaterialisedPatrolFiresAtItsQuarry(GameTestHelper h) {
        Engagement e = engagement(h, FlagKind.JOLLY_ROGER);
        loadByHand(h, e);
        Hunting.updateMaterialised(h.getLevel().getServer(), e.voyage().id());
        h.assertTrue(voyage(h, e.voyage()).pursuit().equals(Optional.of(e.quarry().id())), "no chase: " + seen(h, e.quarry()) + ", patrol at "
                + com.richardsenger.piratesnships.worldsim.materialize.Materializer.centre(e.patrol().ship()));
        h.assertTrue(Gunnery.state(e.patrol().ship().id()).equals(GunneryState.target(e.quarry().id())),
                "gunnery " + Gunnery.state(e.patrol().ship().id()));
        var course = com.richardsenger.piratesnships.station.helm.HelmCourses.course(e.patrol().ship().id());
        h.assertTrue(course != null && course.loop() && course.waypoints().size() == Hunting.RING_POINTS, "course " + course);
        checkEvery(h, e, 20);
        h.succeedWhen(() -> {
            h.assertTrue(cannon(h, e).reloadUntil() != 0, "not fired yet");
            h.assertTrue(hits(e.quarry().id()) >= 1, "no hit on the quarry yet");
            cleanup(h, e);
        });
    }

    /**
     * Striking the colours: with short gun timers the patrol fires; the quarry strikes its Jolly Roger; within
     * {@code fire_interval_ticks} (plus a fuse already lit) the gun falls silent and stays so, the patrol shadows it, and
     * after {@code surrender_linger_ticks} (100 here) the chase ends and the patrol heads back to its route.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 700, batch = CONFIG_BATCH + "struck")
    public static void strikingTheColoursStopsTheFire(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.RELOAD_TICKS, 20);
        ConfigOverrides.during(h, CannonConfig.CREW_LOAD_TICKS, 20);
        ConfigOverrides.during(h, GunneryConfig.FIRE_INTERVAL_TICKS, 20);
        ConfigOverrides.during(h, GunneryConfig.NPC_BLOCK_DAMAGE_MULTIPLIER, 0.0);
        ConfigOverrides.during(h, NavyConfig.SURRENDER_LINGER_TICKS, 100);
        Engagement e = engagement(h, FlagKind.JOLLY_ROGER);
        if (!(h.getLevel().getBlockEntity(e.chest()) instanceof Container chest)) throw new GameTestAssertException("no chest");
        chest.setItem(0, new ItemStack(Items.GUNPOWDER, 8));
        chest.setItem(1, new ItemStack(CombatContent.CANNONBALL.get(), 8));
        AtomicInteger shots = new AtomicInteger();
        long[] lastReload = {0};
        h.onEachTick(() -> {
            long r = cannon(h, e).reloadUntil();
            if (r != lastReload[0]) {
                lastReload[0] = r;
                shots.incrementAndGet();
            }
        });
        checkEvery(h, e, 20);
        long[] struckAt = {-1};
        int[] shotsAtSilence = {-1};
        h.succeedWhen(() -> {
            long now = h.getLevel().getGameTime();
            if (struckAt[0] < 0) {
                h.assertTrue(shots.get() >= 1, "the patrol has not fired yet");
                if (!(h.getLevel().getBlockEntity(e.quarry().polePlot()) instanceof FlagpoleBlockEntity pole)) {
                    throw new GameTestAssertException("no flagpole at " + e.quarry().polePlot());
                }
                pole.commandSet(FlagKind.JOLLY_ROGER, true, null);
                ShipAllegiance.refresh(e.quarry().ship().ship());
                h.assertTrue(ShipAllegiance.of(e.quarry().ship().ship()).isStruck(), "the colours are not struck");
                Hunting.updateMaterialised(h.getLevel().getServer(), e.voyage().id());
                h.assertTrue(Gunnery.state(e.patrol().ship().id()).isOff(), "the guns are still on: " + Gunnery.state(e.patrol().ship().id()));
                Optional<NavyData.Pursuit> p = NavyData.get(h.getLevel().getServer()).get(e.voyage().id());
                h.assertTrue(p.isPresent() && p.get().surrenderedUntil() > now, "not shadowing: " + p);
                struckAt[0] = now;
                throw new GameTestAssertException("struck, waiting for the guns to fall silent");
            }
            long since = now - struckAt[0];
            int silenceAfter = GunneryConfig.FIRE_INTERVAL_TICKS.get() + 20; // a fuse lit just before the strike still fires
            if (since < silenceAfter) throw new GameTestAssertException("waiting " + since);
            if (shotsAtSilence[0] < 0) shotsAtSilence[0] = shots.get();
            h.assertTrue(shots.get() == shotsAtSilence[0], "fired after the colours were struck: " + shots.get() + " > " + shotsAtSilence[0]);
            if (since < NavyConfig.SURRENDER_LINGER_TICKS.get() + 25) {
                h.assertTrue(voyage(h, e.voyage()).pursuit().isPresent(), "left the quarry before the linger ended");
                throw new GameTestAssertException("shadowing " + since);
            }
            Voyage after = voyage(h, e.voyage());
            h.assertTrue(after.pursuit().isEmpty(), "still shadowing after the linger");
            h.assertTrue(Gunnery.state(e.patrol().ship().id()).isOff(), "guns on after the chase");
            h.assertTrue(com.richardsenger.piratesnships.station.helm.HelmCourses.course(e.patrol().ship().id()) == null,
                    "the chase course is still set");
            h.assertTrue(hits(e.quarry().id()) >= 1, "the shots before the strike missed");
            cleanup(h, e);
        });
    }

    // ------------------------------------------------------------------ world events

    /** A patrol's soldier killing a pirate reports {@code pirate_killed_by_navy}: Navy–Pirates tension rises. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "kill")
    public static void aPatrolKillOfAPirateIsAWorldEvent(GameTestHelper h) {
        basin(h);
        MinecraftServer server = h.getLevel().getServer();
        Quarry q = quarry(h, FlagKind.MERCHANT);
        Voyage v = abstractPatrol(h, q);
        h.assertTrue(FactionConfig.active(), "the faction state is off");
        NavySoldier soldier = h.spawn(MobContent.NAVY_SOLDIER.get(), new BlockPos(5, 5, 5));
        soldier.addTag(VoyageCrew.voyageTag(v.id()));
        soldier.addTag(VoyageCrew.FIGHTER_TAG);
        Pirate pirate = h.spawn(MobContent.PIRATE.get(), new BlockPos(8, 5, 5));
        double before = Factions.tension(server, Faction.NAVY, Faction.PIRATES);
        pirate.hurt(h.getLevel().damageSources().mobAttack(soldier), 1000f);
        h.assertTrue(!pirate.isAlive(), "the pirate survived");
        double after = Factions.tension(server, Faction.NAVY, Faction.PIRATES);
        h.assertTrue(after > before || after >= 1.0, "tension " + before + " -> " + after);
        soldier.discard();
        end(h, v);
        h.succeed();
    }
}

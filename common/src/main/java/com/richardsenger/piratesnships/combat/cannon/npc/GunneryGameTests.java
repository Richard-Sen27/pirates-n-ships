package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NPC gunnery (WS4a): a crew at a cannon of one hull fires at will at another hull 24 blocks away in a 40×40 basin.
 *
 * <p>Layout (relative): a dry dock, stone up to y 4 over x and z 1..38; the gunner's 5×5 plank hull at x 3..7, z 17..21 (deck y 8,
 * helm at (5, 9, 19)), its cannon's master at (6, 9, 18) facing east with the rear at (5, 9, 18), a chest at
 * (4, 9, 18); the target hull at x 27..31, same z, 24 blocks east, with a flagpole at (28, 9, 18). Every test runs in a
 * batch of its own, because the crews look for targets within 64 blocks and would see the ships of a parallel test.
 * Hits are counted through {@link CannonShipHits}; shots by the cannon's reload timer.
 */
public final class GunneryGameTests {

    private static final String BATCH = "pirates_n_ships_combat_gunnery_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_combat_gunnery_";
    private static final int SIZE = 40;
    private static final int GUNNER_X = 3, TARGET_X = 27, Z = 17;

    /** Hits per target ship, with the firing ship and shooter of each, filled by one listener for all tests. */
    private static final Map<UUID, List<CannonShipHits.ShipHit>> HITS = new ConcurrentHashMap<>();
    private static volatile boolean listening;

    private GunneryGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GunneryGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Setup(Fixture gunner, Fixture target, BlockPos cannon, BlockPos chest) {
        UUID targetId() {
            return target.ship().id();
        }
    }

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        CannonShipHits.register(hit -> HITS.computeIfAbsent(hit.hitShip(), id -> new CopyOnWriteArrayList<>()).add(hit));
    }

    private static int hits(UUID ship) {
        List<CannonShipHits.ShipHit> l = HITS.get(ship);
        return l == null ? 0 : l.size();
    }

    /**
     * A dry stone dock over the whole template, solid up to y 4, so both hulls rest on it (bottom y 5) and hold their
     * heading: a 5×5 hull afloat yaws freely under its own recoil and the hits, and a crew whose target drifts out of
     * line rightly holds fire. Open sky.
     */
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

    /** A closed 5×5×4 plank hull at x x0..x0+4, z Z..Z+4, bottom y {@code y0}; returns the helm on its deck. */
    private static BlockPos hull(GameTestHelper h, int x0, int y0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = Z; z <= Z + 4; z++) {
                for (int y = y0; y <= y0 + 3; y++) {
                    boolean shell = y == y0 || y == y0 + 3 || x == x0 || x == x0 + 4 || z == Z || z == Z + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, y0 + 4, Z + 2);
        h.setBlock(helm, AssemblyContent.HELM.get());
        return helm;
    }

    /**
     * Both hulls, assembled: the gunner with a cannon facing {@code facing} (master one block east and north of the
     * helm) and a chest, the target flying {@code flag} (struck when asked). A raised target rests on a stone pier
     * whose top is at y {@code targetBottom − 1}; otherwise ({@code targetBottom} 5) it rests on the dock.
     */
    private static Setup setup(GameTestHelper h, Direction facing, FlagKind flag, boolean struck, int targetBottom) {
        listen();
        basin(h);
        BlockPos helm = hull(h, GUNNER_X, 5);
        BlockPos master = helm.offset(1, 0, -1);
        BlockState state = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, facing);
        h.setBlock(master, state);
        h.setBlock(CannonRules.rearOf(master, facing), CannonContent.CANNON.get().rearState(state));
        h.setBlock(helm.offset(-1, 0, -1), Blocks.CHEST);

        if (targetBottom > 5) {
            for (int x = TARGET_X - 1; x <= TARGET_X + 5; x++) {
                for (int z = Z - 1; z <= Z + 5; z++) {
                    for (int y = 5; y < targetBottom; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos targetHelm = hull(h, TARGET_X, targetBottom);
        BlockPos pole = targetHelm.offset(-1, 0, -1);
        h.setBlock(pole, ShipDecor.FLAGPOLE.get());
        if (!(h.getBlockEntity(pole) instanceof FlagpoleBlockEntity be)) throw new GameTestAssertException("no flagpole at " + pole);
        be.commandSet(flag, struck, null);

        Fixture gunner = DryHullGameTests.assemble(h, helm);
        Fixture target = DryHullGameTests.assemble(h, targetHelm);
        BlockPos cannon = gunner.helmPlot().offset(1, 0, -1);
        BlockPos chest = gunner.helmPlot().offset(-1, 0, -1);
        h.assertTrue(CannonBlock.isMaster(h.getLevel().getBlockState(cannon)), "the cannon is not in the plot at " + cannon);
        h.assertTrue(ShipAllegiance.of(target.ship()).kind() == flag, "the target flies " + ShipAllegiance.of(target.ship()));
        return new Setup(gunner, target, cannon, chest);
    }

    private static Setup setup(GameTestHelper h, Direction facing, FlagKind flag, boolean struck) {
        return setup(h, facing, flag, struck, 5);
    }

    /** A crew member on the gunner's deck assigned to the cannon. */
    private static CrewMember gunner(GameTestHelper h, Setup s) {
        Vec3 deck = s.gunner().ship().toWorld(Vec3.atBottomCenterOf(s.gunner().helmPlot().offset(1, 0, 1)));
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), h.relativeVec(deck));
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.cannon());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        return c;
    }

    /** A mock captain on the gunner's deck with a whistle. */
    private static Player captain(GameTestHelper h, Setup s) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = s.gunner().ship().toWorld(Vec3.atBottomCenterOf(s.gunner().helmPlot().offset(1, 0, 1)));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    private static void loadByHand(GameTestHelper h, Setup s) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonService.load(level, s.cannon(), creative, new ItemStack(Items.GUNPOWDER)).outcome()
                == CannonService.Outcome.POWDER_IN, "powder was refused");
        h.assertTrue(CannonService.load(level, s.cannon(), creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the ball was refused");
    }

    private static void supply(GameTestHelper h, Setup s, int shots) {
        if (!(h.getLevel().getBlockEntity(s.chest()) instanceof Container c)) throw new GameTestAssertException("no chest");
        c.setItem(0, new ItemStack(Items.GUNPOWDER, shots));
        c.setItem(1, new ItemStack(CombatContent.CANNONBALL.get(), shots));
    }

    private static CannonBlockEntity cannon(GameTestHelper h, Setup s) {
        if (h.getLevel().getBlockEntity(s.cannon()) instanceof CannonBlockEntity be) return be;
        throw new GameTestAssertException("no cannon at " + s.cannon());
    }

    private static CannonLoad load(GameTestHelper h, Setup s) {
        return h.getLevel().getBlockState(s.cannon()).getValue(CannonBlock.LOAD);
    }

    private static String explain(GameTestHelper h, Setup s) {
        return Gunnery.explain(h.getLevel(), s.gunner().ship(), s.cannon(), s.target().ship());
    }

    private static void fireAtWill(GameTestHelper h, Setup s) {
        WhistleOrders.Result r = WhistleOrders.handle(captain(h, s), WhistleOrder.FIRE_AT_WILL.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "fire at will: " + r);
    }

    private static void cleanup(GameTestHelper h, Setup s, CrewMember... crew) {
        Gunnery.clear(s.gunner().ship());
        h.getEntities(CannonContent.CANNONBALL.get()).forEach(CannonballEntity::discard);
        for (CrewMember c : crew) c.discard();
        HITS.remove(s.targetId());
    }

    /** Over {@code ticks} ticks the gun fires nothing (its reload timer never starts and it stays loaded). */
    private static void neverFires(GameTestHelper h, Setup s, CrewMember crew, int ticks) {
        h.onEachTick(() -> {
            if (cannon(h, s).reloadUntil() != 0) throw new GameTestAssertException("the gun fired");
        });
        h.runAfterDelay(ticks, () -> {
            h.assertTrue(load(h, s) == CannonLoad.LOADED, "the gun is not loaded any more");
            h.assertTrue(hits(s.targetId()) == 0, "the target was hit");
            cleanup(h, s, crew);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ tests

    /**
     * "Fire at will" with a loaded, crewed gun facing a Jolly Roger hull 24 blocks ahead: the gun fires within 100
     * ticks, the ball hits the target ({@link CannonShipHits}, no shooter, fired from the gunner), carries the NPC block
     * damage factor, and the ship's gunnery is at will and engaged with the target.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = BATCH + "at_will")
    public static void atWillFiresAtAJollyRogerAndHitsIt(GameTestHelper h) {
        Setup s = setup(h, Direction.EAST, FlagKind.JOLLY_ROGER, false);
        CrewMember crew = gunner(h, s);
        loadByHand(h, s);
        long start = h.getLevel().getGameTime();
        fireAtWill(h, s);
        double[] factor = {-1};
        h.onEachTick(() -> {
            for (CannonballEntity ball : h.getEntities(CannonContent.CANNONBALL.get())) factor[0] = ball.blockDamageFactor();
        });
        h.succeedWhen(() -> {
            long fired = cannon(h, s).reloadUntil() - CannonConfig.RELOAD_TICKS.get();
            h.assertTrue(cannon(h, s).reloadUntil() != 0, "not fired yet");
            h.assertTrue(fired - start <= 100, "fired after " + (fired - start) + " ticks");
            h.assertTrue(Gunnery.state(s.gunner().ship().id()).equals(GunneryState.AT_WILL), "gunnery " + Gunnery.state(s.gunner().ship().id()));
            h.assertTrue(Gunnery.engaged(s.gunner().ship()).map(s.targetId()::equals).orElse(false), "not engaged with the target");
            h.assertTrue(hits(s.targetId()) >= 1, "no hit on the target yet: " + explain(h, s));
            CannonShipHits.ShipHit hit = HITS.get(s.targetId()).get(0);
            h.assertTrue(hit.shooter() == null, "a crew shot has no owner: " + hit.shooter());
            h.assertTrue(s.gunner().ship().id().equals(hit.firingShip()), "fired from " + hit.firingShip());
            h.assertTrue(Math.abs(factor[0] - GunneryConfig.NPC_BLOCK_DAMAGE_MULTIPLIER.get()) < 1e-9,
                    "the ball's block damage factor is " + factor[0]);
            cleanup(h, s, crew);
        });
    }

    /** A gun facing north (bow) with the Jolly Roger hull abeam to the east: outside the arc, no shot. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = BATCH + "arc")
    public static void aTargetOutsideTheArcGetsNoShot(GameTestHelper h) {
        Setup s = setup(h, Direction.NORTH, FlagKind.JOLLY_ROGER, false);
        CrewMember crew = gunner(h, s);
        loadByHand(h, s);
        fireAtWill(h, s);
        neverFires(h, s, crew, 120);
    }

    /** The target has struck its Jolly Roger: no shot. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = BATCH + "struck")
    public static void aStruckTargetGetsNoShot(GameTestHelper h) {
        Setup s = setup(h, Direction.EAST, FlagKind.JOLLY_ROGER, true);
        CrewMember crew = gunner(h, s);
        loadByHand(h, s);
        Gunnery.set(h.getLevel(), s.gunner().ship(), GunneryState.AT_WILL);
        neverFires(h, s, crew, 120);
    }

    /** A merchant-flag target is no enemy of a merchant (unflagged) gunner: no shot; a chosen target is shot at. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "neutral")
    public static void aNeutralTargetIsShotOnlyWhenChosen(GameTestHelper h) {
        Setup s = setup(h, Direction.EAST, FlagKind.MERCHANT, false);
        CrewMember crew = gunner(h, s);
        loadByHand(h, s);
        Gunnery.set(h.getLevel(), s.gunner().ship(), GunneryState.AT_WILL);
        h.runAfterDelay(80, () -> {
            h.assertTrue(cannon(h, s).reloadUntil() == 0, "fired at a neutral ship at will");
            Gunnery.set(h.getLevel(), s.gunner().ship(), GunneryState.target(s.targetId()));
            h.succeedWhen(() -> {
                h.assertTrue(hits(s.targetId()) >= 1, "the chosen target was not hit: " + explain(h, s));
                cleanup(h, s, crew);
            });
        });
    }

    /** {@code cannons.npc.enabled} off: "Fire at will" is refused at the gun and nothing fires. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = CONFIG_BATCH + "disabled")
    public static void disabledGunneryFiresNothing(GameTestHelper h) {
        ConfigOverrides.during(h, GunneryConfig.ENABLED, false);
        Setup s = setup(h, Direction.EAST, FlagKind.JOLLY_ROGER, false);
        CrewMember crew = gunner(h, s);
        loadByHand(h, s);
        WhistleOrders.Result r = WhistleOrders.handle(captain(h, s), WhistleOrder.FIRE_AT_WILL.id());
        h.assertTrue(r.crew() == 0, "a crew took the order: " + r);
        Gunnery.set(h.getLevel(), s.gunner().ship(), GunneryState.AT_WILL); // even when set by AI
        neverFires(h, s, crew, 120);
    }

    /**
     * A target raised on a pier (its bounds centre about 6 blocks above the gun) makes the crew step the barrel up
     * from the level step, one step per aim interval.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "raised")
    public static void aRaisedTargetMakesTheGunStepItsElevationUp(GameTestHelper h) {
        Setup s = setup(h, Direction.EAST, FlagKind.JOLLY_ROGER, false, 13);
        CrewMember crew = gunner(h, s);
        int level = cannon(h, s).elevationStep();
        h.assertTrue(level == CannonConfig.levelStep(), "a new cannon starts level: " + level);
        Gunnery.set(h.getLevel(), s.gunner().ship(), GunneryState.AT_WILL);
        int interval = GunneryConfig.AIM_INTERVAL_TICKS.get();
        h.runAfterDelay(interval * 2L + 5, () -> {
            int now = cannon(h, s).elevationStep();
            h.assertTrue(now >= level + 2, "after two aim intervals the step is " + now + " (level " + level + ")");
            cleanup(h, s, crew);
            h.succeed();
        });
    }

    /**
     * Hit rate: with a supply of five shots, auto reload and short timers, the crew fires all five at will and at least
     * four of them hit the Jolly Roger hull. The rate is logged.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 900, batch = CONFIG_BATCH + "hit_rate")
    public static void atWillHitsMostShots(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.RELOAD_TICKS, 20);
        ConfigOverrides.during(h, CannonConfig.CREW_LOAD_TICKS, 20);
        ConfigOverrides.during(h, GunneryConfig.FIRE_INTERVAL_TICKS, 20);
        Setup s = setup(h, Direction.EAST, FlagKind.JOLLY_ROGER, false);
        supply(h, s, 5);
        CrewMember crew = gunner(h, s);
        fireAtWill(h, s);
        long[] last = {0};
        int[] shots = {0};
        h.onEachTick(() -> {
            long r = cannon(h, s).reloadUntil();
            if (r != last[0]) {
                last[0] = r;
                shots[0]++;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(shots[0] >= 5, "shots so far: " + shots[0] + ", hits " + hits(s.targetId())
                    + ", crew alive " + crew.isAlive() + " at " + crew.assignment() + ", " + explain(h, s));
            h.assertTrue(h.getEntities(CannonContent.CANNONBALL.get()).isEmpty(), "a ball is still flying");
            int hit = hits(s.targetId());
            Constants.LOG.info("WS4a gunnery hit rate: {} of {} shots at 24 blocks", hit, shots[0]);
            h.assertTrue(hit >= 4, "only " + hit + " of " + shots[0] + " shots hit");
            cleanup(h, s, crew);
        });
    }
}

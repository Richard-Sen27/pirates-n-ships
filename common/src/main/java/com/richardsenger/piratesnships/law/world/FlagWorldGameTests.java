package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.FlagConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.FalseColorsDetection;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.lookout.LookoutContent;
import com.richardsenger.piratesnships.station.lookout.Lookouts;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Flags acting in the world (FL2, docs/design.md §4.7 "In the world"). Fixtures: the closed 5×4×5 plank hull of
 * {@link DryHullGameTests} afloat, with a flagpole on its deck. The single-ship tests use a basin over x 0..13 and solid
 * land over x 14..23 (ground at y=8) where a navy soldier or pirate stands, about 12 blocks from the deck; the two-ship
 * tests use the full basin with ship A at x 2..6 and ship B at x 16..20. Players are mock players added to the level
 * (so Sable carries them and mobs see them), kept on the deck every tick, with resistance against musket balls.
 * <p>
 * Every test has a batch of its own: the navy looks at ships within {@code law.flags.observe_range} (48 blocks), so
 * tests of one batch would see each other's ships.
 */
public final class FlagWorldGameTests {

    private static final String BATCH = "pirates_n_ships_flags_world_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_law_flags_";
    /** The flagpole on the deck, relative to the helm (helm at deck + 1). */
    private static final BlockPos POLE_FROM_HELM = new BlockPos(-1, 0, -1);

    private static volatile Field testInfoField;

    private FlagWorldGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FlagWorldGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    record Ship(Fixture f, BlockPos plotPole) {
        ShipBody body() {
            return f.ship();
        }

        UUID id() {
            return f.ship().id();
        }

        FlagpoleBlockEntity pole(GameTestHelper h) {
            if (h.getLevel().getBlockEntity(plotPole) instanceof FlagpoleBlockEntity be) return be;
            throw new GameTestAssertException("no flagpole at plot " + plotPole);
        }
    }

    /** Basin over x 0..13, land over x 14..23; one hull at x 4..8, z 9..13 with a pole flying {@code flag} (NONE: empty). */
    static Ship shipBesideLand(GameTestHelper h, FlagKind flag, boolean struck) {
        return shipBesideLand(h, flag, struck, helm -> { });
    }

    /** As {@link #shipBesideLand(GameTestHelper, FlagKind, boolean)}; {@code beforeAssembly} gets the helm (relative) to fit the hull out. */
    static Ship shipBesideLand(GameTestHelper h, FlagKind flag, boolean struck, java.util.function.Consumer<BlockPos> beforeAssembly) {
        DryHullGameTests.basin(h, 0, 13, true);
        for (int x = 14; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        SailingGameTestsShips.openSky(h, 24);
        return ship(h, 4, flag, struck, beforeAssembly);
    }

    /** A hull at x a..a+4 with a flagpole on its deck flying {@code flag}, assembled. */
    private static Ship ship(GameTestHelper h, int a, FlagKind flag, boolean struck) {
        return ship(h, a, flag, struck, helm -> { });
    }

    private static Ship ship(GameTestHelper h, int a, FlagKind flag, boolean struck, java.util.function.Consumer<BlockPos> beforeAssembly) {
        BlockPos helm = DryHullGameTests.hull(h, a, false);
        BlockPos pole = helm.offset(POLE_FROM_HELM);
        h.setBlock(pole, ShipDecor.FLAGPOLE.get());
        if (flag != FlagKind.NONE) {
            if (!(h.getBlockEntity(pole) instanceof FlagpoleBlockEntity be)) throw new GameTestAssertException("no flagpole at " + pole);
            be.commandSet(flag, struck, null);
        }
        beforeAssembly.accept(helm);
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos plotPole = f.helmPlot().offset(POLE_FROM_HELM);
        h.assertTrue(h.getLevel().getBlockEntity(plotPole) instanceof FlagpoleBlockEntity, "the flagpole was not assembled at " + plotPole);
        return new Ship(f, plotPole);
    }

    static void setOwner(GameTestHelper h, Ship ship, UUID owner) {
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        ShipData d = registry.find(ship.id()).orElseThrow();
        registry.put(new ShipData(d.id(), d.name(), Optional.of(owner), d.crew(), d.flag(), d.dimension(), d.blownCoverUntil()));
    }

    private static ShipData data(GameTestHelper h, Ship ship) {
        return ShipRegistry.get(h.getLevel().getServer()).find(ship.id())
                .orElseThrow(() -> new GameTestAssertException("no ship record for " + ship.id()));
    }

    /**
     * A survival mock player added to the level at a test-relative position, discarded when the test ends (vanilla's
     * mock player is in no level, so mobs could not see it and Sable would not carry it). Resistant to damage.
     */
    private static Player playerInLevel(GameTestHelper h, Vec3 relative) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 4, false, false));
        h.getLevel().addFreshEntity(player);
        testInfo(h).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
    }

    /** A player in the level standing on the ship's deck (one block east of the helm), put back there every tick. */
    static Player playerAboard(GameTestHelper h, Ship ship) {
        Player player = playerInLevel(h, new Vec3(1.5, 12, 1.5));
        Runnable put = () -> {
            if (!ship.body().isRemoved()) {
                Vec3 at = ship.body().toWorld(Vec3.atBottomCenterOf(ship.f().helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
        return player;
    }

    /** A navy soldier or pirate on the land, about 12 blocks east of the deck, keeping to its post. */
    static <T extends SeafarerMob> T onLand(GameTestHelper h, EntityType<T> type) {
        T mob = h.spawn(type, new BlockPos(19, 9, 11));
        mob.setYRot(90f);
        mob.setYHeadRot(90f);
        mob.yBodyRot = 90f;
        mob.setStationary(true);
        return mob;
    }

    static long count(Player player, CrimeType type) {
        return LawService.record(player).recent().stream().filter(o -> o.type() == type).count();
    }

    /** The plot block of {@code target}'s west wall at deck height, where {@link #fireAt} aims. */
    private static BlockPos westWall(Ship target) {
        return target.f().helmPlot().offset(-2, -1, 0);
    }

    /** Fires a ball at the west wall of {@code target} (deck height) from 2 blocks west of it, as if from {@code firingShip}. */
    private static CannonballEntity fireAt(ServerLevel level, Ship target, @org.jetbrains.annotations.Nullable Player owner,
                                           @org.jetbrains.annotations.Nullable UUID firingShip) {
        BlockPos wall = westWall(target);
        Vec3 to = target.body().toWorld(Vec3.atCenterOf(wall));
        Vec3 from = to.add(-2.0, 0, 0);
        Vec3 v = to.subtract(from).normalize().scale(CannonConfig.MUZZLE_VELOCITY.get());
        CannonballEntity ball = new CannonballEntity(level, from, v, CannonConfig.entityDamage(), 40);
        ball.setOwner(owner);
        ball.setFiringShip(firingShip);
        level.addFreshEntity(ball);
        return ball;
    }

    private static int delay() {
        return FlagConfig.HOIST_DELAY_TICKS.get();
    }

    // ------------------------------------------------------------------ allegiance

    /**
     * Hoisting the Jolly Roger at a pole on the ship (through the pole's own delay) sets the ship's flag, striking it
     * makes the ship surrendered, and breaking the pole leaves it flying nothing.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = BATCH + "allegiance")
    public static void hoistingAndStrikingOnAShipSetItsAllegiance(GameTestHelper h) {
        Ship ship = shipBesideLand(h, FlagKind.NONE, false);
        h.assertValueEqual(data(h, ship).flag(), FlagReading.NO_FLAG, "flag of a ship with an empty pole");
        Player sailor = h.makeMockPlayer(GameType.SURVIVAL);
        int wait = delay() + 5;
        h.startSequence()
                .thenExecute(() -> ship.pole(h).interact(sailor,
                        new FlagpoleMachine.Input.Hoist(FlagKind.JOLLY_ROGER, new ItemStack(Flags.JOLLY_ROGER_FLAG.get()))))
                .thenExecuteAfter(wait, () -> {
                    h.assertValueEqual(data(h, ship).flag(), FlagReading.flying(FlagKind.JOLLY_ROGER), "flag after hoisting");
                    h.assertValueEqual(ShipAllegiance.of(ship.body()), FlagReading.flying(FlagKind.JOLLY_ROGER), "cached allegiance");
                    h.assertValueEqual(FlagCrimes.stanceOf(data(h, ship), LawService.now(h.getLevel().getServer())),
                            ShipStance.JOLLY_ROGER, "stance after hoisting");
                    ship.pole(h).interact(sailor, new FlagpoleMachine.Input.Toggle());
                })
                .thenExecuteAfter(wait, () -> {
                    h.assertValueEqual(data(h, ship).flag(), FlagReading.struck(FlagKind.JOLLY_ROGER), "flag after striking");
                    h.assertValueEqual(FlagCrimes.stanceOf(data(h, ship), LawService.now(h.getLevel().getServer())),
                            ShipStance.SURRENDERED, "stance after striking");
                    h.getLevel().destroyBlock(ship.plotPole(), false);
                })
                .thenExecuteAfter(2, () -> h.assertValueEqual(data(h, ship).flag(), FlagReading.NO_FLAG, "flag after the pole broke"))
                .thenSucceed();
    }

    /** A flag hoisted before assembly flies from the ship at once. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = BATCH + "assembly")
    public static void aFlagHoistedBeforeAssemblyFliesFromTheShip(GameTestHelper h) {
        Ship ship = shipBesideLand(h, FlagKind.NAVY, false);
        h.assertValueEqual(data(h, ship).flag(), FlagReading.flying(FlagKind.NAVY), "flag after assembly");
        h.succeed();
    }

    // ------------------------------------------------------------------ navy and pirates

    /**
     * A navy soldier targets a player aboard a ship under the Jolly Roger, and the owner is charged with
     * {@code seen_under_jolly_roger} once although the navy keeps looking (the crime's repeat window).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = BATCH + "navy_jolly_roger")
    public static void navyTargetsAJollyRogerCrewAndChargesTheOwnerOnce(GameTestHelper h) {
        Ship ship = shipBesideLand(h, FlagKind.JOLLY_ROGER, false);
        Player player = playerAboard(h, ship);
        setOwner(h, ship, player.getUUID());
        SeafarerMob soldier = onLand(h, MobContent.NAVY_SOLDIER.get());
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        long[] seenAt = {-1};
        h.onEachTick(() -> {
            if (seenAt[0] < 0 && soldier.getTarget() == player && count(player, CrimeType.SEEN_UNDER_JOLLY_ROGER) > 0) {
                seenAt[0] = h.getTick();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(seenAt[0] >= 0, "not yet targeted and charged: target " + soldier.getTarget()
                    + ", stance " + FlagCrimes.stanceOf(player) + ", seen " + count(player, CrimeType.SEEN_UNDER_JOLLY_ROGER));
            h.assertTrue(h.getTick() >= seenAt[0] + 3L * interval, "waiting for three more looks");
            h.assertValueEqual(count(player, CrimeType.SEEN_UNDER_JOLLY_ROGER), 1L, "seen_under_jolly_roger crimes");
        });
    }

    /** A pirate leaves a player aboard a Jolly Roger ship alone, and goes for them once the ship flies a merchant flag. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = BATCH + "pirate")
    public static void piratesLeaveAJollyRogerCrewAlone(GameTestHelper h) {
        Ship ship = shipBesideLand(h, FlagKind.JOLLY_ROGER, false);
        Player player = playerAboard(h, ship);
        SeafarerMob pirate = onLand(h, MobContent.PIRATE.get());
        h.startSequence()
                .thenExecuteAfter(80, () -> {
                    h.assertTrue(FlagCrimes.stanceOf(player) == ShipStance.JOLLY_ROGER, "the player is not aboard the Jolly Roger ship: "
                            + FlagCrimes.stanceOf(player));
                    h.assertTrue(pirate.getTarget() == null, "the pirate targets a player under the Jolly Roger");
                    ship.pole(h).commandSet(FlagKind.MERCHANT, false, null);
                })
                .thenWaitUntil(() -> h.assertTrue(pirate.getTarget() == player, "the pirate ignores a merchant ship's crew"))
                .thenSucceed();
    }

    /** A wanted player aboard a ship that struck its colours is not targeted by the navy; raising them again is. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = BATCH + "struck_navy")
    public static void navyLeavesASurrenderedCrewAlone(GameTestHelper h) {
        Ship ship = shipBesideLand(h, FlagKind.JOLLY_ROGER, true);
        Player player = playerAboard(h, ship);
        LawService.setScore(player, 100);
        SeafarerMob soldier = onLand(h, MobContent.NAVY_SOLDIER.get());
        h.startSequence()
                .thenExecuteAfter(80, () -> {
                    h.assertTrue(FlagCrimes.stanceOf(player) == ShipStance.SURRENDERED, "stance " + FlagCrimes.stanceOf(player));
                    h.assertTrue(soldier.getTarget() == null, "the navy targets a wanted player on a surrendered ship");
                    ship.pole(h).commandStrike(false);
                })
                .thenWaitUntil(() -> h.assertTrue(soldier.getTarget() == player, "the navy ignores the crew after the colours went up"))
                .thenSucceed();
    }

    /**
     * Interim false colours: a navy flag on a ship whose owner is a suspect is seen through (detection strength forced
     * high) within a few looks: {@code caught_false_colors} is recorded, the cover is blown and the navy now counts the
     * crew as unmasked.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = CONFIG_BATCH + "false_colours")
    public static void falseColoursAreSeenThroughWhenTheOwnerIsWanted(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.FALSE_FLAG_DETECTION_STRENGTH, 100.0);
        ConfigOverrides.during(h, LawConfig.OBSERVE_INTERVAL_TICKS, 10);
        Ship ship = shipBesideLand(h, FlagKind.NAVY, false);
        Player player = playerAboard(h, ship);
        setOwner(h, ship, player.getUUID());
        LawService.setScore(player, 20); // suspect, not wanted: the navy has no other reason to attack
        h.assertTrue(LawService.fliesFalseColours(player, FlagKind.NAVY), "a suspect's navy flag is not false colours");
        SeafarerMob soldier = onLand(h, MobContent.NAVY_SOLDIER.get());
        h.succeedWhen(() -> {
            h.assertTrue(count(player, CrimeType.CAUGHT_FALSE_COLORS) == 1, "not caught yet");
            long now = LawService.now(h.getLevel().getServer());
            ShipData d = data(h, ship);
            h.assertTrue(d.coverBlown(now) && d.blownCoverUntil() <= now + LawConfig.BLOWN_COVER_TICKS.get(),
                    "the cover is not blown: until " + d.blownCoverUntil() + ", now " + now);
            h.assertValueEqual(FlagCrimes.stanceOf(player), ShipStance.UNMASKED, "stance of the crew");
            h.assertTrue(soldier.getTarget() == player, "the navy does not go for the unmasked crew");
        });
    }

    // ------------------------------------------------------------------ ship hits

    private record TwoShips(Ship a, Ship b) {
    }

    private static TwoShips twoShips(GameTestHelper h, FlagKind flagB, boolean struckB) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        return new TwoShips(ship(h, 2, FlagKind.MERCHANT, false), ship(h, 16, flagB, struckB));
    }

    /** A player's ball from ship A hitting ship B, which struck its colours, is {@code attack_struck_colors}. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "hit_struck")
    public static void firingOnAStruckShipIsACrimeOfTheShooter(GameTestHelper h) {
        TwoShips s = twoShips(h, FlagKind.JOLLY_ROGER, true);
        Player shooter = h.makeMockPlayer(GameType.SURVIVAL);
        h.runAfterDelay(20, () -> fireAt(h.getLevel(), s.b(), shooter, s.a().id()));
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertValueEqual(count(shooter, CrimeType.ATTACK_STRUCK_COLORS), 1L, "attack_struck_colors crimes");
            h.assertValueEqual(count(shooter, CrimeType.ATTACK_NEUTRAL_SHIP), 0L, "attack_neutral_ship crimes");
        }));
    }

    /** A crew's ball (no shooter) from ship A hitting ship B under a merchant flag charges A's owner with {@code attack_neutral_ship}. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "hit_neutral")
    public static void crewFiringOnAMerchantShipChargesTheOwner(GameTestHelper h) {
        TwoShips s = twoShips(h, FlagKind.MERCHANT, false);
        Player owner = playerInLevel(h, new Vec3(0.5, 9, 0.5));
        setOwner(h, s.a(), owner.getUUID());
        h.runAfterDelay(20, () -> fireAt(h.getLevel(), s.b(), null, s.a().id()));
        h.runAfterDelay(21, () -> h.succeedWhen(() ->
                h.assertValueEqual(count(owner, CrimeType.ATTACK_NEUTRAL_SHIP), 1L, "attack_neutral_ship crimes of A's owner")));
    }

    /** Hitting a ship of one's own (same owner) is no crime, even when it flies a merchant flag. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "hit_own")
    public static void firingOnOnesOwnShipIsNoCrime(GameTestHelper h) {
        TwoShips s = twoShips(h, FlagKind.MERCHANT, false);
        Player shooter = h.makeMockPlayer(GameType.SURVIVAL);
        setOwner(h, s.b(), shooter.getUUID());
        CannonballEntity[] ball = new CannonballEntity[1];
        h.runAfterDelay(20, () -> ball[0] = fireAt(h.getLevel(), s.b(), shooter, s.a().id()));
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(ball[0].isRemoved(), "the ball is still flying");
            h.assertTrue(h.getLevel().getBlockState(westWall(s.b())).isAir(), "the ball did not hit ship B's wall");
            h.assertValueEqual(LawService.record(shooter).totalCrimes(), 0, "crimes for hitting one's own ship");
        }));
    }

    // ------------------------------------------------------------------ disabled

    /**
     * With {@code law.flags.enabled} off, the navy ignores a Jolly Roger crew and charges nobody, and a ball hitting a
     * merchant ship is no crime.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = CONFIG_BATCH + "disabled")
    public static void disabledFlagsReactToNothingAndRecordNothing(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.FLAGS_ENABLED, false);
        Ship ship = shipBesideLand(h, FlagKind.JOLLY_ROGER, false);
        Player player = playerAboard(h, ship);
        setOwner(h, ship, player.getUUID());
        SeafarerMob soldier = onLand(h, MobContent.NAVY_SOLDIER.get());
        Player shooter = h.makeMockPlayer(GameType.SURVIVAL);
        CannonballEntity[] ball = new CannonballEntity[1];
        h.startSequence()
                .thenExecuteAfter(100, () -> {
                    h.assertTrue(soldier.getTarget() == null, "the navy targets a Jolly Roger crew with flags disabled");
                    h.assertValueEqual(LawService.record(player).totalCrimes(), 0, "crimes of the Jolly Roger ship's owner");
                    h.assertValueEqual(FlagCrimes.stanceOf(player), ShipStance.NONE, "stance with flags disabled");
                    ship.pole(h).commandSet(FlagKind.MERCHANT, false, null);
                    ball[0] = fireAt(h.getLevel(), ship, shooter, null);
                })
                .thenWaitUntil(() -> h.assertTrue(ball[0].isRemoved(), "the ball is still flying"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(westWall(ship)).isAir(), "the ball did not hit the ship's wall");
                    h.assertValueEqual(LawService.record(shooter).totalCrimes(), 0, "crimes for hitting a merchant ship");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ LAW4: the crow's nest

    /** The crow's nest on the observer ship, relative to the helm: on a fence one block starboard-aft of it. */
    private static final BlockPos NEST_FROM_HELM = new BlockPos(1, 1, 1);

    /**
     * Two ships in the full basin: the navy observer ship A (x 2..6, navy flag, no owner, a fence mast with an
     * unmanned crow's nest) with a navy soldier kept on its deck, and ship B (x 16..20) under false navy colours with
     * its suspect owner aboard. The soldier's eyes are about 12 blocks from B's hull.
     */
    private record NestScene(Ship observer, Ship target, Player owner, SeafarerMob soldier) {
        BlockPos nest() {
            return observer.f().helmPlot().offset(NEST_FROM_HELM);
        }

        AABB hull() {
            return target.body().worldBounds();
        }
    }

    private static NestScene nestScene(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        Ship a = ship(h, 2, FlagKind.NAVY, false, helm -> {
            h.setBlock(helm.offset(1, 0, 1), Blocks.OAK_FENCE);
            h.setBlock(helm.offset(NEST_FROM_HELM), LookoutContent.CROWS_NEST.get());
        });
        h.assertTrue(h.getLevel().getBlockState(a.f().helmPlot().offset(NEST_FROM_HELM)).is(LookoutContent.CROWS_NEST.get()),
                "the crow's nest was not assembled");
        Ship b = ship(h, 16, FlagKind.NAVY, false);
        Player player = playerAboard(h, b);
        setOwner(h, b, player.getUUID());
        LawService.setScore(player, 20); // suspect: false colours, but no other reason for the navy to attack
        h.assertTrue(LawService.fliesFalseColours(player, FlagKind.NAVY), "a suspect's navy flag is not false colours");
        SeafarerMob soldier = h.spawn(MobContent.NAVY_SOLDIER.get(), new BlockPos(3, 9, 12));
        soldier.setStationary(true);
        Runnable put = () -> {
            if (!a.body().isRemoved()) {
                Vec3 at = a.body().toWorld(Vec3.atBottomCenterOf(a.f().helmPlot().offset(-1, 0, 1)));
                soldier.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
        return new NestScene(a, b, player, soldier);
    }

    /** A crew member without AI on the observer's deck, seated at the crow's nest. */
    private static CrewMember lookout(GameTestHelper h, NestScene s) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new GameTestAssertException("no crew member");
        Vec3 p = s.observer().body().toWorld(Vec3.atBottomCenterOf(s.observer().f().helmPlot().south()));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.nest());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign the lookout: " + r);
        testInfo(h).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { c.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { c.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { c.discard(); }
        });
        return c;
    }

    private static boolean aboardObserver(NestScene s) {
        ShipBody on = ShipEntities.standingOrRiding(s.soldier());
        return on != null && on.id().equals(s.observer().id());
    }

    /** Narrow sight for the range tests: 6 blocks to notice a flag at all and 6 to tell it, x4 from a manned nest. */
    private static void narrowSight(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.FALSE_FLAG_DETECTION_STRENGTH, 100.0);
        ConfigOverrides.during(h, LawConfig.OBSERVE_INTERVAL_TICKS, 10);
        ConfigOverrides.during(h, LawConfig.OBSERVE_RANGE, 6.0);
        ConfigOverrides.during(h, LawConfig.DETECTION_CLOSE_RANGE, 0.0);
        ConfigOverrides.during(h, LawConfig.DETECTION_MAX_RANGE, 6.0);
        ConfigOverrides.during(h, LawConfig.CROWS_NEST_RANGE_FACTOR, 4.0);
    }

    /**
     * LAW4: a navy soldier aboard a ship 12 blocks off a false-flagged ship is beyond its 6 blocks of sight and never
     * catches it; once a crew lookout mans the ship's crow's nest the sight reaches 24 blocks and the false colours are
     * seen through.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = CONFIG_BATCH + "crows_nest_range")
    public static void aMannedCrowsNestSeesFalseColoursFarther(GameTestHelper h) {
        narrowSight(h);
        NestScene s = nestScene(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(aboardObserver(s), "the soldier is not aboard the observer ship yet"))
                .thenExecuteAfter(60, () -> {
                    double d = FlagCrimes.distance(s.soldier().getEyePosition(), s.hull());
                    h.assertTrue(d > 6 && d < 24, "the soldier's eyes are " + d + " blocks from the target");
                    h.assertFalse(FlagCrimes.hasMannedNest(h.getLevel(), s.soldier(), new HashMap<>()), "an empty nest counts as manned");
                    h.assertValueEqual(count(s.owner(), CrimeType.CAUGHT_FALSE_COLORS), 0L,
                            "caught by an observer without a lookout, " + d + " blocks off");
                    lookout(h, s);
                })
                .thenWaitUntil(() -> h.assertTrue(count(s.owner(), CrimeType.CAUGHT_FALSE_COLORS) == 1,
                        "not caught with a manned nest (nest manned: "
                                + FlagCrimes.hasMannedNest(h.getLevel(), s.soldier(), new HashMap<>()) + ")"))
                .thenExecute(() -> h.assertTrue(data(h, s.target()).coverBlown(LawService.now(h.getLevel().getServer())),
                        "the cover is not blown"))
                .thenSucceed();
    }

    /**
     * LAW4: within the detection range the same soldier's chance per check grows by exactly crows_nest_rate_factor's
     * rate once the nest is manned, and a navy soldier ashore (on the basin wall) keeps the plain chance. The chances
     * are computed directly; {@code law.flags.enabled} off keeps the navy's own rolls out of the way.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = CONFIG_BATCH + "crows_nest_rate")
    public static void aMannedCrowsNestSeesThroughFalseColoursFaster(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.FLAGS_ENABLED, false);
        ConfigOverrides.during(h, LawConfig.CROWS_NEST_RATE_FACTOR, 3.0);
        NestScene s = nestScene(h);
        SeafarerMob ashore = h.spawn(MobContent.NAVY_SOLDIER.get(), new BlockPos(0, 9, 12));
        ashore.setStationary(true);
        double[] before = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(aboardObserver(s), "the soldier is not aboard the observer ship yet"))
                .thenExecute(() -> {
                    FalseColorsDetection.Params p = LawConfig.detectionParams();
                    double score = LawService.score(s.owner());
                    before[0] = FlagCrimes.detectionChance(h.getLevel(), s.soldier(), s.hull(), p, score, 40, new HashMap<>());
                    before[1] = FlagCrimes.detectionChance(h.getLevel(), ashore, s.hull(), p, score, 40, new HashMap<>());
                    h.assertTrue(before[0] > 0 && before[0] < 1, "chance without a lookout " + before[0]);
                    lookout(h, s);
                })
                .thenExecuteAfter(5, () -> {
                    FalseColorsDetection.Params p = LawConfig.detectionParams();
                    double score = LawService.score(s.owner());
                    Map<UUID, Boolean> nests = new HashMap<>();
                    h.assertTrue(FlagCrimes.hasMannedNest(h.getLevel(), s.soldier(), nests), "the manned nest does not count");
                    double with = FlagCrimes.detectionChance(h.getLevel(), s.soldier(), s.hull(), p, score, 40, nests);
                    double d = FlagCrimes.distance(s.soldier().getEyePosition(), s.hull());
                    double expected = FalseColorsDetection.chance(p, true, d, true, score, 40);
                    h.assertTrue(Math.abs(with - expected) < 1e-12, "chance " + with + ", the nest's formula " + expected);
                    double rateBefore = -Math.log1p(-before[0]);
                    double rateWith = -Math.log1p(-with);
                    h.assertTrue(rateWith > 3.0 * rateBefore * 0.999, "rate " + rateWith + " not 3x (or more) " + rateBefore);
                    h.assertFalse(FlagCrimes.hasMannedNest(h.getLevel(), ashore, new HashMap<>()), "a soldier ashore has a nest");
                    double ashoreNow = FlagCrimes.detectionChance(h.getLevel(), ashore, s.hull(), p, score, 40, new HashMap<>());
                    // the target drifts a little in the water: compare with the plain formula at today's distance
                    double plain = FalseColorsDetection.chance(p, true, FlagCrimes.distance(ashore.getEyePosition(), s.hull()),
                            false, score, 40);
                    h.assertTrue(before[1] > 0 && Math.abs(ashoreNow - plain) < 1e-12,
                            "the soldier ashore: " + ashoreNow + ", the plain formula " + plain);
                    h.assertValueEqual(count(s.owner(), CrimeType.CAUGHT_FALSE_COLORS), 0L, "the navy rolled during the test");
                })
                .thenSucceed();
    }

    /** {@code law.flags_brig.crows_nest_observers} off: a manned nest gives the soldier no farther sight. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = CONFIG_BATCH + "crows_nest_off")
    public static void switchedOffCrowsNestObserversSeeNoFarther(GameTestHelper h) {
        narrowSight(h);
        ConfigOverrides.during(h, LawConfig.CROWS_NEST_OBSERVERS, false);
        NestScene s = nestScene(h);
        lookout(h, s);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(aboardObserver(s), "the soldier is not aboard the observer ship yet"))
                .thenExecuteAfter(5, () -> h.assertTrue(Lookouts.isManned(h.getLevel(), s.observer().body()), "the nest is not manned"))
                .thenExecuteAfter(100, () -> {
                    h.assertFalse(FlagCrimes.hasMannedNest(h.getLevel(), s.soldier(), new HashMap<>()), "the nest counts while switched off");
                    h.assertValueEqual(count(s.owner(), CrimeType.CAUGHT_FALSE_COLORS), 0L, "caught with the nest switched off");
                })
                .thenSucceed();
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1 (same accessor as {@code ConfigOverrides}). */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        testInfoField = f = candidate;
                        break;
                    }
                }
            }
            if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

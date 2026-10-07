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

    private record Ship(Fixture f, BlockPos plotPole) {
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
    private static Ship shipBesideLand(GameTestHelper h, FlagKind flag, boolean struck) {
        DryHullGameTests.basin(h, 0, 13, true);
        for (int x = 14; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        SailingGameTestsShips.openSky(h, 24);
        return ship(h, 4, flag, struck);
    }

    /** A hull at x a..a+4 with a flagpole on its deck flying {@code flag}, assembled. */
    private static Ship ship(GameTestHelper h, int a, FlagKind flag, boolean struck) {
        BlockPos helm = DryHullGameTests.hull(h, a, false);
        BlockPos pole = helm.offset(POLE_FROM_HELM);
        h.setBlock(pole, ShipDecor.FLAGPOLE.get());
        if (flag != FlagKind.NONE) {
            if (!(h.getBlockEntity(pole) instanceof FlagpoleBlockEntity be)) throw new GameTestAssertException("no flagpole at " + pole);
            be.commandSet(flag, struck, null);
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos plotPole = f.helmPlot().offset(POLE_FROM_HELM);
        h.assertTrue(h.getLevel().getBlockEntity(plotPole) instanceof FlagpoleBlockEntity, "the flagpole was not assembled at " + plotPole);
        return new Ship(f, plotPole);
    }

    private static void setOwner(GameTestHelper h, Ship ship, UUID owner) {
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
    private static Player playerAboard(GameTestHelper h, Ship ship) {
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
    private static <T extends SeafarerMob> T onLand(GameTestHelper h, EntityType<T> type) {
        T mob = h.spawn(type, new BlockPos(19, 9, 11));
        mob.setYRot(90f);
        mob.setYHeadRot(90f);
        mob.yBodyRot = 90f;
        mob.setStationary(true);
        return mob;
    }

    private static long count(Player player, CrimeType type) {
        return LawService.record(player).recent().stream().filter(o -> o.type() == type).count();
    }

    /** Fires a ball at the west wall of {@code target} (deck height) from 2 blocks west of it, as if from {@code firingShip}. */
    private static CannonballEntity fireAt(ServerLevel level, Ship target, @org.jetbrains.annotations.Nullable Player owner,
                                           @org.jetbrains.annotations.Nullable UUID firingShip) {
        BlockPos wall = target.f().helmPlot().offset(-2, -1, 0);
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
                .thenExecute(() -> h.assertValueEqual(LawService.record(shooter).totalCrimes(), 0, "crimes for hitting a merchant ship"))
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

package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Loading, aiming and firing with a mock player in a real server. The mock player is not ticked, so holding is driven
 * by calling {@code onUseTick} and {@code releaseUsing} with the remaining session time, the way
 * {@code LivingEntity#updateUsingItem} and {@code releaseUsingItem} do. Iron golems (100 health, no armor) without AI
 * are the targets. Yaw 0 faces +Z.
 */
public final class FirearmGameTests {

    private static final String MISFIRE_BATCH = "pirates_n_ships_config_firearms_misfire";
    private static final String DISABLED_BATCH = "pirates_n_ships_config_firearms_disabled";
    private static final String AIM_MIN_BATCH = "pirates_n_ships_config_firearms_aim_min";
    private static final String AIMED_SPREAD_BATCH = "pirates_n_ships_config_firearms_aimed_spread";

    private FirearmGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FirearmGameTests.class);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Player shooter(GameTestHelper helper, Vec3 relative, float yaw, float pitch) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, yaw, pitch);
        player.setYHeadRot(yaw);
        return player;
    }

    private static ItemStack hold(Player player, Item gun, boolean loaded) {
        ItemStack stack = new ItemStack(gun);
        FirearmContent.setLoaded(stack, loaded);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static void giveAmmo(Player player, int leadShot, int gunpowder) {
        if (leadShot > 0) player.getInventory().add(new ItemStack(CombatContent.LEAD_SHOT.get(), leadShot));
        if (gunpowder > 0) player.getInventory().add(new ItemStack(Items.GUNPOWDER, gunpowder));
    }

    private static int count(Player player, Item item) {
        return FirearmService.count(player.getInventory(), item);
    }

    private static InteractionResult use(GameTestHelper helper, Player player) {
        return player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
    }

    /** Holds use on a loading gun until {@code heldTicks} into the session (one call, as the tick would). */
    private static void holdLoading(GameTestHelper helper, Player player, ItemStack gun, int heldTicks) {
        gun.onUseTick(helper.getLevel(), player, FirearmRules.LOAD_SESSION_TICKS - heldTicks);
    }

    /** Presses use on the held (loaded) gun, aims for {@code heldTicks} and lets go. */
    private static void aimAndRelease(GameTestHelper helper, Player player, int heldTicks) {
        ItemStack gun = player.getMainHandItem();
        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of a loaded gun");
        helper.assertTrue(player.isUsingItem(), "a loaded gun is held to aim");
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - heldTicks);
        player.stopUsingItem();
    }

    private static List<LeadBallEntity> balls(GameTestHelper helper) {
        return helper.getEntities(FirearmContent.LEAD_BALL.get());
    }

    private static void removeBalls(GameTestHelper helper) {
        balls(helper).forEach(LeadBallEntity::discard);
    }

    // ---- loading ----------------------------------------------------------------------------------------------

    @ModGameTest
    public static void loadingTakesOneLeadShotAndOneGunpowder(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);
        giveAmmo(player, 3, 2);

        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of an unloaded gun with ammo");
        helper.assertTrue(player.isUsingItem(), "loading holds the gun");
        helper.assertValueEqual(gun.getUseDuration(player), FirearmRules.LOAD_SESSION_TICKS, "use duration of a loading session");
        int reload = FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks();
        holdLoading(helper, player, gun, reload - 1);
        helper.assertFalse(FirearmContent.isLoaded(gun), "not loaded before the reload time");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 3, "lead shot while loading");

        holdLoading(helper, player, gun, reload);
        helper.assertTrue(FirearmContent.isLoaded(gun), "gun should be loaded after the reload time");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 2, "lead shot after loading");
        helper.assertValueEqual(count(player, Items.GUNPOWDER), 1, "gunpowder after loading");
        helper.succeed();
    }

    @ModGameTest
    public static void releasingEarlyLoadsNothingAndTakesNothing(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.MUSKET.get(), false);
        giveAmmo(player, 1, 1);

        use(helper, player);
        helper.assertTrue(player.isUsingItem(), "loading should start");
        player.releaseUsingItem();
        helper.assertFalse(player.isUsingItem(), "letting go should stop loading");
        helper.assertFalse(FirearmContent.isLoaded(gun), "gun must stay unloaded");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 1, "lead shot after cancelling");
        helper.assertValueEqual(count(player, Items.GUNPOWDER), 1, "gunpowder after cancelling");
        helper.succeed();
    }

    @ModGameTest
    public static void creativeLoadsWithoutAmmunition(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        player.getAbilities().instabuild = true;
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);

        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "creative use of an unloaded gun");
        holdLoading(helper, player, gun, FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks());
        helper.assertTrue(FirearmContent.isLoaded(gun), "creative gun should load without ammunition");
        helper.succeed();
    }

    /** Holding on after the gun has loaded and letting go then neither fires nor loads twice. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void lettingGoAfterLoadingDoesNotFire(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.MUSKET.get(), false);
        giveAmmo(player, 2, 2);
        use(helper, player);
        int reload = FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
        holdLoading(helper, player, gun, reload);
        helper.assertTrue(FirearmContent.isLoaded(gun), "loaded after the reload time");
        holdLoading(helper, player, gun, reload + 40); // still held: nothing more happens
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.LOAD_SESSION_TICKS - reload - 40);
        player.stopUsingItem();
        helper.assertTrue(balls(helper).isEmpty(), "letting go of a loading session must not fire");
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun stays loaded");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 1, "one lead shot used");
        helper.succeed();
    }

    @ModGameTest
    public static void unloadedGunWithoutAmmoOnlyClicks(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);
        giveAmmo(player, 0, 5); // gunpowder alone is not enough

        helper.assertValueEqual(use(helper, player), InteractionResult.FAIL, "use without lead shot");
        helper.assertFalse(player.isUsingItem(), "no loading without lead shot");
        helper.assertFalse(FirearmContent.isLoaded(gun), "gun stays unloaded");
        helper.assertTrue(balls(helper).isEmpty(), "an unloaded gun must not fire");
        helper.succeed();
    }

    // ---- firing -----------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void firingSpawnsOneBallAndUnloads(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);

        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of a loaded gun");
        helper.assertTrue(player.isUsingItem(), "a loaded gun is held to aim");
        helper.assertValueEqual(gun.getUseDuration(player), FirearmRules.AIM_SESSION_TICKS, "use duration of an aim");
        helper.assertTrue(balls(helper).isEmpty(), "pressing use only aims");
        // let go after aiming for 10 ticks: the shot leaves
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - 10);
        player.stopUsingItem();
        List<LeadBallEntity> balls = balls(helper);
        helper.assertValueEqual(balls.size(), 1, "balls after one shot");
        LeadBallEntity ball = balls.getFirst();
        helper.assertTrue(ball.getOwner() == player, "the shooter must own the ball");
        helper.assertValueEqual(ball.damage(), FirearmsConfig.type(FirearmKind.PISTOL).damage(), "ball damage");
        helper.assertTrue(ball.getDeltaMovement().z > 1.0, "the ball should fly fast along the aim (+Z), was " + ball.getDeltaMovement());
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        helper.assertTrue(player.getCooldowns().isOnCooldown(gun.getItem()), "the gun should be on cooldown");
        removeBalls(helper);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void ballHitDealsTheConfiguredDamage(GameTestHelper helper) {
        IronGolem target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(4.5, 1, 7.5));
        float full = target.getHealth();
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        hold(player, CombatContent.MUSKET.get(), true);
        float damage = FirearmsConfig.type(FirearmKind.MUSKET).damage();

        aimAndRelease(helper, player, 5);
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < full, "the golem has not been hit yet");
            helper.assertTrue(Math.abs(target.getHealth() - (full - damage)) < 0.01f,
                    "expected health " + (full - damage) + ", got " + target.getHealth());
            helper.assertTrue(target.getLastHurtByMob() == player, "the shooter should be the attacker");
            helper.assertTrue(balls(helper).isEmpty(), "the ball should be gone after the hit");
        });
    }

    @ModGameTest
    public static void ballVanishesAfterItsLifetime(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        LeadBallEntity ball = new LeadBallEntity(helper.getLevel(), player, 1.0f, 5);
        ball.setNoGravity(true);
        ball.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(ball);
        helper.runAfterDelay(3, () -> helper.assertFalse(ball.isRemoved(), "the ball vanished too early"));
        helper.succeedWhen(() -> helper.assertTrue(ball.isRemoved(), "the ball should vanish after its lifetime"));
    }

    /** A plain click (let go in the same tick) fires at once with the default minimum hold of 0. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void anInstantClickStillFires(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        aimAndRelease(helper, player, 0);
        helper.assertValueEqual(balls(helper).size(), 1, "balls after a click");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }

    // ---- config -----------------------------------------------------------------------------------------------

    /** With a minimum hold, letting go too early puts the gun down still loaded; after the minimum it fires. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = AIM_MIN_BATCH)
    public static void releaseFiresAfterTheMinimumHold(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.AIM_MIN_TICKS, 5);
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        aimAndRelease(helper, player, 2);
        helper.assertTrue(balls(helper).isEmpty(), "let go before the minimum hold: no shot");
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun stays loaded");
        aimAndRelease(helper, player, 5);
        helper.assertValueEqual(balls(helper).size(), 1, "balls after the minimum hold");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }

    /** With a wide spread and an aimed factor of 0, a steadied shot (held for the steady time) flies exactly along the aim. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = AIMED_SPREAD_BATCH)
    public static void aimedSpreadFactorAppliesAfterTheSteadyTime(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.PISTOL_SPREAD, 30.0);
        ConfigOverrides.during(helper, FirearmsConfig.AIM_STEADY_TICKS, 20);
        ConfigOverrides.during(helper, FirearmsConfig.AIMED_SPREAD_FACTOR, 0.0);
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        for (int i = 0; i < 5; i++) {
            hold(player, CombatContent.PISTOL.get(), true);
            player.getCooldowns().removeCooldown(player.getMainHandItem().getItem());
            // undo the last shot's recoil (view kick and push)
            player.setXRot(0);
            player.setYRot(0);
            player.setDeltaMovement(Vec3.ZERO);
            aimAndRelease(helper, player, 20);
            List<LeadBallEntity> balls = balls(helper);
            helper.assertValueEqual(balls.size(), 1, "balls after a steadied shot");
            Vec3 v = balls.getFirst().getDeltaMovement();
            helper.assertTrue(Math.abs(v.x) < 1e-6 && Math.abs(v.y) < 1e-6 && v.z > 1.0,
                    "a steadied shot with factor 0 flies straight along +Z, was " + v);
            removeBalls(helper);
        }
        helper.succeed();
    }


    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = MISFIRE_BATCH)
    public static void rainMisfireClicksAndKeepsTheGunLoaded(GameTestHelper helper) {
        ConfigOverrides.during(helper, CombatConfig.FIREARM_MISFIRE_IN_RAIN, true);
        ConfigOverrides.during(helper, CombatConfig.RAIN_MISFIRE_CHANCE, 1.0);
        ServerLevel level = helper.getLevel();
        float rain = level.getRainLevel(1.0f);
        Player player = shooter(helper, new Vec3(4.5, 1, 4.5), 0, 0);
        // clear the barrier ceiling above the shooter so rain can reach it
        for (int y = 2; y < 32; y++) {
            BlockPos p = new BlockPos(4, y, 4);
            if (helper.getBlockState(p).is(Blocks.BARRIER)) helper.setBlock(p, Blocks.AIR);
        }
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        try {
            level.setWeatherParameters(0, 6000, true, false);
            level.setRainLevel(1.0f);
            helper.assertTrue(FirearmService.inRain(player), "the shooter should stand in the rain");

            aimAndRelease(helper, player, 3);
            helper.assertTrue(balls(helper).isEmpty(), "a misfire must not fire a ball");
            helper.assertTrue(FirearmContent.isLoaded(gun), "a misfire leaves the gun loaded");
            helper.assertTrue(player.getCooldowns().isOnCooldown(gun.getItem()), "a misfire still starts the cooldown");
        } finally {
            level.setWeatherParameters(20000000, 20000000, false, false);
            level.setRainLevel(rain);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = DISABLED_BATCH)
    public static void disabledFirearmsAreInert(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.ENABLED, false);
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack loaded = hold(player, CombatContent.PISTOL.get(), true);
        giveAmmo(player, 1, 1); // after hold: add() fills the selected slot first, which hold() would overwrite
        helper.assertValueEqual(use(helper, player), InteractionResult.PASS, "use of a loaded gun while disabled");
        helper.assertTrue(balls(helper).isEmpty(), "a disabled gun must not fire");
        helper.assertTrue(FirearmContent.isLoaded(loaded), "a disabled gun keeps its state");

        hold(player, CombatContent.MUSKET.get(), false);
        helper.assertValueEqual(use(helper, player), InteractionResult.PASS, "use of an unloaded gun while disabled");
        helper.assertFalse(player.isUsingItem(), "a disabled gun must not load");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 1, "no lead shot used");
        helper.succeed();
    }
}

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
 * Loading and firing with a mock player in a real server. The mock player is not ticked, so a completed reload is
 * driven by calling {@code finishUsingItem} the way {@code LivingEntity#completeUsingItem} does. Iron golems (100
 * health, no armor) without AI are the targets. Yaw 0 faces +Z.
 */
public final class FirearmGameTests {

    private static final String MISFIRE_BATCH = "pirates_n_ships_config_firearms_misfire";
    private static final String DISABLED_BATCH = "pirates_n_ships_config_firearms_disabled";

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
        helper.assertTrue(player.isUsingItem(), "loading should hold the gun like a drawn bow");
        helper.assertValueEqual(gun.getUseDuration(player), FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks(), "use duration");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 3, "lead shot while loading");

        gun.finishUsingItem(helper.getLevel(), player);
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
        gun.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(FirearmContent.isLoaded(gun), "creative gun should load without ammunition");
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

        use(helper, player);
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

    // ---- config -----------------------------------------------------------------------------------------------

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

            use(helper, player);
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

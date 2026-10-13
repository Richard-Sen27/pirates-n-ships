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
import net.minecraft.server.level.ServerPlayer;
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
    private static final String LOWER_ON_SNEAK_BATCH = "pirates_n_ships_config_firearms_lower_on_sneak";
    private static final String RELEASE_CLICK_BATCH = "pirates_n_ships_config_firearms_release_click";

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

    /**
     * Presses use on the held (loaded) gun, aims for {@code heldTicks} and fires the way the current scheme does: the
     * attack key with {@code firearms.fire_on_attack} on (FA1, {@link FirearmTrigger}), then lets go (which only
     * lowers); with it off, letting go fires.
     */
    private static void aimAndRelease(GameTestHelper helper, Player player, int heldTicks) {
        ItemStack gun = player.getMainHandItem();
        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of a loaded gun");
        helper.assertTrue(player.isUsingItem(), "a loaded gun is held to aim");
        if (FirearmsConfig.FIRE_ON_ATTACK.get()) FirearmTrigger.pullAimedFor(helper.getLevel(), player, heldTicks);
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
        // the attack key while aiming (FA1): the shot leaves; letting go afterwards adds nothing
        FirearmTrigger.pull(helper.getLevel(), player);
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

    /** Release scheme ({@code fire_on_attack} off): a plain click (let go in the same tick) fires at once with the default minimum hold of 0. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = RELEASE_CLICK_BATCH)
    public static void anInstantClickStillFires(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.FIRE_ON_ATTACK, false);
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        aimAndRelease(helper, player, 0);
        helper.assertValueEqual(balls(helper).size(), 1, "balls after a click");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }

    // ---- lowering and the item bar (P5) -------------------------------------------------------------------------

    /** Sneaking while aiming and letting go lowers the gun: no ball, still loaded, no ammunition used, no cooldown. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void sneakingLowersAnAimedGunWithoutFiring(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        giveAmmo(player, 2, 2);

        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of a loaded gun");
        helper.assertTrue(player.isUsingItem(), "the gun is raised to aim");
        player.setShiftKeyDown(true); // the client releases the use right after the sneak command
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - 30);
        player.stopUsingItem();
        helper.assertTrue(balls(helper).isEmpty(), "lowering must not fire");
        helper.assertTrue(FirearmContent.isLoaded(gun), "the lowered gun stays loaded");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 2, "no lead shot used");
        helper.assertValueEqual(count(player, Items.GUNPOWDER), 2, "no gunpowder used");
        helper.assertFalse(player.getCooldowns().isOnCooldown(gun.getItem()), "lowering starts no cooldown");

        // still sneaking with the use key held: the gun is not raised again
        helper.assertValueEqual(use(helper, player), InteractionResult.PASS, "use of a loaded gun while sneaking");
        helper.assertFalse(player.isUsingItem(), "a loaded gun stays lowered while sneaking");

        // standing up again: aiming and firing work as before
        player.setShiftKeyDown(false);
        aimAndRelease(helper, player, 5);
        helper.assertValueEqual(balls(helper).size(), 1, "balls after standing up and firing");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }

    /** Sneaking does not stop loading: an unloaded gun loads while sneaking. */
    @ModGameTest
    public static void sneakingStillLoads(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);
        giveAmmo(player, 1, 1);
        player.setShiftKeyDown(true);
        helper.assertValueEqual(use(helper, player), InteractionResult.CONSUME, "use of an unloaded gun while sneaking");
        holdLoading(helper, player, gun, FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks());
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun loads while sneaking");
        helper.succeed();
    }

    /** With {@code lower_on_sneak} off, a sneaking player aims and the release fires. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LOWER_ON_SNEAK_BATCH)
    public static void lowerOnSneakOffFiresWhileSneaking(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.LOWER_ON_SNEAK, false);
        Player player = shooter(helper, new Vec3(4.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        player.setShiftKeyDown(true);
        aimAndRelease(helper, player, 10);
        helper.assertValueEqual(balls(helper).size(), 1, "balls after a sneaking release with lower_on_sneak off");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }

    /** The item bar from the stack alone (no client here): full and gold when loaded, hidden when empty. */
    @ModGameTest
    public static void itemBarShowsTheLoadedState(GameTestHelper helper) {
        ItemStack loaded = new ItemStack(CombatContent.MUSKET.get());
        FirearmContent.setLoaded(loaded, true);
        helper.assertTrue(loaded.isBarVisible(), "a loaded gun shows a bar");
        helper.assertValueEqual(loaded.getBarWidth(), FirearmBar.MAX_WIDTH, "a loaded gun's bar is full");
        helper.assertValueEqual(loaded.getBarColor(), FirearmBar.LOADED_COLOR, "a loaded gun's bar is gold");

        ItemStack empty = new ItemStack(CombatContent.PISTOL.get());
        helper.assertFalse(empty.isBarVisible(), "an empty gun shows no bar");
        helper.succeed();
    }

    /** A loading session cancelled by letting go leaves the gun unloaded and its bar hidden. */
    @ModGameTest
    public static void cancelledLoadingLeavesTheBarHidden(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(1.5, 1, 1.5), 0, 0);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);
        giveAmmo(player, 1, 1);
        use(helper, player);
        holdLoading(helper, player, gun, 10);
        player.releaseUsingItem();
        helper.assertFalse(player.isUsingItem(), "letting go stops loading");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun stays unloaded");
        helper.assertFalse(gun.isBarVisible(), "no bar after a cancelled load");
        helper.succeed();
    }

    // ---- reload time with a ticked server player (FA2) -------------------------------------------------------

    /**
     * A real server player with a mock connection, not added to the level (the career tests' pattern): unlike the
     * mock player it can be ticked ({@link ServerPlayer#doTick}, what the connection does every server tick), so its
     * use time counts down through vanilla's {@code LivingEntity#updatingUsingItem} and the gun's {@code onUseTick}
     * sees what it sees in a game.
     */
    private static ServerPlayer tickedShooter(GameTestHelper helper, Vec3 relative) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fa2_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0, 0);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Presses use with the server's own handler of the use packet ({@code ServerPlayerGameMode#useItem}). */
    private static InteractionResult pressUse(GameTestHelper helper, ServerPlayer player) {
        return player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /** Bar width the local player would see for {@code gun} while it is {@code player}'s use item. */
    private static int barWidthSeenBy(ServerPlayer player, ItemStack gun) {
        com.richardsenger.piratesnships.combat.firearms.client.FirearmClientState before =
                com.richardsenger.piratesnships.combat.firearms.client.FirearmClientState.get();
        // the client's view of its own session, read from this player the way LocalFirearmClientState reads the local one
        com.richardsenger.piratesnships.combat.firearms.client.FirearmClientState.set(stack -> {
            if (!player.isUsingItem() || (player.getUseItem() != stack && player.getMainHandItem() != stack)) return -1;
            int remaining = player.getUseItemRemainingTicks();
            return FirearmRules.isAimSession(remaining) ? -1 : FirearmRules.heldTicks(remaining);
        });
        try {
            return gun.getBarWidth();
        } finally {
            com.richardsenger.piratesnships.combat.firearms.client.FirearmClientState.set(before);
        }
    }

    /**
     * FA2: holding use on an unloaded musket for a quarter of its reload time, ticked like a game, neither loads it
     * nor takes the ammunition; the bar shows a quarter; letting go leaves it unloaded.
     */
    @ModGameTest
    public static void aQuarterOfTheReloadTimeDoesNotLoad(GameTestHelper helper) {
        ServerPlayer player = tickedShooter(helper, new Vec3(1.5, 1, 1.5));
        ItemStack gun = hold(player, CombatContent.MUSKET.get(), false);
        giveAmmo(player, 2, 2);
        int reload = FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
        helper.assertTrue(reload >= 8, "the musket's reload time should be a real wait, was " + reload);

        helper.assertValueEqual(pressUse(helper, player), InteractionResult.CONSUME, "use of an unloaded musket with ammo");
        helper.assertTrue(player.isUsingItem(), "loading holds the gun");
        int quarter = reload / 4;
        for (int i = 0; i < quarter; i++) {
            player.doTick();
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(player.isUsingItem(), "still loading after " + (i + 1) + " ticks");
            helper.assertFalse(FirearmContent.isLoaded(held), "loaded after only " + (i + 1) + " of " + reload + " ticks");
        }
        ItemStack held = player.getMainHandItem();
        helper.assertValueEqual(FirearmRules.heldTicks(player.getUseItemRemainingTicks()), quarter, "held ticks after a quarter");
        helper.assertValueEqual(barWidthSeenBy(player, held), FirearmBar.loadingWidth(quarter, reload), "bar width after a quarter");
        helper.assertTrue(barWidthSeenBy(player, held) < FirearmBar.MAX_WIDTH / 2, "the bar is far from full after a quarter");

        player.releaseUsingItem();
        player.doTick();
        held = player.getMainHandItem();
        helper.assertFalse(player.isUsingItem(), "letting go stops loading");
        helper.assertFalse(FirearmContent.isLoaded(held), "letting go after a quarter leaves the musket unloaded");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 2, "no lead shot taken");
        helper.assertValueEqual(count(player, Items.GUNPOWDER), 2, "no gunpowder taken");
        helper.succeed();
    }

    /** FA2: the same in creative mode (no ammunition): a quarter of the reload time does not load. */
    @ModGameTest
    public static void aQuarterOfTheReloadTimeDoesNotLoadInCreative(GameTestHelper helper) {
        ServerPlayer player = tickedShooter(helper, new Vec3(1.5, 1, 1.5));
        player.setGameMode(GameType.CREATIVE);
        hold(player, CombatContent.PISTOL.get(), false);
        int reload = FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks();
        helper.assertValueEqual(pressUse(helper, player), InteractionResult.CONSUME, "creative use of an unloaded pistol");
        for (int i = 0; i < reload / 4; i++) {
            player.doTick();
            helper.assertFalse(FirearmContent.isLoaded(player.getMainHandItem()), "loaded after only " + (i + 1) + " of " + reload + " ticks");
        }
        player.releaseUsingItem();
        helper.assertFalse(FirearmContent.isLoaded(player.getMainHandItem()), "letting go early leaves the pistol unloaded");
        helper.succeed();
    }

    /** FA2: the full reload time, ticked like a game, loads the musket exactly at its end. */
    @ModGameTest(timeoutTicks = 200)
    public static void theFullReloadTimeLoads(GameTestHelper helper) {
        ServerPlayer player = tickedShooter(helper, new Vec3(1.5, 1, 1.5));
        hold(player, CombatContent.MUSKET.get(), false);
        giveAmmo(player, 2, 2);
        int reload = FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
        pressUse(helper, player);
        for (int i = 1; i <= reload; i++) {
            player.doTick();
            boolean loaded = FirearmContent.isLoaded(player.getMainHandItem());
            if (i < reload) {
                helper.assertFalse(loaded, "loaded after " + i + " of " + reload + " ticks");
                helper.assertValueEqual(barWidthSeenBy(player, player.getMainHandItem()), FirearmBar.loadingWidth(i, reload),
                        "bar width after " + i + " ticks");
            }
        }
        // the tick that counts the last reload tick runs onUseTick with that many ticks held
        player.doTick();
        helper.assertTrue(FirearmContent.isLoaded(player.getMainHandItem()), "loaded after the full reload time");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 1, "one lead shot taken");
        player.releaseUsingItem();
        helper.assertTrue(FirearmContent.isLoaded(player.getMainHandItem()), "letting go keeps it loaded");
        helper.assertTrue(balls(helper).isEmpty(), "letting go of a loading session never fires");
        helper.succeed();
    }

    /**
     * FA2: a press that reaches the server while it still runs a loading session (the client ended its own session
     * without a release reaching the server) starts the session over, so the load takes the full reload time from the
     * new press and not from the old one.
     */
    @ModGameTest(timeoutTicks = 300)
    public static void aNewPressRestartsAStaleLoadingSession(GameTestHelper helper) {
        ServerPlayer player = tickedShooter(helper, new Vec3(1.5, 1, 1.5));
        hold(player, CombatContent.MUSKET.get(), false);
        giveAmmo(player, 2, 2);
        int reload = FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
        pressUse(helper, player);
        int stale = reload * 3 / 4;
        for (int i = 0; i < stale; i++) player.doTick();
        helper.assertFalse(FirearmContent.isLoaded(player.getMainHandItem()), "not loaded after three quarters");

        helper.assertValueEqual(pressUse(helper, player), InteractionResult.CONSUME, "the new press");
        helper.assertTrue(player.isUsingItem(), "the new press holds the gun");
        helper.assertValueEqual(FirearmRules.heldTicks(player.getUseItemRemainingTicks()), 0, "the new press starts from 0");
        for (int i = 0; i < reload / 2; i++) {
            player.doTick();
            helper.assertFalse(FirearmContent.isLoaded(player.getMainHandItem()),
                    "loaded " + (i + 1) + " ticks after the new press (the old press counted)");
        }
        for (int i = reload / 2; i <= reload; i++) player.doTick();
        helper.assertTrue(FirearmContent.isLoaded(player.getMainHandItem()), "loaded a full reload time after the new press");
        helper.assertValueEqual(count(player, CombatContent.LEAD_SHOT.get()), 1, "one lead shot taken");
        helper.succeed();
    }

    /** FA2: the same press on a loaded gun during an aim session starts a new aim, never fires. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aNewPressDuringAnAimNeverFires(GameTestHelper helper) {
        ServerPlayer player = tickedShooter(helper, new Vec3(4.5, 1, 1.5));
        hold(player, CombatContent.MUSKET.get(), true);
        pressUse(helper, player);
        for (int i = 0; i < 30; i++) player.doTick();
        helper.assertValueEqual(pressUse(helper, player), InteractionResult.CONSUME, "the new press on a loaded gun");
        helper.assertTrue(FirearmRules.isAimSession(player.getUseItemRemainingTicks()), "a new aim session");
        helper.assertValueEqual(FirearmRules.heldTicks(player.getUseItemRemainingTicks()), 0, "the new aim starts from 0");
        helper.assertTrue(balls(helper).isEmpty(), "restarting an aim never fires");
        helper.assertTrue(FirearmContent.isLoaded(player.getMainHandItem()), "the gun stays loaded");
        helper.succeed();
    }

    // ---- config -----------------------------------------------------------------------------------------------

    /** Release scheme: with a minimum hold, letting go too early puts the gun down still loaded; after the minimum it fires. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = AIM_MIN_BATCH)
    public static void releaseFiresAfterTheMinimumHold(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.FIRE_ON_ATTACK, false);
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

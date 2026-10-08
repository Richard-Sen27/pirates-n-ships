package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmTriggerRules.Outcome;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * The attack-key trigger (FA1) on the server: {@link FirearmTrigger#pull} is what the {@link FirearmFirePayload}
 * handler runs. A mock player (not ticked) stands in for the sender; holding use is driven as in
 * {@link FirearmGameTests}. Yaw 0 faces +Z.
 */
public final class FirearmTriggerGameTests {

    private static final String SPREAD_BATCH = "pirates_n_ships_config_firearms_trigger_spread";
    private static final String TOGGLE_BATCH = "pirates_n_ships_config_firearms_fire_on_attack";

    private FirearmTriggerGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FirearmTriggerGameTests.class);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Player shooter(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        player.setYHeadRot(0);
        return player;
    }

    private static ItemStack hold(Player player, Item item, boolean loaded) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof FirearmItem) FirearmContent.setLoaded(stack, loaded);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static void aim(GameTestHelper helper, Player player) {
        InteractionResult r = player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertValueEqual(r, InteractionResult.CONSUME, "use of a loaded gun");
        helper.assertTrue(player.isUsingItem() && FirearmRules.isAimSession(player.getUseItemRemainingTicks()), "the gun is raised to aim");
    }

    private static FirearmTrigger.Result pull(GameTestHelper helper, Player player) {
        return FirearmTrigger.pull(helper.getLevel(), player);
    }

    private static List<LeadBallEntity> balls(GameTestHelper helper) {
        return helper.getEntities(FirearmContent.LEAD_BALL.get());
    }

    private static void removeBalls(GameTestHelper helper) {
        balls(helper).forEach(LeadBallEntity::discard);
    }

    /** Undoes a shot's recoil and cooldown and reloads the held gun. */
    private static void reset(Player player) {
        FirearmContent.setLoaded(player.getMainHandItem(), true);
        player.getCooldowns().removeCooldown(player.getMainHandItem().getItem());
        player.setXRot(0);
        player.setYRot(0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    // ---- firing -----------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void attackFiresALoadedGunFromTheHip(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        FirearmTrigger.Result r = pull(helper, player);
        helper.assertValueEqual(r.outcome(), Outcome.FIRE, "outcome of the attack key on a loaded gun");
        helper.assertValueEqual(r.shot(), FirearmService.Shot.FIRED, "shot");
        helper.assertFalse(r.aimed(), "a shot without aiming is from the hip");
        helper.assertValueEqual(r.spread(), (double) FirearmsConfig.type(FirearmKind.PISTOL).spreadDegrees(), "hip spread");
        helper.assertValueEqual(balls(helper).size(), 1, "balls after a hip shot");
        helper.assertTrue(balls(helper).getFirst().getOwner() == player, "the shooter owns the ball");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        helper.assertTrue(player.getCooldowns().isOnCooldown(gun.getItem()), "the gun is on cooldown");
        removeBalls(helper);
        helper.succeed();
    }

    /** Aim (hold use), fire with the attack key, then let go of use: one ball, and the release adds nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void attackFiresAnAimedGunAndTheReleaseOnlyLowers(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.MUSKET.get(), true);
        aim(helper, player);
        FirearmTrigger.Result r = pull(helper, player);
        helper.assertValueEqual(r.outcome(), Outcome.FIRE, "outcome of the attack key while aiming");
        helper.assertTrue(r.aimed(), "a shot while holding use is aimed");
        helper.assertValueEqual(balls(helper).size(), 1, "balls after an aimed shot");
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - 40);
        player.stopUsingItem();
        helper.assertValueEqual(balls(helper).size(), 1, "letting go after the shot fires nothing more");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded");
        removeBalls(helper);
        helper.succeed();
    }

    /** Letting go of an aim never fires: the gun goes down still loaded, with no ammunition used and no cooldown. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void releasingAnAimDoesNotFire(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        for (int held : new int[]{0, 5, 40}) {
            aim(helper, player);
            gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - held);
            player.stopUsingItem();
            helper.assertTrue(balls(helper).isEmpty(), "letting go after " + held + " ticks must not fire");
            helper.assertTrue(FirearmContent.isLoaded(gun), "the gun stays loaded");
            helper.assertFalse(player.getCooldowns().isOnCooldown(gun.getItem()), "no cooldown without a shot");
        }
        helper.succeed();
    }

    // ---- refusals ---------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void attackWhileReloadingDoesNothing(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), false);
        player.getInventory().add(new ItemStack(CombatContent.LEAD_SHOT.get(), 2));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 2));
        helper.assertValueEqual(gun.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult(),
                InteractionResult.CONSUME, "use of an unloaded gun with ammunition");
        helper.assertTrue(player.isUsingItem() && !FirearmRules.isAimSession(player.getUseItemRemainingTicks()), "a loading session runs");
        FirearmTrigger.Result r = pull(helper, player);
        helper.assertValueEqual(r.outcome(), Outcome.RELOADING, "outcome while loading");
        helper.assertTrue(balls(helper).isEmpty(), "no shot while loading");
        helper.assertTrue(player.isUsingItem(), "loading goes on");
        helper.assertValueEqual(FirearmService.count(player.getInventory(), CombatContent.LEAD_SHOT.get()), 2, "no lead shot used");
        // the loading still completes afterwards
        gun.onUseTick(helper.getLevel(), player, FirearmRules.LOAD_SESSION_TICKS - FirearmsConfig.type(FirearmKind.PISTOL).reloadTicks());
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun loads after the refused attack");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void attackWithAnEmptyGunOnlyClicks(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.MUSKET.get(), false);
        FirearmTrigger.Result r = pull(helper, player);
        helper.assertValueEqual(r.outcome(), Outcome.EMPTY, "outcome of the attack key on an empty gun");
        helper.assertTrue(r.shot() == null, "an empty gun pulls no shot");
        helper.assertTrue(balls(helper).isEmpty(), "an empty gun must not fire");
        helper.assertFalse(player.getCooldowns().isOnCooldown(gun.getItem()), "a dry click starts no cooldown");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void attackDuringTheCooldownDoesNothing(GameTestHelper helper) {
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        helper.assertValueEqual(pull(helper, player).outcome(), Outcome.FIRE, "first shot");
        removeBalls(helper);
        FirearmContent.setLoaded(gun, true);
        FirearmTrigger.Result r = pull(helper, player);
        helper.assertValueEqual(r.outcome(), Outcome.COOLDOWN, "outcome during the cooldown");
        helper.assertTrue(balls(helper).isEmpty(), "no shot during the cooldown");
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun stays loaded");
        helper.succeed();
    }

    @ModGameTest
    public static void attackWithoutAGunDoesNothing(GameTestHelper helper) {
        Player player = shooter(helper);
        hold(player, Items.IRON_SWORD, false);
        // a gun in the off hand does not take the attack key either while nothing is in use
        ItemStack offhand = new ItemStack(CombatContent.PISTOL.get());
        FirearmContent.setLoaded(offhand, true);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        helper.assertValueEqual(pull(helper, player).outcome(), Outcome.NO_GUN, "outcome without a gun in the main hand");
        helper.assertTrue(balls(helper).isEmpty(), "no shot without a gun");
        helper.assertTrue(FirearmContent.isLoaded(offhand), "the off-hand gun stays loaded");
        helper.succeed();
    }

    // ---- spread and the toggle --------------------------------------------------------------------------------

    /**
     * With a 30 degree spread, an instant steady time and an aimed factor of 0: an aimed shot flies exactly along the
     * look, a hip shot leaves with the full spread and scatters.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = SPREAD_BATCH)
    public static void hipShotsScatterWiderThanAimedShots(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.PISTOL_SPREAD, 30.0);
        ConfigOverrides.during(helper, FirearmsConfig.AIM_STEADY_TICKS, 0);
        ConfigOverrides.during(helper, FirearmsConfig.AIMED_SPREAD_FACTOR, 0.0);
        Player player = shooter(helper);
        hold(player, CombatContent.PISTOL.get(), true);
        double widestHip = 0.0;
        for (int i = 0; i < 6; i++) {
            reset(player);
            aim(helper, player);
            FirearmTrigger.Result aimed = pull(helper, player);
            player.stopUsingItem();
            helper.assertTrue(aimed.aimed() && aimed.spread() == 0.0, "an aimed shot has no spread here, was " + aimed);
            Vec3 v = balls(helper).getFirst().getDeltaMovement();
            helper.assertTrue(Math.abs(v.x) < 1e-6 && Math.abs(v.y) < 1e-6 && v.z > 1.0, "an aimed shot flies along +Z, was " + v);
            removeBalls(helper);

            reset(player);
            FirearmTrigger.Result hip = pull(helper, player);
            helper.assertTrue(!hip.aimed() && hip.spread() == 30.0, "a hip shot has the full spread, was " + hip);
            Vec3 h = balls(helper).getFirst().getDeltaMovement();
            double angle = Math.toDegrees(Math.acos(Math.clamp(h.normalize().z, -1.0, 1.0)));
            helper.assertTrue(angle <= 30.0 + 1e-3, "a hip shot stays inside the spread cone, was " + angle);
            widestHip = Math.max(widestHip, angle);
            removeBalls(helper);
        }
        helper.assertTrue(widestHip > 1.0, "hip shots should scatter, widest was " + widestHip + " degrees");
        helper.succeed();
    }

    /** With {@code fire_on_attack} off the attack key does nothing and letting go of an aim fires, as before FA1. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = TOGGLE_BATCH)
    public static void toggleOffRestoresReleaseToFire(GameTestHelper helper) {
        ConfigOverrides.during(helper, FirearmsConfig.FIRE_ON_ATTACK, false);
        Player player = shooter(helper);
        ItemStack gun = hold(player, CombatContent.PISTOL.get(), true);
        helper.assertValueEqual(pull(helper, player).outcome(), Outcome.OFF, "outcome of the attack key with the toggle off");
        aim(helper, player);
        helper.assertValueEqual(pull(helper, player).outcome(), Outcome.OFF, "outcome while aiming with the toggle off");
        helper.assertTrue(balls(helper).isEmpty(), "the attack key must not fire with the toggle off");
        helper.assertTrue(FirearmContent.isLoaded(gun), "the gun stays loaded");
        gun.releaseUsing(helper.getLevel(), player, FirearmRules.AIM_SESSION_TICKS - 10);
        player.stopUsingItem();
        helper.assertValueEqual(balls(helper).size(), 1, "letting go of the aim fires with the toggle off");
        helper.assertFalse(FirearmContent.isLoaded(gun), "the gun is unloaded after the shot");
        removeBalls(helper);
        helper.succeed();
    }
}

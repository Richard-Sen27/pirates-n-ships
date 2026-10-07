package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActionPayload;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActions;
import com.richardsenger.piratesnships.combat.melee.net.MeleeStateSync;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * The server side of the melee network layer on a mock player: {@link MeleeActions} drives {@link MeleeService}, and
 * state changes become {@link com.richardsenger.piratesnships.combat.melee.net.MeleeStatePayload}s (observed through
 * {@link MeleeStateSync#record}; delivery to tracking clients is the loader's {@code sendToTrackingEntityAndSelf} and
 * needs real connections, see the playtest). Yaw 90 faces -X, yaw -90 faces +X.
 */
public final class MeleeNetGameTests {

    private MeleeNetGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MeleeNetGameTests.class);
    }

    private static Player swordsman(GameTestHelper helper, ResourceLocation weapon, double x, double z, float yaw) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(weapon)));
        player.moveTo(helper.absoluteVec(new Vec3(x, 1, z)), yaw, 0f);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        return player;
    }

    private static void act(Player player, MeleeAction action) {
        MeleeActions.handle(new MeleeActionPayload(action, 0), player);
    }

    private static void finish(GameTestHelper helper, Player player) {
        MeleeStateSync.stopRecording(player.getId());
        player.discard();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void slashActionStartsWindupAndIsBroadcast(GameTestHelper helper) {
        Player player = swordsman(helper, DefaultWeapons.CUTLASS_ID, 4.5, 4.5, -90);
        List<MeleeStateSync.Sent> sent = MeleeStateSync.record(player.getId());
        act(player, MeleeAction.SLASH);
        CombatState s = MeleeService.state(player);
        helper.assertValueEqual(s.phase(), Phase.WINDUP, "phase after slash");
        helper.assertValueEqual(s.attack(), AttackKind.SLASH, "attack after slash");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(sent.stream().anyMatch(x -> x.target() == MeleeStateSync.Target.OBSERVERS
                    && x.payload().phase() == Phase.WINDUP && x.payload().attack() == AttackKind.SLASH
                    && !x.payload().hasStamina()), "wind-up sent to observers without stamina, sent " + sent);
            float max = MeleeConfig.STAMINA_MAX.get().floatValue();
            helper.assertTrue(sent.stream().anyMatch(x -> x.target() == MeleeStateSync.Target.OWNER
                    && x.payload().phase() == Phase.WINDUP && x.payload().hasStamina()
                    && x.payload().stamina() < max && x.payload().refusal() == Refusal.NONE),
                    "wind-up with spent stamina sent to the owner, sent " + sent);
            finish(helper, player);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void guardDownAndUpToggleTheGuard(GameTestHelper helper) {
        Player player = swordsman(helper, DefaultWeapons.SABER_ID, 4.5, 4.5, -90);
        act(player, MeleeAction.GUARD_DOWN);
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.GUARDING, "phase after guard down");
        helper.assertTrue(MeleeService.state(player).guardHeld(), "guard held");
        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(MeleeService.state(player).phase(), Phase.GUARDING, "still guarding while held");
            act(player, MeleeAction.GUARD_UP);
            helper.assertValueEqual(MeleeService.state(player).phase(), Phase.IDLE, "phase after guard up");
            helper.assertFalse(MeleeService.state(player).guardHeld(), "guard released");
            finish(helper, player);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void parryActionDeflectsAMobHit(GameTestHelper helper) {
        Player player = swordsman(helper, DefaultWeapons.SABER_ID, 2.5, 4.5, 90);
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 1, 4);
        attacker.setYRot(-90);
        attacker.setYHeadRot(-90);
        attacker.yBodyRot = -90;
        float health = player.getHealth();
        act(player, MeleeAction.PARRY);
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.PARRYING, "phase after parry");
        helper.runAfterDelay(2, () -> {
            player.hurt(helper.getLevel().damageSources().mobAttack(attacker), 5.0f);
            helper.assertTrue(Math.abs(player.getHealth() - health) < 0.01f, "parried hit does no damage, health " + player.getHealth());
            helper.assertValueEqual(MeleeService.state(attacker).phase(), Phase.STAGGERED, "attacker staggered");
            helper.assertTrue(MeleeService.state(player).riposteReady(), "riposte window open");
            finish(helper, player);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void refusedActionLeavesStateUnchangedAndIsAnswered(GameTestHelper helper) {
        Player player = swordsman(helper, DefaultWeapons.RAPIER_ID, 4.5, 4.5, -90);
        List<MeleeStateSync.Sent> sent = MeleeStateSync.record(player.getId());
        act(player, MeleeAction.THRUST);
        CombatState before = MeleeService.state(player);
        helper.assertValueEqual(before.phase(), Phase.WINDUP, "thrust started");
        InputResult r = MeleeActions.apply(player, MeleeAction.PARRY);
        helper.assertTrue(r != null && r.refusal() == Refusal.BUSY, "parry during an attack is refused as busy, got " + r);
        helper.assertValueEqual(MeleeService.state(player), before, "state after the refused parry");
        act(player, MeleeAction.PARRY);
        helper.assertValueEqual(MeleeService.state(player), before, "state after the refused parry payload");
        helper.assertTrue(sent.stream().anyMatch(x -> x.target() == MeleeStateSync.Target.OWNER
                && x.payload().refusal() == Refusal.BUSY), "refusal answered to the owner, sent " + sent);
        helper.assertTrue(sent.stream().noneMatch(x -> x.target() == MeleeStateSync.Target.OBSERVERS
                && x.payload().refusal() != Refusal.NONE), "observers never get refusals");
        finish(helper, player);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void vanillaSwordIsIgnored(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)), 0f, 0f);
        helper.assertTrue(MeleeActions.apply(player, MeleeAction.SLASH) == null, "slash with an iron sword is ignored");
        helper.assertTrue(MeleeActions.apply(player, MeleeAction.GUARD_DOWN) == null, "guard with an iron sword is ignored");
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.IDLE, "phase");
        helper.assertFalse(MeleeService.isActive(player), "no combatant created");
        finish(helper, player);
    }

    /** Own batch: changes config. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_melee_net_off")
    public static void actionsAreIgnoredWithSkillBasedCombatOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, MeleeConfig.SKILL_BASED, false);
        Player player = swordsman(helper, DefaultWeapons.CUTLASS_ID, 4.5, 4.5, -90);
        List<MeleeStateSync.Sent> sent = MeleeStateSync.record(player.getId());
        for (MeleeAction a : MeleeAction.values()) {
            helper.assertTrue(MeleeActions.apply(player, a) == null, a + " ignored");
            act(player, a);
        }
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.IDLE, "phase");
        helper.assertFalse(MeleeService.isActive(player), "no combatant created");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(sent.isEmpty(), "nothing sent, got " + sent);
            finish(helper, player);
        });
    }
}

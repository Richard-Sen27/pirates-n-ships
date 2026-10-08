package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActionPayload;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActions;
import com.richardsenger.piratesnships.combat.melee.net.MeleeStateSync;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * MEL1, the sneak case: sneaking changes nothing in the rules. A sneaking swordsman starts slashes and thrusts,
 * guards and parries through the same payloads, goes through the same phases at the same ticks (what other clients
 * animate) and hits like a standing one; only the client keeps the crouch under the sword pose
 * ({@code client/anim/CrouchPose}). Pillagers without AI stand in for targets, yaw -90 faces +X.
 */
public final class MeleeSneakGameTests {

    private MeleeSneakGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MeleeSneakGameTests.class);
    }

    private static Player swordsman(GameTestHelper helper, double z, boolean sneaking) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.CUTLASS_ID)));
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 1, z)), -90f, 0f);
        player.setYHeadRot(-90f);
        player.yBodyRot = -90f;
        player.setShiftKeyDown(sneaking);
        player.setPose(sneaking ? Pose.CROUCHING : Pose.STANDING);
        return player;
    }

    private static Mob target(GameTestHelper helper, int z) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 3, 1, z);
        mob.setYRot(90);
        mob.setYHeadRot(90);
        mob.yBodyRot = 90;
        return mob;
    }

    private static void act(Player player, MeleeAction action) {
        MeleeActions.handle(new MeleeActionPayload(action, 0), player);
    }

    /** The phases other clients were told about, in order (what their animation layers play). */
    private static List<Phase> observed(List<MeleeStateSync.Sent> sent) {
        return sent.stream().filter(x -> x.target() == MeleeStateSync.Target.OBSERVERS).map(x -> x.payload().phase()).toList();
    }

    private static void finish(GameTestHelper helper, Player... players) {
        for (Player p : players) {
            MeleeStateSync.stopRecording(p.getId());
            p.discard();
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void sneakingSlashRunsTheSamePhasesAndHitsTheSame(GameTestHelper helper) {
        Player standing = swordsman(helper, 2.5, false);
        Player sneaking = swordsman(helper, 6.5, true);
        Mob a = target(helper, 2);
        Mob b = target(helper, 6);
        helper.assertTrue(sneaking.isCrouching(), "the mock player crouches");
        List<MeleeStateSync.Sent> sentStanding = MeleeStateSync.record(standing.getId());
        List<MeleeStateSync.Sent> sentSneaking = MeleeStateSync.record(sneaking.getId());
        act(standing, MeleeAction.SLASH);
        act(sneaking, MeleeAction.SLASH);
        helper.assertValueEqual(MeleeService.state(sneaking).phase(), Phase.WINDUP, "a sneaking player starts the slash");
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(MeleeService.state(sneaking).phase(), Phase.IDLE, "slash over");
            helper.assertValueEqual(observed(sentSneaking), observed(sentStanding), "phases broadcast while sneaking");
            helper.assertValueEqual(observed(sentSneaking), List.of(Phase.WINDUP, Phase.ACTIVE, Phase.RECOVERY, Phase.IDLE),
                    "the slash's phases");
            float dealt = DefaultWeapons.CUTLASS.slash().damage();
            helper.assertTrue(Math.abs(b.getHealth() - a.getHealth()) < 0.01f && a.getMaxHealth() - a.getHealth() > dealt - 0.01f,
                    "same hit while sneaking: standing target " + a.getHealth() + ", sneaking target " + b.getHealth());
            finish(helper, standing, sneaking);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void sneakingPlayerThrustsGuardsAndParries(GameTestHelper helper) {
        Player player = swordsman(helper, 4.5, true);
        act(player, MeleeAction.GUARD_DOWN);
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.GUARDING, "guard while sneaking");
        act(player, MeleeAction.GUARD_UP);
        act(player, MeleeAction.PARRY);
        helper.assertValueEqual(MeleeService.state(player).phase(), Phase.PARRYING, "parry while sneaking");
        helper.runAfterDelay(MeleeConfig.PARRY_WINDOW.get() + MeleeConfig.params().parryLockoutTicks() + 2, () -> {
            act(player, MeleeAction.THRUST);
            helper.assertValueEqual(MeleeService.state(player).phase(), Phase.WINDUP, "thrust while sneaking");
            finish(helper, player);
        });
    }
}

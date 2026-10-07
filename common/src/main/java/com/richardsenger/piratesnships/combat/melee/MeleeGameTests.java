package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResolver;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Melee on real entities in a real server. Pillagers (24 health, no armor, don't burn in daylight) without AI stand
 * in for duelists; yaw -90 faces +X, yaw 90 faces -X.
 */
public final class MeleeGameTests {

    private static final float FULL = 24.0f;

    private MeleeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MeleeGameTests.class);
    }

    private static Mob duelist(GameTestHelper helper, int x, int z, float yaw) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.PILLAGER, x, 1, z);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(0);
        return mob;
    }

    private static void assertHealth(GameTestHelper helper, Mob mob, float expected, String what) {
        helper.assertTrue(Math.abs(mob.getHealth() - expected) < 0.01f, what + ": expected health " + expected + ", got " + mob.getHealth());
    }

    @ModGameTest
    public static void weaponDefinitionsAreLoaded(GameTestHelper helper) {
        var defs = MeleeWeapons.WEAPONS.server();
        DefaultWeapons.all().forEach((id, def) -> {
            helper.assertTrue(defs.contains(id), "weapon " + id + " not loaded, have " + defs.ids());
            helper.assertValueEqual(defs.require(id), def, "weapon " + id);
        });
        helper.assertTrue(MeleeWeapons.forStack(new ItemStack(Items.IRON_SWORD), defs).isEmpty(), "iron sword is no skill weapon");
        helper.assertTrue(MeleeWeapons.forStack(ItemStack.EMPTY, defs).isEmpty(), "empty stack is no weapon");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void slashHitsTargetsInArcAndReachOnly(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob front = duelist(helper, 3, 4, 90);
        Mob side = duelist(helper, 3, 5, 90);
        Mob behind = duelist(helper, 0, 4, 90);
        Mob far = duelist(helper, 7, 4, 90);
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, front, FULL - DefaultWeapons.CUTLASS.slash().damage(), "target in front");
            assertHealth(helper, side, FULL - DefaultWeapons.CUTLASS.slash().damage(), "second target in the arc");
            assertHealth(helper, behind, FULL, "target behind");
            assertHealth(helper, far, FULL, "target out of reach");
            helper.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void thrustHitsOnlyTheFirstTargetInLine(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob first = duelist(helper, 2, 4, 90);
        Mob second = duelist(helper, 3, 4, 90);
        helper.assertTrue(MeleeService.startThrust(attacker, DefaultWeapons.RAPIER).accepted(), "thrust accepted");
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, first, FULL - DefaultWeapons.RAPIER.thrust().damage(), "first target");
            assertHealth(helper, second, FULL, "second target");
            helper.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void timedParryPreventsDamageAndStaggersAttacker(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 3, 4, 90);
        // Cutlass wind-up 5 < parry window 7: the hit lands inside the window
        helper.assertTrue(MeleeService.parry(defender, DefaultWeapons.RAPIER).accepted(), "parry accepted");
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(12, () -> {
            assertHealth(helper, defender, FULL, "parried defender");
            CombatState a = MeleeService.state(attacker);
            helper.assertValueEqual(a.phase(), Phase.STAGGERED, "attacker phase");
            helper.assertTrue(MeleeService.state(defender).riposteReady(), "defender has a riposte window");
            helper.assertFalse(MeleeService.state(defender).lockedOut(), "a successful parry doesn't lock out");
            helper.succeed();
        });
    }

    /** P9: a held guard absorbs the whole frontal hit (it used to only reduce it) and pays the block cost in stamina. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void guardAbsorbsHitAndDrainsStamina(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 3, 4, 90);
        helper.assertTrue(MeleeService.guardDown(defender, DefaultWeapons.RAPIER).accepted(), "guard accepted");
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(15, () -> {
            float dmg = DefaultWeapons.CUTLASS.slash().damage();
            assertHealth(helper, defender, FULL, "guarded defender (P9: no damage)");
            CombatState d = MeleeService.state(defender);
            helper.assertValueEqual(d.phase(), Phase.GUARDING, "defender still guarding");
            float blockCost = HitResolver.guardCost(DefaultWeapons.RAPIER.guard(), dmg, dmg, MeleeConfig.params());
            helper.assertTrue(d.stamina() < MeleeConfig.STAMINA_MAX.get() - blockCost, "stamina drained by the block, have " + d.stamina());
            helper.succeed();
        });
    }

    /** P9: a vanilla mob hit on a guarding sword holder is absorbed and costs stamina. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void guardAbsorbsVanillaMobHit(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 2, 4, 90);
        helper.assertTrue(MeleeService.guardDown(defender, DefaultWeapons.SABER).accepted(), "guard accepted");
        float before = MeleeService.state(defender).stamina();
        attacker.doHurtTarget(defender);
        assertHealth(helper, defender, FULL, "vanilla hit absorbed by the guard");
        CombatState d = MeleeService.state(defender);
        helper.assertValueEqual(d.phase(), Phase.GUARDING, "still guarding");
        helper.assertTrue(d.stamina() < before, "block cost paid, stamina " + before + " -> " + d.stamina());
        helper.succeed();
    }

    // --- P9: a mock player guarding against a pirate ----------------------------------------------------------

    /** A pirate without AI (driven through {@link MeleeService}) at (1, 4) facing +X. Looked up by id: no mob import. */
    @SuppressWarnings("unchecked")
    private static Mob pirate(GameTestHelper helper) {
        EntityType<? extends Mob> type = (EntityType<? extends Mob>) BuiltInRegistries.ENTITY_TYPE.get(Constants.id("pirate"));
        Mob mob = helper.spawnWithNoFreeWill(type, 1, 1, 4);
        mob.setYRot(-90);
        mob.setYHeadRot(-90);
        mob.yBodyRot = -90;
        mob.setXRot(0);
        return mob;
    }

    /**
     * A survival mock player in the level (the engine finds its targets in the level) at (3.5, 4.5) facing -X, holding
     * a cutlass. No natural regeneration (food 17, no saturation), so health only changes by hits. Removed by
     * {@link #finish} or, on a failed check, by {@link #checked}.
     */
    private static Player guardingPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.CUTLASS_ID)));
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 1, 4.5));
        player.moveTo(at.x, at.y, at.z, 90f, 0f);
        player.setYHeadRot(90f);
        player.yBodyRot = 90f;
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0f);
        helper.getLevel().addFreshEntity(player);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void finish(GameTestHelper helper, Player player) {
        player.discard();
        helper.succeed();
    }

    /** Runs {@code checks}; on a failed assertion the mock player is removed before the failure propagates. */
    private static void checked(Player player, Runnable checks) {
        try {
            checks.run();
        } catch (RuntimeException e) {
            player.discard();
            throw e;
        }
    }

    /** Vanilla difficulty scaling of a mob hit on a player ({@code Player#hurt}). */
    private static float scaledForPlayer(GameTestHelper helper, float dmg) {
        return switch (helper.getLevel().getDifficulty()) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(dmg / 2f + 1f, dmg);
            case NORMAL -> dmg;
            case HARD -> dmg * 1.5f;
        };
    }

    /** P9: a player guarding with a cutlass takes nothing from a pirate's frontal slash and pays stamina for it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void playerGuardAbsorbsPirateSlash(GameTestHelper helper) {
        Mob pirate = pirate(helper);
        Player player = guardingPlayer(helper);
        checked(player, () -> {
            helper.assertTrue(MeleeService.guardDown(player, DefaultWeapons.CUTLASS).accepted(), "guard accepted");
            helper.assertTrue(MeleeService.startSlash(pirate, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        });
        float full = player.getHealth();
        helper.runAfterDelay(15, () -> checked(player, () -> {
            float dmg = DefaultWeapons.CUTLASS.slash().damage();
            helper.assertTrue(player.getHealth() == full, "guarded player took damage: " + full + " -> " + player.getHealth());
            CombatState s = MeleeService.state(player);
            helper.assertValueEqual(s.phase(), Phase.GUARDING, "player still guarding");
            float blockCost = HitResolver.guardCost(DefaultWeapons.CUTLASS.guard(), dmg, dmg, MeleeConfig.params());
            helper.assertTrue(s.stamina() < MeleeConfig.STAMINA_MAX.get() - blockCost,
                    "stamina drained by the block cost " + blockCost + " (with absorb surcharge), have " + s.stamina());
            finish(helper, player);
        }));
    }

    /**
     * P9: the pirate slashes into the held guard again and again. Every blocked hit is absorbed and drains the block
     * cost (weapon cost plus the absorb surcharge); the first hit the stamina can't pay breaks the guard: it lands with
     * the cutlass guard's reduction and the player staggers. The pirate times its last slash so that hit arrives while
     * the guard still holds (stamina above zero but below the block cost).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 400)
    public static void drainedGuardBreaksAndTheHitLands(GameTestHelper helper) {
        Mob pirate = pirate(helper);
        Player player = guardingPlayer(helper);
        checked(player, () -> helper.assertTrue(MeleeService.guardDown(player, DefaultWeapons.CUTLASS).accepted(), "guard accepted"));
        float dmg = DefaultWeapons.CUTLASS.slash().damage();
        float reduced = (float) (dmg * (1 - DefaultWeapons.CUTLASS.guard().damageReduction()));
        float blockCost = HitResolver.guardCost(DefaultWeapons.CUTLASS.guard(), dmg, dmg, MeleeConfig.params());
        float[] health = {player.getHealth()};
        float[] stamina = {MeleeService.state(player).stamina()};
        int[] absorbed = {0};
        int[] ticks = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            checked(player, () -> {
                helper.assertTrue(++ticks[0] <= 350, "guard never broke; absorbed " + absorbed[0] + " hits, stamina "
                        + MeleeService.state(player).stamina());
                CombatState s = MeleeService.state(player);
                if (player.getHealth() < health[0]) {
                    done[0] = true;
                    helper.assertTrue(absorbed[0] >= 1, "the first hits should have been absorbed");
                    helper.assertValueEqual(s.phase(), Phase.STAGGERED, "guard break staggers");
                    helper.assertValueEqual(s.stamina(), 0f, "guard break empties the stamina");
                    float expected = scaledForPlayer(helper, reduced);
                    float took = health[0] - player.getHealth();
                    helper.assertTrue(Math.abs(took - expected) < 0.01f, "guard break deals the reduced " + expected + ", took " + took);
                    finish(helper, player);
                    return;
                }
                helper.assertValueEqual(s.phase(), Phase.GUARDING, "guard dropped without a hit landing");
                float spent = stamina[0] - s.stamina();
                if (spent > 5f) { // a blocked hit, not the guard's per-tick drain
                    absorbed[0]++;
                    helper.assertTrue(spent >= blockCost - 0.01f, "blocked hit cost " + spent + ", expected at least " + blockCost);
                }
                stamina[0] = s.stamina();
                // Hold the next slash while the guard could just pay for it but would be left too low to last until the
                // following hit: the hold drain would then empty the stamina and drop the guard before a hit breaks it.
                // Holding until the stamina is below the block cost makes the next hit the guard break, whatever the tuning.
                boolean marginal = s.stamina() >= blockCost && s.stamina() < blockCost + 15f;
                if (!marginal && MeleeService.state(pirate).phase() == Phase.IDLE) MeleeService.startSlash(pirate, DefaultWeapons.CUTLASS);
            });
        });
    }

    /** Own batch: with {@code melee.guard_absorbs_all} off a held guard only takes off the weapon's reduction. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_melee_guard_absorbs_all")
    public static void guardAbsorbsAllOffOnlyReduces(GameTestHelper helper) {
        ConfigOverrides.during(helper, MeleeConfig.GUARD_ABSORBS_ALL, false);
        Mob pirate = pirate(helper);
        Player player = guardingPlayer(helper);
        checked(player, () -> {
            helper.assertTrue(MeleeService.guardDown(player, DefaultWeapons.CUTLASS).accepted(), "guard accepted");
            helper.assertTrue(MeleeService.startSlash(pirate, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        });
        float full = player.getHealth();
        helper.runAfterDelay(15, () -> checked(player, () -> {
            float dmg = DefaultWeapons.CUTLASS.slash().damage();
            float expected = scaledForPlayer(helper, (float) (dmg * (1 - DefaultWeapons.CUTLASS.guard().damageReduction())));
            float took = full - player.getHealth();
            helper.assertTrue(Math.abs(took - expected) < 0.01f, "reduced damage " + expected + " expected, took " + took);
            helper.assertValueEqual(MeleeService.state(player).phase(), Phase.GUARDING, "player still guarding");
            finish(helper, player);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void parryCancelsVanillaMobHit(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 2, 4, 90);
        helper.assertTrue(MeleeService.parry(defender, DefaultWeapons.SABER).accepted(), "parry accepted");
        attacker.doHurtTarget(defender);
        assertHealth(helper, defender, FULL, "vanilla hit parried");
        helper.assertValueEqual(MeleeService.state(attacker).phase(), Phase.STAGGERED, "vanilla attacker staggered");
        helper.assertTrue(MeleeService.state(defender).riposteReady(), "riposte window after parrying a mob");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void projectilesCannotBeParried(GameTestHelper helper) {
        Mob shooter = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 3, 4, 90);
        helper.assertTrue(MeleeService.parry(defender, DefaultWeapons.SABER).accepted(), "parry accepted");
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        helper.assertTrue(arrow != null, "arrow created");
        arrow.setPos(shooter.getEyePosition());
        defender.hurt(helper.getLevel().damageSources().arrow(arrow, shooter), 4.0f);
        assertHealth(helper, defender, FULL - 4.0f, "arrow ignores the parry");
        helper.assertValueEqual(MeleeService.state(defender).phase(), Phase.PARRYING, "parry still open");
        arrow.discard();
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_melee_skill_based")
    public static void skillBasedOffLeavesVanillaMelee(GameTestHelper helper) {
        ConfigOverrides.during(helper, MeleeConfig.SKILL_BASED, false);
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 2, 4, 90);
        helper.assertValueEqual(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).refusal(), Refusal.DISABLED, "slash refusal");
        helper.assertValueEqual(MeleeService.parry(defender, DefaultWeapons.SABER).refusal(), Refusal.DISABLED, "parry refusal");
        float before = defender.getHealth();
        attacker.doHurtTarget(defender);
        helper.assertTrue(defender.getHealth() < before, "vanilla hit lands with skill-based combat off");
        helper.succeed();
    }
}

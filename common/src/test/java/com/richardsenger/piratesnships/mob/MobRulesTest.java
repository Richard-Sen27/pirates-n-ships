package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import com.richardsenger.piratesnships.combat.melee.npc.SkillTier;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.mob.client.DuelistArmPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skill tiers per mob type, the musket decision, the synced melee pose and the telegraph arm pose. */
class MobRulesTest {

    // --- skill tiers ----------------------------------------------------------------------------------------------

    @Test
    void duelistTypesAndTheirDefaultSkills() {
        assertEquals(DuelistSkill.PIRATE, MobKind.PIRATE.defaultSkill());
        assertEquals(DuelistSkill.NAVY_OFFICER, MobKind.NAVY_OFFICER.defaultSkill());
        assertFalse(MobKind.NAVY_SOLDIER.duelist(), "the soldier fights with a musket");
        assertFalse(MobKind.SAILOR.duelist());
        assertEquals(MobFaction.NAVY, MobKind.NAVY_SOLDIER.faction());
        assertEquals(MobFaction.CIVILIAN, MobKind.SAILOR.faction());
    }

    @Test
    void configuredSkillsUseTheDefaultsAndTheNpcMultiplier() {
        assertEquals(DuelistSkill.PIRATE, MobConfig.skill(MobKind.PIRATE)); // unbound config = default
        assertEquals(DuelistSkill.NAVY_OFFICER, MobConfig.skill(MobKind.NAVY_OFFICER));
        SkillTier pirate = MobConfig.skill(MobKind.PIRATE).tier(1.0);
        SkillTier officer = MobConfig.skill(MobKind.NAVY_OFFICER).tier(1.0);
        assertTrue(officer.parryChance() > pirate.parryChance(), "officers parry more often");
        assertTrue(officer.reactionTicks() < pirate.reactionTicks(), "officers react faster");
        SkillTier doubled = MobConfig.skill(MobKind.PIRATE).tier(2.0);
        assertEquals(Math.min(1.0, pirate.parryChance() * 2), doubled.parryChance(), 1e-9);
        assertEquals(3, doubled.reactionTicks());
    }

    @Test
    void kindsRoundTripTheirIds() {
        for (MobKind k : MobKind.values()) assertEquals(k, MobKind.byId(k.id()).orElseThrow());
        assertEquals("navy_soldier", MobKind.NAVY_SOLDIER.id());
        assertTrue(MobKind.byId("kraken").isEmpty());
    }

    // --- muskets --------------------------------------------------------------------------------------------------

    private static final MusketRules.Params MUSKET = new MusketRules.Params(20, 4, 1.8, 20);

    @Test
    void musketeerAimsThenFires() {
        MusketRules.Decision aiming = MusketRules.decide(10, true, true, 5, true, MUSKET);
        assertEquals(MusketRules.Move.HOLD, aiming.move());
        assertTrue(aiming.aim());
        assertFalse(aiming.fire());
        assertTrue(MusketRules.decide(10, true, true, 20, true, MUSKET).fire());
    }

    @Test
    void musketeerKeepsItsDistance() {
        assertEquals(MusketRules.Move.APPROACH, MusketRules.decide(30, true, true, 0, true, MUSKET).move());
        assertEquals(MusketRules.Move.APPROACH, MusketRules.decide(10, false, true, 0, true, MUSKET).move(), "no line of sight");
        MusketRules.Decision close = MusketRules.decide(3, true, true, 30, true, MUSKET);
        assertEquals(MusketRules.Move.BACK_OFF, close.move());
        assertFalse(close.fire(), "no point-blank shots");
    }

    @Test
    void musketeerShovesAtArmsLengthAndWaitsForTheReload() {
        MusketRules.Decision shove = MusketRules.decide(1.5, true, true, 30, true, MUSKET);
        assertTrue(shove.shove());
        assertFalse(MusketRules.decide(1.5, true, true, 30, false, MUSKET).shove(), "shove on cooldown");
        MusketRules.Decision reloading = MusketRules.decide(10, true, false, 30, true, MUSKET);
        assertFalse(reloading.fire());
        assertFalse(reloading.aim());
    }

    // --- synced pose and telegraph ------------------------------------------------------------------------------------

    @Test
    void meleePoseRoundTrips() {
        for (Phase phase : Phase.values()) {
            for (AttackKind attack : new AttackKind[]{null, AttackKind.SLASH, AttackKind.THRUST}) {
                MeleePose pose = new MeleePose(phase, attack, attack != null, 37);
                assertEquals(pose, MeleePose.unpack(pose.pack()));
            }
        }
        assertEquals(MeleePose.IDLE, MeleePose.unpack(MeleePose.IDLE.pack()));
        MeleePose feint = new MeleePose(Phase.RECOVERY, AttackKind.SLASH, false, 6, true);
        assertEquals(feint, MeleePose.unpack(feint.pack()), "the feint bit survives packing");
    }

    @Test
    void telegraphRaisesTheArmBeforeTheSlash() {
        assertNull(DuelistArmPose.of(MeleePose.IDLE, 1f), "idle leaves the animations alone");
        DuelistArmPose.Angles cocked = DuelistArmPose.of(new MeleePose(Phase.WINDUP, AttackKind.SLASH, false, 5), 1f);
        DuelistArmPose.Angles struck = DuelistArmPose.of(new MeleePose(Phase.ACTIVE, AttackKind.SLASH, false, 3), 1f);
        assertNotNull(cocked);
        assertNotNull(struck);
        assertTrue(cocked.armX() < -120, "slash wind-up: arm raised high, " + cocked);
        assertTrue(struck.armX() > cocked.armX(), "the swing brings the arm down");
        DuelistArmPose.Angles drawn = DuelistArmPose.of(new MeleePose(Phase.WINDUP, AttackKind.THRUST, false, 8), 1f);
        DuelistArmPose.Angles lunge = DuelistArmPose.of(new MeleePose(Phase.ACTIVE, AttackKind.THRUST, false, 3), 1f);
        assertTrue(drawn.armX() > 0 && lunge.armX() < -60, "thrust: drawn back, then straight forward");
        DuelistArmPose.Angles half = DuelistArmPose.of(new MeleePose(Phase.WINDUP, AttackKind.SLASH, false, 5), 0.5f);
        assertTrue(half.armX() > cocked.armX() && half.armX() < -30, "wind-up progresses");
    }
}

package com.richardsenger.piratesnships.mob;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.mob.HostilityRules.Target;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostilityRulesTest {

    private static final HostilityRules.Params DEFAULTS = new HostilityRules.Params(true, true, true, false);
    private static final Target INNOCENT = Target.ofPlayer(false, false);
    private static final Target WANTED = Target.ofPlayer(false, true);
    private static final Target CREATIVE = Target.ofPlayer(true, true);

    @Test
    void piratesAttackPlayersAndNavyByDefault() {
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, INNOCENT, DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMob(MobFaction.NAVY), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMob(MobFaction.PIRATE), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMob(MobFaction.CIVILIAN), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMonster(), DEFAULTS));
    }

    @Test
    void piratesHostileOffSparesPlayersButNotNavy() {
        var p = new HostilityRules.Params(false, true, true, false);
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, INNOCENT, p));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMob(MobFaction.NAVY), p));
    }

    @Test
    void navyAttacksWantedPlayersAndPiratesOnly() {
        assertTrue(HostilityRules.attacksOnSight(MobFaction.NAVY, WANTED, DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, INNOCENT, DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.NAVY, Target.ofMob(MobFaction.PIRATE), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, Target.ofMob(MobFaction.NAVY), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, Target.ofMob(MobFaction.CIVILIAN), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, WANTED, new HostilityRules.Params(true, false, true, false)),
                "navy_hostile off spares wanted players");
    }

    @Test
    void factionsFightOffKeepsThemApart() {
        var p = new HostilityRules.Params(true, true, false, false);
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, Target.ofMob(MobFaction.PIRATE), p));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, Target.ofMob(MobFaction.NAVY), p));
    }

    @Test
    void creativePlayersAndPeacefulMobsNeverFight() {
        for (MobFaction f : MobFaction.values()) {
            assertFalse(HostilityRules.attacksOnSight(f, CREATIVE, DEFAULTS), f + " attacks a creative player");
            assertFalse(HostilityRules.retaliates(f, CREATIVE, DEFAULTS));
        }
        var peaceful = new HostilityRules.Params(true, true, true, true);
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, INNOCENT, peaceful));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, WANTED, peaceful));
        assertFalse(HostilityRules.retaliates(MobFaction.PIRATE, INNOCENT, peaceful));
    }

    @Test
    void sailorsNeverAttackOrRetaliate() {
        assertFalse(HostilityRules.attacksOnSight(MobFaction.CIVILIAN, WANTED, DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.CIVILIAN, Target.ofMonster(), DEFAULTS));
        assertFalse(HostilityRules.retaliates(MobFaction.CIVILIAN, INNOCENT, DEFAULTS));
    }

    @Test
    void grudgeKeepsATargetTheMobWouldNotPick() {
        var passivePirates = new HostilityRules.Params(false, true, true, false);
        assertFalse(HostilityRules.keepsTarget(MobFaction.PIRATE, INNOCENT, passivePirates, false));
        assertTrue(HostilityRules.keepsTarget(MobFaction.PIRATE, INNOCENT, passivePirates, true));
        assertTrue(HostilityRules.keepsTarget(MobFaction.NAVY, INNOCENT, DEFAULTS, true), "navy hits back at an innocent attacker");
        assertFalse(HostilityRules.keepsTarget(MobFaction.NAVY, Target.ofMob(MobFaction.NAVY), DEFAULTS, true), "no feuds inside a faction");
        assertTrue(HostilityRules.keepsTarget(MobFaction.NAVY, WANTED, DEFAULTS, false));
    }

    @Test
    void sailorsFleeFromThreats() {
        assertTrue(HostilityRules.flees(MobFaction.CIVILIAN, Target.ofMonster(), false, true));
        assertTrue(HostilityRules.flees(MobFaction.CIVILIAN, Target.ofMob(MobFaction.PIRATE), false, true));
        assertFalse(HostilityRules.flees(MobFaction.CIVILIAN, Target.ofMob(MobFaction.PIRATE), false, false));
        assertFalse(HostilityRules.flees(MobFaction.CIVILIAN, Target.ofMob(MobFaction.NAVY), false, true));
        assertTrue(HostilityRules.flees(MobFaction.CIVILIAN, Target.ofMob(MobFaction.NAVY), true, true), "anyone targeting it");
        assertFalse(HostilityRules.flees(MobFaction.CIVILIAN, INNOCENT, false, true));
        assertFalse(HostilityRules.flees(MobFaction.PIRATE, Target.ofMonster(), true, true), "only sailors flee");
    }
}

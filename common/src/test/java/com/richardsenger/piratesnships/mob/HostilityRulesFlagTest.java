package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.law.flag.ShipStance;
import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.mob.HostilityRules.Target;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FL2: what the flag of the ship a player or crew member is aboard changes in the hostility rules. */
class HostilityRulesFlagTest {

    private static final HostilityRules.Params DEFAULTS = new HostilityRules.Params(true, true, true, false);

    private static Target player(boolean wanted, ShipStance ship) {
        return Target.ofPlayer(false, wanted, ship);
    }

    /** A crew member (no faction, not a player) aboard a ship. */
    private static Target crew(ShipStance ship) {
        return new Target(null, false, false, false, false, ship);
    }

    @Test
    void navyAttacksPeopleAboardAJollyRogerShipOrAnUnmaskedOne() {
        assertTrue(HostilityRules.attacksOnSight(MobFaction.NAVY, player(false, ShipStance.JOLLY_ROGER), DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.NAVY, player(false, ShipStance.UNMASKED), DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.NAVY, crew(ShipStance.JOLLY_ROGER), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, player(false, ShipStance.NONE), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, crew(ShipStance.NONE), DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, player(false, ShipStance.JOLLY_ROGER),
                new HostilityRules.Params(true, false, true, false)), "navy_hostile off");
    }

    @Test
    void piratesSparePlayersAboardAJollyRogerShipButFightBack() {
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, player(false, ShipStance.JOLLY_ROGER), DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, player(false, ShipStance.NONE), DEFAULTS));
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, player(false, ShipStance.UNMASKED), DEFAULTS));
        assertTrue(HostilityRules.keepsTarget(MobFaction.PIRATE, player(false, ShipStance.JOLLY_ROGER), DEFAULTS, true),
                "attacked, a pirate fights back");
    }

    @Test
    void nobodyAttacksASurrenderedCrewOnSightEvenAWantedOne() {
        Target wanted = player(true, ShipStance.SURRENDERED);
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, wanted, DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, wanted, DEFAULTS));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, crew(ShipStance.SURRENDERED), DEFAULTS));
        assertFalse(HostilityRules.keepsTarget(MobFaction.NAVY, wanted, DEFAULTS, false), "a target that surrenders is dropped");
        assertTrue(HostilityRules.keepsTarget(MobFaction.NAVY, wanted, DEFAULTS, true), "a surrendered crew that keeps fighting is fought");
    }

    @Test
    void creativePlayersStayExemptWhateverTheFlag() {
        Target creative = new Target(null, true, true, true, false, ShipStance.JOLLY_ROGER);
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, creative, DEFAULTS));
    }
}

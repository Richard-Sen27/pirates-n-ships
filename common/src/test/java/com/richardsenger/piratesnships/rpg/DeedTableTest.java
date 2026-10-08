package com.richardsenger.piratesnships.rpg;

import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.mob.HostilityRules;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.rpg.deeds.CombatDeeds;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedTable;
import com.richardsenger.piratesnships.rpg.deeds.LawDeeds;
import com.richardsenger.piratesnships.rpg.market.MarketReputation;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The deed table and its config, the deed sources' pure mappings and the pirates' liking rule. */
class DeedTableTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultsMatchTheDecision() {
        DeedTable t = DeedTable.defaults();
        assertEquals(-15, t.delta(Deed.KILL_NAVY, Faction.NAVY));
        assertEquals(5, t.delta(Deed.KILL_NAVY, Faction.PIRATES));
        assertEquals(5, t.delta(Deed.KILL_PIRATE, Faction.NAVY));
        assertEquals(-15, t.delta(Deed.KILL_PIRATE, Faction.PIRATES));
        assertEquals(-25, t.delta(Deed.KILL_VILLAGER, Faction.VILLAGERS));
        assertEquals(-10, t.delta(Deed.KILL_VILLAGER, Faction.NAVY));
        assertEquals(Map.of(Faction.PIRATES, 8, Faction.NAVY, -8, Faction.VILLAGERS, -5), t.row(Deed.PLUNDER_MERCHANT));
        assertEquals(Map.of(Faction.PIRATES, 3), t.row(Deed.FENCE_PLUNDER));
        assertEquals(Map.of(Faction.VILLAGERS, 1), t.row(Deed.TRADE_VILLAGE));
        assertEquals(6, t.delta(Deed.TURN_IN_PIRATE, Faction.NAVY));
        assertEquals(Map.of(Faction.NAVY, 3), t.row(Deed.PAY_FINE));
        assertEquals(Map.of(Faction.NAVY, -10), t.row(Deed.FLY_FALSE_COLOURS));
    }

    @Test
    void readFillsGapsWithDefaultsAndClamps() {
        DeedTable t = DeedTable.read((d, f) -> {
            if (d == Deed.KILL_NAVY && f == Faction.NAVY) return -500;
            if (d == Deed.PAY_FINE && f == Faction.NAVY) return 7;
            return null;
        });
        assertEquals(-DeedTable.LIMIT, t.delta(Deed.KILL_NAVY, Faction.NAVY), "clamped");
        assertEquals(7, t.delta(Deed.PAY_FINE, Faction.NAVY));
        assertEquals(Deed.KILL_PIRATE.defaultDelta(Faction.PIRATES), t.delta(Deed.KILL_PIRATE, Faction.PIRATES), "default");
        assertTrue(t.row(Deed.FENCE_PLUNDER).keySet().stream().noneMatch(f -> t.delta(Deed.FENCE_PLUNDER, f) == 0),
                "rows skip zero deltas");
    }

    @Test
    void everyDeedAndFactionHasAConfigValueWithItsDefault() {
        for (Deed d : Deed.values()) {
            for (Faction f : Faction.values()) {
                ConfigValue<Integer> v = ReputationConfig.DEED_DELTAS.get(d).get(f);
                assertEquals(List.of("reputation", "deeds", d.id(), f.id()), v.path());
                assertEquals(d.defaultDelta(f), v.defaultValue());
            }
        }
        assertEquals(DeedTable.defaults(), ReputationConfig.deedTable(), "the config table without a loader is the default table");
        assertEquals(List.of("reputation", "price_swing"), ReputationConfig.PRICE_SWING.path());
        assertEquals(0.10, ReputationConfig.PRICE_SWING.defaultValue());
        assertEquals(40, ReputationConfig.PIRATE_FRIENDLY_THRESHOLD.defaultValue());
        assertEquals(-60, ReputationConfig.NAVY_HOSTILE_THRESHOLD.defaultValue());
        assertEquals(-60, ReputationConfig.VILLAGER_TRADE_THRESHOLD.defaultValue());
    }

    @Test
    void combatDeedsByVictimSide() {
        assertEquals(Deed.KILL_NAVY, CombatDeeds.deedFor(CombatDeeds.Side.NAVY, true));
        assertEquals(Deed.ATTACK_PIRATE, CombatDeeds.deedFor(CombatDeeds.Side.PIRATE, false));
        assertEquals(Deed.KILL_VILLAGER, CombatDeeds.deedFor(CombatDeeds.Side.VILLAGER, true));
        assertEquals(Optional.of(CombatDeeds.Side.NAVY), CombatDeeds.side(true, true, true), "navy first");
        assertEquals(Optional.of(CombatDeeds.Side.PIRATE), CombatDeeds.side(false, true, true));
        assertEquals(Optional.empty(), CombatDeeds.side(false, false, false));
        assertTrue(CombatDeeds.newAttack(null, 100, 600));
        assertFalse(CombatDeeds.newAttack(100L, 699, 600));
        assertTrue(CombatDeeds.newAttack(100L, 700, 600));
        assertTrue(CombatDeeds.newAttack(100L, 50, 600), "a clock running backwards starts over");
    }

    @Test
    void crimesThatAreDeeds() {
        assertEquals(Optional.of(Deed.FLY_FALSE_COLOURS), LawDeeds.deedForCrime(CrimeType.CAUGHT_FALSE_COLORS));
        assertEquals(Optional.of(Deed.PLUNDER_MERCHANT), LawDeeds.deedForCrime(CrimeType.PIRACY));
        assertEquals(Optional.of(Deed.ATTACK_MERCHANT_SHIP), LawDeeds.deedForCrime(CrimeType.ATTACK_NEUTRAL_SHIP));
        // Combat crimes come from CombatDeeds (pirates included), not from the crime hook: no double counting
        assertEquals(Optional.empty(), LawDeeds.deedForCrime(CrimeType.KILL_NAVY));
        assertEquals(Optional.empty(), LawDeeds.deedForCrime(CrimeType.ATTACK_VILLAGER));
        assertEquals(Optional.empty(), LawDeeds.deedForCrime(CrimeType.FENCE_PLUNDER));
    }

    @Test
    void priceFactionPerPortKind() {
        assertEquals(Optional.of(Faction.VILLAGERS), MarketReputation.priceFaction(PortKind.SEAFARER_VILLAGE));
        assertEquals(Optional.of(Faction.PIRATES), MarketReputation.priceFaction(PortKind.PIRATE_ISLAND));
        assertEquals(Optional.empty(), MarketReputation.priceFaction(PortKind.NAVY_OUTPOST));
    }

    @Test
    void piratesLeaveALikedPlayerAloneButFightBack() {
        HostilityRules.Params p = new HostilityRules.Params(true, true, true, false);
        HostilityRules.Target stranger = HostilityRules.Target.ofPlayer(false, false);
        HostilityRules.Target friend = stranger.withLikedByPirates(true);
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, stranger, p));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, friend, p));
        assertTrue(HostilityRules.retaliates(MobFaction.PIRATE, friend, p), "a liked player who hits a pirate is fought");
        assertTrue(HostilityRules.keepsTarget(MobFaction.PIRATE, friend, p, true), "kept with a grudge");
        assertFalse(HostilityRules.keepsTarget(MobFaction.PIRATE, friend, p, false), "dropped without one");
        assertFalse(HostilityRules.attacksOnSight(MobFaction.NAVY, friend, p), "the navy ignores the pirates' liking");
        assertTrue(friend.withShip(com.richardsenger.piratesnships.law.flag.ShipStance.NONE).likedByPirates(), "withShip keeps it");
    }
}

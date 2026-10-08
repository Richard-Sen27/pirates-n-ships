package com.richardsenger.piratesnships.worldsim.faction;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure faction rules and the state codec of WS1 (design.md §10.4). */
class FactionRulesTest {

    private static final double EPS = 1e-9;
    private static final long MAX = 1_000_000L;

    @Test
    void initialStateHasEveryValue() {
        FactionState s = FactionState.INITIAL;
        for (Faction f : Faction.values()) {
            assertEquals(FactionState.REST_AGGRESSION, s.aggression(f), EPS);
            assertEquals(FactionState.defaultWealth(f), s.wealth(f));
        }
        assertEquals(0.2, s.tension(Faction.PIRATES, Faction.NAVY), EPS);
        assertEquals(0.0, s.tension(Faction.NAVY, Faction.NAVY), EPS);
        assertEquals(0.0, s.tension(Faction.MERCHANTS, Faction.NAVY), EPS);
    }

    @Test
    void pairIsUnordered() {
        assertEquals(FactionPair.NAVY_PIRATES, FactionPair.of(Faction.PIRATES, Faction.NAVY));
        assertEquals(FactionPair.PIRATES_MERCHANTS, FactionPair.of(Faction.MERCHANTS, Faction.PIRATES));
        assertThrows(IllegalArgumentException.class, () -> FactionPair.of(Faction.NAVY, Faction.NAVY));
    }

    @Test
    void eventAppliesItsShiftsTimesStrength() {
        FactionState s = FactionRules.apply(FactionState.INITIAL, FactionEvent.PIRATE_KILLED_BY_NAVY, 1.0, MAX);
        assertEquals(0.25, s.tension(FactionPair.NAVY_PIRATES), EPS);
        assertEquals(0.32, s.aggression(Faction.PIRATES), EPS);
        assertEquals(0.31, s.aggression(Faction.NAVY), EPS);
        FactionState half = FactionRules.apply(FactionState.INITIAL, FactionEvent.PIRATE_KILLED_BY_NAVY, 0.5, MAX);
        assertEquals(0.225, half.tension(FactionPair.NAVY_PIRATES), EPS);
    }

    @Test
    void wealthMovesAndIsClamped() {
        FactionState s = FactionRules.apply(FactionState.INITIAL, FactionEvent.MERCHANT_PLUNDERED, 1.0, MAX);
        assertEquals(9_800L, s.wealth(Faction.MERCHANTS));
        assertEquals(2_200L, s.wealth(Faction.PIRATES));
        FactionState poor = FactionState.INITIAL.withWealth(Faction.MERCHANTS, 50);
        assertEquals(0L, FactionRules.apply(poor, FactionEvent.CONVOY_SUNK, 1.0, MAX).wealth(Faction.MERCHANTS));
        FactionState rich = FactionState.INITIAL.withWealth(Faction.MERCHANTS, 9_990);
        assertEquals(10_000L, FactionRules.apply(rich, FactionEvent.CONVOY_DELIVERED, 1.0, 10_000).wealth(Faction.MERCHANTS));
    }

    @Test
    void valuesStayWithinZeroAndOne() {
        FactionState s = FactionState.INITIAL;
        for (int i = 0; i < 100; i++) s = FactionRules.apply(s, FactionEvent.RAID_SUCCEEDED, 1.0, MAX);
        assertEquals(1.0, s.tension(FactionPair.NAVY_PIRATES), EPS);
        assertEquals(1.0, s.aggression(Faction.PIRATES), EPS);
        for (int i = 0; i < 100; i++) s = FactionRules.apply(s, FactionEvent.RAID_REPELLED, 1.0, MAX);
        assertEquals(0.0, s.aggression(Faction.PIRATES), EPS);
        FactionState clamped = new FactionState(Map.of(Faction.NAVY, 7.0), Map.of(Faction.NAVY, -5L),
                Map.of(FactionPair.NAVY_PIRATES, -1.0));
        assertEquals(1.0, clamped.aggression(Faction.NAVY), EPS);
        assertEquals(0L, clamped.wealth(Faction.NAVY));
        assertEquals(0.0, clamped.tension(FactionPair.NAVY_PIRATES), EPS);
    }

    @Test
    void zeroStrengthChangesNothing() {
        assertSame(FactionState.INITIAL, FactionRules.apply(FactionState.INITIAL, FactionEvent.PATROL_LOST, 0.0, MAX));
    }

    @Test
    void decayMovesTowardRestWithoutOvershoot() {
        FactionState s = FactionState.INITIAL.withAggression(Faction.NAVY, 0.8).withAggression(Faction.PIRATES, 0.28)
                .withTension(FactionPair.NAVY_PIRATES, 0.5).withWealth(Faction.PIRATES, 1234);
        FactionState d = FactionRules.decay(s, 0.05, 1);
        assertEquals(0.75, d.aggression(Faction.NAVY), EPS);
        assertEquals(0.3, d.aggression(Faction.PIRATES), EPS);
        assertEquals(0.3, d.aggression(Faction.MERCHANTS), EPS);
        assertEquals(0.45, d.tension(FactionPair.NAVY_PIRATES), EPS);
        assertEquals(1234L, d.wealth(Faction.PIRATES));
        FactionState three = FactionRules.decay(s, 0.05, 3);
        assertEquals(0.65, three.aggression(Faction.NAVY), EPS);
        FactionState many = FactionRules.decay(s, 0.05, 1000);
        assertEquals(0.3, many.aggression(Faction.NAVY), EPS);
        assertEquals(0.0, many.tension(FactionPair.NAVY_PIRATES), EPS);
        assertSame(s, FactionRules.decay(s, 0.0, 5));
        assertSame(s, FactionRules.decay(s, 0.05, 0));
    }

    @Test
    void stateSurvivesTheCodec() {
        FactionState s = FactionRules.apply(FactionState.INITIAL, FactionEvent.MERCHANT_PLUNDERED, 1.0, MAX)
                .withWealth(Faction.NAVY, 5_000_000_000L);
        Tag nbt = FactionState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        assertEquals(s, FactionState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        assertEquals(s, FactionState.CODEC.parse(JsonOps.INSTANCE,
                FactionState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow()).getOrThrow());
        assertEquals(FactionState.INITIAL, FactionState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow(),
                "an empty save reads as the start state");
    }

    @Test
    void savedDataRoundTrip() {
        FactionData d = new FactionData();
        d.setState(FactionState.INITIAL.withTension(FactionPair.PIRATES_MERCHANTS, 0.6));
        d.setLastDay(42);
        CompoundTag tag = d.save(new CompoundTag(), null);
        FactionData back = FactionData.load(tag, null);
        assertEquals(d.state(), back.state());
        assertEquals(42L, back.lastDay());
        assertTrue(d.isDirty());
    }
}

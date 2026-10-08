package com.richardsenger.piratesnships.rpg;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRecord;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure reputation arithmetic: clamping, decay, the shown score, the price swing and the record. */
class ReputationRulesTest {

    private static final long DAY = ReputationRules.TICKS_PER_DAY;

    @Test
    void scoresAreClampedToPlusMinusHundred() {
        assertEquals(100.0, ReputationRules.clamp(250));
        assertEquals(-100.0, ReputationRules.clamp(-101));
        assertEquals(42.5, ReputationRules.clamp(42.5));
        assertEquals(0.0, ReputationRules.clamp(Double.NaN));
        ReputationRecord r = ReputationRecord.EMPTY.plus(Faction.NAVY, 80).plus(Faction.NAVY, 80);
        assertEquals(100.0, r.navy());
        assertEquals(-100.0, r.plus(Faction.PIRATES, -500).pirates());
        assertEquals(-100.0, new ReputationRecord(0, 0, -1e9, 0, -1, 0).villagers());
    }

    @Test
    void decayMovesTowardZeroAndStopsThere() {
        assertEquals(8.0, ReputationRules.decay(10, DAY, 2.0), 1e-9);
        assertEquals(-8.0, ReputationRules.decay(-10, DAY, 2.0), 1e-9);
        assertEquals(9.0, ReputationRules.decay(10, DAY / 2, 2.0), 1e-9);
        assertEquals(0.0, ReputationRules.decay(3, 10 * DAY, 2.0));
        assertEquals(0.0, ReputationRules.decay(-3, 10 * DAY, 2.0));
        assertEquals(10.0, ReputationRules.decay(10, DAY, 0.0));
        assertEquals(10.0, ReputationRules.decay(10, -DAY, 2.0), "a clock running backwards never decays");
    }

    @Test
    void decayInStepsEqualsDecayAtOnce() {
        ReputationRecord r = new ReputationRecord(50, -30, 1, 1000, -1, 0);
        ReputationRecord once = r.decayTo(1000 + 3 * DAY, 4.0);
        ReputationRecord steps = r.decayTo(1000 + DAY, 4.0).decayTo(1000 + 2 * DAY + 77, 4.0).decayTo(1000 + 3 * DAY, 4.0);
        for (Faction f : Faction.values()) assertEquals(once.get(f), steps.get(f), 1e-9, f.id());
        assertEquals(38.0, once.navy(), 1e-9);
        assertEquals(-18.0, once.pirates(), 1e-9);
        assertEquals(0.0, once.villagers());
        assertSame(r, r.decayTo(500, 4.0), "an earlier time changes nothing");
    }

    @Test
    void shownScoreRoundsHalfAwayFromZero() {
        assertEquals(0, ReputationRules.display(0.4));
        assertEquals(1, ReputationRules.display(0.5));
        assertEquals(-1, ReputationRules.display(-0.5));
        assertEquals(-60, ReputationRules.display(-60.2));
    }

    @Test
    void priceSwingFavoursLikedCustomers() {
        assertEquals(0.05, ReputationRules.swing(50, 0.10), 1e-12);
        assertEquals(-0.10, ReputationRules.swing(-100, 0.10), 1e-12);
        assertEquals(0.10, ReputationRules.swing(250, 0.10), 1e-12, "the score is clamped");
        assertEquals(0.0, ReputationRules.swing(70, -1.0), "a negative swing is none");
        // liked: buy cheaper, sell dearer
        assertEquals(950, ReputationRules.buyPrice(1000, 50, 0.10));
        assertEquals(1050, ReputationRules.sellPrice(1000, 50, 0.10));
        // disliked: the reverse
        assertEquals(1050, ReputationRules.buyPrice(1000, -50, 0.10));
        assertEquals(950, ReputationRules.sellPrice(1000, -50, 0.10));
        // neutral, nothing to pay, and a cheap good that never becomes free
        assertEquals(1000, ReputationRules.buyPrice(1000, 0, 0.10));
        assertEquals(0, ReputationRules.sellPrice(0, 100, 0.10));
        assertEquals(1, ReputationRules.buyPrice(1, 100, 0.9));
        assertEquals(0, ReputationRules.buyPrice(0, 100, 0.10));
    }

    @Test
    void villageTradeDeedsAreCappedPerDay() {
        ReputationRecord r = ReputationRecord.EMPTY;
        r = r.countTradeDeed(5, 2);
        assertNotNull(r);
        r = r.countTradeDeed(5, 2);
        assertNotNull(r);
        assertNull(r.countTradeDeed(5, 2), "third deed on day 5");
        ReputationRecord nextDay = r.countTradeDeed(6, 2);
        assertNotNull(nextDay, "a new day starts a new count");
        assertEquals(1, nextDay.tradeDeeds());
        assertNull(ReputationRecord.EMPTY.countTradeDeed(0, 0), "cap 0 counts nothing");
    }

    @Test
    void recordCodecRoundTrips() {
        ReputationRecord r = new ReputationRecord(12.5, -40, 99, 123456L, 7L, 3);
        var json = ReputationRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
        assertEquals(r, ReputationRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var empty = ReputationRecord.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
        assertEquals(ReputationRecord.EMPTY, empty, "missing fields read as the empty record");
    }
}

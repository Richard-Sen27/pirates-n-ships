package com.richardsenger.piratesnships.rpg.career;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The career record: invariants, counters and the codec round trip. */
class CareerRecordTest {

    @Test
    void codecRoundTrip() {
        CareerRecord r = CareerRecord.EMPTY.withNavy(NavyRank.COMMODORE).withInfamy(InfamyRank.DECKHAND)
                .plus(CareerCounter.PIRATES_KILLED, 31).plus(CareerCounter.NAVY_QUESTS, 6)
                .withLetter(LetterState.VOIDED, 123_456L).withPrize(77);
        JsonElement json = CareerRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
        CareerRecord back = CareerRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(r, back);
        assertEquals(CareerRecord.EMPTY, CareerRecord.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(), "empty save");
    }

    @Test
    void unknownCountersAreSkipped() {
        JsonObject json = new JsonObject();
        JsonObject counters = new JsonObject();
        counters.addProperty("pirates_killed", 3);
        counters.addProperty("dragons_slain", 9);
        json.add("counters", counters);
        CareerRecord r = CareerRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Map.of(CareerCounter.PIRATES_KILLED, 3L), r.counters());
    }

    @Test
    void serviceAndRankGoTogether() {
        CareerRecord odd = new CareerRecord(NavyRank.CAPTAIN, false, InfamyRank.DECKHAND, LetterState.NONE, 0, Map.of(), 0);
        assertEquals(NavyRank.NONE, odd.navy(), "a rank without service");
        CareerRecord odd2 = new CareerRecord(NavyRank.NONE, true, InfamyRank.DECKHAND, LetterState.NONE, 0, Map.of(), 0);
        assertFalse(odd2.enlisted(), "service without a rank");
        assertTrue(CareerRecord.EMPTY.withNavy(NavyRank.MIDSHIPMAN).enlisted());
        assertFalse(CareerRecord.EMPTY.withNavy(NavyRank.MIDSHIPMAN).withNavy(NavyRank.NONE).enlisted());
    }

    @Test
    void countersNeverGoNegativeOrOverflow() {
        CareerRecord r = CareerRecord.EMPTY.plus(CareerCounter.PLUNDER_COINS, 5).plus(CareerCounter.PLUNDER_COINS, -10);
        assertEquals(0, r.count(CareerCounter.PLUNDER_COINS));
        CareerRecord big = CareerRecord.EMPTY.plus(CareerCounter.PLUNDER_COINS, Long.MAX_VALUE - 1).plus(CareerCounter.PLUNDER_COINS, 10);
        assertEquals(Long.MAX_VALUE, big.count(CareerCounter.PLUNDER_COINS));
        assertEquals(0, CareerRecord.EMPTY.withPrize(-5).prizeMoney());
        assertEquals(Long.MAX_VALUE, CareerRecord.EMPTY.withPrize(Long.MAX_VALUE - 1).plusPrize(10).prizeMoney());
    }
}

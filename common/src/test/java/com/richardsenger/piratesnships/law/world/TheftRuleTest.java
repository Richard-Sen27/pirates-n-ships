package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.world.TheftRule.Params;
import com.richardsenger.piratesnships.law.world.TheftRule.Verdict;
import com.richardsenger.piratesnships.law.world.TheftRule.Visit;
import com.richardsenger.piratesnships.law.world.TheftRule.Witness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The theft rule, including the "inside a village structure" part that a GameTest can't build. */
class TheftRuleTest {

    private static final Params DEFAULT = new Params(true, true, 16, true);

    @Test
    void witnessedRemovalFromVillageContainerIsTheft() {
        assertEquals(Verdict.THEFT, TheftRule.judge(DEFAULT, new Visit(true, false, 1, 3)));
    }

    @Test
    void eachConditionCanPreventIt() {
        assertEquals(Verdict.DISABLED, TheftRule.judge(new Params(false, true, 16, true), new Visit(true, false, 1, 3)));
        assertEquals(Verdict.NOTHING_TAKEN, TheftRule.judge(DEFAULT, new Visit(true, false, 1, 0)));
        assertEquals(Verdict.NOT_IN_VILLAGE, TheftRule.judge(DEFAULT, new Visit(false, false, 1, 3)));
        assertEquals(Verdict.PLAYER_PLACED, TheftRule.judge(DEFAULT, new Visit(true, true, 1, 3)));
        assertEquals(Verdict.UNWITNESSED, TheftRule.judge(DEFAULT, new Visit(true, false, 0, 3)));
    }

    @Test
    void villageRequirementCanBeSwitchedOff() {
        Params anywhere = new Params(true, false, 16, true);
        assertEquals(Verdict.THEFT, TheftRule.judge(anywhere, new Visit(false, false, 1, 3)));
        assertEquals(Verdict.PLAYER_PLACED, TheftRule.judge(anywhere, new Visit(false, true, 1, 3)));
    }

    @Test
    void witnessesNeedRangeAndSight() {
        List<Witness> ws = List.of(new Witness(15 * 15, true), new Witness(17 * 17, true), new Witness(4, false));
        assertEquals(1, TheftRule.countWitnesses(ws, DEFAULT));
        assertEquals(2, TheftRule.countWitnesses(ws, new Params(true, true, 16, false)));
        assertEquals(0, TheftRule.countWitnesses(ws, new Params(true, true, 0, true)));
        assertEquals(1, TheftRule.countWitnesses(List.of(new Witness(16 * 16, true)), DEFAULT), "range is inclusive");
    }

    @Test
    void onlyNetRemovalPerItemCounts() {
        assertEquals(0, TheftRule.netRemoved(Map.of("diamond", 10), Map.of("diamond", 10, "dirt", 64)), "putting in");
        assertEquals(4, TheftRule.netRemoved(Map.of("diamond", 10), Map.of("diamond", 6)));
        assertEquals(0, TheftRule.netRemoved(Map.of("diamond", 10), Map.of("diamond", 12)), "more than before");
        assertEquals(10, TheftRule.netRemoved(Map.of("diamond", 10), Map.of("dirt", 10)), "swapping is still theft");
        assertEquals(15, TheftRule.netRemoved(Map.of("a", 5, "b", 10), Map.of()));
    }
}

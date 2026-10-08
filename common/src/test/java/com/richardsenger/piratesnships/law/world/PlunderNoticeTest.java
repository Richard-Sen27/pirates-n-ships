package com.richardsenger.piratesnships.law.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.world.PlunderNotice.Holding;
import com.richardsenger.piratesnships.law.world.PlunderNotice.Params;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.market.PortKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** LAW3's pure rules: counting plunder-marked units aboard, the notice limit, the desk refusal and the two crimes. */
class PlunderNoticeTest {

    @Test
    void marked_stacks_count_their_units_and_clean_ones_nothing() {
        assertEquals(20, PlunderNotice.markedUnits(List.of(Holding.of(12, true), Holding.of(64, false), Holding.of(8, true))));
        assertEquals(0, PlunderNotice.markedUnits(List.of()));
        assertEquals(0, PlunderNotice.markedUnits(List.of(Holding.of(64, false))));
    }

    @Test
    void nested_content_counts_once_per_carrying_unit() {
        // two clean shulker boxes, each with 10 marked and 5 clean units
        Holding box = new Holding(2, false, List.of(Holding.of(10, true), Holding.of(5, false)));
        assertEquals(20, PlunderNotice.markedUnits(box));
        // a marked crate carrying 30 marked units counts itself and its load
        Holding crate = new Holding(1, true, List.of(Holding.of(30, true)));
        assertEquals(31, PlunderNotice.markedUnits(crate));
        // an empty stack carries nothing
        assertEquals(0, PlunderNotice.markedUnits(new Holding(0, true, List.of(Holding.of(30, true)))));
    }

    @Test
    void more_than_the_limit_is_noticed_while_the_notice_is_on() {
        Params p = Params.DEFAULTS;
        assertEquals(new Params(true, 16), p);
        assertFalse(PlunderNotice.noticed(10, p));
        assertFalse(PlunderNotice.noticed(16, p), "the limit itself passes");
        assertTrue(PlunderNotice.noticed(17, p));
        assertTrue(PlunderNotice.noticed(20, p));
        assertFalse(PlunderNotice.noticed(1000, new Params(false, 16)), "notice off");
        assertTrue(PlunderNotice.noticed(1, new Params(true, 0)));
        assertFalse(PlunderNotice.noticed(0, new Params(true, -5)), "a negative limit counts as 0");
    }

    @Test
    void config_defaults_match_the_rule() {
        assertEquals(Params.DEFAULTS, LawConfig.plunderNoticeParams());
    }

    @Test
    void only_the_fence_takes_plunder() {
        assertFalse(TradeService.refusesPlunder(PortKind.PIRATE_ISLAND, true));
        assertTrue(TradeService.refusesPlunder(PortKind.SEAFARER_VILLAGE, true));
        assertTrue(TradeService.refusesPlunder(PortKind.NAVY_OUTPOST, true));
        for (PortKind k : PortKind.values()) assertFalse(TradeService.refusesPlunder(k, false), "plunder marks off: " + k);
    }

    @Test
    void the_new_crimes_have_their_config_and_a_day_long_repeat_window() {
        assertEquals("selling_plunder", CrimeType.SELLING_PLUNDER.id());
        assertEquals("suspected_piracy", CrimeType.SUSPECTED_PIRACY.id());
        assertEquals(Math.round(CrimeType.PIRACY.defaultSeverity() / 3.0), CrimeType.SELLING_PLUNDER.defaultSeverity(),
                "a third of piracy");
        assertEquals(15, CrimeType.SUSPECTED_PIRACY.defaultSeverity());
        for (CrimeType t : List.of(CrimeType.SELLING_PLUNDER, CrimeType.SUSPECTED_PIRACY)) {
            assertEquals(t.defaultSeverity(), LawConfig.SEVERITIES.get(t).get());
            assertEquals(1200, LawConfig.COOLDOWNS.get(t).get(), "one in-game day");
            assertEquals(CrimeRules.TICKS_PER_DAY, CrimeRules.defaults().cooldownOf(t));
        }
    }
}

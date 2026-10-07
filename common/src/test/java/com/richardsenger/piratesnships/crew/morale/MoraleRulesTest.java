package com.richardsenger.piratesnships.crew.morale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoraleRulesTest {

    private static final MoraleRules.Settings ON = MoraleRules.Settings.DEFAULTS;
    private static final MoraleRules.Settings OFF = new MoraleRules.Settings(false, 70, 5, 10);

    @Test
    void defaultsMatchTheDesign() {
        assertEquals(new MoraleRules.Settings(true, 70, 5, 10), ON);
    }

    @Test
    void unsetMoraleReadsAsStart() {
        assertEquals(70, MoraleRules.effective(ON, MoraleRules.UNSET));
        assertEquals(55, MoraleRules.effective(new MoraleRules.Settings(true, 55, 5, 10), MoraleRules.UNSET));
        assertEquals(42, MoraleRules.effective(ON, 42));
    }

    @Test
    void dawnDeltas() {
        assertEquals(5, MoraleRules.dawnDelta(ON, NightOutcome.SLEPT));
        assertEquals(-10, MoraleRules.dawnDelta(ON, NightOutcome.NO_HAMMOCK));
        assertEquals(0, MoraleRules.dawnDelta(ON, NightOutcome.ON_DUTY), "on duty all night: no change");
        assertEquals(0, MoraleRules.dawnDelta(ON, NightOutcome.NONE));
    }

    @Test
    void aNightInAHammockAndOneWithout() {
        assertEquals(75, MoraleRules.adjust(ON, MoraleRules.UNSET, MoraleRules.dawnDelta(ON, NightOutcome.SLEPT)));
        assertEquals(60, MoraleRules.adjust(ON, MoraleRules.UNSET, MoraleRules.dawnDelta(ON, NightOutcome.NO_HAMMOCK)));
        assertEquals(70, MoraleRules.adjust(ON, 70, MoraleRules.dawnDelta(ON, NightOutcome.ON_DUTY)));
    }

    @Test
    void cappedAt100AndFlooredAt0() {
        assertEquals(100, MoraleRules.adjust(ON, 98, 5));
        assertEquals(100, MoraleRules.adjust(ON, 100, 5));
        assertEquals(0, MoraleRules.adjust(ON, 4, -10));
        assertEquals(0, MoraleRules.adjust(ON, 0, -10));
        assertEquals(100, MoraleRules.clamp(250));
        assertEquals(0, MoraleRules.clamp(-3));
    }

    @Test
    void disabledMoraleIsFrozenAtStart() {
        assertEquals(70, MoraleRules.effective(OFF, 30), "frozen at start whatever was stored");
        assertEquals(70, MoraleRules.adjust(OFF, 30, -10));
        assertEquals(0, MoraleRules.dawnDelta(OFF, NightOutcome.SLEPT));
        assertEquals(0, MoraleRules.dawnDelta(OFF, NightOutcome.NO_HAMMOCK));
    }

    @Test
    void nightOutcomeIdsRoundTrip() {
        for (NightOutcome o : NightOutcome.values()) {
            assertEquals(o, NightOutcome.byId(o.id()));
        }
        assertEquals(NightOutcome.NONE, NightOutcome.byId("nonsense"));
    }
}

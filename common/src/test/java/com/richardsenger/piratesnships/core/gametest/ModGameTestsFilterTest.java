package com.richardsenger.piratesnships.core.gametest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scoped-run filter of {@link ModGameTests}: unset runs everything, a list selects classes by simple name. */
class ModGameTestsFilterTest {
    @Test
    void unsetOrBlankSelectsEverything() {
        assertTrue(ModGameTests.selected("FlagGameTests", null));
        assertTrue(ModGameTests.selected("FlagGameTests", ""));
        assertTrue(ModGameTests.selected("FlagGameTests", "  "));
    }

    @Test
    void aListSelectsItsClassesCaseInsensitively() {
        assertTrue(ModGameTests.selected("FlagGameTests", "FlagGameTests,FlagStackGameTests"));
        assertTrue(ModGameTests.selected("FlagStackGameTests", "flaggametests, flagstackgametests"));
        assertFalse(ModGameTests.selected("AnchorGameTests", "FlagGameTests,FlagStackGameTests"));
    }
}

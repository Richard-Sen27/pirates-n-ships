package com.richardsenger.piratesnships.core.gametest;

import com.richardsenger.piratesnships.core.CoreConfig;
import com.richardsenger.piratesnships.station.StationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Override handles without a game (unbound config values). */
class ConfigOverridesTest {

    @AfterEach
    void cleanup() {
        ConfigOverrides.restoreAll();
        CoreConfig.DEBUG.reset();
        StationConfig.TICKS_PER_TRIM_STEP.reset();
    }

    @Test
    void applyAndRestore() {
        var handle = ConfigOverrides.apply(CoreConfig.DEBUG, true);
        assertTrue(CoreConfig.DEBUG.get());
        handle.restore();
        assertFalse(CoreConfig.DEBUG.get());
        assertFalse(CoreConfig.DEBUG.hasOverride(), "restoring an unbound value clears the override");
        handle.restore(); // idempotent
        assertTrue(handle.isRestored());
    }

    @Test
    void nestedOverridesRestoreInReverseOrder() {
        CoreConfig.DEBUG.set(false);
        var outer = ConfigOverrides.apply(CoreConfig.DEBUG, true);
        var inner = ConfigOverrides.apply(CoreConfig.DEBUG, false);
        assertFalse(CoreConfig.DEBUG.get());
        ConfigOverrides.restoreAll();
        assertTrue(inner.isRestored() && outer.isRestored());
        assertFalse(CoreConfig.DEBUG.get());
        assertTrue(CoreConfig.DEBUG.hasOverride(), "a pre-existing local override is kept");
    }

    /** Two GameTests of one batch: the one that started first ends first (Q3). */
    @Test
    void olderOverrideRestoredFirstKeepsTheNewerOneUntilItEnds() {
        int original = StationConfig.TICKS_PER_TRIM_STEP.get();
        var a = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 10);
        var b = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 20);
        a.restore();
        assertEquals(20, StationConfig.TICKS_PER_TRIM_STEP.get(), "the newer override holds while it is active");
        assertTrue(a.isRestored());
        b.restore();
        assertEquals(original, StationConfig.TICKS_PER_TRIM_STEP.get(), "the original is back at the end");
        assertFalse(StationConfig.TICKS_PER_TRIM_STEP.hasOverride(), "restoring an unbound value clears the override");
    }

    @Test
    void newerOverrideRestoredFirstGoesBackToTheOlderOne() {
        int original = StationConfig.TICKS_PER_TRIM_STEP.get();
        var a = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 10);
        var b = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 20);
        b.restore();
        assertEquals(10, StationConfig.TICKS_PER_TRIM_STEP.get(), "the older override holds while it is active");
        a.restore();
        assertEquals(original, StationConfig.TICKS_PER_TRIM_STEP.get(), "the original is back at the end");
        assertFalse(StationConfig.TICKS_PER_TRIM_STEP.hasOverride(), "restoring an unbound value clears the override");
    }

    @Test
    void middleOverrideRestoredFirstHandsItsValueOn() {
        StationConfig.TICKS_PER_TRIM_STEP.set(40);
        var a = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 10);
        var b = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 20);
        var c = ConfigOverrides.apply(StationConfig.TICKS_PER_TRIM_STEP, 30);
        b.restore();
        assertEquals(30, StationConfig.TICKS_PER_TRIM_STEP.get());
        c.restore();
        assertEquals(10, StationConfig.TICKS_PER_TRIM_STEP.get(), "c now puts back a's value, which b had saved");
        a.restore();
        assertEquals(40, StationConfig.TICKS_PER_TRIM_STEP.get());
        assertTrue(StationConfig.TICKS_PER_TRIM_STEP.hasOverride(), "a pre-existing local override is kept");
    }
}

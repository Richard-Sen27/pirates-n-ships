package com.richardsenger.piratesnships.core.gametest;

import com.richardsenger.piratesnships.core.CoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Override handles without a game (unbound config values). */
class ConfigOverridesTest {

    @AfterEach
    void cleanup() {
        ConfigOverrides.restoreAll();
        CoreConfig.DEBUG.reset();
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
}

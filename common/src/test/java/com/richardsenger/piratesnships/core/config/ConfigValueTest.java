package com.richardsenger.piratesnships.core.config;

import com.richardsenger.piratesnships.core.CoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Config handles work without any loader: defaults when unbound, local overrides for tests. */
class ConfigValueTest {

    @AfterEach
    void cleanup() {
        CoreConfig.DEBUG.reset();
    }

    @Test
    void unboundValueReturnsDefault() {
        assertFalse(CoreConfig.DEBUG.get());
        assertEquals(List.of("core", "debug"), CoreConfig.DEBUG.path());
        assertEquals("pirates_n_ships.configuration.core.debug", CoreConfig.DEBUG.translationKey());
    }

    @Test
    void setOverridesUntilReset() {
        CoreConfig.DEBUG.set(true);
        assertTrue(CoreConfig.DEBUG.get());
        CoreConfig.DEBUG.reset();
        assertFalse(CoreConfig.DEBUG.get());
    }

    @Test
    void sectionsValidateDeclarations() {
        ConfigSection s = ModConfigs.client("junit_test", "only used by tests");
        ConfigValue<Integer> v = s.intRange("count", 3, 0, 10, "a count");
        assertEquals(3, v.get());
        assertEquals(ConfigValue.Kind.INT, v.kind());
        assertThrows(IllegalStateException.class, () -> s.intRange("count", 3, 0, 10, "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> s.intRange("bad_default", 11, 0, 10, "out of range"));
        assertThrows(IllegalArgumentException.class, () -> s.bool("NotSnakeCase", true, "bad name"));
    }
}

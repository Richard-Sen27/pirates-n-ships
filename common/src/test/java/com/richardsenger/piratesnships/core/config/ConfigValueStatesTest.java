package com.richardsenger.piratesnships.core.config;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the documented set/reset behavior in each backing state, and the section-name rule. */
class ConfigValueStatesTest {

    /** Stands in for the loader config. */
    private static final class FakeBacking<T> implements ConfigValue.Backing<T> {
        boolean loaded;
        T stored;

        FakeBacking(T stored) {
            this.stored = stored;
        }

        @Override public boolean isLoaded() { return loaded; }
        @Override public T get() { return stored; }
        @Override public void set(T value) { stored = value; }
    }

    private static final ConfigSection S = ModConfigs.server("junit_states", "only used by tests");

    @Test
    void unboundSetIsALocalOverrideAndResetRestoresTheDefault() {
        ConfigValue<Integer> v = S.intRange("unbound", 3, 0, 10, "x");
        v.set(7);
        assertEquals(7, v.get());
        assertTrue(v.hasOverride());
        assertFalse(v.isLive());
        v.reset();
        assertEquals(3, v.get());
        assertFalse(v.hasOverride());
    }

    @Test
    void boundButNotLoadedBehavesLikeUnbound() {
        ConfigValue<Integer> v = S.intRange("not_loaded", 3, 0, 10, "x");
        FakeBacking<Integer> backing = new FakeBacking<>(5);
        v.bind(backing);
        assertEquals(3, v.get(), "default until loaded");
        v.set(7);
        assertEquals(5, backing.stored, "backing untouched");
        assertEquals(7, v.get());
        v.reset();
        assertEquals(3, v.get());
    }

    @Test
    void liveSetWritesTheBackingAndResetDoesNotUndoIt() {
        ConfigValue<Integer> v = S.intRange("live", 3, 0, 10, "x");
        FakeBacking<Integer> backing = new FakeBacking<>(5);
        backing.loaded = true;
        v.bind(backing);
        assertTrue(v.isLive());
        assertEquals(5, v.get(), "the real config wins over the default");

        v.set(7);
        assertEquals(7, backing.stored, "set writes the real config");
        assertFalse(v.hasOverride());
        v.reset();
        assertEquals(7, v.get(), "reset() is a no-op for a live value");
        assertEquals(7, backing.stored, "reset() neither restores the old value nor the default");
    }

    @Test
    void configOverridesRestoreALiveValue() {
        ConfigValue<Integer> v = S.intRange("live_override", 3, 0, 10, "x");
        FakeBacking<Integer> backing = new FakeBacking<>(5);
        backing.loaded = true;
        v.bind(backing);
        ConfigOverrides.Handle<Integer> handle = ConfigOverrides.apply(v, 9);
        assertEquals(9, backing.stored);
        handle.restore();
        assertEquals(5, backing.stored, "the tool for tests puts the previous value back");
    }

    @Test
    void overrideSetBeforeLoadingKeepsWinningUntilReset() {
        ConfigValue<Integer> v = S.intRange("late_load", 3, 0, 10, "x");
        FakeBacking<Integer> backing = new FakeBacking<>(5);
        v.bind(backing);
        v.set(7);
        backing.loaded = true;
        assertEquals(7, v.get());
        v.reset();
        assertEquals(5, v.get());
    }

    @Test
    void clientAndServerSectionsWithTheSameNameFailFast() {
        ModConfigs.server("junit_clash", "server side");
        var e = assertThrows(IllegalStateException.class, () -> ModConfigs.client("junit_clash", "client side"));
        assertTrue(e.getMessage().contains("CLIENT:junit_clash"), e.getMessage());
        assertTrue(e.getMessage().contains("SERVER:junit_clash"), e.getMessage());
        assertFalse(ModConfigs.schema(ConfigType.CLIENT).sections().containsKey(java.util.List.of("junit_clash")), "nothing declared");

        ModConfigs.client("junit_clash_reverse", "client side");
        assertThrows(IllegalStateException.class, () -> ModConfigs.server("junit_clash_reverse", "server side"));
        // Nested names may repeat: their paths differ by the top-level section
        ModConfigs.server("junit_nested_a", "a").section("waves", "nested");
        ModConfigs.client("junit_nested_b", "b").section("waves", "nested");
    }
}

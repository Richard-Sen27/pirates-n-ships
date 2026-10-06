package com.richardsenger.piratesnships.core.gametest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Temporarily changes a config value in a test and restores the previous value afterwards.
 *
 * <pre>{@code
 * @ModGameTest(batch = "pirates_n_ships_config_ship_flooding")   // own batch: required, see below
 * public static void floodingCanBeDisabled(GameTestHelper helper) {
 *     ConfigOverrides.during(helper, ShipConfig.FLOODING_ENABLED, false);  // restored when the test ends
 *     ...
 * }
 * }</pre>
 *
 * <p><b>Rule: a GameTest that changes config must use its own batch</b> ({@code @ModGameTest(batch = ...)}), named
 * {@code pirates_n_ships_config_<module>_<what>}. In a running game {@link ConfigValue#set} writes the real server
 * config, which every test of the same batch sees, because tests of one batch run at the same time. Batches run one
 * after another, so a test alone in its batch cannot leak into others. Do not put two tests that override
 * the same value in one batch.
 *
 * <p>The value is restored when the test passes, fails, times out or is rerun, and, as a safety net, when the server
 * stops. In JUnit (no game), use {@link #apply} and call {@link Handle#restore()} in a {@code finally} block or
 * {@code @AfterEach}. Overrides of the same value must be restored in reverse order.
 */
public final class ConfigOverrides {

    /** One active override. {@link #restore()} is idempotent. */
    public static final class Handle<T> {
        private final ConfigValue<T> value;
        private final T previous;
        /** Unbound value without a prior local override: restore by clearing the override. */
        private final boolean clearOnRestore;
        private boolean restored;

        private Handle(ConfigValue<T> value) {
            this.value = value;
            this.previous = value.get();
            this.clearOnRestore = !value.isLive() && !value.hasOverride();
        }

        /** Puts the previous value back (no-op if already restored). */
        public void restore() {
            synchronized (ACTIVE) {
                if (restored) return;
                restored = true;
                ACTIVE.remove(this);
            }
            if (clearOnRestore && !value.isLive()) value.reset();
            else value.set(previous);
        }

        public boolean isRestored() {
            synchronized (ACTIVE) {
                return restored;
            }
        }

        public T previous() {
            return previous;
        }
    }

    private static final Set<Handle<?>> ACTIVE = new LinkedHashSet<>();
    private static volatile Field testInfoField;

    private ConfigOverrides() {
    }

    /** Sets {@code value} to {@code newValue} until the returned handle is restored. Works with and without a game. */
    public static <T> Handle<T> apply(ConfigValue<T> value, T newValue) {
        Handle<T> handle = new Handle<>(value);
        synchronized (ACTIVE) {
            ACTIVE.add(handle);
        }
        value.set(newValue);
        return handle;
    }

    /** Sets {@code value} to {@code newValue} for the rest of this GameTest (see class doc for the batch rule). */
    public static <T> Handle<T> during(GameTestHelper helper, ConfigValue<T> value, T newValue) {
        GameTestInfo info = testInfo(helper);
        Handle<T> handle = apply(value, newValue);
        info.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { handle.restore(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { handle.restore(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { handle.restore(); }
        });
        return handle;
    }

    /** Restores every override that is still active, newest first. */
    public static void restoreAll() {
        List<Handle<?>> active;
        synchronized (ACTIVE) {
            active = new ArrayList<>(ACTIVE);
        }
        for (int i = active.size() - 1; i >= 0; i--) active.get(i).restore();
    }

    /** Called by {@code CoreModule}: the server-stop safety net. */
    public static void registerEvents() {
        CommonEvents.SERVER_STOPPING.register(server -> {
            if (!ACTIVE.isEmpty()) {
                Constants.LOG.warn("Restoring {} config override(s) left over from GameTests", ACTIVE.size());
                restoreAll();
            }
        });
    }

    /**
     * {@link GameTestHelper} keeps its {@link GameTestInfo} private and has no end-of-test hook. The field is found
     * by type, not by name, so this works under any mapping (Mojang names on NeoForge, intermediary on Fabric).
     */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        f = candidate;
                        break;
                    }
                }
                if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
                testInfoField = f;
            }
            return (GameTestInfo) f.get(helper);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot attach config override to the GameTest", e);
        }
    }
}

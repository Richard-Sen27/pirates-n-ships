package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The sea state of every server level (docs/design.md §5.4, WV1): one {@link SeaStateModel.Tracker} per level, ticked
 * at the end of the level tick from the vanilla rain and thunder levels, and the {@link WaveField} built from it once
 * per tick (amplitude × {@code waves.amplitude}, direction from the wind at the world origin with the slow offset).
 * Server memory only: after a restart the sea starts at the weather's state. An override ({@code /pirates waves set},
 * tests) holds a state and optionally a direction.
 */
public final class SeaStates {

    private static final class Entry {
        final SeaStateModel.Tracker tracker = new SeaStateModel.Tracker();
        WaveField field = WaveField.FLAT;
    }

    private static final Map<ServerLevel, Entry> LEVELS = new IdentityHashMap<>();

    private SeaStates() {
    }

    /** End of a level tick: eases the sea toward the weather and rebuilds the field. */
    public static synchronized void onLevelTick(ServerLevel level) {
        Entry e = LEVELS.computeIfAbsent(level, l -> new Entry());
        long now = level.getGameTime();
        SeaState target = SeaStateModel.target(level.getRainLevel(1.0f), level.getThunderLevel(1.0f),
                SeaStateModel.clearNoise(level.getSeed(), now));
        e.tracker.tick(target, SeaStateModel.ratePerTick(HazardConfig.STATE_CHANGE_SECONDS.get()), now);
        e.field = build(level, e.tracker, now);
    }

    private static WaveField build(ServerLevel level, SeaStateModel.Tracker t, long now) {
        if (!HazardConfig.WAVES_ENABLED.get() || t.override() == null && stillByDefault(level)) {
            return WaveField.FLAT;
        }
        double amplitude = t.amplitude() * HazardConfig.WAVE_AMPLITUDE.get();
        Double fixed = t.overrideDirection();
        double direction = fixed != null ? fixed
                : SeaStateModel.directionDegrees(WindService.sample(level, Vec3.ZERO).towardDegrees(), level.getSeed(), now);
        return new WaveField(amplitude, direction, WaveField.COMPONENTS, t.origin());
    }

    /**
     * Whether the sea of {@code level} is flat unless something holds a state: on the vanilla GameTest server
     * ({@link net.minecraft.gametest.framework.GameTestServer}). Every older ship GameTest measures behaviour in still
     * water (a 5x4x5 hull must not heel past 5°, a ship at rest must not roll), and the GameTest world's clear weather
     * would otherwise give a calm or moderate sea that varies with the game time, i.e. with the test order. Wave tests
     * hold their state with {@link #set}.
     */
    public static boolean stillByDefault(ServerLevel level) {
        return level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
    }

    /** The wave field of {@code level} this tick (flat before its first tick or with waves off). */
    public static synchronized WaveField field(ServerLevel level) {
        Entry e = LEVELS.get(level);
        return e == null || !HazardConfig.WAVES_ENABLED.get() ? WaveField.FLAT : e.field;
    }

    /** The state the sea of {@code level} is in now (calm before its first tick). */
    public static synchronized SeaState current(ServerLevel level) {
        Entry e = LEVELS.get(level);
        return e == null ? SeaState.CALM : e.tracker.current();
    }

    /** The state the sea of {@code level} is heading for. */
    public static synchronized SeaState target(ServerLevel level) {
        Entry e = LEVELS.get(level);
        return e == null ? SeaState.CALM : e.tracker.target();
    }

    /** The active override of {@code level}, or null. */
    public static synchronized @Nullable SeaState override(ServerLevel level) {
        Entry e = LEVELS.get(level);
        return e == null ? null : e.tracker.override();
    }

    /**
     * Holds the sea of {@code level} at {@code state} from now on (no easing), running toward {@code directionDegrees}
     * (compass bearing; null follows the wind), until {@link #clear} or game time {@code untilGameTime}.
     */
    public static synchronized void set(ServerLevel level, SeaState state, @Nullable Double directionDegrees, long untilGameTime) {
        set(level, state, directionDegrees, untilGameTime, WaveField.Origin.NONE);
    }

    /**
     * {@link #set(ServerLevel, SeaState, Double, long)} with the field's phases pinned to {@code origin} while the
     * override holds (GameTests only; see {@link WaveField.Origin}). Clients keep the unpinned phase, which only their
     * cosmetic wave sampling uses.
     */
    public static synchronized void set(ServerLevel level, SeaState state, @Nullable Double directionDegrees, long untilGameTime,
                                        WaveField.Origin origin) {
        Entry e = LEVELS.computeIfAbsent(level, l -> new Entry());
        e.tracker.setOverride(state, directionDegrees, untilGameTime, origin);
        e.field = build(level, e.tracker, level.getGameTime());
    }

    /** Drops the override of {@code level}; the sea eases back to the weather's state. */
    public static synchronized void clear(ServerLevel level) {
        Entry e = LEVELS.get(level);
        if (e != null) {
            e.tracker.clearOverride();
        }
    }

    public static synchronized void clearAll() {
        LEVELS.clear();
    }
}

package com.richardsenger.piratesnships.core.config;

import com.richardsenger.piratesnships.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A handle to one config value, declared through a {@link ConfigSection}. Features read values only through
 * {@link #get()}.
 *
 * <p>Value resolution, in order: a test override set with {@link #set} while unbound, then the loader-backed value
 * once the loader has bound and loaded the config, otherwise the declared default. This means JUnit tests can read
 * (and override) config values without any loader, and server config reads before a world is loaded return
 * defaults instead of throwing.
 */
public final class ConfigValue<T> {

    /** The supported value kinds. The loader translates each into its native config entry. */
    public enum Kind { BOOLEAN, INT, DOUBLE, ENUM, STRING, STRING_LIST }

    /** Loader-side storage for a value. Implemented by the config service. */
    public interface Backing<T> {
        /** Whether the backing config file is currently loaded. */
        boolean isLoaded();

        T get();

        void set(T value);
    }

    private final ConfigType type;
    private final List<String> path;
    private final Kind kind;
    private final T defaultValue;
    private final String comment;
    private final @Nullable Number min;
    private final @Nullable Number max;
    private volatile @Nullable Backing<T> backing;
    private volatile @Nullable T override;

    ConfigValue(ConfigType type, List<String> path, Kind kind, T defaultValue, String comment, @Nullable Number min, @Nullable Number max) {
        this.type = type;
        this.path = List.copyOf(path);
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.comment = comment;
        this.min = min;
        this.max = max;
    }

    /** The current value (see class doc for resolution order). */
    public T get() {
        T o = override;
        if (o != null) {
            return o;
        }
        Backing<T> b = backing;
        if (b != null && b.isLoaded()) {
            return b.get();
        }
        return defaultValue;
    }

    /**
     * Changes the value. When bound and loaded, this writes the loader config (e.g. for GameTests that check a
     * toggle). Otherwise it sets a local override, which is what JUnit tests use. Undo with {@link #reset()}.
     */
    public void set(T value) {
        Backing<T> b = backing;
        if (b != null && b.isLoaded()) {
            b.set(value);
        } else {
            override = value;
        }
    }

    /** Clears a local override set by {@link #set} (tests call this in their cleanup). */
    public void reset() {
        override = null;
    }

    /** Called by the config service when it registers the schema. */
    public void bind(Backing<T> backing) {
        this.backing = backing;
    }

    public ConfigType type() {
        return type;
    }

    /** Full path inside the config file, e.g. {@code [core, debug]}. */
    public List<String> path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String comment() {
        return comment;
    }

    /** Lower bound for {@link Kind#INT} and {@link Kind#DOUBLE}, else {@code null}. */
    public @Nullable Number min() {
        return min;
    }

    /** Upper bound for {@link Kind#INT} and {@link Kind#DOUBLE}, else {@code null}. */
    public @Nullable Number max() {
        return max;
    }

    /** Translation key used by config screens, e.g. {@code pirates_n_ships.configuration.core.debug}. */
    public String translationKey() {
        return translationKey(path);
    }

    /** Translation key for a value or section path. */
    public static String translationKey(List<String> path) {
        return Constants.MOD_ID + ".configuration." + String.join(".", path);
    }

    @Override
    public String toString() {
        return type + ":" + String.join(".", path);
    }
}

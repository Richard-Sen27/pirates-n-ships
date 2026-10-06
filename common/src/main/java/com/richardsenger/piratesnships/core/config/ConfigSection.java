package com.richardsenger.piratesnships.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declares config values inside one named group. Get a top-level section from {@link ModConfigs#server} or
 * {@link ModConfigs#client}, nest with {@link #section}. Declaring never touches a loader, so section classes are
 * safe to load in unit tests.
 *
 * <pre>{@code
 * public final class FloodingConfig {
 *     private static final ConfigSection S = ModConfigs.server("flooding", "Hull breaches and water inflow");
 *     public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true, "Ships take on water through breaches");
 *     public static final ConfigValue<Double> INFLOW_RATE = S.doubleRange("inflow_rate", 1.0, 0.0, 10.0, "Inflow multiplier");
 *     public static void init() {} // called from the module's registerConfig() to load the class in time
 * }
 * }</pre>
 *
 * Names use {@code snake_case}. The config screen title of each entry is generated into the lang file from its
 * name, and its tooltip from its comment.
 */
public final class ConfigSection {

    private final ConfigSchema schema;
    private final List<String> path;

    ConfigSection(ConfigSchema schema, List<String> path, String comment) {
        this.schema = schema;
        this.path = List.copyOf(path);
        schema.addSection(this.path, comment);
    }

    /** A nested group. */
    public ConfigSection section(String name, String comment) {
        return new ConfigSection(schema, child(name), comment);
    }

    public ConfigValue<Boolean> bool(String name, boolean defaultValue, String comment) {
        return add(name, ConfigValue.Kind.BOOLEAN, defaultValue, comment, null, null);
    }

    public ConfigValue<Integer> intRange(String name, int defaultValue, int min, int max, String comment) {
        checkRange(name, defaultValue, min, max);
        return add(name, ConfigValue.Kind.INT, defaultValue, comment, min, max);
    }

    public ConfigValue<Double> doubleRange(String name, double defaultValue, double min, double max, String comment) {
        checkRange(name, defaultValue, min, max);
        return add(name, ConfigValue.Kind.DOUBLE, defaultValue, comment, min, max);
    }

    public <E extends Enum<E>> ConfigValue<E> enumValue(String name, E defaultValue, String comment) {
        return add(name, ConfigValue.Kind.ENUM, defaultValue, comment, null, null);
    }

    public ConfigValue<String> string(String name, String defaultValue, String comment) {
        return add(name, ConfigValue.Kind.STRING, defaultValue, comment, null, null);
    }

    /** A list of strings (e.g. ids). May be empty. */
    public ConfigValue<List<String>> stringList(String name, List<String> defaultValue, String comment) {
        return add(name, ConfigValue.Kind.STRING_LIST, List.copyOf(defaultValue), comment, null, null);
    }

    private <T> ConfigValue<T> add(String name, ConfigValue.Kind kind, T defaultValue, String comment, Number min, Number max) {
        Objects.requireNonNull(defaultValue, "default");
        return schema.add(new ConfigValue<>(schema.type(), child(name), kind, defaultValue, comment, min, max));
    }

    private List<String> child(String name) {
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Config names must be snake_case: " + name);
        }
        List<String> p = new ArrayList<>(path);
        p.add(name);
        return p;
    }

    private static void checkRange(String name, double def, double min, double max) {
        if (min > max || def < min || def > max) {
            throw new IllegalArgumentException("Bad range for config value " + name);
        }
    }
}

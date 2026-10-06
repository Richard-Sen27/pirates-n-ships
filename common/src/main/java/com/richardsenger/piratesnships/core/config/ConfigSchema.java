package com.richardsenger.piratesnships.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every section and value declared for one {@link ConfigType}, in declaration order. Frozen once the loader has
 * registered it; declaring values afterwards is a bug and throws.
 */
public final class ConfigSchema {

    private final ConfigType type;
    private final Map<List<String>, String> sections = new LinkedHashMap<>();
    private final List<ConfigValue<?>> values = new ArrayList<>();
    private boolean frozen;

    ConfigSchema(ConfigType type) {
        this.type = type;
    }

    public ConfigType type() {
        return type;
    }

    /** Section paths and their comments, in declaration order. */
    public synchronized Map<List<String>, String> sections() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sections));
    }

    /** All values, in declaration order. */
    public synchronized List<ConfigValue<?>> values() {
        return List.copyOf(values);
    }

    /** Called by the config service right before it builds the loader spec. */
    public synchronized void freeze() {
        frozen = true;
    }

    synchronized void addSection(List<String> path, String comment) {
        checkOpen(path);
        if (sections.putIfAbsent(List.copyOf(path), comment) != null) {
            throw new IllegalStateException("Duplicate config section " + type + ":" + String.join(".", path));
        }
    }

    synchronized <T> ConfigValue<T> add(ConfigValue<T> value) {
        checkOpen(value.path());
        for (ConfigValue<?> v : values) {
            if (v.path().equals(value.path())) {
                throw new IllegalStateException("Duplicate config value " + value);
            }
        }
        values.add(value);
        return value;
    }

    private void checkOpen(List<String> path) {
        if (frozen) {
            throw new IllegalStateException("Config " + type + " is already registered, too late to declare "
                    + String.join(".", path) + ". Declare config from your module's registerConfig().");
        }
    }
}

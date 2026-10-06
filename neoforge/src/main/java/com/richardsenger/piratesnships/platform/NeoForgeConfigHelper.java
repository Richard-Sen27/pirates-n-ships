package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.core.config.ConfigSchema;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.platform.services.IConfigHelper;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Translates our {@link ConfigSchema} into a NeoForge {@link ModConfigSpec}. SERVER maps to
 * {@link ModConfig.Type#SERVER} (per world, synced to clients), CLIENT to {@link ModConfig.Type#CLIENT}.
 */
public final class NeoForgeConfigHelper implements IConfigHelper {

    private final Map<ConfigType, ModConfigSpec> specs = new EnumMap<>(ConfigType.class);

    @Override
    public synchronized void register(ConfigSchema schema) {
        schema.freeze();
        if (schema.values().isEmpty()) return;
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Map<List<String>, String> sections = schema.sections();
        List<String> current = new ArrayList<>();
        List<Runnable> binds = new ArrayList<>();
        ModConfigSpec[] spec = new ModConfigSpec[1];
        for (ConfigValue<?> value : schema.values()) {
            List<String> parent = value.path().subList(0, value.path().size() - 1);
            while (!parent.subList(0, Math.min(current.size(), parent.size())).equals(current) || current.size() > parent.size()) {
                builder.pop();
                current.removeLast();
            }
            while (current.size() < parent.size()) {
                current.add(parent.get(current.size()));
                String comment = sections.get(current);
                if (comment != null && !comment.isEmpty()) builder.comment(comment);
                builder.translation(ConfigValue.translationKey(current)).push(current.getLast());
            }
            binds.add(define(builder, value, () -> spec[0]));
        }
        while (!current.isEmpty()) {
            builder.pop();
            current.removeLast();
        }
        spec[0] = builder.build();
        binds.forEach(Runnable::run);
        specs.put(schema.type(), spec[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Runnable define(ModConfigSpec.Builder builder, ConfigValue<?> value, java.util.function.Supplier<ModConfigSpec> spec) {
        String name = value.path().getLast();
        builder.comment(value.comment()).translation(value.translationKey());
        ModConfigSpec.ConfigValue<?> native_ = switch (value.kind()) {
            case BOOLEAN -> builder.define(name, (boolean) (Boolean) value.defaultValue());
            case INT -> builder.defineInRange(name, (Integer) value.defaultValue(), value.min().intValue(), value.max().intValue());
            case DOUBLE -> builder.defineInRange(name, (Double) value.defaultValue(), value.min().doubleValue(), value.max().doubleValue());
            case ENUM -> builder.defineEnum(name, (Enum) value.defaultValue());
            case STRING -> builder.define(name, (String) value.defaultValue());
            case STRING_LIST -> builder.defineListAllowEmpty(name, (List<String>) value.defaultValue(), () -> "", o -> o instanceof String);
        };
        ConfigValue<Object> handle = (ConfigValue<Object>) value;
        ModConfigSpec.ConfigValue<Object> nat = (ModConfigSpec.ConfigValue<Object>) native_;
        boolean isList = value.kind() == ConfigValue.Kind.STRING_LIST;
        return () -> handle.bind(new ConfigValue.Backing<>() {
            @Override
            public boolean isLoaded() {
                return spec.get().isLoaded();
            }

            @Override
            public Object get() {
                Object v = nat.get();
                return isList ? List.copyOf((List<?>) v) : v;
            }

            @Override
            public void set(Object v) {
                nat.set(v);
            }
        });
    }

    /** Called once by the entry point: registers the built specs with our mod container. */
    public synchronized void attach(ModContainer container) {
        ModConfigSpec server = specs.get(ConfigType.SERVER);
        ModConfigSpec client = specs.get(ConfigType.CLIENT);
        if (server != null) container.registerConfig(ModConfig.Type.SERVER, server);
        if (client != null) container.registerConfig(ModConfig.Type.CLIENT, client);
    }
}

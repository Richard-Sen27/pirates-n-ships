package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.config.ConfigSchema;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.platform.services.IConfigHelper;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Translates our {@link ConfigSchema} into a {@link ModConfigSpec} through Forge Config API Port, which provides
 * NeoForge's config API on Fabric (design.md §18, "Config library"). The translation is the one of
 * {@code NeoForgeConfigHelper} (same classes, same files, same translation keys); only the registration differs:
 * {@link NeoForgeConfigRegistry} instead of the mod container. SERVER maps to {@link ModConfig.Type#SERVER} (per world
 * in {@code serverconfig/}, loaded at server start and synced to clients by the port), CLIENT to
 * {@link ModConfig.Type#CLIENT}.
 */
public final class FabricConfigHelper implements IConfigHelper {

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
        NeoForgeConfigRegistry.INSTANCE.register(Constants.MOD_ID,
                schema.type() == ConfigType.SERVER ? ModConfig.Type.SERVER : ModConfig.Type.CLIENT, spec[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Runnable define(ModConfigSpec.Builder builder, ConfigValue<?> value, Supplier<ModConfigSpec> spec) {
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
}

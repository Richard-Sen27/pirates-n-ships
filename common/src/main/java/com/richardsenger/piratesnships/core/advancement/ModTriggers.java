package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registers one {@link ModTrigger} per {@link TriggerIds} id in {@code minecraft:trigger_type} (ADV1). */
public final class ModTriggers {

    private static final Map<String, RegistryEntry<CriterionTrigger<?>, ModTrigger>> BY_ID = new LinkedHashMap<>();

    static {
        for (String id : TriggerIds.ALL) {
            BY_ID.put(id, Services.REGISTRY.register(Registries.TRIGGER_TYPE, id, ModTrigger::new));
        }
    }

    private ModTriggers() {
    }

    /** The trigger of {@code id} (one of {@link TriggerIds}); throws for an unknown id. */
    public static ModTrigger get(String id) {
        RegistryEntry<CriterionTrigger<?>, ModTrigger> e = BY_ID.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown trigger " + id);
        return e.get();
    }

    public static Map<String, RegistryEntry<CriterionTrigger<?>, ModTrigger>> all() {
        return Collections.unmodifiableMap(BY_ID);
    }

    /** Called from {@code AdvancementModule.registerContent()}. */
    public static void init() {
    }
}

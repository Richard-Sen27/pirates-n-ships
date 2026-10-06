package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Collects English translations. Duplicate keys fail the data run. */
public final class LangBuilder {

    private final Map<String, String> entries = new TreeMap<>();

    public LangBuilder add(String key, String value) {
        if (entries.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("Duplicate lang key " + key);
        }
        return this;
    }

    public LangBuilder block(Supplier<? extends Block> block, String name) {
        return add(block.get().getDescriptionId(), name);
    }

    public LangBuilder item(Supplier<? extends Item> item, String name) {
        return add(item.get().getDescriptionId(), name);
    }

    Map<String, String> entries() {
        return entries;
    }
}

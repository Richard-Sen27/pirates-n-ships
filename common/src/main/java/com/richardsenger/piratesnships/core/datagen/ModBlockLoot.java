package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Block loot for our blocks only. Vanilla's {@link BlockLootSubProvider} walks the whole block registry (and the
 * NeoForge patch that limits it is not visible from common), so this subclass captures {@link #add} calls itself and
 * emits only those. The usual helpers are widened to {@code public} so module contributors can call them.
 */
public final class ModBlockLoot extends BlockLootSubProvider {

    private final List<Consumer<ModBlockLoot>> contributors;
    private final Map<ResourceKey<LootTable>, LootTable.Builder> tables = new LinkedHashMap<>();

    ModBlockLoot(HolderLookup.Provider registries, List<Consumer<ModBlockLoot>> contributors) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        this.contributors = contributors;
    }

    // public, not protected: Fabric API's access widener makes the vanilla method public, and the fabric module
    // compiles this file against it (FAB1)
    @Override
    public void generate() {
        contributors.forEach(c -> c.accept(this));
    }

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
        generate();
        tables.forEach(output);
    }

    @Override
    public void add(Block block, LootTable.Builder builder) {
        if (tables.put(block.getLootTable(), builder) != null) {
            throw new IllegalStateException("Duplicate loot table for " + block);
        }
    }

    @Override
    public void dropSelf(Block block) {
        super.dropSelf(block);
    }

    @Override
    public void dropOther(Block block, ItemLike drop) {
        super.dropOther(block, drop);
    }

    /** Lookup for enchantments etc. when building custom tables. */
    public HolderLookup.Provider registries() {
        return registries;
    }
}

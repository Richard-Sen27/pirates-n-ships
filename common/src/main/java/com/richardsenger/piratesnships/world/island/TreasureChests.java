package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Buried treasure chests (design.md §10.1, WG2): a vanilla chest at a treasure marker's position with the loot table
 * {@link #LOOT_TABLE}, rolled when a player first opens it. The sand above stays as the treasure spot placed it.
 */
public final class TreasureChests {

    public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/buried_treasure"));

    private TreasureChests() {
    }

    /** Sets the chest at {@code pos} (in a chunk {@code level} can write) and its loot table. */
    public static void bury(WorldGenLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, LOOT_TABLE);
    }
}

package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Processor {@code pirates_n_ships:loot_chests} (STR6), in the processor list {@code pirates_n_ships:connections} that
 * every port pool element names: while {@code world.structures.loot} ({@link WorldConfig#STRUCTURE_LOOT}) is off, a
 * block whose data names one of the port structures' loot tables ({@link StructureLoot#isStructureTable}) is placed
 * without its {@code LootTable} and {@code LootTableSeed}, so the chest generates empty. Other tables (wrecks, anything
 * a datapack adds) and every other block pass unchanged.
 */
public final class LootChestProcessor extends StructureProcessor {

    public static final LootChestProcessor INSTANCE = new LootChestProcessor();
    public static final MapCodec<LootChestProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    private LootChestProcessor() {
    }

    /** The block entity data a template block is placed with: {@code nbt} without the loot table while loot is off. */
    public static @Nullable CompoundTag placed(@Nullable CompoundTag nbt, boolean lootOn) {
        if (lootOn || nbt == null || !StructureLoot.isStructureTable(nbt.getString("LootTable"))) return nbt;
        CompoundTag out = nbt.copy();
        out.remove("LootTable");
        out.remove("LootTableSeed");
        return out;
    }

    @Override
    public @Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureBlockInfo blockInfo,
                                                     StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings) {
        CompoundTag nbt = relativeBlockInfo.nbt();
        CompoundTag kept = placed(nbt, WorldConfig.STRUCTURE_LOOT.get());
        return kept == nbt ? relativeBlockInfo : new StructureBlockInfo(relativeBlockInfo.pos(), relativeBlockInfo.state(), kept);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return PortStructures.LOOT_CHESTS.get();
    }
}

package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Sailing blocks: the yard (square sails, docs/design.md §5.2 rule F5a), the sail winch and the capstan. The cleat
 * of the triangular sails (rule F5b) is registered in {@code sail.TriangularSailContent}.
 */
public final class SailingBlocks {

    /** Blocks that may stand between the two yards of a square sail (besides air): logs, wooden fences, our masts later. */
    public static final TagKey<Block> MASTS = TagKey.create(Registries.BLOCK, Constants.id("masts"));

    public static final RegistryEntry<Block, YardBlock> YARD = ModRegistry.blockWithItem("yard",
            () -> new YardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<YardBlockEntity>> YARD_BLOCK_ENTITY =
            ModRegistry.blockEntity("yard", YardBlockEntity::new, YARD);
    public static final RegistryEntry<Block, SailWinchBlock> SAIL_WINCH = ModRegistry.blockWithItem("sail_winch",
            () -> new SailWinchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).ignitedByLava()));
    public static final RegistryEntry<Block, CapstanBlock> CAPSTAN = ModRegistry.blockWithItem("capstan",
            () -> new CapstanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD).ignitedByLava()));

    private SailingBlocks() {
    }

    public static void init() {
        com.richardsenger.piratesnships.sailing.sail.TriangularSailContent.init(); // cleat and rope (F5b)
    }
}

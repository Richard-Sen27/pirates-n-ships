package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.force.SailType;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/**
 * Sailing blocks: the yard (square sails, docs/design.md §5.2 rule F5a), the one-block fore-and-aft sail (until F5b
 * replaces it), the sail winch and the capstan.
 */
public final class SailingBlocks {

    /** Blocks that may stand between the two yards of a square sail (besides air): logs, wooden fences, our masts later. */
    public static final TagKey<Block> MASTS = TagKey.create(Registries.BLOCK, Constants.id("masts"));

    public static final RegistryEntry<Block, YardBlock> YARD = ModRegistry.blockWithItem("yard",
            () -> new YardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<YardBlockEntity>> YARD_BLOCK_ENTITY =
            ModRegistry.blockEntity("yard", YardBlockEntity::new, YARD);
    public static final RegistryEntry<Block, SailBlock> FORE_AND_AFT_SAIL = sail("fore_and_aft_sail", SailTypes.FORE_AND_AFT);
    public static final RegistryEntry<Block, SailWinchBlock> SAIL_WINCH = ModRegistry.blockWithItem("sail_winch",
            () -> new SailWinchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final RegistryEntry<Block, CapstanBlock> CAPSTAN = ModRegistry.blockWithItem("capstan",
            () -> new CapstanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));

    private SailingBlocks() {
    }

    /** The one-block sails (only the fore-and-aft sail; square sails are built from yards). */
    public static List<RegistryEntry<Block, SailBlock>> sails() {
        return List.of(FORE_AND_AFT_SAIL);
    }

    /** The sail type of a one-block sail, or null when the block is not one. */
    public static @Nullable SailType typeOf(Block block) {
        return block instanceof SailBlock s ? s.type() : null;
    }

    private static RegistryEntry<Block, SailBlock> sail(String name, SailType type) {
        return ModRegistry.blockWithItem(name, () -> new SailBlock(type, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                .strength(0.8f).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));
    }

    public static void init() {
    }
}

package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.force.SailType;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/** Sail blocks (one per {@link SailTypes} entry) and the sail winch. */
public final class SailingBlocks {

    public static final RegistryEntry<Block, SailBlock> SMALL_SQUARE_SAIL = sail("small_square_sail", SailTypes.SMALL_SQUARE);
    public static final RegistryEntry<Block, SailBlock> LARGE_SQUARE_SAIL = sail("large_square_sail", SailTypes.LARGE_SQUARE);
    public static final RegistryEntry<Block, SailBlock> FORE_AND_AFT_SAIL = sail("fore_and_aft_sail", SailTypes.FORE_AND_AFT);
    public static final RegistryEntry<Block, SailWinchBlock> SAIL_WINCH = ModRegistry.blockWithItem("sail_winch",
            () -> new SailWinchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).ignitedByLava()));
    public static final RegistryEntry<Block, CapstanBlock> CAPSTAN = ModRegistry.blockWithItem("capstan",
            () -> new CapstanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD).ignitedByLava()));

    private SailingBlocks() {
    }

    public static List<RegistryEntry<Block, SailBlock>> sails() {
        return List.of(SMALL_SQUARE_SAIL, LARGE_SQUARE_SAIL, FORE_AND_AFT_SAIL);
    }

    /** The sail type of a block, or null when it is not a sail. */
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

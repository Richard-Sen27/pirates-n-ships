package com.richardsenger.piratesnships.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The empty block entity of the passive half of a two-block {@link net.minecraft.world.level.block.EntityBlock} (the
 * cannon's rear, the brig door's upper half): no data, no ticker, no renderer; the other half keeps the real one.
 *
 * <p>Why the passive half needs one at all (FIX3): {@code BlockState#hasBlockEntity} is true for every state of an
 * {@code EntityBlock}, so worldgen ({@code WorldGenRegion#setBlock} on a proto chunk) leaves a {@code DUMMY} block
 * entity tag at every such position, and when the chunk becomes a level chunk {@code LevelChunk#promotePendingBlockEntity}
 * asks {@code newBlockEntity} for it: a {@code null} there logs "Tried to load a block entity for block ... but failed".
 * Vanilla's bed answers the same way (both halves carry a {@code BedBlockEntity}). Each block registers its own type
 * (a type's valid blocks are fixed) and passes it in.
 */
public class PartBlockEntity extends BlockEntity {

    public PartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}

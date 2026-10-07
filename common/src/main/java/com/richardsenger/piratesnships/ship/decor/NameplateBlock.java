package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The nameplate (design.md §4.8): vanilla {@link LadderBlock} placement and shape (a 3-pixel slab of space against a
 * wall, facing away from it, waterloggable). Not climbable, because climbing comes from {@code #minecraft:climbable}.
 * Its look is the hand-made Blockbench model {@code block/nameplate} ({@code art/models/nameplate.bbmodel}): a dark oak
 * board with a raised spruce panel and bevelled edges, hung 1 px off the wall on two iron brackets.
 * A subclass because the vanilla constructor is protected (it keeps LadderBlock's codec, whose type is fixed).
 * The ship's name on the board comes from {@link NameplateBlockEntity}.
 */
public class NameplateBlock extends LadderBlock implements EntityBlock {

    public NameplateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NameplateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ShipDecor.NAMEPLATE_BLOCK_ENTITY.get()) return null;
        return (l, p, s, be) -> ((NameplateBlockEntity) be).serverTick();
    }
}

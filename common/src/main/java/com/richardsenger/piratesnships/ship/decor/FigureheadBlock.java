package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A decorative bow figurehead (design.md §4.8). Its carved front faces the direction the placing player looks,
 * so a player standing on deck and facing the bow places it looking out to sea. No other behavior.
 * <p>
 * Each design is a hand-made Blockbench model ({@code art/models/figurehead_<design>.bbmodel}) of carved, painted wood:
 * a mounting plate on the side opposite {@link #FACING} (against the hull block behind it) and a forward-leaning figure
 * that reaches up to a block beyond the front face and hangs a little below the block, like a figurehead under a
 * bowsprit. Only the look extends past the block; the collision and selection shape stay the full block.
 */
public class FigureheadBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<FigureheadBlock> CODEC = simpleCodec(FigureheadBlock::new);

    public FigureheadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
}

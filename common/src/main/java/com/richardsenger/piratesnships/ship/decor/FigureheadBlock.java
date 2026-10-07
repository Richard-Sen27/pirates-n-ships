package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A decorative bow figurehead (design.md §4.8). It mounts on the block that was clicked: placed against a hull side, its
 * mounting plate sits on that block and the figure looks away from it, i.e. toward the player standing in front of the
 * bow ({@link #FACING} = the clicked face). Placed on top of or under a block, it looks toward the placing player
 * (the opposite of the player's horizontal direction). No other behavior.
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
        return defaultBlockState().setValue(FACING, placementFacing(context.getClickedFace(), context.getHorizontalDirection()));
    }

    /**
     * The facing for a figurehead placed against {@code clickedFace} by a player looking {@code playerLooking}: a
     * horizontal clicked face puts the plate on the clicked block, a top or bottom face turns the figure toward the player.
     */
    static net.minecraft.core.Direction placementFacing(net.minecraft.core.Direction clickedFace, net.minecraft.core.Direction playerLooking) {
        return clickedFace.getAxis().isHorizontal() ? clickedFace : playerLooking.getOpposite();
    }
}

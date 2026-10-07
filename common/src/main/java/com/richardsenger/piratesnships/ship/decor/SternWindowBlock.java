package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stern window (ART2, design.md §4.8): a full block for a wall opening, a framed cabin window of four panes with a
 * brass sill and a hood on its outer side ({@link #FACING}, towards the player who placed it, so build it from
 * outside the cabin) and a window stool inside. Using it opens or closes the dark oak {@link #SHUTTERS} on the outer
 * side. Open, it lets sky light through like glass. Hand-made models {@code block/stern_window} and
 * {@code block/stern_window_shutters} ({@code art/models/stern_window.bbmodel}, cutout render type for the glass).
 */
public class SternWindowBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SternWindowBlock> CODEC = simpleCodec(SternWindowBlock::new);
    public static final BooleanProperty SHUTTERS = BooleanProperty.create("shutters");

    public SternWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHUTTERS, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHUTTERS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            toggle(level, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens closed shutters or closes open ones. Server side. */
    public static void toggle(Level level, BlockPos pos, BlockState state, Player player) {
        boolean close = !state.getValue(SHUTTERS);
        level.setBlock(pos, state.setValue(SHUTTERS, close), Block.UPDATE_ALL);
        level.playSound(null, pos, close ? SoundEvents.WOODEN_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, close ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(SHUTTERS);
    }
}

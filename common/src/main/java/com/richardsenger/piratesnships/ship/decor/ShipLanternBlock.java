package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The ship's lantern (ART2, design.md §4.8): a brass-framed lantern that stands on the floor, hangs from the ceiling
 * on a hook or hangs from a bracket on a wall, like a lever ({@link #FACE}; on a wall {@link #FACING} points away from
 * it). Light level 14 (set in its properties), waterloggable, no other behaviour. Hand-made models
 * {@code block/ship_lantern} (floor), {@code _ceiling} and {@code _wall} ({@code art/models/ship_lantern.bbmodel}).
 * Floor and ceiling need only a centre support (a fence or a beam will do), a wall a sturdy face.
 */
public class ShipLanternBlock extends FaceAttachedHorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<ShipLanternBlock> CODEC = simpleCodec(ShipLanternBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape FLOOR = Block.box(4.5, 0, 4.5, 11.5, 12.75, 11.5);
    private static final VoxelShape CEILING = Block.box(4.5, 2, 4.5, 11.5, 16, 11.5);
    private static final Map<Direction, VoxelShape> WALL = DecorShapes.horizontal(DecorShapes.b(4.5, 1, 4.5, 11.5, 15, 16));

    public ShipLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Block.canSupportCenter(level, pos.below(), Direction.UP);
            case CEILING -> Block.canSupportCenter(level, pos.above(), Direction.DOWN);
            case WALL -> super.canSurvive(state, level, pos);
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> WALL.get(state.getValue(FACING));
        };
    }
}

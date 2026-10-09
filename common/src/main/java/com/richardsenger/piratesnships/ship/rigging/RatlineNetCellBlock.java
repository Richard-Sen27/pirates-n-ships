package com.richardsenger.piratesnships.ship.rigging;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ratline net cell (RL2): one block of a net strung between two fittings, invisible (fitting A's renderer draws the
 * whole net) and climbable (in {@code minecraft:climbable}, so players climb the net like a ladder, also in a ship's plot
 * through Sable's {@code climbing_sub_levels} mixin). No collision; a small outline so the net can be broken by hand;
 * replaced by any block placed into it except another net. Waterloggable, no loot, no item. Its
 * {@link RatlineNetCellBlockEntity} names the net's fitting A; removing the cell in any way but Sable's assembly breaks
 * the whole net ({@link RatlineNets#onCellRemoved}).
 *
 * <p>{@link #FACING} carries no look: it is the frame of the stored offset to A, so a rotated disassembly (which turns
 * the property but never block entity data) keeps the offset right.
 *
 * <p><b>Boarding nets (RAIL5).</b> A cell of a net hung along the rail ({@link BoardingNets}) has {@link #BOARDING} set
 * and its {@link #FACING} on the net's outboard side (still the frame of its offsets, placed that way). It holds a
 * boarder who comes in from outboard ({@link #entityInside}), cannot be mined or built over, and a cutlass cuts it
 * ({@link #attack}).
 */
public class RatlineNetCellBlock extends Block implements SimpleWaterloggedBlock, EntityBlock {

    public static final MapCodec<RatlineNetCellBlock> CODEC = simpleCodec(RatlineNetCellBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Waterlogging.WATERLOGGED;
    /** RAIL5: a cell of a boarding net along the rail. */
    public static final BooleanProperty BOARDING = BooleanProperty.create("boarding");

    private static final VoxelShape OUTLINE = box(5, 5, 5, 11, 11, 11);

    public RatlineNetCellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false)
                .setValue(BOARDING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, BOARDING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** A block placed into the net takes the cell's place (and so breaks the net); a second net does not. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !state.getValue(BOARDING) && !context.getItemInHand().is(RiggingContent.NET_ITEM.get());
    }

    /** RAIL5: a boarding net holds a boarder coming over the rail ({@link BoardingNets#onInside}). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(BOARDING)) {
            BoardingNets.onInside(state, level, pos, entity);
        }
    }

    /** RAIL5: a cutlass hit on a boarding net ({@link BoardingNets#hit}). */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(BOARDING)) {
            BoardingNets.hit(level, pos, player);
        }
    }

    /** RAIL5: a boarding net is not mined; a cutlass cuts it ({@link #attack}). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(BOARDING) ? 0f : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !Waterlogging.isWaterlogged(state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        RatlineNets.onCellRemoved(level, pos, movedByPiston, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RatlineNetCellBlockEntity(pos, state);
    }
}

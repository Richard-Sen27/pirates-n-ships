package com.richardsenger.piratesnships.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * The vanilla waterlogging pattern in one place (WLOG1, docs/design.md §4.8): every block of ours whose shape is not a
 * full cube is a {@link net.minecraft.world.level.block.SimpleWaterloggedBlock} with {@link #WATERLOGGED} in its state
 * definition (default {@code false}) and calls these helpers from the places vanilla does:
 * <ul>
 *   <li>{@code getStateForPlacement}: {@link #placed} (a water source at the clicked position),</li>
 *   <li>{@code updateShape}: {@link #tickFluid} before anything else (a waterlogged block lets its water flow),</li>
 *   <li>{@code getFluidState}: {@link #fluid},</li>
 *   <li>a part placed or removed by code (the other half of a two-block cannon, hammock or cot, a plank segment):
 *       {@link #at} for the new part's own position, {@link #leftBehind} instead of plain air when clearing it.</li>
 * </ul>
 * Bucket filling and emptying ({@code placeLiquid}, {@code pickupBlock}) come from the interface itself. Every helper
 * leaves a state without the property alone, so it is safe on any block. The rule and its named exceptions are
 * {@link WaterloggingRules}.
 */
public final class Waterlogging {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private Waterlogging() {
    }

    /** Whether {@code state} carries water in its own cell. */
    public static boolean isWaterlogged(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED);
    }

    /** {@code state} waterlogged exactly when a water source is at {@code pos} now (vanilla's placement rule). */
    public static BlockState at(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.hasProperty(WATERLOGGED)) return state;
        return state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    /** {@link #at} for a placement; {@code null} (the block refuses the spot) stays {@code null}. */
    public static @Nullable BlockState placed(@Nullable BlockState state, BlockPlaceContext context) {
        return state == null ? null : at(state, context.getLevel(), context.getClickedPos());
    }

    /** The fluid of {@code state}: a still water source when waterlogged, otherwise {@code dry} (the super call). */
    public static FluidState fluid(BlockState state, FluidState dry) {
        return isWaterlogged(state) ? Fluids.WATER.getSource(false) : dry;
    }

    /** From {@code updateShape}: a waterlogged block schedules its water's tick, as vanilla's waterloggables do. */
    public static void tickFluid(BlockState state, LevelAccessor level, BlockPos pos) {
        if (isWaterlogged(state)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }

    /** What clearing {@code removed} by code should leave: its water when it was waterlogged, else air. */
    public static BlockState leftBehind(BlockState removed) {
        return removed.getFluidState().createLegacyBlock();
    }
}

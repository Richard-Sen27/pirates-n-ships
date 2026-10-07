package com.richardsenger.piratesnships.chart.sample;

import com.richardsenger.piratesnships.chart.data.CellClass;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Set;

/**
 * Which {@link CellClass} a column of the world is (work package MAP1), without world access: the top block of the
 * column (the {@code MOTION_BLOCKING} heightmap: solid blocks, leaves and anything holding a fluid), the block above
 * it (a thin snow layer is not motion blocking) and the water depth.
 *
 * <p>Vanilla blocks are listed by hand, so the classification also works without loaded tags (JUnit); the vanilla
 * tags {@code #sand}, {@code #ice} and {@code #snow} add modded blocks in a running game.
 */
public final class ChartClassifier {

    /** What the top of a column is made of. */
    public enum Surface { NONE, WATER, SAND, SNOW, GROUND }

    private static final Set<Block> SAND = Set.of(Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND, Blocks.SANDSTONE,
            Blocks.RED_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL);
    private static final Set<Block> SNOW = Set.of(Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE,
            Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.FROSTED_ICE);

    private ChartClassifier() {
    }

    public static Surface surface(BlockState state) {
        if (state.isAir()) return Surface.NONE;
        if (state.getFluidState().getType().isSame(Fluids.WATER)) return Surface.WATER;
        Block b = state.getBlock();
        if (SNOW.contains(b) || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW)) return Surface.SNOW;
        if (SAND.contains(b) || state.is(BlockTags.SAND)) return Surface.SAND;
        return Surface.GROUND;
    }

    /**
     * The class of a column whose top block is {@code top} with {@code above} on it; {@code waterDepth} counts the
     * water blocks down to the ground (only read for water). Water up to {@code shallowDepth} deep is shallow.
     */
    public static CellClass classify(BlockState top, BlockState above, int waterDepth, int shallowDepth) {
        Surface s = surface(top);
        if (s != Surface.WATER && surface(above) == Surface.SNOW) return CellClass.SNOW_ICE;
        return classify(s, waterDepth, shallowDepth);
    }

    public static CellClass classify(Surface surface, int waterDepth, int shallowDepth) {
        return switch (surface) {
            case NONE -> CellClass.UNKNOWN;
            case WATER -> waterDepth <= shallowDepth ? CellClass.SHALLOW_WATER : CellClass.DEEP_WATER;
            case SAND -> CellClass.BEACH;
            case SNOW -> CellClass.SNOW_ICE;
            case GROUND -> CellClass.LAND;
        };
    }
}

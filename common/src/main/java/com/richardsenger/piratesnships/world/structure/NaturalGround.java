package com.richardsenger.piratesnships.world.structure;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks world generation leaves on a port's site that a structure may clear or cut away (STR8): soil, sand,
 * gravel, clay, the overworld's stone, snow and ice, leaves, and plants (anything replaceable that is not a fluid).
 * Used by the trail bed's cut ({@link TrailBedProcessor}) and the door clearing ({@link DoorClearing}), so neither
 * touches a block a structure piece placed (planks, bricks, furniture, fences, lanterns).
 */
public final class NaturalGround {

    private NaturalGround() {
    }

    /** Whether {@code state} is natural ground or vegetation (never air, never a fluid). */
    public static boolean is(BlockState state) {
        if (state.isAir() || isFluid(state)) return false;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE)
                || state.is(BlockTags.TERRACOTTA)) {
            return true;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.MUD)) {
            return true;
        }
        // grass, flowers, ferns, dead bushes, seagrass out of the water, snow layers: replaceable plants
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** Whether {@code state} is a liquid block (water, lava) or a waterlogged plant: never cleared nor filled over. */
    public static boolean isFluid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }
}

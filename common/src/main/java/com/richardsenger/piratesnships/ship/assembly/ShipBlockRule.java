package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The one rule for "is this block state part of a ship" (assembly gather) and "may a ship block be put here"
 * (disassembly target check).
 *
 * <p>Ship blocks: everything except air, fluids (water, lava, and fluid-only blocks such as kelp, seagrass and bubble
 * columns, i.e. blocks with a fluid that are not waterloggable), {@link #TERRAIN} and {@link #NEVER_ASSEMBLE}.
 * Waterlogged ship blocks stay ship blocks. Docks are not special: a dock touching the hull is gathered like any other
 * build, so moor with a one-block gap of air or water. The block limit stops a gather that runs into a pier or town.
 */
public final class ShipBlockRule {

    /** Natural world blocks (soil, stone, sand, ores, ice, leaves, plants, coral …). Never part of a ship. */
    public static final TagKey<Block> TERRAIN = tag("terrain");
    /** For pack makers: blocks that must never move with a ship. */
    public static final TagKey<Block> NEVER_ASSEMBLE = tag("never_assemble");

    private ShipBlockRule() {
    }

    public static boolean isShipBlock(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (!state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        return !state.is(TERRAIN) && !state.is(NEVER_ASSEMBLE);
    }

    /** Whether disassembly may overwrite this world state: air, water or a replaceable block (not lava). */
    public static boolean isFreeForShip(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return state.canBeReplaced() || state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
    }

    /** Sea water for the water rules: a water source block. */
    public static boolean isWaterSource(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
    }

    /** Any water block (source or flowing), which the hull drain removes. */
    public static boolean isWaterBlock(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name));
    }
}

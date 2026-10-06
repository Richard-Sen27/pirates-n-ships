package com.richardsenger.piratesnships.ship.hull.world;

import com.richardsenger.piratesnships.ship.hull.CellFaces;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Turns a block into a hull cell kind. Rules, first match wins:
 * <ol>
 *     <li>{@code #pirates_n_ships:not_watertight} → {@link CellKind#AIR} (pack-maker override, ships empty).</li>
 *     <li>{@code #pirates_n_ships:watertight} → {@link CellKind#SOLID} (pack-maker override, ships empty).</li>
 *     <li>Doors, trapdoors, fence gates → {@link CellKind#OPENING}, open according to their {@code open} property.</li>
 *     <li>Gappy blocks → {@link CellKind#AIR}: {@code #minecraft:fences}, {@code #minecraft:walls},
 *     {@code #minecraft:climbable} (ladders, vines, scaffolding), iron bars and glass panes ({@link IronBarsBlock}),
 *     chains.</li>
 *     <li>Ship-building blocks → {@link CellKind#SOLID}: slabs and stairs ({@code #minecraft:slabs},
 *     {@code #minecraft:stairs}, or the vanilla block classes, so modded ones count too) and glass
 *     ({@code #minecraft:impermeable}). A half-slab gap is not a leak.</li>
 *     <li>Air, fluid blocks and replaceables (grass, snow layers, …) → {@link CellKind#AIR}. Waterlogging does not
 *     matter: a waterlogged slab is still a slab.</li>
 *     <li>Otherwise: a full-cube collision shape → {@link CellKind#SOLID}, anything else (torches, buttons, carpets,
 *     signs, …) → {@link CellKind#AIR}.</li>
 * </ol>
 */
public final class HullBlockClassifier {

    private HullBlockClassifier() {
    }

    public static CellKind classify(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return CellKind.AIR;
        if (state.is(HullTags.NOT_WATERTIGHT)) return CellKind.AIR;
        if (state.is(HullTags.WATERTIGHT)) return CellKind.SOLID;
        if (isOpeningBlock(state)) return CellKind.OPENING;
        Block block = state.getBlock();
        if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.CLIMBABLE)
                || block instanceof IronBarsBlock || block instanceof ChainBlock) {
            return CellKind.AIR;
        }
        if (state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.IMPERMEABLE)
                || block instanceof SlabBlock || block instanceof StairBlock) {
            return CellKind.SOLID;
        }
        if (block instanceof LiquidBlock || state.canBeReplaced()) return CellKind.AIR;
        return Block.isShapeFullBlock(state.getCollisionShape(level, pos)) ? CellKind.SOLID : CellKind.AIR;
    }

    /**
     * The {@link CellFaces} mask of the faces of a solid or opening cell that its block does not fully cover; 0 for a
     * full block and for air cells. A non-zero mask makes the cell a <em>partial</em> cell ({@code HullGrid#isPartial}).
     * Solid cells use their collision shape. Openings use the union over both open states (a toggle never triggers a
     * re-analysis, so the mask must hold for either state).
     */
    public static int uncoveredFaces(BlockState state, BlockGetter level, BlockPos pos, CellKind kind) {
        if (kind == CellKind.SOLID) {
            return uncoveredFaces(state.getCollisionShape(level, pos));
        }
        if (kind == CellKind.OPENING) {
            int mask = uncoveredFaces(state.getCollisionShape(level, pos));
            if (state.hasProperty(BlockStateProperties.OPEN)) {
                BlockState other = state.setValue(BlockStateProperties.OPEN, !state.getValue(BlockStateProperties.OPEN));
                mask |= uncoveredFaces(other.getCollisionShape(level, pos));
            }
            return mask;
        }
        return 0;
    }

    /** The {@link CellFaces} mask of the cube faces {@code shape} does not fully cover (0 for a full cube). */
    public static int uncoveredFaces(VoxelShape shape) {
        if (Block.isShapeFullBlock(shape)) {
            return 0;
        }
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (!Block.isFaceFull(shape, d)) {
                mask |= CellFaces.bit(d.get3DDataValue());
            }
        }
        return mask;
    }

    /** Whether an opening block is open (only meaningful when {@link #classify} returned OPENING). */
    public static boolean isOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    private static boolean isOpeningBlock(BlockState state) {
        Block b = state.getBlock();
        return (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock)
                && state.hasProperty(BlockStateProperties.OPEN);
    }
}

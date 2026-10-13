package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Half slabs on a trail's height steps (STR9, the human: "on height steps it would be fantastical if half slabs could
 * be used to make it smoother"). Since STR8 every street, trail and lane piece lies level on its own bed row, one row
 * above or below the piece it hangs from ({@link TrailLevels}); where two such pieces meet one row apart the walker had
 * to jump. Run by {@link PortStructure#afterPlace} per chunk, last (after the pieces, the beard, the bed's cut and fill and the door clearing):
 * along the joint the lower piece's last row of cells gets a bottom slab on its paving, so the step is two half steps.
 *
 * <ul>
 *     <li>a joint is a trail piece and the trail piece (or the start) it hangs from, read from the trail's first
 *     junction ({@link Step}); its rows are the pieces' bed rows (a trail's {@code box.minY() - 1}, the start's
 *     {@code box.minY()});</li>
 *     <li>a cell gets its slab where the lower piece's paving is a full block with air (or a plant, snow) above it,
 *     and the higher piece's cell beyond it is a full block with room above: so never on water (the boardwalk's), never
 *     in a wall or on a kerb;</li>
 *     <li>the slab is the paving's own where one exists (cobblestone, stone bricks, mossy ones, andesite, sandstone,
 *     planks), else a cobblestone slab (dirt path, gravel, coarse dirt, grass).</li>
 * </ul>
 * Only cells inside the chunk being placed are touched. {@code world.structures.trail_slabs} switches it off.
 */
public final class TrailSlabs {

    /** How far a joint's row of slabs runs to each side of the connector (a square is 15 wide). */
    static final int HALF_WIDTH = 8;

    /** A paving block and its slab. */
    private static final Map<Block, Block> SLABS = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB),
            Map.entry(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB),
            Map.entry(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB),
            Map.entry(Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB),
            Map.entry(Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB),
            Map.entry(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB),
            Map.entry(Blocks.STONE, Blocks.STONE_SLAB),
            Map.entry(Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB),
            Map.entry(Blocks.ANDESITE, Blocks.ANDESITE_SLAB),
            Map.entry(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB),
            Map.entry(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB),
            Map.entry(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB),
            Map.entry(Blocks.SAND, Blocks.SANDSTONE_SLAB),
            Map.entry(Blocks.RED_SAND, Blocks.RED_SANDSTONE_SLAB),
            Map.entry(Blocks.BRICKS, Blocks.BRICK_SLAB),
            Map.entry(Blocks.OAK_PLANKS, Blocks.OAK_SLAB),
            Map.entry(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB),
            Map.entry(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB),
            Map.entry(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_SLAB),
            Map.entry(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_SLAB));

    private TrailSlabs() {
    }

    /**
     * One joint one row high: the lower piece's cells along it ({@code cells}, at {@code row}, the lower bed row) and the
     * direction from the lower piece to the higher one.
     */
    public record Step(List<BlockPos> cells, int row, Direction up, PoolElementStructurePiece higher) {
    }

    /** Lays the slabs of {@code pieces}' steps inside {@code chunk}; returns how many were placed. */
    public static int place(WorldGenLevel level, BoundingBox chunk, List<StructurePiece> pieces, StructureTemplateManager templates,
                            PortKind kind) {
        int placed = 0;
        for (Step step : steps(pieces, templates, 1)) {
            java.util.Set<Long> higher = null;
            for (BlockPos cell : step.cells()) {
                if (!chunk.isInside(cell)) continue;
                BlockState slab;
                if (chunk.isInside(cell.relative(step.up()))) {
                    slab = slabAt(level, cell, step.row(), step.up(), kind);
                } else {
                    // the higher piece's cell lies in a chunk not placed yet: read it from its template
                    if (higher == null) higher = walkable(step.higher(), templates);
                    slab = higher.contains(column(cell.relative(step.up()))) ? slabOn(level, cell, step.row(), kind) : null;
                }
                if (slab == null) continue;
                level.setBlock(cell.atY(step.row() + 1), slab, Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        return placed;
    }

    /**
     * The slab for the cell at ({@code cell}, {@code row}) of a joint rising toward {@code up}, or null when the cell is
     * not a walkable step (no full paving, something above it, no full block beyond it, water).
     */
    static @Nullable BlockState slabAt(BlockGetter level, BlockPos cell, int row, Direction up, PortKind kind) {
        BlockPos beyond = cell.relative(up).atY(row + 1);
        if (!fullBlock(level, beyond, level.getBlockState(beyond))) return null;
        BlockState high = level.getBlockState(beyond.above());
        if (!high.isAir() && !high.canBeReplaced()) return null;
        return slabOn(level, cell, row, kind);
    }

    /** The slab for the lower cell alone: full paving at {@code row}, air or a plant above it, no water. */
    static @Nullable BlockState slabOn(BlockGetter level, BlockPos cell, int row, PortKind kind) {
        BlockPos paving = cell.atY(row);
        BlockState ground = level.getBlockState(paving);
        BlockState over = level.getBlockState(cell.atY(row + 1));
        if (NaturalGround.isFluid(over) || NaturalGround.isFluid(ground)) return null;
        if (!fullBlock(level, paving, ground) || !(over.isAir() || over.canBeReplaced())) return null;
        return slabFor(ground, kind);
    }

    /**
     * The columns of a levelled trail piece whose template has a block on row 0 (its paving, a jigsaw's final state),
     * or that lie within one block of one (the bed's filled margin), and none on row 1: where its bed can be walked onto.
     */
    static java.util.Set<Long> walkable(PoolElementStructurePiece piece, StructureTemplateManager templates) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        StructureTemplate template = DoorClearing.template(piece, templates).orElse(null);
        if (template == null) return out;
        net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings settings =
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(piece.getRotation());
        net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag blocks = saved.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        java.util.Set<Long> ground = new java.util.HashSet<>();
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.nbt.CompoundTag block = blocks.getCompound(i);
            net.minecraft.nbt.ListTag pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            int y = pos.getInt(1);
            if (y > 1) continue;
            String name = palette.getCompound(block.getInt("state")).getString("Name");
            if (name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:structure_void")) continue;
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, new BlockPos(pos.getInt(0), 0, pos.getInt(2)))
                    .offset(piece.getPosition());
            (y == 0 ? ground : covered).add(column(world));
        }
        // the bed processor fills the bed row within one block of the bed with ground as well
        for (Long c : ground) {
            int x = (int) (c >> 32);
            int z = (int) (long) c;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long n = column(new BlockPos(x + dx, 0, z + dz));
                    if (!covered.contains(n)) out.add(n);
                }
            }
        }
        return out;
    }

    private static long column(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }

    private static boolean fullBlock(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof SlabBlock) && state.isCollisionShapeFullBlock(level, pos);
    }

    /** The bottom slab for a paving block: its own, or a cobblestone slab. */
    public static BlockState slabFor(BlockState paving, PortKind kind) {
        Block slab = SLABS.getOrDefault(paving.getBlock(), Blocks.COBBLESTONE_SLAB);
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /**
     * The joints of {@code pieces} whose two bed rows differ by exactly {@code rise} (the start counts as a trail): for
     * each, the lower piece's cells along the joint. The tests read every rise.
     */
    public static List<Step> steps(List<StructurePiece> pieces, StructureTemplateManager templates, int rise) {
        List<Step> out = new ArrayList<>();
        List<LandFilter.Node> nodes = PortStructure.landNodes(pieces);
        for (int i = 1; i < pieces.size(); i++) {
            if (!isTrail(pieces.get(i)) || !(pieces.get(i) instanceof PoolElementStructurePiece child) || child.getJunctions().isEmpty()) continue;
            int p = nodes.get(i).parent();
            if (p < 0 || !(p == 0 || isTrail(pieces.get(p))) || !(pieces.get(p) instanceof PoolElementStructurePiece parent)) continue;
            JigsawJunction joint = child.getJunctions().get(0);
            StructureTemplate.StructureBlockInfo jigsaw = WallRuns.jigsawAt(parent, templates, joint.getSourceX(), joint.getSourceZ());
            if (jigsaw == null) continue;
            Direction out1 = net.minecraft.world.level.block.JigsawBlock.getFrontFacing(jigsaw.state());
            if (out1.getAxis().isVertical()) continue;
            int childRow = bedRow(child, false);
            int parentRow = bedRow(parent, p == 0 && !isTrail(parent));
            if (Math.abs(childRow - parentRow) != rise) continue;
            BlockPos parentCell = jigsaw.pos();
            BlockPos childCell = parentCell.relative(out1);
            boolean childLower = childRow < parentRow;
            BlockPos first = childLower ? childCell : parentCell;
            Direction up = childLower ? out1.getOpposite() : out1;
            BoundingBox lower = childLower ? child.getBoundingBox() : parent.getBoundingBox();
            BoundingBox higher = childLower ? parent.getBoundingBox() : child.getBoundingBox();
            List<BlockPos> cells = new ArrayList<>();
            Direction side = up.getClockWise();
            for (Direction way : new Direction[] {side, side.getOpposite()}) {
                for (int t = way == side ? 0 : 1; t <= HALF_WIDTH; t++) {
                    BlockPos c = first.relative(way, t);
                    if (!insideXZ(lower, c) || !insideXZ(higher, c.relative(up))) break;
                    cells.add(c);
                }
            }
            out.add(new Step(cells, Math.min(childRow, parentRow), up, childLower ? parent : child));
        }
        return out;
    }

    /** A trail's bed row ({@code box.minY() - 1}, STR8), or the start's paving row ({@code box.minY()}). */
    static int bedRow(StructurePiece piece, boolean start) {
        return start ? piece.getBoundingBox().minY() : piece.getBoundingBox().minY() - 1;
    }

    static boolean isTrail(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p && p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
    }

    private static boolean insideXZ(BoundingBox box, BlockPos pos) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
    }
}

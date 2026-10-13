package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lays a street, trail or lane level on a levelled bed (STR8, processor type {@code pirates_n_ships:trail_bed}; the
 * human: the streets rode the terrain, the kerb stepping up and down every few blocks). A terrain-matching piece gets
 * vanilla's gravity processor, which puts every column of the template on the surface; this processor runs after it (in
 * {@link #finalizeProcessing}, before the boardwalk) and:
 * <ol>
 *     <li>puts every block back where the piece would place it rigidly, one row down: the template's row 0 on the bed
 *     row {@code box.minY() - 1}, which {@code PortStructure.plan} chose ({@link TrailLevels}: the surface under the
 *     piece, at most one block from its neighbour's);</li>
 *     <li>cuts the natural ground ({@link NaturalGround}) above the bed row away, up to {@link #rows()} rows, in every
 *     column within one block of the bed (the template's row 0) and inside the piece's footprint, where the template
 *     has no block of its own;</li>
 *     <li>fills the air under the bed and, in the margin, the bed row itself with the ground found below (grass on
 *     soil, sand on sand, stone on stone), up to {@link #rows()} rows; never over water, where the boardwalk lays
 *     boards on the sea surface instead.</li>
 * </ol>
 * Only positions inside the placement's bounding box (the chunk being placed) are cut or filled. With
 * {@code world.structures.level_trails} off it changes nothing (the trail follows the ground block by block, as before
 * STR8). The processor lists that name it ({@code village_street_level}, {@code pirate_island_trail_level},
 * {@code navy_outpost_lane}) are new in STR8, so pieces of ports planned before keep their old lists and their look.
 */
public final class TrailBedProcessor extends StructureProcessor {

    public static final TrailBedProcessor INSTANCE = new TrailBedProcessor();
    public static final MapCodec<TrailBedProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    /** Rows of natural ground cut away above the bed row (STR8; since STR9 at least {@link #rows()}). */
    public static final int CUT_ROWS = 6;
    /** Rows of air filled under the bed row (STR8; since STR9 at least {@link #rows()}). */
    public static final int FILL_ROWS = 6;

    /**
     * STR9: the rows cut and filled, enough for a bed {@code world.structures.max_trail_step_rows} off the ground under
     * the piece's centre and two more for the ground beside it (at least STR8's six).
     */
    public static int rows() {
        return Math.max(CUT_ROWS, WorldConfig.STRUCTURE_MAX_TRAIL_STEP_ROWS.get() + 2);
    }

    private TrailBedProcessor() {
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                       List<StructureBlockInfo> originalBlockInfos,
                                                       List<StructureBlockInfo> processedBlockInfos, StructurePlaceSettings settings) {
        if (!WorldConfig.STRUCTURE_LEVEL_TRAILS.get() || processedBlockInfos.size() != originalBlockInfos.size()) {
            return processedBlockInfos;
        }
        BoundingBox chunk = settings.getBoundingBox();
        List<StructureBlockInfo> out = new ArrayList<>(processedBlockInfos.size() + 64);
        Set<BlockPos> own = new HashSet<>();
        Set<Long> bed = new HashSet<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int bedRow = offset.getY() - 1;
        for (int i = 0; i < processedBlockInfos.size(); i++) {
            StructureBlockInfo original = originalBlockInfos.get(i);
            StructureBlockInfo processed = processedBlockInfos.get(i);
            BlockPos rigid = StructureTemplate.calculateRelativePosition(settings, original.pos()).offset(offset).below();
            out.add(new StructureBlockInfo(rigid, processed.state(), processed.nbt()));
            own.add(rigid);
            if (original.pos().getY() == 0) bed.add(column(rigid.getX(), rigid.getZ()));
            minX = Math.min(minX, rigid.getX());
            maxX = Math.max(maxX, rigid.getX());
            minZ = Math.min(minZ, rigid.getZ());
            maxZ = Math.max(maxZ, rigid.getZ());
        }
        if (bed.isEmpty()) return out;
        int rows = rows();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onBed = bed.contains(column(x, z));
                if (!onBed && !nearBed(bed, x, z)) continue;
                if (chunk != null && (x < chunk.minX() || x > chunk.maxX() || z < chunk.minZ() || z > chunk.maxZ())) continue;
                // cut: natural ground above the bed row where the template has nothing
                for (int y = bedRow + 1; y <= bedRow + rows; y++) {
                    cursor.set(x, y, z);
                    if (own.contains(cursor)) continue;
                    BlockState here = level.getBlockState(cursor);
                    if (NaturalGround.is(here)) out.add(new StructureBlockInfo(cursor.immutable(), Blocks.AIR.defaultBlockState(), null));
                }
                // fill: never over water (the boardwalk's), from the bed row (the margin) or the row under it (the bed) down
                cursor.set(x, bedRow, z);
                if (NaturalGround.isFluid(level.getBlockState(cursor))) continue;
                BlockState ground = groundBelow(level, x, bedRow, z, rows);
                if (ground == null) continue;
                int top = own.contains(cursor) ? bedRow - 1 : bedRow;
                for (int y = top; y > bedRow - 1 - rows; y--) {
                    cursor.set(x, y, z);
                    BlockState here = level.getBlockState(cursor);
                    if (NaturalGround.isFluid(here)) break;
                    if (!here.isAir() && !here.canBeReplaced()) {
                        if (y < bedRow) break;
                        continue;
                    }
                    out.add(new StructureBlockInfo(cursor.immutable(), y == bedRow ? ground : under(ground), null));
                }
            }
        }
        return out;
    }

    /** The first solid block under the bed row in this column (within {@code rows} + 4 rows), or null. */
    private static BlockState groundBelow(ServerLevelAccessor level, int x, int bedRow, int z, int rows) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, bedRow, z);
        for (int y = bedRow; y >= bedRow - rows - 4; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (NaturalGround.isFluid(state)) return null;
            if (!state.isAir() && !state.canBeReplaced()) return surface(state);
        }
        return null;
    }

    /** The block to lay on top of a filled column whose ground is {@code ground}. */
    static BlockState surface(BlockState ground) {
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM)) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (ground.is(BlockTags.SAND)) return ground.getBlock().defaultBlockState();
        if (ground.is(Blocks.GRAVEL) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.SNOW_BLOCK)) return ground.getBlock().defaultBlockState();
        if (ground.is(BlockTags.BASE_STONE_OVERWORLD)) return ground.getBlock().defaultBlockState();
        return Blocks.DIRT.defaultBlockState();
    }

    /** The block under the top of a filled column: dirt under grass, sandstone under sand, the ground itself otherwise. */
    static BlockState under(BlockState top) {
        if (top.is(Blocks.GRASS_BLOCK)) return Blocks.DIRT.defaultBlockState();
        if (top.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (top.is(BlockTags.SAND)) return Blocks.SANDSTONE.defaultBlockState();
        if (top.is(Blocks.GRAVEL) || top.is(Blocks.SNOW_BLOCK)) return Blocks.DIRT.defaultBlockState();
        return top;
    }

    private static boolean nearBed(Set<Long> bed, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (bed.contains(column(x + dx, z + dz))) return true;
            }
        }
        return false;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return PortStructures.TRAIL_BED.get();
    }
}

package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.ship.assembly.ShipBlockRule;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.ShipSpot;
import com.richardsenger.piratesnships.world.port.ShipSpots;
import com.richardsenger.piratesnships.world.village.BerthMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep berths at new ports (STR7, design.md §10.1): after a port structure is placed in a chunk
 * ({@code PortStructure.afterPlace}) the sea floor round each berth is deepened so the largest shipped ship
 * ({@link ShipSpots#LARGEST_SHIPPED}, draught {@link ShipSpots#LARGEST_DRAUGHT}) lies there with
 * {@code world.ports.berth_depth} blocks of water under her keel.
 *
 * <p><b>The basin</b> of a berth ({@link #basin}) is every mooring {@code ShipSpot} tries there (alongside, on the side
 * away from the pier, centred and slid seaward up to 16 blocks) grown by {@code world.ports.ship_clearance}, so it
 * also reaches under the pier's deck. <b>The carve</b> ({@link #carveColumn}) only touches columns whose sea surface
 * row is not ground (the beach, a sand bar at the surface and every building stay): from the surface down to the
 * draught plus the berth depth, ground and plants (sand, gravel, stone, kelp, seagrass) become water; structure blocks
 * stay, and a pile or a mole standing on the old floor is carried down to the new one with its own block.
 *
 * <p>Worldgen runs this per chunk, so the structure's box is grown by {@link #margin} ({@code PortStructure}'s
 * {@code adjustBoundingBox}) to get the chunks round the berths placed as well. The beard never reaches the basin:
 * the pier, jetty and quay and their extensions are unbearded (STR2–STR4), and the carve runs after the terrain.
 */
public final class BerthBasins {

    private BerthBasins() {
    }

    /** A berth with the side its pier lies on (null when the berth is not on its piece's edge). */
    public record Moored(Berth berth, @Nullable Direction pierSide) {
    }

    /** Blocks below the sea surface that must be water at a berth: the largest draught plus the berth depth. */
    public static int depth() {
        return ShipSpots.LARGEST_DRAUGHT + WorldConfig.PORTS_BERTH_DEPTH.get();
    }

    /**
     * How far beyond the pieces' box a basin may reach: half the largest hull's length, the longest slide, the
     * clearance and a block.
     */
    public static int margin() {
        return (ShipSpots.LARGEST_SHIPPED.length() - 1) / 2 + ShipSpot.BERTH_SLIDES[ShipSpot.BERTH_SLIDES.length - 1]
                + WorldConfig.PORTS_SHIP_CLEARANCE.get() + 2;
    }

    /**
     * The columns to deepen round a berth (pure): the union of {@code hull}'s moorings on the side away from the pier
     * (both sides when the pier side is unknown) for every slide, grown by {@code clearance}.
     */
    public static ShipSpot.Rect basin(ShipSpot.Anchor berth, ShipSpot.Hull hull, int clearance) {
        List<Direction> sides = berth.pierSide() == null ? ShipSpot.berthSides(berth) : List.of(ShipSpot.berthSides(berth).get(0));
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Direction side : sides) {
            for (int slide : ShipSpot.BERTH_SLIDES) {
                ShipSpot.Rect r = ShipSpot.berthFootprint(hull, berth, side, slide);
                minX = Math.min(minX, r.minX());
                minZ = Math.min(minZ, r.minZ());
                maxX = Math.max(maxX, r.maxX());
                maxZ = Math.max(maxZ, r.maxZ());
            }
        }
        return new ShipSpot.Rect(minX, minZ, maxX, maxZ).inflate(clearance);
    }

    /**
     * The side of a berth its pier lies on, from the box of the piece that holds it: the perpendicular neighbour inside
     * the box while the other one is outside (the berth markers stand on the box's edge columns, ST1).
     */
    public static @Nullable Direction pierSide(BoundingBox piece, Berth berth) {
        Direction right = berth.bow().getClockWise();
        Direction left = berth.bow().getCounterClockWise();
        boolean inRight = insideXZ(piece, berth.pos().relative(right));
        boolean inLeft = insideXZ(piece, berth.pos().relative(left));
        return inRight == inLeft ? null : inRight ? right : left;
    }

    private static boolean insideXZ(BoundingBox box, BlockPos p) {
        return p.getX() >= box.minX() && p.getX() <= box.maxX() && p.getZ() >= box.minZ() && p.getZ() <= box.maxZ();
    }

    /** Every berth of the pieces with its pier side. */
    public static List<Moored> berths(List<StructurePiece> pieces, StructureTemplateManager templates) {
        List<Moored> out = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            for (Berth b : BerthMarkers.fromJigsaws(p.getElement().getShuffledJigsawBlocks(templates, p.getPosition(), p.getRotation(),
                    RandomSource.create(0L)))) {
                out.add(new Moored(b, pierSide(p.getBoundingBox(), b)));
            }
        }
        return out;
    }

    /** The basins of every berth of the pieces. */
    public static List<ShipSpot.Rect> basins(List<StructurePiece> pieces, StructureTemplateManager templates, int clearance) {
        List<ShipSpot.Rect> out = new ArrayList<>();
        for (Moored m : berths(pieces, templates)) {
            ShipSpot.Anchor anchor = new ShipSpot.Anchor(m.berth().pos().getX(), m.berth().pos().getZ(), m.berth().bow(), true, m.pierSide());
            out.add(basin(anchor, ShipSpots.LARGEST_SHIPPED, clearance));
        }
        return out;
    }

    /**
     * Deepens the basins of {@code pieces} inside {@code chunkBox} (the chunk being placed): every column of a basin is
     * carved ({@link #carveColumn}) down to {@link #depth()} below the berths' sea surface. Returns the columns carved.
     */
    public static int carve(WorldGenLevel level, BoundingBox chunkBox, List<StructurePiece> pieces, StructureTemplateManager templates) {
        // a chunk that is not there is never generated for the carve (worldgen always has its own chunk; a direct
        // placement in a loaded level, as /place or a GameTest does, may reach past the loaded area)
        if (!level.hasChunk(chunkBox.minX() >> 4, chunkBox.minZ() >> 4)) return 0;
        List<Moored> berths = berths(pieces, templates);
        if (berths.isEmpty()) return 0;
        int surface = berths.get(0).berth().pos().getY();
        int depth = depth();
        int carved = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (ShipSpot.Rect r : basins(pieces, templates, WorldConfig.PORTS_SHIP_CLEARANCE.get())) {
            int x0 = Math.max(r.minX(), chunkBox.minX()), x1 = Math.min(r.maxX(), chunkBox.maxX());
            int z0 = Math.max(r.minZ(), chunkBox.minZ()), z1 = Math.min(r.maxZ(), chunkBox.maxZ());
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (!done.add(BlockPos.asLong(x, 0, z))) continue;
                    int cx = x, cz = z;
                    if (carveColumn(y -> level.getBlockState(at.set(cx, y, cz)),
                            (y, s) -> level.setBlock(new BlockPos(cx, y, cz), s, Block.UPDATE_CLIENTS), surface, depth)) carved++;
                }
            }
        }
        return carved;
    }

    /** Writes a block of a column. */
    @FunctionalInterface
    public interface Writer {
        void set(int y, BlockState state);
    }

    /**
     * Carves one column (see the class comment): nothing when its surface row (or the one above) is ground; else every
     * ground or plant block from the surface down to {@code depth} below it becomes water, and a solid structure block
     * left standing on carved water is carried down with its own block to the first block that is not water. True when
     * something changed.
     */
    public static boolean carveColumn(ShipSpots.Column column, Writer writer, int surface, int depth) {
        if (ShipSpots.isGround(column.at(surface)) || ShipSpots.isGround(column.at(surface + 1))) return false;
        BlockState water = Blocks.WATER.defaultBlockState();
        int floor = surface - depth;
        boolean[] carved = new boolean[depth + 1];
        boolean changed = false;
        for (int y = surface; y >= floor; y--) {
            if (carvable(column.at(y))) {
                writer.set(y, water);
                carved[surface - y] = true;
                changed = true;
            }
        }
        if (!changed) return false;
        // a pile, a footing or a mole that stood on the ground just carved goes down to the new floor (a wale over
        // open water stays where it is: only a block whose own footing was carved away is carried down)
        for (int y = surface; y > floor; y--) {
            if (!carved[surface - y + 1] || !isStructure(column.at(y))) continue;
            BlockState s = column.at(y);
            for (int d = y - 1; d >= floor - 64 && column.at(d).is(Blocks.WATER); d--) writer.set(d, s);
            break;
        }
        return true;
    }

    /** Ground or a plant of the terrain (sand, stone, ice, kelp, seagrass, coral): carved to water. */
    static boolean carvable(BlockState s) {
        if (s.isAir() || s.is(Blocks.WATER)) return false;
        return s.is(ShipBlockRule.TERRAIN);
    }

    /** A solid block placed by the structure (a pile, a footing, a mole), not terrain. */
    static boolean isStructure(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(ShipBlockRule.TERRAIN) && s.canOcclude();
    }
}

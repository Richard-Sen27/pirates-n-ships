package com.richardsenger.piratesnships.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clears the ground in front of every door of a placed port structure (STR8, the human: "entrances of sub structures
 * are still blocked with one or multiple blocks"; his screenshots showed a snow-topped dirt or grass block squarely in
 * the cell before a hut's door). STR6's {@code cave_air} approach clears only the cells inside the piece's box; the cell
 * beyond it is the terrain's or a neighbour's, and the beard's fill and the surface rules leave ground there. Run by
 * {@link PortStructure#afterPlace} per chunk, after the pieces, so it sees the final ground:
 *
 * <ul>
 *     <li>for every door (lower half, read from the piece's template, so a door in a neighbouring chunk is known too),
 *     on both sides, the two cells in front of it on the door's two rows and the row above, and the cells diagonally
 *     beside the first one (a path meeting the door at an angle) on the door's rows;</li>
 *     <li>inside the door's own piece, and in the row above, only natural ground and plants go ({@link NaturalGround}:
 *     never the piece's own furniture);</li>
 *     <li>in a street, trail or lane piece or a decoration, whatever blocks a walker goes as well (a lantern post, a
 *     barrel, a kerb);</li>
 *     <li>in another building, a wall or a tower only natural ground goes: its own blocks are its walls (a door that
 *     opens onto another piece's wall is the generator's to fix, as STR6 did for the tavern).</li>
 * </ul>
 * Only cells inside the chunk being placed are touched (the others are cleared with their own chunk).
 * {@code world.structures.clear_doors} switches it off.
 */
public final class DoorClearing {

    /** How many cells in front of a door are cleared, on each side. */
    public static final int DEPTH = 2;

    /** A door's lower half: where it stands and its {@code facing}. */
    public record Door(BlockPos pos, Direction facing) {
    }

    private static final Pattern LOCATION = Pattern.compile("Left\\[([a-z0-9_.-]+:[a-z0-9_./-]+)\\]");
    private static final Map<StructureTemplate, Map<Rotation, Shape>> CACHE = new WeakHashMap<>();
    private static volatile List<Block> doorBlocks;

    private DoorClearing() {
    }

    /** Clears the doors' approaches of {@code pieces} inside {@code chunkBox}; returns how many blocks went. */
    public static int clear(WorldGenLevel level, BoundingBox chunkBox, List<StructurePiece> pieces, StructureTemplateManager templates) {
        int cleared = 0;
        Map<StructurePiece, Placed> placed = new java.util.IdentityHashMap<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            if (!piece.getBoundingBox().inflatedBy(DEPTH + 1).intersects(chunkBox)) continue;
            for (Door door : placed(p, templates, placed).doors()) {
                for (Direction side : new Direction[] {door.facing(), door.facing().getOpposite()}) {
                    for (int d = 1; d <= DEPTH; d++) {
                        BlockPos cell = door.pos().relative(side, d);
                        for (int dy = 0; dy <= 2; dy++) {
                            cleared += clearCell(level, chunkBox, cell.above(dy), p, pieces, dy == 2, templates, placed);
                        }
                    }
                    BlockPos first = door.pos().relative(side);
                    for (Direction lateral : new Direction[] {side.getClockWise(), side.getCounterClockWise()}) {
                        for (int dy = 0; dy <= 1; dy++) {
                            cleared += clearCell(level, chunkBox, first.relative(lateral).above(dy), p, pieces, true, templates, placed);
                        }
                    }
                }
            }
        }
        return cleared;
    }

    /**
     * Clears one cell if the rules allow it; 1 if a block went. A block a piece's template put there stays, unless the
     * piece is wayside ({@link #isWayside}: a trail or a decoration, not the door's own piece) and the block is in the
     * way; any other block goes when it is natural ground or a plant.
     */
    static int clearCell(WorldGenLevel level, BoundingBox chunkBox, BlockPos cell, PoolElementStructurePiece own,
                         List<StructurePiece> pieces, boolean naturalOnly, StructureTemplateManager templates,
                         Map<StructurePiece, Placed> placed) {
        if (!chunkBox.isInside(cell)) return 0;
        BlockState state = level.getBlockState(cell);
        if (state.isAir() || NaturalGround.isFluid(state)) return 0;
        StructurePiece owner = own.getBoundingBox().isInside(cell) ? own : owner(cell, own, pieces);
        boolean templateBlock = owner instanceof PoolElementStructurePiece o && placed(o, templates, placed).fills(cell);
        boolean go;
        if (templateBlock) {
            go = owner != own && !naturalOnly && isWayside(owner) && !DoorAccess.passable(level, cell, state);
        } else {
            go = NaturalGround.is(state);
        }
        if (!go) return 0;
        level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        // a tall plant's upper half would float
        BlockPos above = cell.above();
        BlockState up = level.getBlockState(above);
        if (chunkBox.isInside(above) && up.getBlock() instanceof DoublePlantBlock
                && up.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return 1;
    }

    /** A piece whose blocks may be cleared from a door's way: a street, trail or lane, or a decoration. */
    static boolean isWayside(StructurePiece piece) {
        if (!(piece instanceof PoolElementStructurePiece p)) return false;
        return p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING
                || StructureLayout.templateName(p.getElement().toString()).startsWith("deco_");
    }

    /** The piece other than {@code own} whose box holds {@code cell} (a trail's bed lies one row under its box), or null. */
    static StructurePiece owner(BlockPos cell, StructurePiece own, List<StructurePiece> pieces) {
        for (StructurePiece piece : pieces) {
            if (piece == own) continue;
            BoundingBox box = piece.getBoundingBox();
            boolean trail = piece instanceof PoolElementStructurePiece p
                    && p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
            if (cell.getX() >= box.minX() && cell.getX() <= box.maxX() && cell.getZ() >= box.minZ() && cell.getZ() <= box.maxZ()
                    && cell.getY() >= box.minY() - (trail ? 1 : 0) && cell.getY() <= box.maxY()) {
                return piece;
            }
        }
        return null;
    }

    /**
     * The doors (lower halves) of a placed piece, in world positions, read from its template. A terrain-matching piece
     * (a lane's end with a store shed) lies one row lower than its box when it is laid level ({@link TrailBedProcessor});
     * with the levelling off its blocks follow the ground and its doors are not known, so it has none here.
     */
    public static List<Door> doors(PoolElementStructurePiece piece, StructureTemplateManager templates) {
        return placed(piece, templates, new java.util.IdentityHashMap<>()).doors();
    }

    /** The world cells a placed piece's template fills in its local row {@code localY} (for the tests). */
    public static List<BlockPos> cells(PoolElementStructurePiece piece, StructureTemplateManager templates, int localY) {
        Placed placed = placed(piece, templates, new java.util.IdentityHashMap<>());
        return placed.filled().stream().filter(p -> p.getY() == localY).map(p -> p.offset(placed.at())).toList();
    }

    /** A piece's template shape where it lies: its origin ({@code at}), its doors in world positions and its cells. */
    record Placed(BlockPos at, List<Door> doors, java.util.Set<BlockPos> filled) {

        static final Placed NONE = new Placed(BlockPos.ZERO, List.of(), java.util.Set.of());

        boolean fills(BlockPos pos) {
            return filled.contains(pos.subtract(at));
        }
    }

    private static Placed placed(PoolElementStructurePiece piece, StructureTemplateManager templates, Map<StructurePiece, Placed> memo) {
        Placed known = memo.get(piece);
        if (known != null) return known;
        Placed out = Placed.NONE;
        boolean trail = piece.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
        Optional<StructureTemplate> template = template(piece, templates);
        if (template.isPresent() && !(trail && !com.richardsenger.piratesnships.world.WorldConfig.STRUCTURE_LEVEL_TRAILS.get())) {
            Shape shape;
            synchronized (CACHE) {
                shape = CACHE.computeIfAbsent(template.get(), t -> new EnumMap<>(Rotation.class))
                        .computeIfAbsent(piece.getRotation(), r -> scan(template.get(), r));
            }
            BlockPos at = trail ? piece.getPosition().below() : piece.getPosition();
            List<Door> doors = new ArrayList<>(shape.doors().size());
            for (Door door : shape.doors()) doors.add(new Door(door.pos().offset(at), door.facing()));
            out = new Placed(at, doors, shape.filled());
        }
        memo.put(piece, out);
        return out;
    }

    /**
     * The template's door lower halves and the cells it fills (any block but air and cave air), rotated by
     * {@code rotation} about its origin, relative to the origin.
     */
    static Shape scan(StructureTemplate template, Rotation rotation) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        List<Door> doors = new ArrayList<>();
        for (Block block : doorBlocks()) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, block, true)) {
                BlockState state = info.state();
                if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                doors.add(new Door(info.pos().immutable(), state.getValue(DoorBlock.FACING)));
            }
        }
        java.util.Set<BlockPos> filled = new java.util.HashSet<>();
        net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            net.minecraft.nbt.ListTag palettes = saved.getList("palettes", net.minecraft.nbt.Tag.TAG_LIST);
            if (!palettes.isEmpty()) palette = palettes.getList(0);
        }
        net.minecraft.nbt.ListTag blocks = saved.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.nbt.CompoundTag block = blocks.getCompound(i);
            String name = palette.getCompound(block.getInt("state")).getString("Name");
            if (name.equals("minecraft:air") || name.equals("minecraft:cave_air")) continue;
            net.minecraft.nbt.ListTag pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            filled.add(StructureTemplate.calculateRelativePosition(settings, new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))));
        }
        return new Shape(List.copyOf(doors), java.util.Set.copyOf(filled));
    }

    /** A template's doors and filled cells in one rotation, relative to its origin. */
    record Shape(List<Door> doors, java.util.Set<BlockPos> filled) {
    }

    private static List<Block> doorBlocks() {
        List<Block> blocks = doorBlocks;
        if (blocks == null) {
            blocks = BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof DoorBlock).toList();
            doorBlocks = blocks;
        }
        return blocks;
    }

    /** The template a single pool element names ({@code Single[Left[ns:path]]}), if it loads. */
    static Optional<StructureTemplate> template(PoolElementStructurePiece piece, StructureTemplateManager templates) {
        Matcher m = LOCATION.matcher(piece.getElement().toString());
        if (!m.find()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(m.group(1));
        return id == null ? Optional.empty() : templates.get(id);
    }
}

package com.richardsenger.piratesnships.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Wall runs end on land (STR8; the human's screenshot showed the navy outpost's wall run going out over the sea with
 * its end tower standing in the water). The rigid chain from the start ({@link PortStructure#rigidChain}: the fort's
 * walls and towers) is not a land piece, so {@link LandFilter} never looked at it. Here a wall piece of the chain whose
 * far end (the cell where its walkway leaves its box, in line with the connector it hangs from) is over open water is
 * dropped with everything hanging from it, and the run is closed again with a tower from the pool's fallback (the
 * outpost's {@code terminators}) on the connector the dropped piece hung from, if one fits there. The pier, jetty and
 * quay (they belong in the water) and the towers themselves (they close a run) are left alone.
 */
public final class WallRuns {

    private WallRuns() {
    }

    /**
     * One wall run's replacement: the piece at {@code index} becomes {@code tower} (null: dropped without a tower);
     * {@code walls} are the wall pieces dropped for it (the one at {@code index} first, then, since STR9, the walls before
     * it the run was shortened by because no tower stood on land at the end).
     */
    public record Closure(int index, @Nullable PoolElementStructurePiece tower, List<Integer> walls) {

        public Closure(int index, @Nullable PoolElementStructurePiece tower) {
            this(index, tower, List.of(index));
        }
    }

    /** STR8's rule ({@link #closeOnLand(List, List, boolean[], boolean[], LandFilter.Water, StructureTemplateManager, HolderGetter, boolean)} without shortening). */
    public static List<Closure> closeOnLand(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, boolean[] chain,
                                            LandFilter.Water water, StructureTemplateManager templates,
                                            HolderGetter<StructureTemplatePool> pools) {
        return closeOnLand(pieces, nodes, keep, chain, water, templates, pools, false);
    }

    /**
     * Applies the rule to {@code pieces} (jigsaw order) in place: {@code keep} is updated (a dropped piece and its
     * descendants are no longer kept), and each closing tower replaces the dropped piece in {@code pieces}. With
     * {@code shorten} (STR9, {@code world.structures.resite_pieces}) a run whose closing tower finds no room, or would
     * stand with its centre over water, is shortened one wall at a time and the tower tried on the connector before,
     * back to the gate. Returns the closures made.
     */
    public static List<Closure> closeOnLand(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, boolean[] chain,
                                            LandFilter.Water water, StructureTemplateManager templates,
                                            HolderGetter<StructureTemplatePool> pools, boolean shorten) {
        List<Closure> out = new java.util.ArrayList<>();
        boolean[] replaced = new boolean[pieces.size()];
        for (int i = 1; i < pieces.size(); i++) {
            int parent = nodes.get(i).parent();
            if (parent >= 0 && parent < i && (!keep[parent] || replaced[parent])) {
                keep[i] = false;
                continue;
            }
            if (!keep[i] || !chain[i] || !(pieces.get(i) instanceof PoolElementStructurePiece wall) || parent < 0
                    || !(pieces.get(parent) instanceof PoolElementStructurePiece)) {
                continue;
            }
            if (isPierLike(wall, templates) || wall.getJunctions().isEmpty()) continue;
            End end = end(pieces, nodes, i, templates, pools);
            if (end == null || end.terminator()) continue;
            BlockPos far = farEnd(wall.getBoundingBox(), end.jigsaw().pos().relative(end.facing()), end.facing());
            if (!water.at(far.getX(), far.getZ())) continue;
            List<Integer> walls = new java.util.ArrayList<>();
            int at = i;
            End here = end;
            PoolElementStructurePiece tower;
            while (true) {
                walls.add(at);
                replaced[at] = true;
                dropBelow(nodes, keep, at);
                tower = tower(here.jigsaw(), here.fallback(), templates, pieces, keep, at, nodes.get(at).parent());
                if (tower != null && (!shorten || !LandFilter.overWaterCentre(tower.getBoundingBox(), water))) break;
                tower = null;
                if (!shorten) break;
                // STR9: one wall back, if the piece before is a wall of the run (not the gate, the quay or a tower)
                int before = nodes.get(at).parent();
                if (before <= 0 || !chain[before] || !(pieces.get(before) instanceof PoolElementStructurePiece prior)
                        || isPierLike(prior, templates)) {
                    break;
                }
                End back = end(pieces, nodes, before, templates, pools);
                if (back == null || back.terminator()) break;
                keep[at] = false;
                at = before;
                here = back;
            }
            if (tower != null) {
                pieces.set(at, tower);
                keep[at] = true;
            } else {
                keep[at] = false;
            }
            out.add(new Closure(at, tower, List.copyOf(walls)));
        }
        return out;
    }

    /** The connector piece {@code i} hangs from, its facing, its pool's fallback and whether {@code i} is from that fallback. */
    private record End(StructureTemplate.StructureBlockInfo jigsaw, Direction facing, Holder<StructureTemplatePool> fallback, boolean terminator) {
    }

    private static @Nullable End end(List<StructurePiece> pieces, List<LandFilter.Node> nodes, int i, StructureTemplateManager templates,
                                     HolderGetter<StructureTemplatePool> pools) {
        int parent = nodes.get(i).parent();
        if (parent < 0 || !(pieces.get(i) instanceof PoolElementStructurePiece wall) || wall.getJunctions().isEmpty()
                || !(pieces.get(parent) instanceof PoolElementStructurePiece from)) {
            return null;
        }
        JigsawJunction up = wall.getJunctions().get(0);
        StructureTemplate.StructureBlockInfo jigsaw = jigsawAt(from, templates, up.getSourceX(), up.getSourceZ());
        if (jigsaw == null) return null;
        Optional<Holder.Reference<StructureTemplatePool>> pool = pool(jigsaw, pools);
        if (pool.isEmpty()) return null;
        Holder<StructureTemplatePool> fallback = pool.get().value().getFallback();
        boolean terminator = fallback.value().getShuffledTemplates(RandomSource.create(0L)).contains(wall.getElement());
        return new End(jigsaw, JigsawBlock.getFrontFacing(jigsaw.state()), fallback, terminator);
    }

    /** Un-keeps every piece hanging (transitively) from piece {@code i}. */
    private static void dropBelow(List<LandFilter.Node> nodes, boolean[] keep, int i) {
        boolean[] below = new boolean[keep.length];
        below[i] = true;
        for (int j = i + 1; j < keep.length; j++) {
            int p = nodes.get(j).parent();
            if (p >= 0 && p < j && below[p]) {
                below[j] = true;
                keep[j] = false;
            }
        }
    }

    /**
     * The far end of piece {@code i} (the last cell of its box in line with the connector it hangs from), or null when it
     * hangs from nothing known. For the tests: the same reading the rule makes.
     */
    public static @Nullable BlockPos farEnd(List<StructurePiece> pieces, List<LandFilter.Node> nodes, int i, StructureTemplateManager templates) {
        int parent = nodes.get(i).parent();
        if (parent < 0 || !(pieces.get(i) instanceof PoolElementStructurePiece piece) || piece.getJunctions().isEmpty()
                || !(pieces.get(parent) instanceof PoolElementStructurePiece from)) {
            return null;
        }
        JigsawJunction up = piece.getJunctions().get(0);
        StructureTemplate.StructureBlockInfo jigsaw = jigsawAt(from, templates, up.getSourceX(), up.getSourceZ());
        if (jigsaw == null) return null;
        Direction facing = JigsawBlock.getFrontFacing(jigsaw.state());
        return farEnd(piece.getBoundingBox(), jigsaw.pos().relative(facing), facing);
    }

    /** The last cell of {@code box} on the line from {@code in} along {@code facing}. */
    static BlockPos farEnd(BoundingBox box, BlockPos in, Direction facing) {
        BlockPos cell = in;
        while (box.isInside(cell.relative(facing))) cell = cell.relative(facing);
        return cell;
    }

    /** Whether the piece is the pier, the jetty or the quay (it carries one of their landward connectors). */
    static boolean isPierLike(PoolElementStructurePiece piece, StructureTemplateManager templates) {
        for (ResourceLocation name : PierExtensions.PIER_INS) {
            if (PierExtensions.connector(piece.getElement(), templates, piece.getPosition(), piece.getRotation(), name).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The parent's jigsaw block standing in the column (x, z) (where its child's first junction starts). */
    static @Nullable StructureTemplate.StructureBlockInfo jigsawAt(PoolElementStructurePiece piece, StructureTemplateManager templates,
                                                                   int x, int z) {
        for (StructureTemplate.StructureBlockInfo info : piece.getElement().getShuffledJigsawBlocks(templates, piece.getPosition(),
                piece.getRotation(), RandomSource.create(0L))) {
            if (info.pos().getX() == x && info.pos().getZ() == z) return info;
        }
        return null;
    }

    private static Optional<Holder.Reference<StructureTemplatePool>> pool(StructureTemplate.StructureBlockInfo jigsaw,
                                                                          HolderGetter<StructureTemplatePool> pools) {
        if (jigsaw.nbt() == null) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(jigsaw.nbt().getString("pool"));
        if (id == null) return Optional.empty();
        return pools.get(ResourceKey.create(net.minecraft.core.registries.Registries.TEMPLATE_POOL, id));
    }

    /**
     * A tower of {@code fallback} hung from {@code jigsaw} (rigid, level with it, out of the beard's reach), the first
     * element and rotation whose connector fits and whose box overlaps no kept piece; or null.
     */
    static @Nullable PoolElementStructurePiece tower(StructureTemplate.StructureBlockInfo jigsaw, Holder<StructureTemplatePool> fallback,
                                                     StructureTemplateManager templates, List<StructurePiece> pieces, boolean[] keep,
                                                     int replacing, int parent) {
        Direction facing = JigsawBlock.getFrontFacing(jigsaw.state());
        BlockPos target = jigsaw.pos().relative(facing);
        RandomSource random = RandomSource.create(jigsaw.pos().asLong());
        for (StructurePoolElement element : fallback.value().getShuffledTemplates(random)) {
            for (Rotation rotation : Rotation.values()) {
                for (StructureTemplate.StructureBlockInfo in : element.getShuffledJigsawBlocks(templates, BlockPos.ZERO, rotation, random)) {
                    if (!JigsawBlock.canAttach(jigsaw, in)) continue;
                    BlockPos position = target.subtract(in.pos());
                    BoundingBox box = element.getBoundingBox(templates, position, rotation);
                    boolean free = true;
                    for (int j = 0; j < pieces.size() && free; j++) {
                        if (j == replacing || !keep[j] && j != parent) continue;
                        if (pieces.get(j).getBoundingBox().intersects(box)) free = false;
                    }
                    if (!free) continue;
                    PoolElementStructurePiece tower = new PoolElementStructurePiece(templates, element, position,
                            PortStructure.UNBEARDED_GROUND_LEVEL_DELTA, rotation, box, LiquidSettings.APPLY_WATERLOGGING);
                    // the first junction names the parent's connector, as the jigsaw's does ({@code PortStructure.landNodes})
                    tower.addJunction(new JigsawJunction(jigsaw.pos().getX(), jigsaw.pos().getY(), jigsaw.pos().getZ(), 0,
                            StructureTemplatePool.Projection.RIGID));
                    return tower;
                }
            }
        }
        return null;
    }
}

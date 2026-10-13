package com.richardsenger.piratesnships.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Re-sites a port's pieces instead of dropping them (STR9, the human, 2026-10-13: "for some reason they are A LOT
 * smaller now"). STR8's rules dropped a piece that failed them together with everything hanging from it; on real coasts
 * that was most of a port, because a trail near the start that failed took the whole network behind it (the smoke seed:
 * 33 to 56 percent of the jigsaw's pieces kept). The rules stay; what fails them is first moved or replaced:
 *
 * <ul>
 *     <li><b>A building over water</b> ({@link LandFilter}: the centre, four of five samples, all four corners) is
 *     shifted along its connector's axis, away from the trail it hangs from, by one to {@value #MAX_SHIFT} blocks; then
 *     swapped for the smallest piece of its pool that stands on land there (shifted the same way); only then dropped.</li>
 *     <li><b>A trail piece</b> that fails the land rule, or ({@code PortStructure.level}) lies more than
 *     {@code max_trail_step_rows} off its ground, is swapped for a straight piece of the kind's straights-only pool on the
 *     same connector; its children whose connector the straight carries too (a street's side connectors are where a long
 *     street's or a fork's are) hang on from it ({@code rerooted}), the others go.</li>
 * </ul>
 * A replacement must not overlap a kept piece. Nothing here reads the world: the water test and the acceptance rule are
 * passed in, the templates and pools are the plan's.
 */
public final class PlanRepair {

    /** The farthest a building is shifted along its connector's axis. */
    public static final int MAX_SHIFT = 3;

    /** Each kind's straights-only trail pool (STR8), by the pools' folder. */
    static final Map<String, String> STRAIGHTS = Map.of(
            "village", "village/streets_straight",
            "pirate_island", "pirate_island/paths_straight",
            "navy_outpost", "navy_outpost/lanes_straight");

    private PlanRepair() {
    }

    /** What the repairs read: the templates, the pools and the plan's water. */
    public record Env(StructureTemplateManager templates, HolderGetter<StructureTemplatePool> pools, LandFilter.Water water) {
    }

    /**
     * The land rule with re-siting (in jigsaw order, a parent before its children): every land piece that does not stand
     * on land is re-sited ({@code resite}) or dropped; a dropped piece's descendants are dropped. {@code why[i]} records
     * the reason of each piece this pass dropped ({@link PlanReport.Reason#CHILD} for a descendant) or re-sited (the
     * piece kept: the reason its lost children are counted against).
     */
    public static void land(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, PlanReport.Reason[] why, Env env,
                            boolean resite, PlanReport report) {
        for (int i = 1; i < pieces.size(); i++) {
            if (!keep[i]) continue;
            LandFilter.Node node = nodes.get(i);
            int parent = node.parent();
            if (parent >= 0 && parent < i && !keep[parent]) {
                keep[i] = false;
                why[i] = PlanReport.Reason.CHILD;
                continue;
            }
            if (!node.land() || LandFilter.onLand(node, env.water())) continue;
            PlanReport.Reason reason = node.building() && LandFilter.onLand(new LandFilter.Node(node.box(), parent, true), env.water())
                    ? PlanReport.Reason.CORNERS : PlanReport.Reason.LAND;
            if (resite) {
                // a re-sited piece stays with its reason in why (its lost children are counted against it)
                why[i] = reason;
                if (node.building()) {
                    if (shift(pieces, nodes, keep, i, env)) {
                        report.shifted++;
                        continue;
                    }
                    if (swapBuilding(pieces, nodes, keep, why, i, env)) {
                        report.swapped++;
                        continue;
                    }
                } else {
                    int rerooted = swapTrail(pieces, nodes, keep, why, i, env, candidate -> onLand(candidate, parent, false, env.water()));
                    if (rerooted >= 0) {
                        report.swapped++;
                        report.rerooted += rerooted;
                        continue;
                    }
                }
            }
            keep[i] = false;
            why[i] = reason;
        }
    }

    static boolean onLand(StructurePiece piece, int parent, boolean building, LandFilter.Water water) {
        return LandFilter.onLand(new LandFilter.Node(piece.getBoundingBox(), parent, true, building), water);
    }

    // --- buildings ----------------------------------------------------------------------------------------------

    /** Shifts building {@code i} (and what hangs from it) one to {@link #MAX_SHIFT} blocks away from its connector. */
    static boolean shift(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, int i, Env env) {
        StructureTemplate.StructureBlockInfo jigsaw = connector(pieces, nodes, i, env.templates());
        if (jigsaw == null) return false;
        Direction away = JigsawBlock.getFrontFacing(jigsaw.state());
        if (away.getAxis().isVertical()) return false;
        LandFilter.Node node = nodes.get(i);
        for (int s = 1; s <= MAX_SHIFT; s++) {
            BoundingBox moved = node.box().moved(away.getStepX() * s, 0, away.getStepZ() * s);
            if (!LandFilter.onLand(new LandFilter.Node(moved, node.parent(), true, true), env.water())) continue;
            if (!free(moved, pieces, nodes, keep, i)) continue;
            moveWithDescendants(pieces, nodes, keep, i, away.getStepX() * s, away.getStepZ() * s);
            return true;
        }
        return false;
    }

    /** Swaps building {@code i} for the smallest piece of its pool that stands on land at its connector (shifted 0 to 3). */
    static boolean swapBuilding(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, PlanReport.Reason[] why, int i,
                                Env env) {
        if (!(pieces.get(i) instanceof PoolElementStructurePiece old)) return false;
        StructureTemplate.StructureBlockInfo jigsaw = connector(pieces, nodes, i, env.templates());
        if (jigsaw == null) return false;
        Optional<Holder.Reference<StructureTemplatePool>> pool = pool(jigsaw, env.pools());
        if (pool.isEmpty()) return false;
        Direction away = JigsawBlock.getFrontFacing(jigsaw.state());
        int parent = nodes.get(i).parent();
        long area = area(old.getBoundingBox());
        for (StructurePoolElement element : bySize(pool.get(), env.templates())) {
            if (element.toString().equals(old.getElement().toString())) continue;
            if (area(element.getBoundingBox(env.templates(), BlockPos.ZERO, Rotation.NONE)) >= area) continue;
            for (PoolElementStructurePiece candidate : attachments(jigsaw, element, old, env.templates())) {
                for (int s = 0; s <= MAX_SHIFT; s++) {
                    BoundingBox moved = candidate.getBoundingBox().moved(away.getStepX() * s, 0, away.getStepZ() * s);
                    if (!LandFilter.onLand(new LandFilter.Node(moved, parent, true, true), env.water())) continue;
                    if (!free(moved, pieces, nodes, keep, i)) continue;
                    candidate.move(away.getStepX() * s, 0, away.getStepZ() * s);
                    candidate.addJunction(old.getJunctions().get(0));
                    replace(pieces, nodes, keep, why, i, candidate, true, env.templates());
                    return true;
                }
            }
        }
        return false;
    }

    // --- trails -------------------------------------------------------------------------------------------------

    /**
     * Swaps trail piece {@code i} for a straight of its kind's straights-only pool on the same connector, the first
     * ({@code accept}ed, not overlapping) in order of size; returns how many of its children were re-rooted on it, or -1
     * when no straight fits. Children whose connector the straight lacks are dropped.
     */
    public static int swapTrail(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, PlanReport.Reason[] why, int i,
                                Env env, Predicate<PoolElementStructurePiece> accept) {
        if (!(pieces.get(i) instanceof PoolElementStructurePiece old) || old.getJunctions().isEmpty()) return -1;
        StructureTemplate.StructureBlockInfo jigsaw = connector(pieces, nodes, i, env.templates());
        if (jigsaw == null) return -1;
        Optional<Holder.Reference<StructureTemplatePool>> straights = straights(jigsaw, env.pools());
        if (straights.isEmpty()) return -1;
        List<PoolElementStructurePiece> fits = new ArrayList<>();
        for (StructurePoolElement element : bySize(straights.get(), env.templates())) {
            for (PoolElementStructurePiece candidate : attachments(jigsaw, element, old, env.templates())) {
                if (!free(candidate.getBoundingBox(), pieces, nodes, keep, i)) continue;
                if (!accept.test(candidate)) continue;
                fits.add(candidate);
            }
        }
        if (fits.isEmpty()) return -1;
        // the straight that carries the most of the old piece's children on (the smaller one on a tie)
        PoolElementStructurePiece best = null;
        int bestKept = -1;
        for (PoolElementStructurePiece candidate : fits) {
            int kept = children(pieces, nodes, keep, i, candidate, env.templates()).size();
            if (kept > bestKept) {
                best = candidate;
                bestKept = kept;
            }
        }
        best.addJunction(old.getJunctions().get(0));
        return replace(pieces, nodes, keep, why, i, best, false, env.templates());
    }

    /** The straights-only pool of the kind whose pool {@code jigsaw} draws from. */
    static Optional<Holder.Reference<StructureTemplatePool>> straights(StructureTemplate.StructureBlockInfo jigsaw,
                                                                       HolderGetter<StructureTemplatePool> pools) {
        if (jigsaw.nbt() == null) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(jigsaw.nbt().getString("pool"));
        if (id == null) return Optional.empty();
        String path = id.getPath();
        int slash = path.indexOf('/');
        if (slash < 0) return Optional.empty();
        String straight = STRAIGHTS.get(path.substring(0, slash));
        if (straight == null) return Optional.empty();
        return pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), straight)));
    }

    // --- shared -------------------------------------------------------------------------------------------------

    /**
     * Puts {@code replacement} in place of piece {@code i}: its direct children whose connector the replacement carries
     * too stay (with their junctions), the others are dropped ({@code why} CHILD; theirs follow in {@link #land} or the
     * caller's pass). Returns how many children stayed.
     */
    static int replace(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, PlanReport.Reason[] why, int i,
                       PoolElementStructurePiece replacement, boolean building, StructureTemplateManager templates) {
        PoolElementStructurePiece old = (PoolElementStructurePiece) pieces.get(i);
        List<Integer> stay = children(pieces, nodes, keep, i, replacement, templates);
        for (int j = i + 1; j < pieces.size(); j++) {
            if (!keep[j] || nodes.get(j).parent() != i) continue;
            if (stay.contains(j)) {
                JigsawJunction up = ((PoolElementStructurePiece) pieces.get(j)).getJunctions().get(0);
                // the old piece's junction to this child starts at the child's connector cell, in front of {@code up}
                for (JigsawJunction junction : old.getJunctions()) {
                    if (Math.abs(junction.getSourceX() - up.getSourceX()) + Math.abs(junction.getSourceZ() - up.getSourceZ()) == 1
                            && pieces.get(j).getBoundingBox().isInside(new BlockPos(junction.getSourceX(),
                            pieces.get(j).getBoundingBox().minY(), junction.getSourceZ()))) {
                        replacement.addJunction(junction);
                        break;
                    }
                }
            } else {
                keep[j] = false;
                why[j] = PlanReport.Reason.CHILD;
            }
        }
        pieces.set(i, replacement);
        LandFilter.Node node = nodes.get(i);
        nodes.set(i, new LandFilter.Node(replacement.getBoundingBox(), node.parent(), node.land(), building));
        // the dropped children's own children
        for (int j = i + 1; j < pieces.size(); j++) {
            int p = nodes.get(j).parent();
            if (keep[j] && p > i && p < j && !keep[p]) {
                keep[j] = false;
                why[j] = PlanReport.Reason.CHILD;
            }
        }
        return stay.size();
    }

    /**
     * The kept direct children of piece {@code i} that {@code replacement} carries too: their connector (the jigsaw their
     * first junction starts from) has a jigsaw of the replacement in the same cell, facing the same way.
     */
    static List<Integer> children(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, int i,
                                  PoolElementStructurePiece replacement, StructureTemplateManager manager) {
        List<Integer> out = new ArrayList<>();
        PoolElementStructurePiece old = (PoolElementStructurePiece) pieces.get(i);
        List<StructureTemplate.StructureBlockInfo> mine = replacement.getElement().getShuffledJigsawBlocks(manager,
                replacement.getPosition(), replacement.getRotation(), RandomSource.create(0L));
        for (int j = i + 1; j < pieces.size(); j++) {
            if (!keep[j] || nodes.get(j).parent() != i || !(pieces.get(j) instanceof PoolElementStructurePiece child)
                    || child.getJunctions().isEmpty()) {
                continue;
            }
            JigsawJunction up = child.getJunctions().get(0);
            StructureTemplate.StructureBlockInfo theirs = WallRuns.jigsawAt(old, manager, up.getSourceX(), up.getSourceZ());
            if (theirs == null) continue;
            for (StructureTemplate.StructureBlockInfo info : mine) {
                if (info.pos().equals(theirs.pos()) && JigsawBlock.getFrontFacing(info.state()) == JigsawBlock.getFrontFacing(theirs.state())
                        && !replacement.getBoundingBox().intersects(child.getBoundingBox())) {
                    out.add(j);
                    break;
                }
            }
        }
        return out;
    }

    /** The jigsaw block of its parent piece {@code i} hangs from, or null. */
    static @Nullable StructureTemplate.StructureBlockInfo connector(List<StructurePiece> pieces, List<LandFilter.Node> nodes, int i,
                                                                   StructureTemplateManager templates) {
        int parent = nodes.get(i).parent();
        if (parent < 0 || !(pieces.get(i) instanceof PoolElementStructurePiece piece) || piece.getJunctions().isEmpty()
                || !(pieces.get(parent) instanceof PoolElementStructurePiece from)) {
            return null;
        }
        JigsawJunction up = piece.getJunctions().get(0);
        return WallRuns.jigsawAt(from, templates, up.getSourceX(), up.getSourceZ());
    }

    /**
     * {@code element} hung on {@code jigsaw}, every rotation and connector that attaches, its connector on the row the
     * old piece's connector stands on (so it lies where the jigsaw laid the old one), with the old piece's ground level.
     */
    static List<PoolElementStructurePiece> attachments(StructureTemplate.StructureBlockInfo jigsaw, StructurePoolElement element,
                                                       PoolElementStructurePiece old, StructureTemplateManager templates) {
        List<PoolElementStructurePiece> out = new ArrayList<>();
        Direction facing = JigsawBlock.getFrontFacing(jigsaw.state());
        BlockPos target = jigsaw.pos().relative(facing);
        StructureTemplate.StructureBlockInfo oldIn = WallRuns.jigsawAt(old, templates, target.getX(), target.getZ());
        int row = oldIn != null ? oldIn.pos().getY() : target.getY();
        for (Rotation rotation : Rotation.values()) {
            for (StructureTemplate.StructureBlockInfo in : element.getShuffledJigsawBlocks(templates, BlockPos.ZERO, rotation, RandomSource.create(0L))) {
                if (!JigsawBlock.canAttach(jigsaw, in)) continue;
                BlockPos position = new BlockPos(target.getX(), row, target.getZ()).subtract(in.pos());
                BoundingBox box = element.getBoundingBox(templates, position, rotation);
                out.add(new PoolElementStructurePiece(templates, element, position, old.getGroundLevelDelta(), rotation, box,
                        LiquidSettings.APPLY_WATERLOGGING));
            }
        }
        return out;
    }

    /** Whether {@code box} overlaps no kept piece but {@code self} (and the parent, when the old piece overlapped it). */
    static boolean free(BoundingBox box, List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, int self) {
        int parent = nodes.get(self).parent();
        boolean insideParent = parent >= 0 && pieces.get(parent).getBoundingBox().intersects(pieces.get(self).getBoundingBox());
        for (int j = 0; j < pieces.size(); j++) {
            if (j == self || !keep[j]) continue;
            if (j == parent && insideParent) continue;
            if (nodes.get(j).parent() == self) continue;
            if (pieces.get(j).getBoundingBox().intersects(box)) return false;
        }
        return true;
    }

    /** Moves piece {@code i} and every kept piece hanging from it (transitively) sideways. */
    static void moveWithDescendants(List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep, int i, int dx, int dz) {
        boolean[] below = new boolean[pieces.size()];
        below[i] = true;
        for (int j = i; j < pieces.size(); j++) {
            int p = nodes.get(j).parent();
            if (j != i && !(p >= 0 && p < j && below[p])) continue;
            below[j] = true;
            if (!keep[j]) continue;
            pieces.get(j).move(dx, 0, dz);
            LandFilter.Node node = nodes.get(j);
            nodes.set(j, new LandFilter.Node(pieces.get(j).getBoundingBox(), node.parent(), node.land(), node.building()));
        }
    }

    /** The pool's elements (no duplicates, no empty element), smallest footprint first, then by name. */
    static List<StructurePoolElement> bySize(Holder<StructureTemplatePool> pool, StructureTemplateManager templates) {
        LinkedHashSet<StructurePoolElement> unique = new LinkedHashSet<>(pool.value().getShuffledTemplates(RandomSource.create(0L)));
        unique.remove(EmptyPoolElement.INSTANCE);
        List<StructurePoolElement> out = new ArrayList<>(unique);
        out.sort(Comparator.<StructurePoolElement>comparingLong(e -> area(e.getBoundingBox(templates, BlockPos.ZERO, Rotation.NONE)))
                .thenComparing(Object::toString));
        return out;
    }

    static long area(BoundingBox box) {
        return (long) box.getXSpan() * box.getZSpan();
    }

    static Optional<Holder.Reference<StructureTemplatePool>> pool(StructureTemplate.StructureBlockInfo jigsaw,
                                                                  HolderGetter<StructureTemplatePool> pools) {
        if (jigsaw.nbt() == null) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(jigsaw.nbt().getString("pool"));
        if (id == null) return Optional.empty();
        return pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, id));
    }
}

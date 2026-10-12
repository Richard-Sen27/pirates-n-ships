package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Longer piers where the shore is shallow (STR7, design.md §10.1): a section hangs from the seaward end of the village
 * pier, the island's jetty or the outpost's quay, in line with their landward connector ({@link #seawardEnd}); the
 * extension piece ({@code pier_extension}, {@code jetty_extension}, {@code quay_extension}, two berths each) carries
 * the jigsaw {@value #IN} on its landward end and {@value #END} on its seaward end (both pool {@code minecraft:empty},
 * so vanilla's jigsaw never fills them), from which a second section hangs.
 *
 * <p><b>The condition</b> is decided in {@code PortStructure.plan}, after the jigsaw, from the same terrain the
 * placement reads ({@code ChunkGenerator.getFirstFreeHeight} on {@code OCEAN_FLOOR_WG}, or real blocks in GameTests):
 * the column just beyond the end connector is sea (its first free block at most the sea surface) and shallower than
 * the depth rule (its first free block above {@code seaSurface - depth}, {@code depth} being the largest draught plus
 * {@code world.ports.berth_depth}). Then one section is hung from the end, rigidly, level with it, unbearded, and the
 * check repeats at the new end, at most {@link #MAX_SECTIONS} times; it stops when a section would overlap another
 * piece. {@code world.ports.pier_extension} switches it off.
 */
public final class PierExtensions {

    public static final ResourceLocation END = Constants.id("pier_end");
    public static final ResourceLocation IN = Constants.id("pier_extension_in");
    public static final int MAX_SECTIONS = 2;

    private PierExtensions() {
    }

    /** Whether {@code piece} is an extension section (a {@code *_extension} template). */
    public static boolean isExtension(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p && StructureLayout.templateName(p.getElement().toString()).endsWith("_extension");
    }

    /**
     * The pieces without the extension sections: the layout checks (aspect, piece counts) measure the port on land, and
     * the sections only follow the depth of the water at the pier's end.
     */
    public static List<StructurePiece> landward(List<StructurePiece> pieces) {
        return pieces.stream().filter(p -> !isExtension(p)).toList();
    }

    /** Whether the end needs another section (pure): sea at the column beyond it, but shallower than {@code depth}. */
    public static boolean shallow(int firstFree, int seaSurface, int depth) {
        return firstFree <= seaSurface && firstFree > seaSurface - depth;
    }

    /** A connector: where the jigsaw stands and where it points. */
    record Connector(BlockPos pos, Direction facing) {
    }

    /**
     * The sections to add to {@code pieces} (none when nothing needs them): for each piece with an {@value #END}
     * connector, sections from {@code pool} while {@link #shallow} holds at its end.
     */
    public static List<StructurePiece> extend(StructureTemplateManager templates, Holder<StructureTemplatePool> pool,
                                              List<StructurePiece> pieces, PortStructure.Terrain terrain, int seaSurface, int depth) {
        List<StructurePiece> added = new ArrayList<>();
        List<BoundingBox> taken = new ArrayList<>();
        for (StructurePiece p : pieces) taken.add(p.getBoundingBox());
        RandomSource random = RandomSource.create(0L);
        for (StructurePiece piece : List.copyOf(pieces)) {
            if (!(piece instanceof PoolElementStructurePiece parent)) continue;
            Optional<Connector> end = seawardEnd(parent, templates);
            Rotation rotation = parent.getRotation();
            for (int n = 0; n < MAX_SECTIONS && end.isPresent(); n++) {
                BlockPos beyond = end.get().pos().relative(end.get().facing());
                if (!shallow(terrain.firstFree(beyond.getX(), beyond.getZ()), seaSurface, depth)) break;
                PoolElementStructurePiece section = section(templates, pool, end.get(), rotation, random);
                if (section == null) break;
                BoundingBox box = section.getBoundingBox();
                if (taken.stream().anyMatch(b -> b.intersects(box))) break;
                taken.add(box);
                added.add(section);
                end = connector(section.getElement(), templates, section.getPosition(), rotation, END);
            }
        }
        return added;
    }

    /** STR8: how far beyond the end of the pier, jetty or quay the sea must reach. */
    public static final int SEA_BEYOND_END = 4;

    /**
     * STR8 (the human: "piers need to end in water, not on land"): whether the pier, jetty or quay among {@code pieces}
     * stands over the sea along its whole run (the line of its landward connector, from that connector to its seaward
     * end) and {@value #SEA_BEYOND_END} blocks beyond, by the plan's terrain (sea: the first free block at most the sea
     * surface). True when the pieces hold none.
     */
    public static boolean endsInWater(List<StructurePiece> pieces, StructureTemplateManager templates, PortStructure.Terrain terrain,
                                      int seaSurface) {
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p) || isExtension(piece)) continue;
            for (ResourceLocation name : PIER_INS) {
                Optional<Connector> in = connector(p.getElement(), templates, p.getPosition(), p.getRotation(), name);
                if (in.isEmpty()) continue;
                Direction sea = in.get().facing().getOpposite();
                BoundingBox box = p.getBoundingBox();
                int span = sea.getAxis() == Direction.Axis.X ? box.getXSpan() : box.getZSpan();
                if (!runInWater(in.get().pos(), sea, span + SEA_BEYOND_END, terrain, seaSurface)) return false;
            }
        }
        return true;
    }

    /** Whether the {@code length} columns from {@code from} toward {@code sea} are all sea (pure). */
    public static boolean runInWater(BlockPos from, Direction sea, int length, PortStructure.Terrain terrain, int seaSurface) {
        for (int k = 0; k < length; k++) {
            BlockPos cell = from.relative(sea, k);
            if (terrain.firstFree(cell.getX(), cell.getZ()) > seaSurface) return false;
        }
        return true;
    }

    /** The landward connectors of the pier, the jetty and the quay (ST1–ST3), which lie in line with their seaward end. */
    public static final List<ResourceLocation> PIER_INS = List.of(Constants.id("pier_in"), Constants.id("jetty_in"), Constants.id("quay_in"));

    /**
     * Where a section hangs from {@code piece}: a section's own {@value #END}; for the pier, jetty or quay, the deck
     * cell of its seaward end in line with its landward connector ({@link #PIER_INS}), pointing out to sea. The three
     * carry no jigsaw there: one more jigsaw block in their templates would change vanilla's jigsaw random and so the
     * layout of every port generated after the update.
     */
    static Optional<Connector> seawardEnd(PoolElementStructurePiece piece, StructureTemplateManager templates) {
        Optional<Connector> end = connector(piece.getElement(), templates, piece.getPosition(), piece.getRotation(), END);
        if (end.isPresent()) return end;
        for (ResourceLocation name : PIER_INS) {
            Optional<Connector> in = connector(piece.getElement(), templates, piece.getPosition(), piece.getRotation(), name);
            if (in.isEmpty()) continue;
            Direction sea = in.get().facing().getOpposite();
            BoundingBox box = piece.getBoundingBox();
            int span = sea.getAxis() == Direction.Axis.X ? box.getXSpan() : box.getZSpan();
            return Optional.of(new Connector(in.get().pos().relative(sea, span - 1), sea));
        }
        return Optional.empty();
    }

    /** One section hung from {@code end}: its {@value #IN} connector one block beyond, facing back, same rotation. */
    static @Nullable PoolElementStructurePiece section(StructureTemplateManager templates, Holder<StructureTemplatePool> pool,
                                                      Connector end, Rotation rotation, RandomSource random) {
        List<StructurePoolElement> elements = pool.value().getShuffledTemplates(random);
        if (elements.isEmpty()) return null;
        StructurePoolElement element = elements.get(0);
        Optional<Connector> in = connector(element, templates, BlockPos.ZERO, rotation, IN);
        if (in.isEmpty() || in.get().facing() != end.facing().getOpposite()) {
            Constants.LOG.warn("Pier extension {}: no {} facing {} (rotation {})", element, IN, end.facing().getOpposite(), rotation);
            return null;
        }
        BlockPos position = end.pos().relative(end.facing()).subtract(in.get().pos());
        BoundingBox box = element.getBoundingBox(templates, position, rotation);
        return new PoolElementStructurePiece(templates, element, position, PortStructure.UNBEARDED_GROUND_LEVEL_DELTA, rotation, box,
                LiquidSettings.APPLY_WATERLOGGING);
    }

    /** The connector named {@code name} of {@code element} placed at {@code position} with {@code rotation}. */
    static Optional<Connector> connector(StructurePoolElement element, StructureTemplateManager templates, BlockPos position,
                                         Rotation rotation, ResourceLocation name) {
        for (StructureTemplate.StructureBlockInfo info : element.getShuffledJigsawBlocks(templates, position, rotation, RandomSource.create(0L))) {
            CompoundTag nbt = info.nbt();
            if (nbt != null && name.toString().equals(nbt.getString("name"))) {
                return Optional.of(new Connector(info.pos().immutable(), JigsawBlock.getFrontFacing(info.state())));
            }
        }
        return Optional.empty();
    }
}

package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.HullWater;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.assembly.ShipBlockRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Puts a {@link ShipTemplate} into the world in front of someone (operator command now, the shipwright later):
 * rotated so the bow points where they look, the waterline row on the water surface under the ship (or the
 * dimension's sea level), refusing when a template block would replace something solid. Afterwards the hold is
 * drained (the template drops air, so the sea would otherwise fill it), and on request the ship is assembled at its
 * helm.
 */
public final class ShipTemplatePlacer {

    /** Blocks between the caller and the near end of the ship. */
    public static final int GAP = 3;
    /** Blocks between a berth's own column and the near side of a ship moored there (SW1). */
    public static final int BERTH_GAP = 1;
    /**
     * How far a ship may be slid from the berth's centre toward its bow when the centred spot is blocked (a pier of 20
     * blocks with its berth at the middle leaves a 29 block hull's stern over the quay or the beach).
     */
    static final int[] BERTH_SLIDES = {0, 4, 8, 12, 16};
    /** How far above and below the caller the water surface is searched. */
    static final int SURFACE_SEARCH_UP = 4;
    static final int SURFACE_SEARCH_DOWN = 48;

    public enum Outcome {
        PLACED(true), UNKNOWN_TEMPLATE(false), MISSING_STRUCTURE(false), OBSTRUCTED(false), OUT_OF_WORLD(false),
        NO_HELM(false),
        /** At a berth (SW1): another ship lies there. */
        OCCUPIED(false);

        public final boolean success;

        Outcome(boolean success) {
            this.success = success;
        }
    }

    /**
     * What a placement did. {@code helm} is the helm's world position (null for a hull-only template), {@code where}
     * the first obstruction, {@code assembly} the assembly result when asked for one.
     */
    public record Result(Outcome outcome, ResourceLocation id, @Nullable ShipTemplate template, @Nullable BlockPos origin,
                         Rotation rotation, @Nullable BlockPos helm, @Nullable BlockPos where, int blocks, int surfaceY,
                         boolean seaLevel, @Nullable AssemblyResult assembly) {

        static Result fail(Outcome outcome, ResourceLocation id, @Nullable ShipTemplate template, @Nullable BlockPos where) {
            return new Result(outcome, id, template, null, Rotation.NONE, null, where, 0, 0, false, null);
        }
    }

    /** A template's blocks in template coordinates. */
    public record LocalBlock(BlockPos pos, BlockState state) {
    }

    private ShipTemplatePlacer() {
    }

    public static Optional<StructureTemplate> structure(ServerLevel level, ShipTemplate template) {
        return level.getStructureManager().get(template.structure());
    }

    /** Every non-air block of a structure, in template coordinates, ordered by y, z, x. */
    public static List<LocalBlock> blocks(StructureTemplate structure, HolderGetter<Block> blocks) {
        // StructureTemplate keeps its palette private; its saved form is public and complete.
        CompoundTag saved = structure.save(new CompoundTag());
        ListTag palette = saved.getList(StructureTemplate.PALETTE_TAG, Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        }
        List<LocalBlock> out = new ArrayList<>();
        ListTag list = saved.getList(StructureTemplate.BLOCKS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList(StructureTemplate.BLOCK_TAG_POS, Tag.TAG_INT);
            int state = b.getInt(StructureTemplate.BLOCK_TAG_STATE);
            BlockState s = state >= 0 && state < states.size() ? states.get(state) : Blocks.AIR.defaultBlockState();
            if (!s.isAir() && !s.is(Blocks.STRUCTURE_VOID)) {
                out.add(new LocalBlock(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), s));
            }
        }
        out.sort(Comparator.comparingInt((LocalBlock l) -> l.pos().getY()).thenComparingInt(l -> l.pos().getZ())
                .thenComparingInt(l -> l.pos().getX()));
        return out;
    }

    /** The template's helm: the definition's, else the first helm block in it, else null (hull only). */
    public static @Nullable BlockPos helm(ShipTemplate template, List<LocalBlock> blocks) {
        if (template.helm().isPresent()) {
            return template.helm().get();
        }
        return blocks.stream().filter(b -> b.state().is(AssemblyContent.HELM.get())).map(LocalBlock::pos).findFirst().orElse(null);
    }

    /**
     * Places template {@code id} in front of {@code feet} looking {@code facing}. {@code force} skips the obstruction
     * check; {@code assemble} assembles the ship at its helm afterwards ({@code player} becomes the owner).
     */
    public static Result place(ServerLevel level, ResourceLocation id, BlockPos feet, Direction facing, boolean force,
                               boolean assemble, @Nullable Player player) {
        ShipTemplate template = ShipTemplates.TYPE.server().get(id).orElse(null);
        if (template == null) {
            return Result.fail(Outcome.UNKNOWN_TEMPLATE, id, null, null);
        }
        StructureTemplate structure = structure(level, template).orElse(null);
        if (structure == null || structure.getSize().getX() < 1) {
            return Result.fail(Outcome.MISSING_STRUCTURE, id, template, null);
        }
        List<LocalBlock> blocks = blocks(structure, level.holderLookup(Registries.BLOCK));
        BlockPos helmLocal = helm(template, blocks);
        if (assemble && helmLocal == null) {
            return Result.fail(Outcome.NO_HELM, id, template, null);
        }
        Vec3i size = structure.getSize();
        Rotation rotation = TemplatePlacement.rotationFor(template.bow(), facing);
        int waterline = template.waterlineFor(helmLocal);

        // The water surface in the column under the middle of the ship.
        BlockPos flat = TemplatePlacement.origin(size, rotation, facing, feet, GAP, 0, 0);
        BlockPos centre = TemplatePlacement.worldBox(size, rotation, flat).getCenter();
        OptionalInt surface = TemplatePlacement.waterSurface(feet.getY() + SURFACE_SEARCH_UP, feet.getY() - SURFACE_SEARCH_DOWN,
                y -> level.getFluidState(new BlockPos(centre.getX(), y, centre.getZ())).is(FluidTags.WATER));
        boolean seaLevel = surface.isEmpty();
        int surfaceY = surface.orElse(level.getSeaLevel() - 1);
        BlockPos origin = TemplatePlacement.origin(size, rotation, facing, feet, GAP, surfaceY, waterline);

        return placeAt(level, id, template, structure, blocks, helmLocal, origin, rotation, surfaceY, seaLevel, force, assemble, player);
    }

    /** The template's world cells at {@code origin} with {@code rotation}. */
    static Set<BlockPos> cells(List<LocalBlock> blocks, Rotation rotation, BlockPos origin) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (LocalBlock b : blocks) {
            cells.add(TemplatePlacement.toWorld(b.pos(), rotation, origin));
        }
        return cells;
    }

    /** The first cell that is outside the world or (unless {@code force}) not free for a ship; null if none. */
    static @Nullable Result blocked(ServerLevel level, ResourceLocation id, ShipTemplate template, Set<BlockPos> cells, boolean force) {
        Optional<BlockPos> outside = TemplatePlacement.firstBlocked(cells, level::isOutsideBuildHeight);
        if (outside.isPresent()) {
            return Result.fail(Outcome.OUT_OF_WORLD, id, template, outside.get());
        }
        if (!force) {
            Optional<BlockPos> blocked = TemplatePlacement.firstBlocked(cells, p -> !ShipBlockRule.isFreeForShip(level.getBlockState(p)));
            if (blocked.isPresent()) {
                return Result.fail(Outcome.OBSTRUCTED, id, template, blocked.get());
            }
        }
        return null;
    }

    /** Checks the cells, places the template at {@code origin}, drains the hold and assembles on request. */
    private static Result placeAt(ServerLevel level, ResourceLocation id, ShipTemplate template, StructureTemplate structure,
                                  List<LocalBlock> blocks, @Nullable BlockPos helmLocal, BlockPos origin, Rotation rotation,
                                  int surfaceY, boolean seaLevel, boolean force, boolean assemble, @Nullable Player player) {
        Set<BlockPos> cells = cells(blocks, rotation, origin);
        Result refused = blocked(level, id, template, cells, force);
        if (refused != null) {
            return refused;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        structure.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        // Drain the hold: the template has no air, so sea water stayed in the cells the hull encloses.
        for (BlockPos p : HullWater.interiorCells(cells)) {
            if (ShipBlockRule.isWaterBlock(level.getBlockState(p))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos helmWorld = helmLocal == null ? null : TemplatePlacement.toWorld(helmLocal, rotation, origin);
        AssemblyResult assembly = null;
        if (assemble) {
            if (!level.getBlockState(helmWorld).is(AssemblyContent.HELM.get())) {
                Constants.LOG.warn("Ship template {}: no helm at template position {} (world {})", id, helmLocal, helmWorld);
            }
            assembly = ShipAssembler.assemble(level, helmWorld, player);
        }
        return new Result(Outcome.PLACED, id, template, origin, rotation, helmWorld, null, blocks.size(), surfaceY, seaLevel, assembly);
    }

    /**
     * Places template {@code id} at a berth (shipwright pickup, SW1) and assembles it with {@code player} as owner:
     * bow along {@code bow}, centred on the berth along the bow axis, on the side of the berth away from the pier with
     * {@link #BERTH_GAP} blocks of water between (see {@link TemplatePlacement#berthOrigin}), the waterline row at the
     * berth's height (the sea surface). Each side is tried in turn, first centred and then slid toward the bow in
     * steps ({@link #BERTH_SLIDES}) while the cells are not free (outside the world, quay, beach, seabed); a footprint
     * that {@code occupied} rejects (another ship lies there) ends that side. Returns {@link Outcome#OCCUPIED} or the
     * last obstruction when nothing works.
     */
    public static Result placeAtBerth(ServerLevel level, ResourceLocation id, BlockPos berth, Direction bow,
                                      java.util.function.Predicate<net.minecraft.world.level.levelgen.structure.BoundingBox> occupied,
                                      boolean assemble, @Nullable Player player) {
        ShipTemplate template = ShipTemplates.TYPE.server().get(id).orElse(null);
        if (template == null) {
            return Result.fail(Outcome.UNKNOWN_TEMPLATE, id, null, null);
        }
        StructureTemplate structure = structure(level, template).orElse(null);
        if (structure == null || structure.getSize().getX() < 1) {
            return Result.fail(Outcome.MISSING_STRUCTURE, id, template, null);
        }
        List<LocalBlock> blocks = blocks(structure, level.holderLookup(Registries.BLOCK));
        BlockPos helmLocal = helm(template, blocks);
        if (assemble && helmLocal == null) {
            return Result.fail(Outcome.NO_HELM, id, template, null);
        }
        Vec3i size = structure.getSize();
        Rotation rotation = TemplatePlacement.rotationFor(template.bow(), bow);
        int waterline = template.waterlineFor(helmLocal);
        int surfaceY = berth.getY();
        Result last = Result.fail(Outcome.OCCUPIED, id, template, berth);
        List<Direction> sides = TemplatePlacement.berthSides(bow, side -> {
            for (int dy = 0; dy <= 2; dy++) {
                if (!ShipBlockRule.isFreeForShip(level.getBlockState(berth.relative(side).above(dy)))) return true;
            }
            return false;
        });
        for (Direction side : sides) {
            BlockPos centred = TemplatePlacement.berthOrigin(size, rotation, bow, side, berth, BERTH_GAP, surfaceY, waterline);
            for (int slide : BERTH_SLIDES) {
                BlockPos origin = centred.relative(bow, slide);
                if (occupied.test(TemplatePlacement.worldBox(size, rotation, origin))) {
                    // another ship lies here: sliding further out along the same berth would only crowd it
                    break;
                }
                Result refused = blocked(level, id, template, cells(blocks, rotation, origin), false);
                if (refused != null) {
                    last = refused;
                    continue;
                }
                return placeAt(level, id, template, structure, blocks, helmLocal, origin, rotation, surfaceY, false, false, assemble, player);
            }
        }
        return last;
    }

}

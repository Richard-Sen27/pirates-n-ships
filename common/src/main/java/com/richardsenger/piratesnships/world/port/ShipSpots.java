package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.ship.assembly.ShipBlockRule;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplate;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.ship.template.TemplatePlacement;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@link ShipSpot} in a level (STR7): the column flags read from blocks, the berths of a port as starts, the ships
 * already there as occupied footprints, and a spot turned into a template origin. Used for a shipwright's pickup and a
 * granted ship ({@code ShipOrders.deliver}), an NPC ship appearing at a port ({@code Materializer}), and
 * {@code /pirates port spot}.
 */
public final class ShipSpots {

    /**
     * The largest shipped template's footprint and draught, what a new port's berths are made deep and long enough for:
     * the brigs (SHIP1, {@code brig}, {@code navy_brig_armed}, {@code pirate_brig_armed}: 9 wide, 32 long, waterline row
     * 4, so four rows under it; the sloops are 9 × 29 with draught 2). Numbers, not read from the templates: worldgen
     * carves the basins from these constants without a server's datapacks at hand. {@code ShipSpotGameTests} checks every
     * loaded template against them.
     */
    public static final ShipSpot.Hull LARGEST_SHIPPED = new ShipSpot.Hull(9, 32);
    public static final int LARGEST_DRAUGHT = 4;

    private ShipSpots() {
    }

    /** A template's shape for the search: footprint, draught (rows under the waterline row) and rows above it. */
    public record Shape(ResourceLocation id, ShipTemplate template, Vec3i size, int waterline, ShipSpot.Hull hull) {

        public int draught() {
            return waterline;
        }

        public int above() {
            return size.getY() - 1 - waterline;
        }

        /** The rotation and template origin that put this template's box on {@code footprint} heading {@code heading}. */
        public Rotation rotation(Direction heading) {
            return TemplatePlacement.rotationFor(template.bow(), heading);
        }

        public BlockPos origin(ShipSpot.Spot spot, int surfaceY) {
            BoundingBox box = TemplatePlacement.rotatedBox(size, rotation(spot.heading()));
            return new BlockPos(spot.footprint().minX() - box.minX(), surfaceY - waterline, spot.footprint().minZ() - box.minZ());
        }
    }

    /** The shape of template {@code id}, empty when the template or its structure is missing. */
    public static Optional<Shape> shape(ServerLevel level, ResourceLocation id) {
        ShipTemplate template = ShipTemplates.TYPE.server().get(id).orElse(null);
        if (template == null) return Optional.empty();
        StructureTemplate structure = ShipTemplatePlacer.structure(level, template).orElse(null);
        if (structure == null || structure.getSize().getX() < 1) return Optional.empty();
        List<ShipTemplatePlacer.LocalBlock> blocks = ShipTemplatePlacer.blocks(structure, level.holderLookup(Registries.BLOCK));
        int waterline = template.waterlineFor(ShipTemplatePlacer.helm(template, blocks));
        Vec3i size = structure.getSize();
        boolean alongZ = template.bow().getAxis() == Direction.Axis.Z;
        ShipSpot.Hull hull = new ShipSpot.Hull(alongZ ? size.getX() : size.getZ(), alongZ ? size.getZ() : size.getX());
        return Optional.of(new Shape(id, template, size, waterline, hull));
    }

    /** The config's rules ({@code world.ports.ship_clearance}, {@code ship_spot_search}). */
    public static ShipSpot.Rules rules() {
        return new ShipSpot.Rules(WorldConfig.PORTS_SHIP_CLEARANCE.get(), WorldConfig.PORTS_SHIP_SPOT_SEARCH.get());
    }

    /**
     * The berths of {@code port} as starts, in the port's order: the bow from the berth, the pier side from the blocks
     * beside the berth (something solid in the three rows from the surface up, as SW1 decided which side to moor on).
     */
    public static List<ShipSpot.Anchor> anchors(ServerLevel level, Port port) {
        List<ShipSpot.Anchor> out = new ArrayList<>();
        for (Berth b : port.berths()) {
            Direction right = b.bow().getClockWise();
            Direction left = b.bow().getCounterClockWise();
            boolean pierRight = pierOn(level, b.pos(), right);
            boolean pierLeft = pierOn(level, b.pos(), left);
            Direction pier = pierRight == pierLeft ? null : pierRight ? right : left;
            out.add(new ShipSpot.Anchor(b.pos().getX(), b.pos().getZ(), b.bow(), true, pier));
        }
        return out;
    }

    private static boolean pierOn(BlockGetter level, BlockPos berth, Direction side) {
        for (int dy = 0; dy <= 2; dy++) {
            if (!ShipBlockRule.isFreeForShip(level.getBlockState(berth.relative(side).above(dy)))) return true;
        }
        return false;
    }

    /** The sea surface of a port: its first berth's row. */
    public static Optional<Integer> surface(Port port) {
        return port.berths().isEmpty() ? Optional.empty() : Optional.of(port.berths().get(0).pos().getY());
    }

    /** Searches a spot for {@code shape} from {@code anchors} with the water surface at {@code surfaceY}. */
    public static ShipSpot.Search search(ServerLevel level, Shape shape, List<ShipSpot.Anchor> anchors, int surfaceY) {
        int depth = WorldConfig.PORTS_BERTH_DEPTH.get();
        List<AABB> ships = SableShips.all(level).stream().filter(b -> !b.isRemoved()).map(ShipSpots::bounds).toList();
        double y0 = surfaceY - shape.draught() - 1;
        double y1 = surfaceY + shape.above() + 1;
        ShipSpot.Occupied occupied = r -> {
            AABB box = new AABB(r.minX() - 1, y0, r.minZ() - 1, r.maxX() + 2, y1, r.maxZ() + 2);
            for (AABB ship : ships) {
                if (ship.intersects(box)) return true;
            }
            return false;
        };
        return ShipSpot.find((x, z) -> flags(level, x, z, surfaceY, shape.draught(), depth, shape.above()), anchors, shape.hull(),
                rules(), occupied);
    }

    /** A ship's world bounds with its plot's block box carried into the world ({@code ShipOrders.bounds}). */
    static AABB bounds(ShipBody ship) {
        return com.richardsenger.piratesnships.ship.template.ShipOrders.bounds(ship);
    }

    /**
     * The flags of column {@code (x, z)} for a hull of {@code draught} rows under the waterline row and {@code above}
     * rows over it, needing {@code depth} blocks of water under the keel, with the sea surface at {@code surfaceY}. An
     * unloaded column counts as land.
     */
    public static int flags(ServerLevel level, int x, int z, int surfaceY, int draught, int depth, int above) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return ShipSpot.LAND;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, surfaceY, z);
        return flags(y -> chunk.getBlockState(at.setY(y)), surfaceY, draught, depth, above);
    }

    /** One column's blocks by height. */
    @FunctionalInterface
    public interface Column {
        BlockState at(int y);
    }

    /** As {@link #flags(ServerLevel, int, int, int, int, int, int)} for one column's blocks. */
    public static int flags(Column column, int surfaceY, int draught, int depth, int above) {
        int flags = 0;
        BlockState surface = column.at(surfaceY);
        if (surface.getFluidState().is(FluidTags.WATER)) flags |= ShipSpot.WATER;
        for (int y = surfaceY - draught - 1; y <= surfaceY + 2; y++) {
            if (isGround(column.at(y))) {
                flags |= ShipSpot.LAND;
                break;
            }
        }
        boolean open = true;
        for (int y = surfaceY - draught; y <= surfaceY + above && open; y++) {
            open = ShipBlockRule.isFreeForShip(column.at(y));
        }
        if (open) flags |= ShipSpot.OPEN;
        boolean deep = open;
        for (int y = surfaceY - draught - depth; y <= surfaceY && deep; y++) {
            deep = isClearWater(column.at(y));
        }
        if (deep) flags |= ShipSpot.DEEP;
        return flags;
    }

    /** Ground for the clearance rule: natural solid blocks (sand, soil, stone, ice, coral blocks), not plants or kelp. */
    public static boolean isGround(BlockState state) {
        return state.is(ShipBlockRule.TERRAIN) && state.getFluidState().isEmpty() && !state.canBeReplaced();
    }

    /** Water a hull may lie in or over: water, a bubble column, seagrass; never kelp or ice. */
    public static boolean isClearWater(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    /** A spot found for a template: the search, and where the template goes when it found one. */
    public record Found(Shape shape, ShipSpot.Search search, int surfaceY) {

        public Optional<ShipSpot.Spot> spot() {
            return search.spot();
        }

        public @Nullable BlockPos origin() {
            return search.spot().map(s -> shape.origin(s, surfaceY)).orElse(null);
        }

        public Rotation rotation() {
            return search.spot().map(s -> shape.rotation(s.heading())).orElse(Rotation.NONE);
        }
    }

    /** Searches a spot for template {@code id} at {@code port}'s berths; empty when the template or the berths are missing. */
    public static Optional<Found> atPort(ServerLevel level, Port port, ResourceLocation id) {
        Optional<Shape> shape = shape(level, id);
        Optional<Integer> surface = surface(port);
        if (shape.isEmpty() || surface.isEmpty()) return Optional.empty();
        return Optional.of(new Found(shape.get(), search(level, shape.get(), anchors(level, port), surface.get()), surface.get()));
    }

    /** The berths' ports within {@code ship_spot_search} of {@code (x, z)} among {@code ports}, nearest berth first. */
    public static Optional<Port> portNear(List<Port> ports, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                          double x, double z) {
        int reach = WorldConfig.PORTS_SHIP_SPOT_SEARCH.get();
        Port best = null;
        double bestD = Double.MAX_VALUE;
        for (Port p : ports) {
            if (!p.dimension().equals(dimension)) continue;
            for (Berth b : p.berths()) {
                double d = Math.hypot(b.pos().getX() + 0.5 - x, b.pos().getZ() + 0.5 - z);
                if (d <= reach && d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether every column of {@code area} meets the depth rule for {@code shape} at {@code surfaceY} (GameTests). */
    public static boolean deep(ServerLevel level, ShipSpot.Rect area, int surfaceY, int draught, int depth, int above,
                               Predicate<BlockPos> report) {
        boolean all = true;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                if ((flags(level, x, z, surfaceY, draught, depth, above) & ShipSpot.DEEP) == 0) {
                    all = false;
                    if (!report.test(new BlockPos(x, surfaceY, z))) return false;
                }
            }
        }
        return all;
    }
}

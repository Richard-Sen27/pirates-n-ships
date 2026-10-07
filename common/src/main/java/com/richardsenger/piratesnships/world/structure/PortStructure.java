package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.TreasureChests;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.village.ShoreFacing;
import com.richardsenger.piratesnships.world.village.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.List;
import java.util.Optional;

/**
 * A port structure (design.md §10.1; WG1 for the seafarer village, generalised in WG2 for the pirate island): a vanilla
 * jigsaw layout from {@code start_pool} with three things vanilla's {@code minecraft:jigsaw} cannot do.
 * <ol>
 *     <li><b>Faces the sea.</b> From the chunk's centre it probes the four directions for sea water at sea level
 *     ({@link ShoreFacing}) and rotates the start piece so that its −z side points at the water; the start piece's
 *     {@code shore_anchor} (local x, z on its sea edge) lands on the last land column ({@link ShoreAnchor}). No water
 *     within {@code max_distance_from_water} blocks, a candidate in the water, or ground more than
 *     {@code max_shore_height} above the sea: no structure. These values and the {@code enabled} toggle come from the
 *     config of the {@code port_kind} ({@link WorldConfig#placement}).</li>
 *     <li><b>Height.</b> The start piece's y 0 (paving or sand) sits {@code start_height} (1) above the sea surface,
 *     the topmost water block, which is {@code ChunkGenerator.getSeaLevel() - 1}; no heightmap projection. The pier or
 *     jetty hangs from it rigidly (deck level with the start piece, berths at the sea surface). Streets and paths are
 *     terrain matching (their y 0 on the surface row through vanilla's gravity processor); buildings and huts are
 *     rigid and are sunk by one block when they hang from a terrain-matching piece, so their foundation row replaces
 *     the surface row as well (art/README.md "Structures (ST1)").</li>
 *     <li><b>Port.</b> {@link #afterPlace} records the port of {@code port_kind} with the berths and treasure markers
 *     read from the pieces' templates, binds the harbor desks and buries the treasure chests of the chunk being placed
 *     ({@link PortService}, {@link TreasureChests}).</li>
 * </ol>
 * Terrain adaptation comes from the JSON; both ports use {@code none}, because vanilla's beardifier treats every
 * rigid piece alike and would raise land under the pier and around the quay (see {@code VillageData}).
 */
public final class PortStructure extends Structure {

    /**
     * {@code shore_anchor} and {@code port_kind} default to the seafarer village's values, so a WG1 datapack without
     * them still reads as before; datagen always writes them.
     */
    public static final MapCodec<PortStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
            Codec.intRange(-16, 16).optionalFieldOf("start_height", 1).forGetter(s -> s.startHeight),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
            ShoreAnchor.CODEC.optionalFieldOf("shore_anchor", ShoreAnchor.VILLAGE).forGetter(s -> s.shoreAnchor),
            PortKind.CODEC.optionalFieldOf("port_kind", PortKind.SEAFARER_VILLAGE).forGetter(s -> s.portKind)
    ).apply(i, PortStructure::new));

    /** Salt of the frequency roll (independent of the layout random); the port kind is mixed in. */
    private static final long FREQUENCY_SALT = 0x5EAFA2E4L;

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDepth;
    private final int startHeight;
    private final int maxDistanceFromCenter;
    private final ShoreAnchor shoreAnchor;
    private final PortKind portKind;
    private volatile boolean warnedUnconfigured;

    public PortStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int startHeight,
                         int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind) {
        super(settings);
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.shoreAnchor = shoreAnchor;
        this.portKind = portKind;
    }

    /** First free (non-solid, fluids ignored) height of a column: water there iff it is at most the sea surface. */
    @FunctionalInterface
    public interface Terrain {
        int firstFree(int x, int z);
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int startHeight() {
        return startHeight;
    }

    public int maxDistanceFromCenter() {
        return maxDistanceFromCenter;
    }

    public ShoreAnchor shoreAnchor() {
        return shoreAnchor;
    }

    public PortKind portKind() {
        return portKind;
    }

    public Holder<StructureTemplatePool> startPool() {
        return startPool;
    }

    /** The salt of this kind's frequency roll; the village's is WG1's, so existing village worlds roll the same. */
    static long frequencySalt(PortKind kind) {
        return FREQUENCY_SALT ^ ((long) kind.ordinal() << 40);
    }

    /** This kind's enabled placement config, or empty: toggled off, or a kind without config yet (logged once). */
    private Optional<WorldConfig.PortPlacement> activePlacement() {
        Optional<WorldConfig.PortPlacement> placement = WorldConfig.placement(portKind);
        if (placement.isEmpty() && !warnedUnconfigured) {
            warnedUnconfigured = true;
            Constants.LOG.warn("Port structure of kind {} has no placement config yet and never generates", portKind.getSerializedName());
        }
        return placement.filter(p -> p.enabled().get());
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<WorldConfig.PortPlacement> placement = activePlacement();
        if (placement.isEmpty()) return Optional.empty();
        double frequency = placement.get().frequency().get();
        if (frequency < 1.0) {
            WorldgenRandom roll = new WorldgenRandom(new LegacyRandomSource(0L));
            roll.setLargeFeatureSeed(context.seed() ^ frequencySalt(portKind), context.chunkPos().x, context.chunkPos().z);
            if (roll.nextDouble() >= frequency) return Optional.empty();
        }
        ChunkGenerator generator = context.chunkGenerator();
        int seaSurface = generator.getSeaLevel() - 1;
        Terrain terrain = (x, z) -> generator.getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        BlockPos candidate = new BlockPos(context.chunkPos().getMiddleBlockX(), seaSurface, context.chunkPos().getMiddleBlockZ());
        return plan(context, candidate, seaSurface, terrain, maxDepth);
    }

    /**
     * The layout for a port whose site is {@code candidate}, with the sea surface (topmost water block) at
     * {@code seaSurface} and the column heights from {@code terrain}. Checks the {@code enabled} toggle, but not the
     * frequency roll. GameTests call this with a terrain read from real blocks.
     */
    public Optional<GenerationStub> plan(GenerationContext context, BlockPos candidate, int seaSurface, Terrain terrain, int depth) {
        Optional<WorldConfig.PortPlacement> placement = activePlacement();
        if (placement.isEmpty()) return Optional.empty();
        WorldConfig.PortPlacement config = placement.get();
        int cx = candidate.getX();
        int cz = candidate.getZ();
        Optional<ShoreFacing.Shore> shore = ShoreFacing.choose((dx, dz) -> terrain.firstFree(cx + dx, cz + dz) <= seaSurface,
                config.shoreProbeBlocks().get(), config.maxDistanceFromWater().get());
        if (shore.isEmpty()) return Optional.empty();
        Direction sea = shore.get().sea();
        Rotation rotation = ShoreFacing.rotationFacing(sea);
        BlockPos edge = VillageLayout.seaEdge(candidate, sea, shore.get().distance());
        BlockPos probe = shoreAnchor.groundProbe(edge, sea);
        int ground = terrain.firstFree(probe.getX(), probe.getZ()) - 1;
        if (ground - seaSurface > config.maxShoreHeight().get()) return Optional.empty();

        BlockPos origin = shoreAnchor.startOrigin(edge, rotation);
        // addPieces puts the start piece's y 0 at pos.y - groundLevelDelta (1 for single pool elements)
        BlockPos start = new BlockPos(origin.getX(), seaSurface + startHeight + 1, origin.getZ());
        GenerationContext rotated = new GenerationContext(context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
                context.randomState(), context.structureTemplateManager(),
                VillageLayout.randomForRotation(context.seed(), context.chunkPos(), rotation),
                context.seed(), context.chunkPos(), context.heightAccessor(), context.validBiome());
        Optional<GenerationStub> stub = JigsawPlacement.addPieces(rotated, startPool, Optional.empty(), depth, start, false,
                Optional.empty(), maxDistanceFromCenter, PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING);
        return stub.map(s -> new GenerationStub(s.position(), builder -> {
            List<StructurePiece> pieces = s.getPiecesBuilder().build().pieces();
            for (int i = 0; i < pieces.size(); i++) {
                StructurePiece piece = pieces.get(i);
                if (i > 0 && sinksIntoTerrain(piece)) piece.move(0, -1, 0);
                builder.addPiece(piece);
            }
        }));
    }

    /**
     * A rigid piece hung from a terrain-matching parent (a building on a street, a hut on a path): vanilla puts its
     * connector on the first free block above the surface, the ST1 convention wants its foundation row on the surface
     * row. A child's first junction is the one to its parent, carrying the parent's projection.
     */
    static boolean sinksIntoTerrain(StructurePiece piece) {
        if (!(piece instanceof PoolElementStructurePiece p)) return false;
        if (p.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) return false;
        List<JigsawJunction> junctions = p.getJunctions();
        return !junctions.isEmpty() && junctions.get(0).getDestProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunkPos, PiecesContainer pieces) {
        if (pieces.isEmpty()) return;
        ServerLevel server = level.getLevel();
        Port port = PortService.portOf(server, generator, pieces, portKind);
        PortService.report(server, port);
        PortService.intersection(chunkBox, port.box()).ifPresent(area -> PortService.bindDesks(level, area, port.id()));
        for (TreasureSite site : port.treasures()) {
            if (chunkBox.isInside(site.pos())) TreasureChests.bury(level, site.pos(), random);
        }
    }

    @Override
    public StructureType<?> type() {
        return PortStructures.PORT_STRUCTURE.get();
    }
}

package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.captain.IslandCaptains;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.TreasureChests;
import com.richardsenger.piratesnships.world.outpost.Garrison;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.village.ShoreFacing;
import com.richardsenger.piratesnships.world.village.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
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
 * A port structure (design.md §10.1; WG1 for the seafarer village, generalised in WG2 for the pirate island, WG3 for the
 * navy outpost): a vanilla
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
 *     read from the pieces' templates, binds the harbor desks, buries the treasure chests and (navy outposts, WG3)
 *     places the garrison posts of the chunk being placed ({@link PortService}, {@link TreasureChests},
 *     {@link Garrison}); a pirate island places its named captain (BOS1, {@link IslandCaptains}).</li>
 *     <li><b>Room for ships (STR7).</b> Where the water at the pier's, jetty's or quay's end is shallower than the
 *     largest ship's draught plus {@code world.ports.berth_depth}, up to two extension sections from
 *     {@code extension_pool} are hung from its end ({@link PierExtensions}); {@link #afterPlace} deepens the sea floor
 *     round every berth ({@link BerthBasins}), and {@link #adjustBoundingBox} grows the box so those chunks are
 *     placed.</li>
 * </ol>
 * Terrain adaptation comes from the JSON; the village uses {@code none}, because vanilla's beardifier treats every
 * rigid piece alike and would raise land under the pier (see {@code VillageData}); the pirate island (STR2) and the navy
 * outpost (STR4) use {@code beard_thin} with their rigid chain from the start (jetty; quay, walls and towers) kept out of
 * the beard ({@link #rigidChain}, {@link #unbearded}), so only the ground pieces and the start shape the terrain.
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
            PortKind.CODEC.optionalFieldOf("port_kind", PortKind.SEAFARER_VILLAGE).forGetter(s -> s.portKind),
            Codec.BOOL.optionalFieldOf("land_pieces_on_land", false).forGetter(s -> s.landPiecesOnLand),
            Codec.BOOL.optionalFieldOf("unbearded_start", false).forGetter(s -> s.unbeardedStart),
            StructureTemplatePool.CODEC.optionalFieldOf("extension_pool").forGetter(s -> s.extensionPool),
            TagKey.hashedCodec(Registries.BIOME).optionalFieldOf("cold_biomes").forGetter(s -> s.coldBiomes),
            Codec.BOOL.optionalFieldOf("cold", false).forGetter(s -> s.cold)
    ).apply(i, PortStructure::new));

    /** Salt of the frequency roll (independent of the layout random); the port kind is mixed in. */
    private static final long FREQUENCY_SALT = 0x5EAFA2E4L;

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDepth;
    private final int startHeight;
    private final int maxDistanceFromCenter;
    private final ShoreAnchor shoreAnchor;
    private final PortKind portKind;
    private final boolean landPiecesOnLand;
    private final boolean unbeardedStart;
    private final Optional<Holder<StructureTemplatePool>> extensionPool;
    private final Optional<TagKey<Biome>> coldBiomes;
    private final boolean cold;
    private volatile boolean warnedUnconfigured;

    public PortStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int startHeight,
                         int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, boolean landPiecesOnLand) {
        this(settings, startPool, maxDepth, startHeight, maxDistanceFromCenter, shoreAnchor, portKind, landPiecesOnLand, false);
    }

    public PortStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int startHeight,
                         int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, boolean landPiecesOnLand,
                         boolean unbeardedStart) {
        this(settings, startPool, maxDepth, startHeight, maxDistanceFromCenter, shoreAnchor, portKind, landPiecesOnLand, unbeardedStart,
                Optional.empty());
    }

    public PortStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int startHeight,
                         int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, boolean landPiecesOnLand,
                         boolean unbeardedStart, Optional<Holder<StructureTemplatePool>> extensionPool) {
        this(settings, startPool, maxDepth, startHeight, maxDistanceFromCenter, shoreAnchor, portKind, landPiecesOnLand, unbeardedStart,
                extensionPool, Optional.empty(), false);
    }

    public PortStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int startHeight,
                         int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, boolean landPiecesOnLand,
                         boolean unbeardedStart, Optional<Holder<StructureTemplatePool>> extensionPool,
                         Optional<TagKey<Biome>> coldBiomes, boolean cold) {
        super(settings);
        this.coldBiomes = coldBiomes;
        this.cold = cold;
        this.extensionPool = extensionPool;
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.shoreAnchor = shoreAnchor;
        this.portKind = portKind;
        this.landPiecesOnLand = landPiecesOnLand;
        this.unbeardedStart = unbeardedStart;
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

    /** STR2: whether land pieces over open water are dropped after the jigsaw ({@link LandFilter}). */
    public boolean landPiecesOnLand() {
        return landPiecesOnLand;
    }

    /**
     * STR3: whether the start piece is kept out of the beard's reach as well ({@link #unbearded}): the village's dock
     * head stands on the shore at the sea surface, and the beard round it would raise the sea in front of the quay
     * under the pier. The island's camp keeps its beard (it widens the landing).
     */
    public boolean unbeardedStart() {
        return unbeardedStart;
    }

    public Holder<StructureTemplatePool> startPool() {
        return startPool;
    }

    /** STR7: the pool the pier's, jetty's or quay's extension sections come from ({@link PierExtensions}). */
    public Optional<Holder<StructureTemplatePool>> extensionPool() {
        return extensionPool;
    }

    /** STR8: the biomes that make a site cold (the start's, or the sea's in front of it); empty: no biome rule. */
    public Optional<TagKey<Biome>> coldBiomes() {
        return coldBiomes;
    }

    /**
     * STR8: whether this is a kind's cold-shore structure (its own structure set, {@code cold_frequency}): it generates
     * only where {@link #coldBiomes} says the site is cold, the kind's other structure only where it is not.
     */
    public boolean cold() {
        return cold;
    }

    /** The salt of this kind's frequency roll; the village's is WG1's, so existing village worlds roll the same. */
    static long frequencySalt(PortKind kind) {
        return FREQUENCY_SALT ^ ((long) kind.ordinal() << 40);
    }

    /** STR8: the cold structure's roll is independent of the kind's other one (they share the grid). */
    static long frequencySalt(PortKind kind, boolean cold) {
        return cold ? frequencySalt(kind) ^ COLD_SALT : frequencySalt(kind);
    }

    private static final long COLD_SALT = 0xC01DC0A57L;

    /** How far out to sea from the shore edge the cold rule reads the sea's biome. */
    static final int COLD_SEA_PROBE = 16;

    /** STR8: sites the pier rule turned down (each chunk counted once), for the smoke test's log. */
    private static final java.util.Set<Long> REJECTED_SITES = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        double frequency = cold ? placement.get().coldFrequency().get() : placement.get().frequency().get();
        if (frequency < 1.0) {
            WorldgenRandom roll = new WorldgenRandom(new LegacyRandomSource(0L));
            roll.setLargeFeatureSeed(context.seed() ^ frequencySalt(portKind, cold), context.chunkPos().x, context.chunkPos().z);
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
        List<ShoreFacing.Shore> shores = ShoreFacing.ranked((dx, dz) -> terrain.firstFree(cx + dx, cz + dz) <= seaSurface,
                config.shoreProbeBlocks().get(), config.maxDistanceFromWater().get());
        if (shores.isEmpty()) return Optional.empty();
        boolean piersInWater = WorldConfig.STRUCTURE_PIERS_END_IN_WATER.get();
        // STR9: a side where most of the port would be dropped (a spit with a lagoon behind it) gives way to the next
        boolean resite = WorldConfig.STRUCTURE_RESITE_PIECES.get();
        double minShare = resite ? WorldConfig.STRUCTURE_MIN_PORT_SHARE.get() : 0.0;
        boolean pierRejected = false;
        PlanReport small = null;
        // STR8: the best side first (WG1's choice); a side whose pier would not stand in the sea gives way to the next
        for (ShoreFacing.Shore shore : piersInWater || resite ? shores : shores.subList(0, 1)) {
            Facing result = planFacing(context, candidate, seaSurface, terrain, depth, config, shore, piersInWater);
            if (result.stub() != null) {
                PlanReport report = result.report();
                if (report == null || report.kept >= minShare * report.planned) {
                    if (report != null) {
                        report.publish();
                        if (LOGGED_SITES.add(siteKey(context.chunkPos()))) Constants.LOG.info(report.line());
                    }
                    return Optional.of(result.stub());
                }
                if (small == null || report.kept > small.kept) small = report;
                continue;
            }
            pierRejected |= result.pierRejected();
            if (result.stop()) break;
        }
        if (small != null && REJECTED_SITES.add(siteKey(context.chunkPos()))) {
            Constants.LOG.info("STR9: {}{} site at {} {} rejected: its best side keeps {} of {} pieces ({} sites rejected so far)",
                    portKind.getSerializedName(), cold ? " (cold)" : "", cx, cz, small.kept, small.planned, REJECTED_SITES.size());
        } else if (pierRejected && REJECTED_SITES.add(siteKey(context.chunkPos()))) {
            Constants.LOG.info("STR8: {}{} site at {} {} rejected: no side puts its pier over the sea ({} sites rejected so far)",
                    portKind.getSerializedName(), cold ? " (cold)" : "", cx, cz, REJECTED_SITES.size());
        }
        return Optional.empty();
    }

    private long siteKey(ChunkPos chunk) {
        return chunk.toLong() * 8 + portKind.ordinal() * 2L + (cold ? 1 : 0);
    }

    /** STR9: sites whose plan report was logged (each chunk once). */
    private static final java.util.Set<Long> LOGGED_SITES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The outcome of planning one side: the stub, or why not (the pier on land; {@code stop}: no other side is tried). */
    private record Facing(@org.jetbrains.annotations.Nullable GenerationStub stub, boolean pierRejected, boolean stop,
                          @org.jetbrains.annotations.Nullable PlanReport report) {
        Facing(@org.jetbrains.annotations.Nullable GenerationStub stub, boolean pierRejected, boolean stop) {
            this(stub, pierRejected, stop, null);
        }
    }

    private Facing planFacing(GenerationContext context, BlockPos candidate, int seaSurface, Terrain terrain, int depth,
                              WorldConfig.PortPlacement config, ShoreFacing.Shore shore, boolean piersInWater) {
        Direction sea = shore.sea();
        Rotation rotation = ShoreFacing.rotationFacing(sea);
        BlockPos edge = VillageLayout.seaEdge(candidate, sea, shore.distance());
        BlockPos probe = shoreAnchor.groundProbe(edge, sea);
        int ground = terrain.firstFree(probe.getX(), probe.getZ()) - 1;
        if (ground - seaSurface > config.maxShoreHeight().get()) return new Facing(null, false, false);

        BlockPos origin = shoreAnchor.startOrigin(edge, rotation);
        // addPieces puts the start piece's y 0 at pos.y - groundLevelDelta (1 for single pool elements)
        BlockPos start = new BlockPos(origin.getX(), seaSurface + startHeight + 1, origin.getZ());
        GenerationContext rotated = new GenerationContext(context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
                context.randomState(), context.structureTemplateManager(),
                VillageLayout.randomForRotation(context.seed(), context.chunkPos(), rotation),
                context.seed(), context.chunkPos(), context.heightAccessor(), context.validBiome());
        Optional<GenerationStub> stub = JigsawPlacement.addPieces(rotated, startPool, Optional.empty(), depth, start, false,
                Optional.empty(), maxDistanceFromCenter, PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING);
        if (stub.isEmpty()) return new Facing(null, false, false);
        // STR8: the cold shores have their own structure; the biome is read where vanilla reads it (the stub's position)
        // and out at sea in front of the shore, before the jigsaw runs
        if (coldBiomes.isPresent() && isCold(context, stub.get().position(), edge.relative(sea, COLD_SEA_PROBE), seaSurface) != cold) {
            return new Facing(null, false, true);
        }
        List<StructurePiece> pieces = new java.util.ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
        StructureTemplateManager templates = context.structureTemplateManager();
        if (piersInWater && !PierExtensions.endsInWater(pieces, templates, terrain, seaSurface)) return new Facing(null, true, false);
        PlanReport report = new PlanReport(portKind, candidate.getX(), candidate.getZ());
        report.planned = pieces.size();
        List<StructurePiece> placed = arrange(context, pieces, seaSurface, terrain, report);
        BlockPos at = stub.get().position();
        return new Facing(new GenerationStub(at, builder -> placed.forEach(builder::addPiece)), false, false, report);
    }

    /**
     * STR8: whether the site is cold: the biome at the stub's position (where vanilla checks the structure's biomes) or
     * out at sea in front of the shore is in {@link #coldBiomes}.
     */
    private boolean isCold(GenerationContext context, BlockPos at, BlockPos seaward, int seaSurface) {
        TagKey<Biome> tag = coldBiomes.orElseThrow();
        var sampler = context.randomState().sampler();
        var source = context.chunkGenerator().getBiomeSource();
        Holder<Biome> here = source.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(at.getX()),
                net.minecraft.core.QuartPos.fromBlock(at.getY()), net.minecraft.core.QuartPos.fromBlock(at.getZ()), sampler);
        Holder<Biome> sea = source.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(seaward.getX()),
                net.minecraft.core.QuartPos.fromBlock(seaSurface), net.minecraft.core.QuartPos.fromBlock(seaward.getZ()), sampler);
        return here.is(tag) || sea.is(tag);
    }

    /**
     * The jigsaw's pieces made into the port (in order): land pieces off the water ({@link LandFilter}, re-sited first
     * since STR9: {@link PlanRepair}), wall runs ended on land ({@link WallRuns}), buildings sunk onto the surface row,
     * trails levelled ({@link TrailLevels}; a trail too far off its ground swapped for a straight before it is dropped),
     * the rigid chain out of the beard's reach, the pier extended (STR7). {@code report} gets the counts.
     */
    private List<StructurePiece> arrange(GenerationContext context, List<StructurePiece> pieces, int seaSurface, Terrain terrain,
                                         PlanReport report) {
        StructureTemplateManager templates = context.structureTemplateManager();
        net.minecraft.core.HolderGetter<StructureTemplatePool> pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        List<LandFilter.Node> nodes = landNodes(pieces);
        LandFilter.Water water = (x, z) -> terrain.firstFree(x, z) <= seaSurface;
        boolean resite = WorldConfig.STRUCTURE_RESITE_PIECES.get();
        PlanRepair.Env env = new PlanRepair.Env(templates, pools, water);
        boolean[] keep = filled(pieces.size());
        PlanReport.Reason[] why = new PlanReport.Reason[pieces.size()];
        if (landPiecesOnLand) PlanRepair.land(pieces, nodes, keep, why, env, resite, report);
        boolean[] chain = rigidChain(pieces, nodes);
        if (landPiecesOnLand) {
            for (WallRuns.Closure closure : WallRuns.closeOnLand(pieces, nodes, keep, chain, water, templates, pools, resite)) {
                for (int wall : closure.walls()) why[wall] = PlanReport.Reason.WALL;
            }
            for (int i = 1; i < pieces.size(); i++) {
                if (!keep[i] && why[i] == null) why[i] = PlanReport.Reason.CHILD;
            }
        }
        for (int i = 1; i < pieces.size(); i++) {
            if (keep[i] && sinksIntoTerrain(pieces.get(i))) pieces.get(i).move(0, -1, 0);
        }
        if (WorldConfig.STRUCTURE_LEVEL_TRAILS.get()) level(context, pieces, nodes, keep, why, env, resite, landPiecesOnLand, report);
        List<StructurePiece> placed = new java.util.ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            if (!keep[i]) continue;
            // the rigid chain from the start (STR4: quay, walls, towers; jetty; pier) and, with unbearded_start
            // (STR3), the start itself; unbearded_start also drops the rigid junctions' beard
            if ((chain[i] || (i == 0 && unbeardedStart)) && piece instanceof PoolElementStructurePiece p
                    && p.getGroundLevelDelta() != UNBEARDED_GROUND_LEVEL_DELTA) {
                piece = unbearded(p, templates, !unbeardedStart);
            }
            placed.add(piece);
        }
        account(report, pieces, nodes, keep, why, water);
        report.kept = placed.size();
        // STR7: the pier, jetty or quay grows where the water at its end is shallow
        if (extensionPool.isPresent() && WorldConfig.PORTS_PIER_EXTENSION.get()) {
            placed.addAll(PierExtensions.extend(templates, extensionPool.get(), placed, terrain, seaSurface, BerthBasins.depth()));
        }
        return placed;
    }

    /**
     * STR9: counts every dropped piece in {@code report}: by its own reason, or as a child of the reason its nearest
     * dropped (or re-sited) ancestor had.
     */
    static void account(PlanReport report, List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep,
                        PlanReport.Reason[] why, LandFilter.Water water) {
        for (int i = 1; i < keep.length; i++) {
            if (keep[i]) continue;
            PlanReport.Reason reason = why[i] == null ? PlanReport.Reason.CHILD : why[i];
            if (reason != PlanReport.Reason.CHILD) {
                report.drop(reason);
                int below = 0;
                boolean[] under = new boolean[keep.length];
                under[i] = true;
                for (int j = i + 1; j < keep.length; j++) {
                    int p = nodes.get(j).parent();
                    if (p >= 0 && p < j && under[p]) {
                        under[j] = true;
                        below++;
                    }
                }
                StructurePiece piece = pieces.get(i);
                String name = piece instanceof PoolElementStructurePiece pe ? StructureLayout.templateName(pe.getElement().toString()) : "?";
                int depth = 0;
                for (int a = nodes.get(i).parent(); a > 0 && depth < 50; a = nodes.get(a).parent()) depth++;
                report.roots.add(reason + " " + name + " depth " + depth + " wet " + LandFilter.wetSamples(piece.getBoundingBox(), water)
                        + (LandFilter.overWaterCentre(piece.getBoundingBox(), water) ? " centre" : "") + " below " + below);
                continue;
            }
            PlanReport.Reason root = PlanReport.Reason.LAND;
            for (int a = nodes.get(i).parent(); a >= 0; a = a < nodes.size() && nodes.get(a).parent() < a ? nodes.get(a).parent() : -1) {
                if (why[a] != null && why[a] != PlanReport.Reason.CHILD) {
                    root = why[a];
                    break;
                }
            }
            report.child(root);
        }
    }

    private static boolean[] filled(int n) {
        boolean[] out = new boolean[n];
        java.util.Arrays.fill(out, true);
        return out;
    }

    /**
     * STR8: lays the trails level ({@link TrailLevels}) from the generator's surface (the heights vanilla's jigsaw read
     * for them): a trail's natural row under its centre, a building's connector row at its joint. Moves the pieces and
     * their junctions; a dropped trail drops what hangs from it ({@code keep}). STR9: the limit is
     * {@code max_trail_step_rows}; with {@code resite} a trail piece beyond it is first swapped for a straight on the same
     * connector ({@link PlanRepair#swapTrail}) whose row would lie within it, and the rows are solved again.
     */
    private static void level(GenerationContext context, List<StructurePiece> pieces, List<LandFilter.Node> nodes, boolean[] keep,
                              PlanReport.Reason[] why, PlanRepair.Env env, boolean resite, boolean landRule, PlanReport report) {
        ChunkGenerator generator = context.chunkGenerator();
        int maxCut = WorldConfig.STRUCTURE_MAX_TRAIL_STEP_ROWS.get();
        int n = pieces.size();
        Integer[] naturals = new Integer[n];
        boolean[] tried = new boolean[n];
        TrailLevels.Result result;
        List<TrailLevels.Node> levels;
        while (true) {
            levels = levelNodes(context, pieces, nodes, keep, naturals);
            result = TrailLevels.solve(levels, maxCut);
            int root = -1;
            for (int i = 1; i < n; i++) {
                int parent = levels.get(i).parent();
                if (keep[i] && result.dropped()[i] && !tried[i] && !(parent >= 0 && result.dropped()[parent])) {
                    root = i;
                    break;
                }
            }
            if (root < 0 || !resite) break;
            tried[root] = true;
            int r = root;
            int parentRow = parentRow(levels, result, r);
            int rerooted = PlanRepair.swapTrail(pieces, nodes, keep, why, r, env, candidate -> {
                if (landRule && !PlanRepair.onLand(candidate, nodes.get(r).parent(), false, env.water())) return false;
                BoundingBox box = candidate.getBoundingBox();
                int natural = surfaceRow(context, (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2);
                int level = Math.max(parentRow - TrailLevels.MAX_STEP, Math.min(parentRow + TrailLevels.MAX_STEP, natural));
                return Math.abs(level - natural) <= maxCut;
            });
            if (rerooted >= 0) {
                why[r] = PlanReport.Reason.TRAIL;
                naturals[r] = null;
                report.swapped++;
                report.rerooted += rerooted;
            }
        }
        for (int i = 0; i < n; i++) {
            if (!keep[i]) continue;
            if (result.dropped()[i]) {
                keep[i] = false;
                int parent = levels.get(i).parent();
                why[i] = parent >= 0 && result.dropped()[parent] ? PlanReport.Reason.CHILD : PlanReport.Reason.TRAIL;
                continue;
            }
            TrailLevels.Node node = levels.get(i);
            if (node.kind() == TrailLevels.Kind.TRAIL && Math.abs(result.levelled(node, i) - node.natural()) > TrailLevels.MAX_CUT) {
                report.ramps++;
            }
            int delta = result.delta()[i];
            if (delta == 0) continue;
            StructurePiece piece = pieces.get(i);
            piece.move(0, delta, 0);
            if (piece instanceof PoolElementStructurePiece p) {
                List<JigsawJunction> junctions = p.getJunctions();
                for (int j = 0; j < junctions.size(); j++) {
                    JigsawJunction old = junctions.get(j);
                    junctions.set(j, new JigsawJunction(old.getSourceX(), old.getSourceGroundY() + delta, old.getSourceZ(),
                            old.getDeltaY(), old.getDestProjection()));
                }
            }
        }
        // the pieces whose parent was dropped by an earlier pass are not in the solve's tree
        for (int i = 1; i < n; i++) {
            int parent = nodes.get(i).parent();
            if (keep[i] && parent >= 0 && parent < i && !keep[parent]) {
                keep[i] = false;
                why[i] = PlanReport.Reason.CHILD;
            }
        }
    }

    /** The levelled row of piece {@code i}'s parent (the start's or a trail's), or its own planned row. */
    private static int parentRow(List<TrailLevels.Node> levels, TrailLevels.Result result, int i) {
        int parent = levels.get(i).parent();
        if (parent < 0) return levels.get(i).row();
        return result.levelled(levels.get(parent), parent);
    }

    private static int surfaceRow(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                context.randomState()) - 1;
    }

    /** The levelling's view of the pieces (dropped ones hang from nothing); trail naturals are cached in {@code naturals}. */
    private static List<TrailLevels.Node> levelNodes(GenerationContext context, List<StructurePiece> pieces, List<LandFilter.Node> nodes,
                                                     boolean[] keep, Integer[] naturals) {
        List<TrailLevels.Node> levels = new java.util.ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            BoundingBox box = piece.getBoundingBox();
            int parent = keep[i] ? nodes.get(i).parent() : -1;
            if (i == 0) {
                levels.add(new TrailLevels.Node(TrailLevels.Kind.START, -1, box.minY(), box.minY()));
            } else if (piece instanceof PoolElementStructurePiece p
                    && p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING) {
                if (naturals[i] == null) naturals[i] = surfaceRow(context, (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2);
                levels.add(new TrailLevels.Node(TrailLevels.Kind.TRAIL, parent, box.minY() - 1, naturals[i]));
            } else {
                int row = box.minY();
                if (piece instanceof PoolElementStructurePiece p && !p.getJunctions().isEmpty() && parent >= 0
                        && levelsKind(pieces.get(parent)) == TrailLevels.Kind.TRAIL) {
                    if (naturals[i] == null) {
                        JigsawJunction up = p.getJunctions().get(0);
                        naturals[i] = surfaceRow(context, up.getSourceX(), up.getSourceZ());
                    }
                    row = naturals[i];
                }
                levels.add(new TrailLevels.Node(TrailLevels.Kind.OTHER, parent, row, row));
            }
        }
        return levels;
    }

    private static TrailLevels.Kind levelsKind(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p
                && p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING
                ? TrailLevels.Kind.TRAIL : TrailLevels.Kind.OTHER;
    }

    /**
     * The pieces hanging rigidly from the start through rigid pieces only (STR4): the jetty, the pier, the quay, and
     * the navy outpost's whole wall trace with its towers (a wall hangs from the gate or from the wall before it). They
     * are copied {@link #unbearded}. A land piece (terrain matching, or rigid on a terrain-matching parent) breaks the
     * chain, so the buildings and decorations of the court, the camp and the town keep the beard.
     */
    static boolean[] rigidChain(List<StructurePiece> pieces, List<LandFilter.Node> nodes) {
        boolean[] chain = new boolean[pieces.size()];
        for (int i = 1; i < pieces.size(); i++) {
            LandFilter.Node node = nodes.get(i);
            if (node.land() || !(pieces.get(i) instanceof PoolElementStructurePiece p)
                    || p.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) continue;
            int parent = node.parent();
            chain[i] = parent == 0 || (parent > 0 && parent < i && chain[parent]);
        }
        return chain;
    }

    /**
     * The ground level offset that takes a piece out of the beard's reach: vanilla's {@code Beardifier} levels the
     * terrain toward {@code box.minY() + groundLevelDelta} only within 12 blocks above and below that level, so a level
     * a thousand blocks down leaves the piece's surroundings alone. The offset is used by nothing else once the jigsaw
     * has assembled the pieces (it is saved with the piece).
     */
    public static final int UNBEARDED_GROUND_LEVEL_DELTA = -1000;

    /**
     * {@code piece} with its ground level out of the beard's reach (STR2): a rigid piece hanging rigidly from the start
     * piece is the jetty, the pier or the quay, standing in the water. Vanilla's jigsaw gives such a child the ground
     * level of its parent (the jetty hangs five blocks below the camp's sand, so its ground level comes out six above
     * its foot, one above the sea), and with terrain adaptation the beard would raise a sand bar the length of the
     * jetty (seen in the REL3 smoke world). The copy keeps the element, position, rotation, box and junctions.
     */
    static PoolElementStructurePiece unbearded(PoolElementStructurePiece piece, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates) {
        return unbearded(piece, templates, true);
    }

    /**
     * As {@link #unbearded(PoolElementStructurePiece, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager)};
     * without {@code keepRigidJunctions} the copy drops its junctions to rigid pieces (STR3, {@code unbearded_start}):
     * vanilla's beardifier also lays ground round every junction, and the one between the dock head and the pier sits on
     * the quay's sea edge, where it would raise the seabed under the pier's landward end. The junctions to
     * terrain-matching pieces (the street) stay; each junction is kept by both of its pieces, so nothing else is lost.
     */
    static PoolElementStructurePiece unbearded(PoolElementStructurePiece piece,
                                               net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                               boolean keepRigidJunctions) {
        PoolElementStructurePiece copy = new PoolElementStructurePiece(templates, piece.getElement(), piece.getPosition(),
                UNBEARDED_GROUND_LEVEL_DELTA, piece.getRotation(), piece.getBoundingBox(), LiquidSettings.APPLY_WATERLOGGING);
        for (JigsawJunction junction : piece.getJunctions()) {
            if (keepRigidJunctions || junction.getDestProjection() != StructureTemplatePool.Projection.RIGID) copy.addJunction(junction);
        }
        return copy;
    }

    /**
     * The pieces as {@link LandFilter} sees them: a piece's parent is the earlier piece that holds the jigsaw its first
     * junction starts from (a child's first junction is the one to its parent); a land piece is terrain matching
     * (a trail) or hangs rigidly from one ({@link #sinksIntoTerrain}: a building or decoration on a trail).
     */
    static List<LandFilter.Node> landNodes(List<StructurePiece> pieces) {
        List<LandFilter.Node> out = new java.util.ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            int parent = -1;
            boolean land = false;
            if (i > 0 && piece instanceof PoolElementStructurePiece p && !p.getJunctions().isEmpty()) {
                JigsawJunction up = p.getJunctions().get(0);
                for (int j = 0; j < i; j++) {
                    BoundingBox b = pieces.get(j).getBoundingBox();
                    if (up.getSourceX() >= b.minX() && up.getSourceX() <= b.maxX()
                            && up.getSourceZ() >= b.minZ() && up.getSourceZ() <= b.maxZ()) {
                        parent = j;
                        if (up.getSourceGroundY() >= b.minY() - 1 && up.getSourceGroundY() <= b.maxY() + 1) break;
                    }
                }
                land = p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING || sinksIntoTerrain(piece);
            }
            // STR8: a rigid land piece (a building or decoration on a trail) needs its corners on land as well
            out.add(new LandFilter.Node(piece.getBoundingBox(), i == 0 ? -1 : Math.max(parent, 0), land, i > 0 && sinksIntoTerrain(piece)));
        }
        return out;
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

    /**
     * STR7: the box reaches round the berths' basins ({@link BerthBasins#margin}), so worldgen places (and
     * {@link #afterPlace} carves) the chunks of the water beside the pier as well; at least vanilla's 12 for the beard.
     */
    @Override
    public BoundingBox adjustBoundingBox(BoundingBox box) {
        int margin = WorldConfig.PORTS_CARVE_BERTHS.get() ? BerthBasins.margin() : 0;
        return box.inflatedBy(Math.max(margin, terrainAdaptation() != TerrainAdjustment.NONE ? 12 : 0));
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunkPos, PiecesContainer pieces) {
        if (pieces.isEmpty()) return;
        ServerLevel server = level.getLevel();
        // STR7: deep water at the berths (before the port reads its berths: they do not change, the water does)
        if (WorldConfig.PORTS_CARVE_BERTHS.get()) BerthBasins.carve(level, chunkBox, pieces.pieces(), server.getStructureManager());
        // PIER1: the piles and pillars reach the (carved) floor
        if (WorldConfig.STRUCTURE_PILES_TO_FLOOR.get()) PileFootings.extend(level, chunkBox, pieces.pieces());
        Port port = PortService.portOf(server, generator, pieces, portKind);
        PortService.report(server, port);
        PortService.intersection(chunkBox, port.box()).ifPresent(area -> PortService.bindDesks(level, area, port.id()));
        for (TreasureSite site : port.treasures()) {
            if (chunkBox.isInside(site.pos())) TreasureChests.bury(level, site.pos(), random);
        }
        if (portKind == PortKind.NAVY_OUTPOST) Garrison.place(level, chunkBox, pieces);
        if (portKind == PortKind.PIRATE_ISLAND) IslandCaptains.place(level, chunkBox, pieces, port);
        com.richardsenger.piratesnships.mob.harbor.HarborMasters.place(level, chunkBox, pieces, port);
        // LIFE1: the village's people, each in its building
        com.richardsenger.piratesnships.mob.village.Residents.place(level, chunkBox, pieces, port);
        // STR8: last, so the beard's fill, the surface and every piece of this chunk are in place
        if (WorldConfig.STRUCTURE_CLEAR_DOORS.get()) DoorClearing.clear(level, chunkBox, pieces.pieces(), server.getStructureManager());
        // STR9: half slabs on the trails' one-row steps, after the doors (a bottom slab before a door is a step, STR6)
        if (WorldConfig.STRUCTURE_TRAIL_SLABS.get() && WorldConfig.STRUCTURE_LEVEL_TRAILS.get()) {
            TrailSlabs.place(level, chunkBox, pieces.pieces(), server.getStructureManager(), portKind);
        }
    }

    @Override
    public StructureType<?> type() {
        return PortStructures.PORT_STRUCTURE.get();
    }
}

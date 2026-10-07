package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.village.BerthMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ports from generated structures (WG1): builds the {@link Port} record from a structure's placed pieces, hands it to
 * the {@link PortRegistry} on the server thread, opens the port's market, binds harbor desks, and installs the port
 * locator of {@link HarborDeskService} so a desk placed later inside a port's box binds too.
 */
public final class PortService {

    private PortService() {
    }

    /** Market id of a seafarer village whose start piece is centred at (x, z): {@code pirates_n_ships:village_<x>_<z>}. */
    public static ResourceLocation villageId(BlockPos centre) {
        return Constants.id("village_" + centre.getX() + "_" + centre.getZ());
    }

    /** The start piece's centre at its foundation row (as {@code StructureStart} uses as the pivot). */
    public static BlockPos centreOf(PiecesContainer pieces) {
        BoundingBox start = pieces.pieces().get(0).getBoundingBox();
        BlockPos c = start.getCenter();
        return new BlockPos(c.getX(), start.minY(), c.getZ());
    }

    /** Berths of every pool piece, read from the pieces' templates (independent of jigsaw replacement). */
    public static List<Berth> berthsOf(PiecesContainer pieces, StructureTemplateManager templates) {
        List<Berth> out = new ArrayList<>();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            out.addAll(BerthMarkers.fromJigsaws(p.getElement().getShuffledJigsawBlocks(
                    templates, p.getPosition(), p.getRotation(), RandomSource.create(0L))));
        }
        return out;
    }

    /**
     * The port record of a generated village. Safe on a world generation thread: the climate comes from the biome
     * source (noise), not from loaded chunks.
     */
    public static Port villagePort(ServerLevel level, ChunkGenerator generator, PiecesContainer pieces) {
        BlockPos centre = centreOf(pieces);
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(centre.getX()),
                QuartPos.fromBlock(centre.getY()), QuartPos.fromBlock(centre.getZ()), level.getChunkSource().randomState().sampler());
        Climate climate = PortClimate.of(biome.value().getBaseTemperature(), biome.value().hasPrecipitation());
        return new Port(villageId(centre), PortKind.SEAFARER_VILLAGE, level.dimension(), centre,
                pieces.calculateBoundingBox(), climate, berthsOf(pieces, level.getStructureManager()));
    }

    /**
     * Hands a generated port to the registry: directly on the server thread, else queued onto it (world generation
     * runs on worker threads and must not touch saved data).
     */
    public static void report(ServerLevel level, Port port) {
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) {
            register(server, port);
        } else {
            server.execute(() -> register(server, port));
        }
    }

    /** Registers {@code port} and opens its market. Returns whether the port is new. Server thread. */
    public static boolean register(MinecraftServer server, Port port) {
        boolean added = PortRegistry.get(server).add(port);
        openMarket(server, port);
        if (added) Constants.LOG.debug("Registered port {} at {}", port.id(), port.centre());
        return added;
    }

    /** Opens the port's market from its kind and climate the first time (later calls only refresh it). */
    public static void openMarket(MinecraftServer server, Port port) {
        TradeService.openMarket(server, port.id(),
                () -> TradeService.deriveProfile(server, port.kind(), port.climate(), port.id().toString().hashCode()));
    }

    /** Opens the markets of every registered port (after a restart or when goods changed). */
    public static void openAllMarkets(MinecraftServer server) {
        for (Port port : PortRegistry.get(server).index().all()) openMarket(server, port);
    }

    /**
     * Binds every harbor desk inside {@code area} to {@code port}. {@code area} must lie in chunks {@code level} can
     * access (during generation: the chunk being placed). Returns the number of desks bound.
     */
    public static int bindDesks(WorldGenLevel level, BoundingBox area, ResourceLocation port) {
        int bound = 0;
        ChunkPos min = new ChunkPos(BlockPos.containing(area.minX(), 0, area.minZ()));
        ChunkPos max = new ChunkPos(BlockPos.containing(area.maxX(), 0, area.maxZ()));
        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (BlockPos pos : List.copyOf(chunk.getBlockEntitiesPos())) {
                    if (!area.isInside(pos) || !chunk.getBlockState(pos).is(HarborDesks.HARBOR_DESK.get())) continue;
                    if (level.getBlockEntity(pos) instanceof HarborDeskBlockEntity desk) {
                        desk.setPort(Optional.of(port));
                        bound++;
                    }
                }
            }
        }
        return bound;
    }

    /** The intersection of two boxes, or empty if they do not overlap. */
    public static Optional<BoundingBox> intersection(BoundingBox a, BoundingBox b) {
        if (!a.intersects(b)) return Optional.empty();
        return Optional.of(new BoundingBox(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()),
                Math.min(a.maxX(), b.maxX()), Math.min(a.maxY(), b.maxY()), Math.min(a.maxZ(), b.maxZ())));
    }

    /** Installs the desk locator "which registered port's box contains this position". Called on server start. */
    public static void installDeskLocator() {
        HarborDeskService.setPortLocator((level, pos) ->
                PortRegistry.get(level.getServer()).index().containing(level.dimension(), pos).map(Port::id));
    }

    /** Removes the locator again (server stop), so a stale server is never consulted. */
    public static void removeDeskLocator() {
        HarborDeskService.setPortLocator((level, pos) -> Optional.empty());
    }
}

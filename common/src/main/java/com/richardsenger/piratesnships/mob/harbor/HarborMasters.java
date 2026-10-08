package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.port.Port;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every port's harbor master (PRT1a, docs/design.md §10.3), the {@code IslandCaptains} pattern.
 *
 * <ul>
 *   <li><b>Placement:</b> {@link #place} from the port's {@code afterPlace} for every port kind (pirate islands only
 *       with {@code mobs.harbor_master.at_pirate_islands}): he stands at the post of {@link HarborMasterPosts} in the
 *       chunk that holds it, so world generation places him exactly once. Over a loaded world (a GameTest,
 *       {@code /place structure}) a port whose registered harbor master lives, or whose post is manned, gets none.</li>
 *   <li><b>Registry:</b> each one goes into {@link HarborMasterRegistry} under his port id; world generation runs on
 *       worker threads, so the entry is handed to the server thread.</li>
 *   <li><b>Respawn:</b> his death or removal marks the entry lost on that day; {@link #onServerTick} checks every
 *       {@link #CHECK_INTERVAL} ticks and puts a new harbor master on the post {@code respawn_days} later, once the
 *       post's chunk is loaded and ticking.</li>
 * </ul>
 */
public final class HarborMasters {

    public static final int CHECK_INTERVAL = 100;
    public static final long TICKS_PER_DAY = 24000L;

    private HarborMasters() {
    }

    /** The world day players see ({@code overworld day time / 24000}). */
    public static long today(MinecraftServer server) {
        return server.overworld().getDayTime() / TICKS_PER_DAY;
    }

    // --- world generation ---------------------------------------------------------------------------------------

    /** The port's pieces as {@link HarborMasterPosts} reads them (non-pool pieces are skipped). */
    public static List<PlacedPiece> placedPieces(PiecesContainer pieces) {
        List<PlacedPiece> out = new ArrayList<>();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            out.add(new PlacedPiece(HarborMasterPosts.pieceName(p.getElement().toString()), p.getPosition(), p.getRotation()));
        }
        return out;
    }

    /** Whether a port of {@code kind} gets a harbor master (config read when asked). */
    public static boolean placesAt(PortKind kind) {
        return HarborMasterConfig.enabled() && (kind != PortKind.PIRATE_ISLAND || HarborMasterConfig.AT_PIRATE_ISLANDS.get());
    }

    /** Places the port's harbor master if his post lies inside {@code chunkBox}. Returns the number placed (0 or 1). */
    public static int place(WorldGenLevel level, BoundingBox chunkBox, PiecesContainer pieces, Port port) {
        if (!placesAt(port.kind())) return 0;
        Optional<HarborMasterPosts.Placement> post = HarborMasterPosts.post(placedPieces(pieces));
        if (post.isEmpty() || !chunkBox.isInside(post.get().pos())) return 0;
        BlockPos pos = post.get().pos();
        if (!free(level, pos) || manned(level, pos)) return 0;
        ServerLevel server = level.getLevel();
        MinecraftServer ms = server.getServer();
        // over a loaded world the registry is safe to read (server thread): a living harbor master is never doubled
        if (ms.isSameThread() && livingMaster(ms, port.id())) return 0;
        HarborMaster master = create(server, level, port.id(), pos, post.get().facing(), MobSpawnType.STRUCTURE);
        if (master == null || !level.addFreshEntity(master)) return 0;
        HarborMasterRegistry.Entry entry = new HarborMasterRegistry.Entry(master.getUUID(), true, 0L, server.dimension(), pos,
                post.get().facing());
        if (ms.isSameThread()) {
            register(ms, port.id(), entry);
        } else {
            ms.execute(() -> register(ms, port.id(), entry));
        }
        return 1;
    }

    /** The post and the block above it have no collision (terrain may fill a post: then it stays empty). */
    private static boolean free(WorldGenLevel level, BlockPos post) {
        for (BlockPos p : List.of(post, post.above())) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** A harbor master already stands at the post (always false during world generation: the region sees no entities). */
    private static boolean manned(WorldGenLevel level, BlockPos post) {
        return !level.getEntitiesOfClass(HarborMaster.class, new AABB(post).inflate(1.0)).isEmpty();
    }

    private static boolean livingMaster(MinecraftServer server, ResourceLocation port) {
        return HarborMasterRegistry.get(server).get(port).map(HarborMasterRegistry.Entry::alive).orElse(false);
    }

    // --- spawning -----------------------------------------------------------------------------------------------

    /** A new harbor master of {@code port} at {@code pos}, stationary and persistent; not yet added. */
    private static @Nullable HarborMaster create(ServerLevel level, ServerLevelAccessor accessor, ResourceLocation port,
                                                 BlockPos pos, Direction facing, MobSpawnType reason) {
        HarborMaster master = MobContent.HARBOR_MASTER.get().create(level);
        if (master == null) return null;
        float yaw = facing.toYRot();
        master.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        master.setYHeadRot(yaw);
        master.setYBodyRot(yaw);
        master.setStationary(true);
        master.setPersistenceRequired();
        master.assign(port, pos, facing);
        master.finalizeSpawn(accessor, accessor.getCurrentDifficultyAt(pos), reason, null);
        return master;
    }

    /** Records {@code entry} as {@code port}'s harbor master. Server thread. */
    static void register(MinecraftServer server, ResourceLocation port, HarborMasterRegistry.Entry entry) {
        HarborMasterRegistry registry = HarborMasterRegistry.get(server);
        registry.get(port).filter(e -> e.alive() && !e.id().equals(entry.id()))
                .ifPresent(e -> Constants.LOG.warn("Port {} already had a living harbor master {}; {} replaces him", port, e.id(), entry.id()));
        registry.put(port, entry);
    }

    /**
     * Spawns {@code port}'s harbor master at {@code pos} (respawns, GameTests): registered and posted. Refused
     * ({@code null}) while the type is disabled, while the port's registered harbor master lives, or when the level
     * refuses the entity.
     */
    public static @Nullable HarborMaster spawn(ServerLevel level, ResourceLocation port, BlockPos pos, Direction facing) {
        MinecraftServer server = level.getServer();
        if (!HarborMasterConfig.enabled() || livingMaster(server, port)) return null;
        HarborMaster master = create(level, level, port, pos, facing, MobSpawnType.COMMAND);
        if (master == null || !level.addFreshEntity(master)) return null;
        register(server, port, new HarborMasterRegistry.Entry(master.getUUID(), true, 0L, level.dimension(), pos, facing));
        return master;
    }

    // --- loss and respawn ---------------------------------------------------------------------------------------

    /** From {@code HarborMaster#die} and {@code #remove}: the port lost its harbor master today. */
    static void onLost(HarborMaster master) {
        MinecraftServer server = master.level().getServer();
        ResourceLocation port = master.port();
        if (server != null && port != null) HarborMasterRegistry.get(server).markDead(port, master.getUUID(), today(server));
    }

    /** Every {@link #CHECK_INTERVAL} ticks: new harbor masters for ports whose one was lost long enough ago. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        check(server);
    }

    /** Places every due harbor master whose post is loaded; returns how many. */
    public static int check(MinecraftServer server) {
        if (!HarborMasterConfig.enabled()) return 0;
        long today = today(server);
        int respawnDays = HarborMasterConfig.RESPAWN_DAYS.get();
        int placed = 0;
        for (Map.Entry<ResourceLocation, HarborMasterRegistry.Entry> e : HarborMasterRegistry.get(server).all().entrySet()) {
            HarborMasterRegistry.Entry entry = e.getValue();
            if (!entry.respawnDue(today, respawnDays)) continue;
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null || !level.isPositionEntityTicking(entry.post())) continue;
            if (spawn(level, e.getKey(), entry.post(), entry.facing()) != null) placed++;
        }
        return placed;
    }
}

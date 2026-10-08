package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.treasure.TreasureBinding;
import com.richardsenger.piratesnships.world.treasure.TreasureMapService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * Every pirate island's named captain (BOS1, docs/design.md §10.1, §15).
 *
 * <ul>
 *   <li><b>Placement:</b> {@link #place} from the island's {@code afterPlace} (the {@code Garrison} pattern): the
 *       captain stands at the post of {@link CaptainPosts} in the chunk that holds it, so world generation places him
 *       exactly once. Over a loaded world (a GameTest, {@code /place structure}) an island whose registered captain
 *       lives, or whose post is manned, gets none: never two captains.</li>
 *   <li><b>Registry and bounty:</b> each captain goes into {@link CaptainRegistry} under the island's port id, and the
 *       navy puts its standing bounty ({@code mobs.captain.bounty}) on him ({@link LawService#placeStandingBounty}).
 *       World generation runs on worker threads, so both are handed to the server thread.</li>
 *   <li><b>Loss:</b> his death or removal (handed over to the navy) marks the entry lost on that day; if no player
 *       killed him nobody can claim the bounty, so it is withdrawn.</li>
 *   <li><b>Succession:</b> {@link #onServerTick} checks every {@link #CHECK_INTERVAL} ticks: an island whose captain
 *       was lost {@code respawn_days} days ago gets a successor (a new name, a new bounty) once his post's chunk is
 *       loaded and ticking.</li>
 * </ul>
 */
public final class IslandCaptains {

    public static final int CHECK_INTERVAL = 100;
    public static final long TICKS_PER_DAY = 24000L;

    private IslandCaptains() {
    }

    /** The world day players see ({@code overworld day time / 24000}). */
    public static long today(MinecraftServer server) {
        return server.overworld().getDayTime() / TICKS_PER_DAY;
    }

    // --- world generation ---------------------------------------------------------------------------------------

    /** The island's pieces as {@link CaptainPosts} reads them (non-pool pieces are skipped). */
    public static List<PlacedPiece> placedPieces(PiecesContainer pieces) {
        List<PlacedPiece> out = new ArrayList<>();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            out.add(new PlacedPiece(CaptainPosts.pieceName(p.getElement().toString()), p.getPosition(), p.getRotation()));
        }
        return out;
    }

    /** Places the island's captain if his post lies inside {@code chunkBox}. Returns the number placed (0 or 1). */
    public static int place(WorldGenLevel level, BoundingBox chunkBox, PiecesContainer pieces, Port port) {
        if (!CaptainConfig.enabled()) return 0;
        Optional<CaptainPosts.Placement> post = CaptainPosts.post(placedPieces(pieces));
        if (post.isEmpty() || !chunkBox.isInside(post.get().pos())) return 0;
        BlockPos pos = post.get().pos();
        if (!free(level, pos) || manned(level, pos)) return 0;
        ServerLevel server = level.getLevel();
        MinecraftServer ms = server.getServer();
        // over a loaded world the registry is safe to read (server thread): a living captain is never doubled
        if (ms.isSameThread() && livingCaptain(ms, port.id())) return 0;
        String name = CaptainNames.name(port.id().toString(), 0);
        PirateCaptain captain = create(server, level, port.id(), pos, post.get().facing(), name, MobSpawnType.STRUCTURE);
        if (captain == null || !level.addFreshEntity(captain)) return 0;
        CaptainEntry entry = new CaptainEntry(captain.getUUID(), name, 0, true, 0L, server.dimension(), pos, post.get().facing());
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

    /** A captain already stands at the post (always false during world generation: the region sees no entities). */
    private static boolean manned(WorldGenLevel level, BlockPos post) {
        return !level.getEntitiesOfClass(PirateCaptain.class, new AABB(post).inflate(1.0)).isEmpty();
    }

    private static boolean livingCaptain(MinecraftServer server, ResourceLocation port) {
        return CaptainRegistry.get(server).get(port).map(CaptainEntry::alive).orElse(false);
    }

    // --- spawning -----------------------------------------------------------------------------------------------

    /** A new captain of {@code port} at {@code pos}, named, hatted, at full configured health; not yet added. */
    private static @Nullable PirateCaptain create(ServerLevel level, ServerLevelAccessor accessor, ResourceLocation port,
                                                  BlockPos pos, Direction facing, String name, MobSpawnType reason) {
        PirateCaptain captain = MobContent.PIRATE_CAPTAIN.get().create(level);
        if (captain == null) return null;
        float yaw = facing.toYRot();
        captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        captain.setYHeadRot(yaw);
        captain.setYBodyRot(yaw);
        captain.setStationary(true);
        captain.setPersistenceRequired();
        captain.assign(port, pos, facing);
        captain.setCustomName(Component.literal(name));
        captain.finalizeSpawn(accessor, accessor.getCurrentDifficultyAt(pos), reason, null);
        var health = captain.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(CaptainConfig.HEALTH.get());
        captain.setHealth(captain.getMaxHealth());
        return captain;
    }

    /** Records {@code entry} as {@code port}'s captain and puts the navy's standing bounty on him. Server thread. */
    static void register(MinecraftServer server, ResourceLocation port, CaptainEntry entry) {
        CaptainRegistry registry = CaptainRegistry.get(server);
        registry.get(port).filter(e -> e.alive() && !e.id().equals(entry.id()))
                .ifPresent(e -> Constants.LOG.warn("Pirate island {} already had a living captain {}; {} replaces him", port, e.name(), entry.name()));
        registry.put(port, entry);
        LawService.placeStandingBounty(server, BountyTarget.npc(entry.id(), entry.name()), CaptainConfig.BOUNTY.get());
    }

    /**
     * Spawns {@code port}'s captain of {@code generation} at {@code pos} (commands, successors, GameTests): named,
     * registered, with his bounty. Refused ({@code null}) while the type is disabled, while the island's registered
     * captain lives, or when the level refuses the entity.
     */
    public static @Nullable PirateCaptain spawn(ServerLevel level, ResourceLocation port, BlockPos pos, Direction facing, int generation) {
        MinecraftServer server = level.getServer();
        if (!CaptainConfig.enabled() || livingCaptain(server, port)) return null;
        String name = CaptainNames.name(port.toString(), generation);
        PirateCaptain captain = create(level, level, port, pos, facing, name, MobSpawnType.COMMAND);
        if (captain == null || !level.addFreshEntity(captain)) return null;
        register(server, port, new CaptainEntry(captain.getUUID(), name, generation, true, 0L, level.dimension(), pos, facing));
        return captain;
    }

    // --- loss and succession ------------------------------------------------------------------------------------

    /** From {@code PirateCaptain#die}: the island lost its captain; an unclaimable bounty is withdrawn. */
    static void onDeath(PirateCaptain captain, DamageSource source) {
        MinecraftServer server = captain.level().getServer();
        if (server == null) return;
        markLost(server, captain);
        if (!(source.getEntity() instanceof Player)) LawService.withdrawBounties(server, captain.getUUID());
    }

    /** From {@code PirateCaptain#remove} (discarded alive: handed over to the navy, a command, a disabled config). */
    static void onLost(PirateCaptain captain) {
        MinecraftServer server = captain.level().getServer();
        if (server == null) return;
        markLost(server, captain);
        LawService.withdrawBounties(server, captain.getUUID());
    }

    private static void markLost(MinecraftServer server, PirateCaptain captain) {
        ResourceLocation port = captain.port();
        if (port != null) CaptainRegistry.get(server).markDead(port, captain.getUUID(), today(server));
    }

    /** Every {@link #CHECK_INTERVAL} ticks: successors for islands whose captain was lost long enough ago. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        succeed(server);
    }

    /** Places every due successor whose post is loaded; returns how many. */
    public static int succeed(MinecraftServer server) {
        if (!CaptainConfig.enabled()) return 0;
        long today = today(server);
        int respawnDays = CaptainConfig.RESPAWN_DAYS.get();
        int placed = 0;
        for (Map.Entry<ResourceLocation, CaptainEntry> e : CaptainRegistry.get(server).all().entrySet()) {
            CaptainEntry entry = e.getValue();
            if (!entry.successorDue(today, respawnDays)) continue;
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null || !level.isPositionEntityTicking(entry.post())) continue;
            if (spawn(level, e.getKey(), entry.post(), entry.facing(), entry.generation() + 1) != null) placed++;
        }
        return placed;
    }

    // --- drops --------------------------------------------------------------------------------------------------

    /**
     * A treasure map bound to the first unlooted treasure of the captain's island ({@code mobs.captain.drop_map}), or
     * empty: no island, no treasure left, or the map is off.
     */
    public static ItemStack islandMap(ServerLevel level, PirateCaptain captain) {
        ResourceLocation port = captain.port();
        if (!CaptainConfig.DROP_MAP.get() || port == null) return ItemStack.EMPTY;
        Optional<Port> island = PortRegistry.get(level.getServer()).index().byId(port);
        if (island.isEmpty()) return ItemStack.EMPTY;
        ServerLevel siteLevel = level.getServer().getLevel(island.get().dimension());
        if (siteLevel == null) return ItemStack.EMPTY;
        return TreasureBinding.firstUnlooted(island.get())
                .map(site -> TreasureMapService.boundMap(siteLevel, island.get(), site))
                .orElse(ItemStack.EMPTY);
    }
}

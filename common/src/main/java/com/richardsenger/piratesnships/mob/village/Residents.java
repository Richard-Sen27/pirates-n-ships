package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.harbor.HarborMasters;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The people of every seafarer village (LIFE1), the {@code HarborMasters} pattern.
 *
 * <ul>
 *   <li><b>Placement:</b> {@link #place} from the village's {@code afterPlace} ({@code PortStructure}): the village's
 *       {@link VillagePlan} is the same in every chunk, so each villager is placed by the chunk that holds its first
 *       station, on that station. Over a loaded world (a GameTest, {@code /place structure}) a slot whose registered
 *       villager lives, or whose station is taken by a villager already, gets none. Villages generated before LIFE1
 *       keep whatever lived there (vanilla villagers stay vanilla; none are added).</li>
 *   <li><b>Registry:</b> each one goes into {@link ResidentRegistry} under its port and slot; world generation runs on
 *       worker threads, so the entry is handed to the server thread.</li>
 *   <li><b>Respawn:</b> a villager that died, or was hired away, leaves its slot free on that day; {@link #onServerTick}
 *       checks every {@link #CHECK_INTERVAL} ticks and puts a new one into a slot free for
 *       {@code mobs.villagers.respawn_days}, once its station's chunk is loaded and ticking.</li>
 * </ul>
 */
public final class Residents {

    public static final int CHECK_INTERVAL = 100;
    /** Blocks round a villager's home in which the lamplighter looks for lamp posts outside a registered port. */
    public static final int LAMP_RADIUS = 48;

    private Residents() {
    }

    /** Whether new villages get people now (config read when asked). */
    public static boolean placesAt(PortKind kind) {
        return kind == PortKind.SEAFARER_VILLAGE && VillagerConfig.enabled();
    }

    /** {@code plan} as it is placed now: with {@code mobs.villagers.roles} off, a plain villager without a stall. */
    public static ResidentPlan effective(ResidentPlan plan) {
        if (VillagerConfig.ROLES.get()) return plan;
        return new ResidentPlan(plan.slot(), VillageRole.VILLAGER, plan.work(), plan.bed(), plan.seats(), Optional.empty());
    }

    // --- world generation ---------------------------------------------------------------------------------------

    /** Places the village's villagers whose first station lies in {@code chunkBox}; returns how many. */
    public static int place(WorldGenLevel level, BoundingBox chunkBox, PiecesContainer pieces, Port port) {
        if (!placesAt(port.kind())) return 0;
        ServerLevel server = level.getLevel();
        MinecraftServer ms = server.getServer();
        int placed = 0;
        for (ResidentPlan plan : VillagePlan.plan(HarborMasters.placedPieces(pieces))) {
            BlockPos at = plan.home().pos();
            if (!chunkBox.isInside(at) || !free(level, at) || taken(level, at)) continue;
            if (ms.isSameThread() && living(ms, port.id(), plan.slot())) continue;
            VillageResident v = create(server, level, port.id(), effective(plan), MobSpawnType.STRUCTURE);
            if (v == null || !level.addFreshEntity(v)) continue;
            ResidentRegistry.Entry entry = new ResidentRegistry.Entry(v.getUUID(), true, 0L, server.dimension(), plan);
            if (ms.isSameThread()) {
                ResidentRegistry.get(ms).put(port.id(), entry);
            } else {
                ms.execute(() -> ResidentRegistry.get(ms).put(port.id(), entry));
            }
            placed++;
        }
        return placed;
    }

    /** The post and the block above it have no collision (terrain may fill a post: then it stays empty). */
    private static boolean free(ServerLevelAccessor level, BlockPos post) {
        for (BlockPos p : List.of(post, post.above())) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** A villager already stands at the post (always false during world generation: the region sees no entities). */
    private static boolean taken(ServerLevelAccessor level, BlockPos post) {
        return !level.getEntitiesOfClass(VillageResident.class, new AABB(post).inflate(0.5)).isEmpty();
    }

    private static boolean living(MinecraftServer server, ResourceLocation port, String slot) {
        return ResidentRegistry.get(server).get(port, slot).map(ResidentRegistry.Entry::alive).orElse(false);
    }

    // --- spawning -----------------------------------------------------------------------------------------------

    /** A new villager of {@code port}'s slot {@code plan} on its first station, named; not yet added. */
    static @Nullable VillageResident create(ServerLevel level, ServerLevelAccessor accessor, @Nullable ResourceLocation port,
                                             ResidentPlan plan, MobSpawnType reason) {
        VillageResident v = MobContent.VILLAGER.get().create(level);
        if (v == null) return null;
        Post home = plan.home();
        float yaw = home.facing().toYRot();
        v.moveTo(home.pos().getX() + 0.5, home.pos().getY(), home.pos().getZ() + 0.5, yaw, 0f);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);
        v.setPersistenceRequired();
        v.assign(port, plan);
        v.setCustomName(Component.literal(VillageNames.name(plan.role(),
                v.getUUID().getMostSignificantBits() ^ plan.slot().hashCode())));
        v.finalizeSpawn(accessor, accessor.getCurrentDifficultyAt(home.pos()), reason, null);
        return v;
    }

    /**
     * Spawns {@code port}'s villager of slot {@code plan} (respawns, GameTests), registered. Refused ({@code null})
     * while the villagers are disabled, while the slot's registered villager lives, or when the level refuses it.
     */
    public static @Nullable VillageResident spawn(ServerLevel level, @Nullable ResourceLocation port, ResidentPlan plan) {
        MinecraftServer server = level.getServer();
        if (!VillagerConfig.enabled() || port != null && living(server, port, plan.slot())) return null;
        VillageResident v = create(level, level, port, effective(plan), MobSpawnType.COMMAND);
        if (v == null || !level.addFreshEntity(v)) return null;
        if (port != null) ResidentRegistry.get(server).put(port, new ResidentRegistry.Entry(v.getUUID(), true, 0L, level.dimension(), plan));
        return v;
    }

    // --- loss and respawn ---------------------------------------------------------------------------------------

    /** From {@code VillageResident#die} and {@code #remove}: its slot is free from today. */
    static void onLost(VillageResident v) {
        MinecraftServer server = v.level().getServer();
        ResourceLocation port = v.port();
        if (server == null || port == null || !v.hasPlan()) return;
        ResidentRegistry.get(server).markGone(port, v.plan().slot(), v.getUUID(), HarborMasters.today(server));
    }

    /** Every {@link #CHECK_INTERVAL} ticks: new villagers for slots free long enough. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        check(server);
    }

    /** Fills every due slot whose station is loaded; returns how many. */
    public static int check(MinecraftServer server) {
        if (!VillagerConfig.enabled()) return 0;
        long today = HarborMasters.today(server);
        int days = VillagerConfig.RESPAWN_DAYS.get();
        int placed = 0;
        for (Map.Entry<ResourceLocation, Map<String, ResidentRegistry.Entry>> port : ResidentRegistry.get(server).all().entrySet()) {
            for (ResidentRegistry.Entry e : port.getValue().values()) {
                if (!e.respawnDue(today, days)) continue;
                ServerLevel level = server.getLevel(e.dimension());
                BlockPos at = e.plan().home().pos();
                if (level == null || !level.isPositionEntityTicking(at)) continue;
                if (spawn(level, port.getKey(), e.plan()) != null) placed++;
            }
        }
        return placed;
    }

    // --- lamps --------------------------------------------------------------------------------------------------

    /**
     * The lamp posts of {@code v}'s village in round order ({@link LampRoute}, at most {@code mobs.villagers.max_lamps}):
     * standing lanterns on a fence, in the loaded chunks of its port's box (or within {@link #LAMP_RADIUS} blocks of
     * its home without a registered port).
     */
    public static List<BlockPos> lampsNear(ServerLevel level, VillageResident v) {
        BlockPos home = v.plan().home().pos();
        BoundingBox box = Optional.ofNullable(v.port())
                .flatMap(id -> PortRegistry.get(level.getServer()).index().byId(id)).map(Port::box)
                .orElse(new BoundingBox(home).inflatedBy(LAMP_RADIUS));
        List<BlockPos> lamps = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Blocks.LANTERN))) continue;
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        int wy = baseY + y;
                        if (wy < box.minY() || wy > box.maxY()) continue;
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState s = section.getBlockState(x, y, z);
                                if (!s.is(Blocks.LANTERN) || s.getValue(LanternBlock.HANGING)) continue;
                                BlockPos p = new BlockPos(chunk.getPos().getMinBlockX() + x, wy, chunk.getPos().getMinBlockZ() + z);
                                if (box.isInside(p) && level.getBlockState(p.below()).is(BlockTags.FENCES)) lamps.add(p);
                            }
                        }
                    }
                }
            }
        }
        List<BlockPos> route = LampRoute.order(home, lamps, VillagerConfig.MAX_LAMPS.get());
        Constants.LOG.debug("Lamplighter {} found {} lamp posts, {} on the round", v.getUUID(), lamps.size(), route.size());
        return route;
    }
}

package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.ship.decor.ShipsBellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a settlement that raiders are coming (WS5, design.md §10.4 "Events are announced in advance"): a chat line to
 * the players inside the settlement's box, and every bell in that box rung every {@value #RING_INTERVAL} ticks for
 * {@code world_simulation.raids.bell_ticks}. Bells are vanilla bells (the fort gate's alarm bell, ST4c) and ship's bells
 * ({@link ShipsBellBlock}) standing in the world, found once when the raid is sighted in the loaded part of the box.
 * The seafarer village pieces have no bell, so a village hears the chat line only. The end of a raid is announced the
 * same way. The ringing is kept in memory (a restart silences it).
 */
public final class RaidAnnouncer {

    static final String KEY = "worldsim." + Constants.MOD_ID + ".raid.";
    public static final String KEY_SIGHTED = KEY + "sighted";
    public static final String KEY_REPELLED = KEY + "repelled";
    public static final String KEY_SUCCEEDED = KEY + "succeeded";
    static final int RING_INTERVAL = 100;
    /** Players this far around the box still hear the announcement. */
    static final int MARGIN = 16;

    private record Ringing(ResourceKey<Level> dimension, List<BlockPos> bells, long until) {
    }

    private static final Map<ResourceLocation, Ringing> RINGING = new ConcurrentHashMap<>();

    private RaidAnnouncer() {
    }

    /** Announces raiders sighted off {@code port}: the chat line (with {@code announce}) and the bells. Returns the bells. */
    public static List<BlockPos> sighted(ServerLevel level, ResourceLocation port, BoundingBox area) {
        if (RaidConfig.ANNOUNCE.get()) tell(level, area, Component.translatable(KEY_SIGHTED));
        List<BlockPos> bells = bells(level, area);
        int ticks = RaidConfig.BELL_TICKS.get();
        if (ticks > 0 && !bells.isEmpty()) {
            RINGING.put(port, new Ringing(level.dimension(), bells, level.getGameTime() + ticks));
            ring(level, bells);
        }
        return bells;
    }

    /** Announces the end of the raid on the settlement (with {@code announce}). */
    public static void ended(ServerLevel level, ResourceLocation port, BoundingBox area, RaidRules.Outcome outcome) {
        RINGING.remove(port);
        if (!RaidConfig.ANNOUNCE.get() || outcome == RaidRules.Outcome.NONE) return;
        tell(level, area, Component.translatable(outcome == RaidRules.Outcome.REPELLED ? KEY_REPELLED : KEY_SUCCEEDED));
    }

    /** Server tick: rings the bells of each sighted raid every {@value #RING_INTERVAL} ticks until its time is up. */
    public static void onServerTick(MinecraftServer server) {
        if (RINGING.isEmpty() || server.getTickCount() % RING_INTERVAL != 0) return;
        for (Map.Entry<ResourceLocation, Ringing> e : List.copyOf(RINGING.entrySet())) {
            Ringing r = e.getValue();
            ServerLevel level = server.getLevel(r.dimension());
            if (level == null || level.getGameTime() >= r.until()) {
                RINGING.remove(e.getKey(), r);
                continue;
            }
            ring(level, r.bells());
        }
    }

    public static void clear() {
        RINGING.clear();
    }

    /** Rings every bell of {@code bells} that is still there and loaded. */
    static int ring(ServerLevel level, List<BlockPos> bells) {
        int rung = 0;
        for (BlockPos pos : bells) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BellBlock bell) {
                if (bell.attemptToRing(null, level, pos, null)) rung++;
            } else if (state.getBlock() instanceof ShipsBellBlock bell) {
                bell.ring(level, pos, state, null);
                rung++;
            }
        }
        return rung;
    }

    private static boolean isBell(BlockState s) {
        return s.getBlock() instanceof BellBlock || s.getBlock() instanceof ShipsBellBlock;
    }

    /** The bells standing in the loaded part of {@code area}. */
    public static List<BlockPos> bells(ServerLevel level, BoundingBox area) {
        List<BlockPos> found = new ArrayList<>();
        int minY = Math.max(area.minY(), level.getMinBuildHeight());
        int maxY = Math.min(area.maxY(), level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = SectionPos.blockToSectionCoord(area.minX()); cx <= SectionPos.blockToSectionCoord(area.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(area.minZ()); cz <= SectionPos.blockToSectionCoord(area.maxZ()); cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSections().length) continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(RaidAnnouncer::isBell)) continue;
                    for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
                        for (int x = Math.max(area.minX(), cx << 4); x <= Math.min(area.maxX(), (cx << 4) + 15); x++) {
                            for (int z = Math.max(area.minZ(), cz << 4); z <= Math.min(area.maxZ(), (cz << 4) + 15); z++) {
                                if (isBell(section.getBlockState(x & 15, y & 15, z & 15))) found.add(m.set(x, y, z).immutable());
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Sends {@code line} to every player inside {@code area} (plus {@value #MARGIN} blocks). */
    static int tell(ServerLevel level, BoundingBox area, Component line) {
        BoundingBox around = area.inflatedBy(MARGIN);
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (around.isInside(p.blockPosition())) {
                p.sendSystemMessage(line);
                n++;
            }
        }
        return n;
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY_SIGHTED, "Sails on the horizon! Pirates are making for the settlement")
                .add(KEY_REPELLED, "The raiders are beaten off!")
                .add(KEY_SUCCEEDED, "The raiders held the shore and sail off");
    }
}

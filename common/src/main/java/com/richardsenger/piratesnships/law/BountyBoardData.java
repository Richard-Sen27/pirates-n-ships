package com.richardsenger.piratesnships.law;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World copy of the {@link BountyBoard}, saved with the overworld ({@code data/pirates_n_ships_bounties.dat}).
 * Also keeps score reductions from claims on targets that were offline at the time; they are applied when the target
 * logs in.
 */
public final class BountyBoardData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_bounties";

    private static final Codec<Map<UUID, Double>> PENDING_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE);

    /*
     * Vanilla's DimensionDataStorage calls dataFixType.update(...) unconditionally (only NeoForge null-checks it), so a
     * null type would crash on Fabric. Command storage is the closest vanilla "generic mod-ish data" type; for files
     * saved by the same game version the update is a no-op.
     */
    private static final SavedData.Factory<BountyBoardData> FACTORY =
            new SavedData.Factory<>(BountyBoardData::new, BountyBoardData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private BountyBoard board = BountyBoard.EMPTY;
    private final Map<UUID, Double> pendingScoreFactors = new HashMap<>();

    public static BountyBoardData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public BountyBoard board() {
        return board;
    }

    public void setBoard(BountyBoard newBoard) {
        if (!newBoard.equals(board)) {
            board = newBoard;
            setDirty();
        }
    }

    /** Remembers to multiply an offline target's score by {@code factor} on login (factors stack). */
    public void addPendingScoreFactor(UUID target, double factor) {
        pendingScoreFactors.merge(target, factor, (a, b) -> a * b);
        setDirty();
    }

    /** Removes and returns the pending factor for a target, or {@code 1.0}. */
    public double takePendingScoreFactor(UUID target) {
        Double f = pendingScoreFactors.remove(target);
        if (f == null) return 1.0;
        setDirty();
        return f;
    }

    private static BountyBoardData load(CompoundTag tag, HolderLookup.Provider registries) {
        BountyBoardData data = new BountyBoardData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("board")) {
            data.board = BountyBoard.CODEC.parse(ops, tag.get("board"))
                    .resultOrPartial(e -> Constants.LOG.error("Failed to load bounty board: {}", e))
                    .orElse(BountyBoard.EMPTY);
        }
        if (tag.contains("pending_score_factors")) {
            PENDING_CODEC.parse(ops, tag.get("pending_score_factors"))
                    .resultOrPartial(e -> Constants.LOG.error("Failed to load pending score factors: {}", e))
                    .ifPresent(data.pendingScoreFactors::putAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        BountyBoard.CODEC.encodeStart(ops, board)
                .resultOrPartial(e -> Constants.LOG.error("Failed to save bounty board: {}", e))
                .ifPresent(t -> tag.put("board", t));
        PENDING_CODEC.encodeStart(ops, Map.copyOf(pendingScoreFactors))
                .resultOrPartial(e -> Constants.LOG.error("Failed to save pending score factors: {}", e))
                .ifPresent(t -> tag.put("pending_score_factors", t));
        return tag;
    }
}

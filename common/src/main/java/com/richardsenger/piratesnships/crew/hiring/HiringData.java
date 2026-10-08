package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Today's candidates of every port (CRW1), saved with the overworld ({@code data/pirates_n_ships_hiring.dat}): per
 * port the day they were made and who is still looking. {@link Hiring#candidates} makes a new list lazily at a port's
 * first look on a new day. Server thread only.
 */
public final class HiringData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_hiring";

    /** The candidates a port made on {@code day}, hired ones removed. */
    public record Board(long day, List<Candidate> candidates) {
        public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Board::day),
                Candidate.CODEC.listOf().fieldOf("candidates").forGetter(Board::candidates)
        ).apply(i, Board::new));

        public Board {
            candidates = List.copyOf(candidates);
        }
    }

    private static final Codec<Map<ResourceLocation, Board>> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Board.CODEC);

    // Same choice as TradeData: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<HiringData> FACTORY =
            new SavedData.Factory<>(HiringData::new, HiringData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Board> boards = new HashMap<>();

    public static HiringData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Board> board(ResourceLocation port) {
        return Optional.ofNullable(boards.get(port));
    }

    public void setBoard(ResourceLocation port, Board board) {
        if (!board.equals(boards.put(port, board))) setDirty();
    }

    /** Removes the candidate {@code id} from the port's board; false if it was not there. */
    public boolean remove(ResourceLocation port, UUID id) {
        Board b = boards.get(port);
        if (b == null) return false;
        List<Candidate> left = b.candidates().stream().filter(c -> !c.id().equals(id)).toList();
        if (left.size() == b.candidates().size()) return false;
        setBoard(port, new Board(b.day(), left));
        return true;
    }

    /** Forgets the port (GameTest cleanup, a port that is gone). */
    public void forget(ResourceLocation port) {
        if (boards.remove(port) != null) setDirty();
    }

    public static HiringData load(CompoundTag tag, HolderLookup.Provider registries) {
        HiringData data = new HiringData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("boards")) {
            CODEC.parse(ops, tag.get("boards"))
                    .resultOrPartial(e -> Constants.LOG.error("Failed to load hiring data: {}", e))
                    .ifPresent(data.boards::putAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CODEC.encodeStart(ops, Map.copyOf(boards))
                .resultOrPartial(e -> Constants.LOG.error("Failed to save hiring data: {}", e))
                .ifPresent(t -> tag.put("boards", t));
        return tag;
    }
}

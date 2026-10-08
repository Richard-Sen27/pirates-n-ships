package com.richardsenger.piratesnships.worldsim.voyage;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every voyage that has not ended (WS2), saved with the overworld ({@code data/pirates_n_ships_voyages.dat}), in
 * departure order. Ended voyages are removed. Server thread only; use {@link Voyages} rather than this class.
 */
public final class VoyageData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_voyages";
    public static final Codec<List<Voyage>> LIST_CODEC = Voyage.CODEC.listOf();

    private static final SavedData.Factory<VoyageData> FACTORY =
            new SavedData.Factory<>(VoyageData::new, VoyageData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Voyage> voyages = new LinkedHashMap<>();

    public static VoyageData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Voyage> get(UUID id) {
        return Optional.ofNullable(voyages.get(id));
    }

    public List<Voyage> all() {
        return List.copyOf(voyages.values());
    }

    public int size() {
        return voyages.size();
    }

    public void put(Voyage voyage) {
        if (!voyage.equals(voyages.put(voyage.id(), voyage))) setDirty();
    }

    public Optional<Voyage> remove(UUID id) {
        Voyage v = voyages.remove(id);
        if (v != null) setDirty();
        return Optional.ofNullable(v);
    }

    public static VoyageData load(CompoundTag tag, HolderLookup.Provider registries) {
        VoyageData data = new VoyageData();
        if (tag.contains("voyages")) {
            LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("voyages"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the voyages: {}", e))
                    .ifPresent(list -> list.forEach(v -> data.voyages.put(v.id(), v)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(voyages.values()))
                .resultOrPartial(e -> Constants.LOG.error("Could not save the voyages: {}", e))
                .ifPresent(t -> tag.put("voyages", t));
        return tag;
    }
}

package com.richardsenger.piratesnships.mob.captain;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The pirate captains of the world (BOS1): one {@link CaptainEntry} per pirate island, keyed by its port id. Saved
 * with the overworld's data ({@code data/pirates_n_ships_captains.dat}). Server thread only; world generation hands
 * new captains over through {@code IslandCaptains}.
 */
public final class CaptainRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_captains";

    private static final Codec<Map<ResourceLocation, CaptainEntry>> MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, CaptainEntry.CODEC);

    // Same choice as PortRegistry: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<CaptainRegistry> FACTORY =
            new SavedData.Factory<>(CaptainRegistry::new, CaptainRegistry::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, CaptainEntry> captains = new TreeMap<>();

    public static CaptainRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<CaptainEntry> get(ResourceLocation port) {
        return Optional.ofNullable(captains.get(port));
    }

    /** The island whose captain (living or lost) has {@code id}. */
    public Optional<ResourceLocation> portOf(UUID id) {
        return captains.entrySet().stream().filter(e -> e.getValue().id().equals(id)).map(Map.Entry::getKey).findFirst();
    }

    /** Every island's captain, by port id. */
    public Map<ResourceLocation, CaptainEntry> all() {
        return Map.copyOf(captains);
    }

    public void put(ResourceLocation port, CaptainEntry entry) {
        captains.put(port, entry);
        setDirty();
    }

    public boolean remove(ResourceLocation port) {
        boolean removed = captains.remove(port) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Marks the living captain {@code id} of {@code port} lost on {@code day}; false if he is not that island's living captain. */
    public boolean markDead(ResourceLocation port, UUID id, long day) {
        CaptainEntry e = captains.get(port);
        if (e == null || !e.alive() || !e.id().equals(id)) return false;
        put(port, e.dead(day));
        return true;
    }

    /**
     * Marks the living captain {@code id} of {@code port} at sea on {@code voyage}, or back at his post ({@code empty});
     * false if he is not that island's living captain (BOS2).
     */
    public boolean setVoyage(ResourceLocation port, UUID id, Optional<UUID> voyage) {
        CaptainEntry e = captains.get(port);
        if (e == null || !e.alive() || !e.id().equals(id)) return false;
        if (!e.voyage().equals(voyage)) put(port, e.withVoyage(voyage));
        return true;
    }

    public static CaptainRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        CaptainRegistry r = new CaptainRegistry();
        if (tag.contains("captains", Tag.TAG_COMPOUND)) {
            MAP_CODEC.parse(NbtOps.INSTANCE, tag.get("captains"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the pirate captains: {}", e))
                    .ifPresent(r.captains::putAll);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        MAP_CODEC.encodeStart(NbtOps.INSTANCE, captains)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the pirate captains: {}", e))
                .ifPresent(t -> tag.put("captains", t));
        return tag;
    }
}

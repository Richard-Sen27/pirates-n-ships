package com.richardsenger.piratesnships.mob.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The villagers of the world (LIFE1): per port, one {@link Entry} per slot of its plan ({@link ResidentPlan#slot}).
 * Saved with the overworld's data ({@code data/pirates_n_ships_villagers.dat}). Server thread only; world generation
 * hands new villagers over through {@link Residents}. The {@code HarborMasterRegistry} pattern.
 */
public final class ResidentRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_villagers";

    /**
     * The villager of one slot.
     *
     * @param id        the entity's UUID
     * @param alive     false once it died, was hired away or was removed
     * @param goneDay   the world day it was lost on; meaningless while alive
     * @param dimension the level of the village
     * @param plan      the slot's plan, for the next one to move in
     */
    public record Entry(UUID id, boolean alive, long goneDay, ResourceKey<Level> dimension, ResidentPlan plan) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.BOOL.fieldOf("alive").forGetter(Entry::alive),
                Codec.LONG.optionalFieldOf("gone_day", 0L).forGetter(Entry::goneDay),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                ResidentPlan.CODEC.fieldOf("plan").forGetter(Entry::plan)
        ).apply(i, Entry::new));

        public Entry gone(long day) {
            return new Entry(id, false, day, dimension, plan);
        }

        /** Whether a new villager is due on world day {@code today} ({@code respawnDays} 0: on the next check). */
        public boolean respawnDue(long today, int respawnDays) {
            return !alive && today - goneDay >= Math.max(0, respawnDays);
        }
    }

    private static final Codec<Map<ResourceLocation, Map<String, Entry>>> CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.unboundedMap(Codec.STRING, Entry.CODEC));

    private static final SavedData.Factory<ResidentRegistry> FACTORY =
            new SavedData.Factory<>(ResidentRegistry::new, ResidentRegistry::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Map<String, Entry>> ports = new TreeMap<>();

    public static ResidentRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The slots of {@code port}, by slot id (a copy). */
    public Map<String, Entry> of(ResourceLocation port) {
        Map<String, Entry> m = ports.get(port);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    public Optional<Entry> get(ResourceLocation port, String slot) {
        return Optional.ofNullable(ports.getOrDefault(port, Map.of()).get(slot));
    }

    /** Every port's slots (a copy). */
    public Map<ResourceLocation, Map<String, Entry>> all() {
        Map<ResourceLocation, Map<String, Entry>> copy = new TreeMap<>();
        ports.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
        return copy;
    }

    public void put(ResourceLocation port, Entry entry) {
        ports.computeIfAbsent(port, k -> new TreeMap<>()).put(entry.plan().slot(), entry);
        setDirty();
    }

    /** Marks the living villager {@code id} of {@code port}'s {@code slot} gone on {@code day}; false if it is not that slot's. */
    public boolean markGone(ResourceLocation port, String slot, UUID id, long day) {
        Entry e = ports.getOrDefault(port, Map.of()).get(slot);
        if (e == null || !e.alive() || !e.id().equals(id)) return false;
        put(port, e.gone(day));
        return true;
    }

    /** Forgets {@code port} (GameTest cleanup). */
    public boolean remove(ResourceLocation port) {
        boolean removed = ports.remove(port) != null;
        if (removed) setDirty();
        return removed;
    }

    public static ResidentRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ResidentRegistry r = new ResidentRegistry();
        if (tag.contains("ports", Tag.TAG_COMPOUND)) {
            CODEC.parse(NbtOps.INSTANCE, tag.get("ports"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the villagers: {}", e))
                    .ifPresent(m -> m.forEach((k, v) -> r.ports.put(k, new TreeMap<>(v))));
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, ports)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the villagers: {}", e))
                .ifPresent(t -> tag.put("ports", t));
        return tag;
    }
}

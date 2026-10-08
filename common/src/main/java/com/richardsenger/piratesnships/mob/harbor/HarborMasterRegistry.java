package com.richardsenger.piratesnships.mob.harbor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The harbor masters of the world (PRT1a): one {@link Entry} per port, keyed by its port id. Saved with the
 * overworld's data ({@code data/pirates_n_ships_harbor_masters.dat}). Server thread only; world generation hands new
 * harbor masters over through {@link HarborMasters}. The {@code CaptainRegistry} pattern.
 */
public final class HarborMasterRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_harbor_masters";

    /**
     * The harbor master of one port.
     *
     * @param id        the entity's UUID
     * @param alive     false once he died or was removed
     * @param diedDay   the world day ({@code overworld day time / 24000}) he was lost on; meaningless while alive
     * @param dimension the level of his post
     * @param post      the block he stands in
     * @param facing    the direction he faces at his post
     */
    public record Entry(UUID id, boolean alive, long diedDay, ResourceKey<Level> dimension, BlockPos post, Direction facing) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.BOOL.fieldOf("alive").forGetter(Entry::alive),
                Codec.LONG.optionalFieldOf("died_day", 0L).forGetter(Entry::diedDay),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("post").forGetter(Entry::post),
                Direction.CODEC.fieldOf("facing").forGetter(Entry::facing)
        ).apply(i, Entry::new));

        /** The same harbor master, lost on {@code day}. */
        public Entry dead(long day) {
            return new Entry(id, false, day, dimension, post, facing);
        }

        /**
         * Whether a new harbor master is due on world day {@code today}: this one is lost and {@code respawnDays} days
         * have passed since ({@code respawnDays} 0: on the next check).
         */
        public boolean respawnDue(long today, int respawnDays) {
            return !alive && today - diedDay >= Math.max(0, respawnDays);
        }
    }

    private static final Codec<Map<ResourceLocation, Entry>> MAP_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Entry.CODEC);

    // Same choice as PortRegistry: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<HarborMasterRegistry> FACTORY =
            new SavedData.Factory<>(HarborMasterRegistry::new, HarborMasterRegistry::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Entry> masters = new TreeMap<>();

    public static HarborMasterRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Entry> get(ResourceLocation port) {
        return Optional.ofNullable(masters.get(port));
    }

    /** Every port's harbor master, by port id. */
    public Map<ResourceLocation, Entry> all() {
        return Map.copyOf(masters);
    }

    public void put(ResourceLocation port, Entry entry) {
        masters.put(port, entry);
        setDirty();
    }

    public boolean remove(ResourceLocation port) {
        boolean removed = masters.remove(port) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Marks the living harbor master {@code id} of {@code port} lost on {@code day}; false if he is not that port's. */
    public boolean markDead(ResourceLocation port, UUID id, long day) {
        Entry e = masters.get(port);
        if (e == null || !e.alive() || !e.id().equals(id)) return false;
        put(port, e.dead(day));
        return true;
    }

    public static HarborMasterRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        HarborMasterRegistry r = new HarborMasterRegistry();
        if (tag.contains("masters", Tag.TAG_COMPOUND)) {
            MAP_CODEC.parse(NbtOps.INSTANCE, tag.get("masters"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the harbor masters: {}", e))
                    .ifPresent(r.masters::putAll);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        MAP_CODEC.encodeStart(NbtOps.INSTANCE, masters)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the harbor masters: {}", e))
                .ifPresent(t -> tag.put("masters", t));
        return tag;
    }
}

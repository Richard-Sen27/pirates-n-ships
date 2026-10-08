package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's faction state (design.md §10.4, WS1), saved with the overworld's data
 * ({@code data/pirates_n_ships_factions.dat}), and the last overworld day the daily decay saw. Server thread only;
 * change it through {@link Factions}.
 */
public final class FactionData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_factions";

    /** No day seen yet: the first look only remembers the day. */
    public static final long NO_DAY = -1L;

    // Same choice as PortRegistry: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<FactionData> FACTORY =
            new SavedData.Factory<>(FactionData::new, FactionData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private FactionState state = FactionState.INITIAL;
    private long lastDay = NO_DAY;

    public static FactionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public FactionState state() {
        return state;
    }

    public void setState(FactionState next) {
        if (next.equals(state)) return;
        state = next;
        setDirty();
    }

    public long lastDay() {
        return lastDay;
    }

    public void setLastDay(long day) {
        if (day == lastDay) return;
        lastDay = day;
        setDirty();
    }

    public static FactionData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionData d = new FactionData();
        if (tag.contains("state", Tag.TAG_COMPOUND)) {
            d.state = FactionState.CODEC.parse(NbtOps.INSTANCE, tag.get("state"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the faction state: {}", e))
                    .orElse(FactionState.INITIAL);
        }
        if (tag.contains("last_day", Tag.TAG_LONG)) d.lastDay = tag.getLong("last_day");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FactionState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the faction state: {}", e))
                .ifPresent(t -> tag.put("state", t));
        tag.putLong("last_day", lastDay);
        return tag;
    }
}

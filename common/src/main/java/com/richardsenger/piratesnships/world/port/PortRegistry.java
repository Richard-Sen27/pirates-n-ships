package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The server's port registry (design.md §10.4): every generated port with its position, box, climate and berths.
 * Saved with the overworld's data ({@code data/pirates_n_ships_ports.dat}) for all dimensions. Server thread only;
 * world generation hands new ports over through {@link PortService#report}.
 */
public final class PortRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_ports";

    // Same choice as TradeData: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<PortRegistry> FACTORY =
            new SavedData.Factory<>(PortRegistry::new, PortRegistry::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private PortIndex index = PortIndex.EMPTY;

    public static PortRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public PortIndex index() {
        return index;
    }

    /** Adds {@code port}; false if a port with that id is already known. */
    public boolean add(Port port) {
        PortIndex next = index.with(port);
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    /** Replaces the known port with {@code port}'s id (e.g. its shipwright orders changed); false if unknown. */
    public boolean update(Port port) {
        PortIndex next = index.replace(port);
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    public boolean remove(net.minecraft.resources.ResourceLocation id) {
        PortIndex next = index.without(id);
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    public static PortRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PortRegistry r = new PortRegistry();
        if (tag.contains("ports", Tag.TAG_LIST)) {
            r.index = PortIndex.CODEC.parse(NbtOps.INSTANCE, tag.get("ports"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the port registry: {}", e))
                    .orElse(PortIndex.EMPTY);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PortIndex.CODEC.encodeStart(NbtOps.INSTANCE, index)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the port registry: {}", e))
                .ifPresent(t -> tag.put("ports", t));
        return tag;
    }
}

package com.richardsenger.piratesnships.ship;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * All {@link ShipData} records of a server, stored in the overworld's saved data (one file for every dimension, so a
 * ship id resolves from anywhere). Records stay while a ship is merely unloaded and are removed when it is
 * disassembled or destroyed.
 */
public final class ShipRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_ships";
    private static final Codec<List<ShipData>> LIST_CODEC = ShipData.CODEC.listOf();

    private final Map<UUID, ShipData> ships = new LinkedHashMap<>();

    public static ShipRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShipRegistry::new, ShipRegistry::load, null), FILE_ID);
    }

    public Optional<ShipData> find(UUID id) {
        return Optional.ofNullable(ships.get(id));
    }

    public Collection<ShipData> all() {
        return Collections.unmodifiableCollection(ships.values());
    }

    public void put(ShipData data) {
        ships.put(data.id(), data);
        setDirty();
    }

    public boolean remove(UUID id) {
        boolean removed = ships.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    private static ShipRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipRegistry registry = new ShipRegistry();
        if (!tag.contains("ships")) {
            return registry;
        }
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("ships"))
                .resultOrPartial(err -> Constants.LOG.error("Bad ship registry data: {}", err))
                .ifPresent(list -> list.forEach(s -> registry.ships.put(s.id(), s)));
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(ships.values()))
                .resultOrPartial(err -> Constants.LOG.error("Could not save ship registry: {}", err))
                .ifPresent(t -> tag.put("ships", t));
        return tag;
    }
}

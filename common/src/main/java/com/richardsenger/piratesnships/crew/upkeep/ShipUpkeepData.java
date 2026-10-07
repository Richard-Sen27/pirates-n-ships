package com.richardsenger.piratesnships.crew.upkeep;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The {@link ShipUpkeep} of every ship, keyed by ship id, in the overworld's saved data (one file for all dimensions,
 * like {@code ship.ShipRegistry}). Kept apart from {@code ShipData} so that the crew's bookkeeping does not change the
 * ship record that assembly and splitting build. Records of ships that no longer exist are dropped at the next dawn.
 */
public final class ShipUpkeepData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_ship_upkeep";
    private static final Codec<Map<UUID, ShipUpkeep>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ShipUpkeep.CODEC);

    private final Map<UUID, ShipUpkeep> ships = new LinkedHashMap<>();

    public static ShipUpkeepData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShipUpkeepData::new, ShipUpkeepData::load, null), FILE_ID);
    }

    /** The ship's upkeep, {@link ShipUpkeep#INITIAL} when it never had a day tick. */
    public ShipUpkeep get(UUID ship) {
        return ships.getOrDefault(ship, ShipUpkeep.INITIAL);
    }

    public void put(UUID ship, ShipUpkeep upkeep) {
        ships.put(ship, upkeep);
        setDirty();
    }

    /** Drops the records of ships for which {@code gone} holds. */
    public void removeIf(Predicate<UUID> gone) {
        if (ships.keySet().removeIf(gone)) {
            setDirty();
        }
    }

    private static ShipUpkeepData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipUpkeepData data = new ShipUpkeepData();
        if (tag.contains("ships")) {
            MAP_CODEC.parse(NbtOps.INSTANCE, tag.get("ships"))
                    .resultOrPartial(err -> Constants.LOG.error("Bad ship upkeep data: {}", err))
                    .ifPresent(data.ships::putAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        MAP_CODEC.encodeStart(NbtOps.INSTANCE, Map.copyOf(ships))
                .resultOrPartial(err -> Constants.LOG.error("Could not save ship upkeep: {}", err))
                .ifPresent(t -> tag.put("ships", t));
        return tag;
    }
}

package com.richardsenger.piratesnships.trade.fees;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
 * Harbor dues per ship and port (PRT1b): the day of the last charge, what was paid then and what is still owed. Saved
 * with the overworld's data ({@code data/pirates_n_ships_port_visits.dat}). Server thread only.
 */
public final class PortVisitData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_port_visits";

    /**
     * One ship's dues at one port.
     *
     * @param ship  the ship (Sable sub-level id)
     * @param port  the port id
     * @param owner the ship's owner when last charged: the captain who owes
     * @param day   the day ({@code TradeService.day}) of the last charge
     * @param paid  doubloons paid at the last charge (0 when waived or owed)
     * @param owed  doubloons owed at this port, summed over unpaid charges
     */
    public record Visit(UUID ship, ResourceLocation port, UUID owner, long day, int paid, int owed) {
        public static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("ship").forGetter(Visit::ship),
                ResourceLocation.CODEC.fieldOf("port").forGetter(Visit::port),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Visit::owner),
                Codec.LONG.fieldOf("day").forGetter(Visit::day),
                Codec.INT.optionalFieldOf("paid", 0).forGetter(Visit::paid),
                Codec.INT.optionalFieldOf("owed", 0).forGetter(Visit::owed)
        ).apply(i, Visit::new));

        public Visit withOwed(int newOwed) {
            return new Visit(ship, port, owner, day, paid, newOwed);
        }
    }

    private static final Codec<List<Visit>> LIST_CODEC = Visit.CODEC.listOf();

    // Same choice as PortRegistry: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<PortVisitData> FACTORY =
            new SavedData.Factory<>(PortVisitData::new, PortVisitData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, Visit> visits = new LinkedHashMap<>();

    public static PortVisitData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    private static String key(UUID ship, ResourceLocation port) {
        return ship + "|" + port;
    }

    public Optional<Visit> get(UUID ship, ResourceLocation port) {
        return Optional.ofNullable(visits.get(key(ship, port)));
    }

    public void put(Visit visit) {
        visits.put(key(visit.ship(), visit.port()), visit);
        setDirty();
    }

    public List<Visit> all() {
        return List.copyOf(visits.values());
    }

    /** Doubloons {@code owner} owes at {@code port}, over all their ships. */
    public int owed(UUID owner, ResourceLocation port) {
        long n = 0;
        for (Visit v : visits.values()) {
            if (v.owner().equals(owner) && v.port().equals(port)) n += v.owed();
        }
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /** Clears what {@code owner} owes at {@code port}; returns the amount cleared. */
    public int clearOwed(UUID owner, ResourceLocation port) {
        int cleared = 0;
        for (Map.Entry<String, Visit> e : visits.entrySet()) {
            Visit v = e.getValue();
            if (v.owner().equals(owner) && v.port().equals(port) && v.owed() > 0) {
                cleared += v.owed();
                e.setValue(v.withOwed(0));
            }
        }
        if (cleared > 0) setDirty();
        return cleared;
    }

    /** Forgets every visit of {@code ship} (tests, a scrapped ship). */
    public void forget(UUID ship) {
        if (visits.values().removeIf(v -> v.ship().equals(ship))) setDirty();
    }

    public static PortVisitData load(CompoundTag tag, HolderLookup.Provider registries) {
        PortVisitData d = new PortVisitData();
        if (tag.contains("visits", Tag.TAG_LIST)) {
            LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("visits"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the port visits: {}", e))
                    .ifPresent(list -> list.forEach(v -> d.visits.put(key(v.ship(), v.port()), v)));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(visits.values()))
                .resultOrPartial(e -> Constants.LOG.error("Could not save the port visits: {}", e))
                .ifPresent(t -> tag.put("visits", t));
        return tag;
    }
}

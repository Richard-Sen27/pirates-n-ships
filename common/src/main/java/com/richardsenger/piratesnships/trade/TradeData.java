package com.richardsenger.piratesnships.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World copy of all port markets and contracts, saved with the overworld ({@code data/pirates_n_ships_trade.dat}).
 * Markets are keyed by a port id that the later port registry supplies; contracts by their own id (each knows its
 * origin port). {@link #lastOfferDay} remembers when a port last generated offers.
 */
public final class TradeData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_trade";

    /** Everything this saved data stores, as one codec value. */
    public record Snapshot(Map<ResourceLocation, Market> markets, List<DeliveryContract> contracts, Map<ResourceLocation, Long> lastOfferDay) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Market.CODEC).optionalFieldOf("markets", Map.of()).forGetter(Snapshot::markets),
                DeliveryContract.CODEC.listOf().optionalFieldOf("contracts", List.of()).forGetter(Snapshot::contracts),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("last_offer_day", Map.of()).forGetter(Snapshot::lastOfferDay)
        ).apply(i, Snapshot::new));
    }

    // Same choice as BountyBoardData: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<TradeData> FACTORY =
            new SavedData.Factory<>(TradeData::new, TradeData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Market> markets = new HashMap<>();
    private final Map<UUID, DeliveryContract> contracts = new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> lastOfferDay = new HashMap<>();

    public static TradeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Market> market(ResourceLocation port) {
        return Optional.ofNullable(markets.get(port));
    }

    public void setMarket(ResourceLocation port, Market market) {
        if (!market.equals(markets.put(port, market))) setDirty();
    }

    public void removeMarket(ResourceLocation port) {
        if (markets.remove(port) != null) setDirty();
    }

    public Optional<DeliveryContract> contract(UUID id) {
        return Optional.ofNullable(contracts.get(id));
    }

    public Collection<DeliveryContract> contracts() {
        return List.copyOf(contracts.values());
    }

    public void putContract(DeliveryContract contract) {
        if (!contract.equals(contracts.put(contract.id(), contract))) setDirty();
    }

    public void removeContract(UUID id) {
        if (contracts.remove(id) != null) setDirty();
    }

    public long lastOfferDay(ResourceLocation port) {
        return lastOfferDay.getOrDefault(port, Long.MIN_VALUE);
    }

    public void setLastOfferDay(ResourceLocation port, long day) {
        Long old = lastOfferDay.put(port, day);
        if (old == null || old != day) setDirty();
    }

    public Snapshot snapshot() {
        return new Snapshot(Map.copyOf(markets), new ArrayList<>(contracts.values()), Map.copyOf(lastOfferDay));
    }

    void restore(Snapshot s) {
        markets.clear();
        markets.putAll(s.markets());
        contracts.clear();
        for (DeliveryContract c : s.contracts()) contracts.put(c.id(), c);
        lastOfferDay.clear();
        lastOfferDay.putAll(s.lastOfferDay());
    }

    /** Reads data written by {@link #save} (also used by GameTests for a save/load round trip). */
    public static TradeData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradeData data = new TradeData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("trade")) {
            Snapshot.CODEC.parse(ops, tag.get("trade"))
                    .resultOrPartial(e -> Constants.LOG.error("Failed to load trade data: {}", e))
                    .ifPresent(data::restore);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Snapshot.CODEC.encodeStart(ops, snapshot())
                .resultOrPartial(e -> Constants.LOG.error("Failed to save trade data: {}", e))
                .ifPresent(t -> tag.put("trade", t));
        return tag;
    }
}

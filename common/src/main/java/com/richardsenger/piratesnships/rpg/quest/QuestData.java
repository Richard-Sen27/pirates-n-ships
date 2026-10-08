package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The open quest offers of every port, saved with the overworld ({@code data/pirates_n_ships_quests.dat}), and the
 * day each port last made offers. Accepted quests live in the players' {@link QuestLog}s. Server thread only.
 */
public final class QuestData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_quests";

    public record Snapshot(Map<ResourceLocation, List<Quest>> offers, Map<ResourceLocation, Long> lastOfferDay) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Quest.CODEC.listOf()).optionalFieldOf("offers", Map.of()).forGetter(Snapshot::offers),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("last_offer_day", Map.of()).forGetter(Snapshot::lastOfferDay)
        ).apply(i, Snapshot::new));
    }

    // Same choice as TradeData: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<QuestData> FACTORY =
            new SavedData.Factory<>(QuestData::new, QuestData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, List<Quest>> offers = new HashMap<>();
    private final Map<ResourceLocation, Long> lastOfferDay = new HashMap<>();

    public static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public List<Quest> offers(ResourceLocation port) {
        return List.copyOf(offers.getOrDefault(port, List.of()));
    }

    public void setOffers(ResourceLocation port, List<Quest> list) {
        List<Quest> old = offers.get(port);
        if (list.isEmpty()) {
            if (offers.remove(port) != null) setDirty();
        } else if (!list.equals(old)) {
            offers.put(port, new ArrayList<>(list));
            setDirty();
        }
    }

    public void addOffer(Quest quest) {
        List<Quest> list = new ArrayList<>(offers(quest.port()));
        list.add(quest);
        setOffers(quest.port(), list);
    }

    public Optional<Quest> offer(ResourceLocation port, UUID id) {
        return offers(port).stream().filter(q -> q.id().equals(id)).findFirst();
    }

    public boolean removeOffer(ResourceLocation port, UUID id) {
        List<Quest> list = new ArrayList<>(offers(port));
        boolean removed = list.removeIf(q -> q.id().equals(id));
        if (removed) setOffers(port, list);
        return removed;
    }

    public long lastOfferDay(ResourceLocation port) {
        return lastOfferDay.getOrDefault(port, Long.MIN_VALUE);
    }

    public void setLastOfferDay(ResourceLocation port, long day) {
        Long old = lastOfferDay.put(port, day);
        if (old == null || old != day) setDirty();
    }

    /** Forgets everything about {@code port} (GameTest cleanup, a port that is gone). */
    public void forget(ResourceLocation port) {
        boolean changed = offers.remove(port) != null;
        changed |= lastOfferDay.remove(port) != null;
        if (changed) setDirty();
    }

    public Snapshot snapshot() {
        Map<ResourceLocation, List<Quest>> copy = new HashMap<>();
        offers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return new Snapshot(copy, Map.copyOf(lastOfferDay));
    }

    void restore(Snapshot s) {
        offers.clear();
        s.offers().forEach((k, v) -> offers.put(k, new ArrayList<>(v)));
        lastOfferDay.clear();
        lastOfferDay.putAll(s.lastOfferDay());
    }

    public static QuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestData data = new QuestData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("quests")) {
            Snapshot.CODEC.parse(ops, tag.get("quests"))
                    .resultOrPartial(e -> Constants.LOG.error("Failed to load quest data: {}", e))
                    .ifPresent(data::restore);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Snapshot.CODEC.encodeStart(ops, snapshot())
                .resultOrPartial(e -> Constants.LOG.error("Failed to save quest data: {}", e))
                .ifPresent(t -> tag.put("quests", t));
        return tag;
    }
}

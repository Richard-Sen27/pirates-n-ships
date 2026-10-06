package com.richardsenger.piratesnships.trade.good;

import com.richardsenger.piratesnships.core.data.Definitions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Item → trade good lookup built from a set of definitions. A definition whose item doesn't exist is skipped (and
 * reported through {@code log}); when two definitions name the same item, the one with the smaller id wins and the
 * other is reported. Immutable.
 */
public final class TradeGoodIndex {

    /** A good with its definition id. */
    public record Entry(ResourceLocation id, TradeGood good) {
    }

    private final Definitions<TradeGood> source;
    private final Map<ResourceLocation, Entry> byItem;
    private final List<ResourceLocation> skipped;

    private TradeGoodIndex(Definitions<TradeGood> source, Map<ResourceLocation, Entry> byItem, List<ResourceLocation> skipped) {
        this.source = source;
        this.byItem = byItem;
        this.skipped = skipped;
    }

    /** Builds the index. {@code itemExists} decides which items are present; {@code log} receives one line per problem. */
    public static TradeGoodIndex build(Definitions<TradeGood> goods, Predicate<ResourceLocation> itemExists, Consumer<String> log) {
        Map<ResourceLocation, Entry> byItem = new HashMap<>();
        List<ResourceLocation> skipped = new ArrayList<>();
        List<ResourceLocation> ids = new ArrayList<>(goods.ids());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ids) {
            TradeGood good = goods.require(id);
            if (!itemExists.test(good.item())) {
                skipped.add(id);
                log.accept("Trade good " + id + " skipped: item " + good.item() + " does not exist");
                continue;
            }
            Entry previous = byItem.putIfAbsent(good.item(), new Entry(id, good));
            if (previous != null) {
                skipped.add(id);
                log.accept("Trade good " + id + " skipped: item " + good.item() + " already belongs to " + previous.id());
            }
        }
        return new TradeGoodIndex(goods, Map.copyOf(byItem), List.copyOf(skipped));
    }

    /** Builds the index against the vanilla item registry (works for mod items once registries are frozen). */
    public static TradeGoodIndex build(Definitions<TradeGood> goods, Consumer<String> log) {
        return build(goods, BuiltInRegistries.ITEM::containsKey, log);
    }

    public Definitions<TradeGood> source() {
        return source;
    }

    public Optional<Entry> byItem(ResourceLocation itemId) {
        return Optional.ofNullable(byItem.get(itemId));
    }

    public Optional<Entry> of(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return byItem(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Ids of the goods that can actually be traded (item present), sorted. */
    public List<ResourceLocation> tradeableIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Entry e : byItem.values()) ids.add(e.id());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return Collections.unmodifiableList(ids);
    }

    /** The definitions restricted to tradeable goods. */
    public Definitions<TradeGood> tradeable() {
        Map<ResourceLocation, TradeGood> m = new HashMap<>();
        for (Entry e : byItem.values()) m.put(e.id(), e.good());
        return Definitions.of(source.typeName(), m);
    }

    public List<ResourceLocation> skipped() {
        return skipped;
    }
}

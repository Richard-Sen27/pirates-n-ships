package com.richardsenger.piratesnships.trade.good;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.trade.market.Climate;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One trade good (design.md §10.3), a datapack definition: {@code data/<ns>/pirates_n_ships/trade_good/<path>.json}.
 *
 * @param item        the item it stands for, by id (may name an item that doesn't exist; such goods are skipped by
 *                    {@link TradeGoodIndex})
 * @param basePrice   baseline price of one unit in doubloons at a neutral port (before spread)
 * @param weight      cargo weight of one unit, in the same unit as {@code crew.provisions} (one food item = 0.25)
 * @param category    coarse kind, used to derive port profiles
 * @param sensitivity how strongly its price reacts to buying and selling (1 = normal, luxuries higher, bulk lower)
 * @param stockFactor multiplier on the stock a port keeps and the surplus it absorbs (bulk goods higher)
 * @param producedIn  climates where it is produced (empty = no climate produces it, only port kinds do)
 */
public record TradeGood(ResourceLocation item, double basePrice, double weight, GoodCategory category,
                        double sensitivity, double stockFactor, List<Climate> producedIn) {

    public static final Codec<TradeGood> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(TradeGood::item),
            Codec.doubleRange(0.01, 1_000_000.0).fieldOf("base_price").forGetter(TradeGood::basePrice),
            Codec.doubleRange(0.0, 10_000.0).fieldOf("weight").forGetter(TradeGood::weight),
            GoodCategory.CODEC.fieldOf("category").forGetter(TradeGood::category),
            Codec.doubleRange(0.0, 100.0).optionalFieldOf("sensitivity", 1.0).forGetter(TradeGood::sensitivity),
            Codec.doubleRange(0.01, 100.0).optionalFieldOf("stock_factor", 1.0).forGetter(TradeGood::stockFactor),
            Climate.CODEC.listOf().optionalFieldOf("produced_in", List.of()).forGetter(TradeGood::producedIn)
    ).apply(i, TradeGood::new));

    public TradeGood {
        producedIn = List.copyOf(producedIn);
    }

    public boolean producedIn(Climate climate) {
        return producedIn.contains(climate);
    }
}

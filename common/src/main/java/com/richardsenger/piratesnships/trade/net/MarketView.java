package com.richardsenger.piratesnships.trade.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What a market screen shows: the port's goods with buy and sell quotes for {@code quantity} units, the player's
 * doubloons, the port's open offers and the player's accepted contracts. Built by the server ({@link MarketBackend}).
 */
public record MarketView(ResourceLocation port, PortKind kind, int quantity, long coins, List<GoodLine> goods,
                         List<DeliveryContract> offers, List<DeliveryContract> contracts) {

    /** One quote side: total doubloons for the quantity, how many units the port would trade, and the outcome. */
    public record Price(long total, int available, Market.Outcome outcome) {
        public static final Codec<Price> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("total").forGetter(Price::total),
                Codec.INT.fieldOf("available").forGetter(Price::available),
                Codec.STRING.xmap(Market.Outcome::valueOf, Enum::name).fieldOf("outcome").forGetter(Price::outcome)
        ).apply(i, Price::new));

        public static Price of(Market.Quote q) {
            return new Price(q.total(), q.available(), q.outcome());
        }
    }

    /** A good of the port: its item, its role there, and what buying and selling the quantity costs and pays. */
    public record GoodLine(ResourceLocation good, ResourceLocation item, GoodRole role, Price buy, Price sell) {
        public static final Codec<GoodLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("good").forGetter(GoodLine::good),
                ResourceLocation.CODEC.fieldOf("item").forGetter(GoodLine::item),
                GoodRole.CODEC.fieldOf("role").forGetter(GoodLine::role),
                Price.CODEC.fieldOf("buy").forGetter(GoodLine::buy),
                Price.CODEC.fieldOf("sell").forGetter(GoodLine::sell)
        ).apply(i, GoodLine::new));
    }

    public static final Codec<MarketView> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("port").forGetter(MarketView::port),
            PortKind.CODEC.fieldOf("kind").forGetter(MarketView::kind),
            Codec.INT.fieldOf("quantity").forGetter(MarketView::quantity),
            Codec.LONG.fieldOf("coins").forGetter(MarketView::coins),
            GoodLine.CODEC.listOf().fieldOf("goods").forGetter(MarketView::goods),
            DeliveryContract.CODEC.listOf().fieldOf("offers").forGetter(MarketView::offers),
            DeliveryContract.CODEC.listOf().fieldOf("contracts").forGetter(MarketView::contracts)
    ).apply(i, MarketView::new));
}

package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One trade good as the "Harbor Market" page of a recipe viewer shows it (JEI1, docs/compat.md): the prices of one unit
 * at a port with a settled market (no recent trades), from the market rules of design.md §10.3 ({@code Market}):
 * mid price = base price × role factor, buy = mid × (1 + spread/2), sell = mid × (1 − spread/2). Pure.
 *
 * @param good          the trade good's id
 * @param item          the item it stands for
 * @param buy           buy price of one unit at a port that neither makes nor needs it
 * @param sell          sell price of one unit there
 * @param buyProduced   buy price where the port makes it (the cheapest place to buy)
 * @param sellDemanded  sell price where the port wants it (the best place to sell)
 * @param producedIn    climates whose ports make it
 * @param weight        cargo weight of one unit
 */
public record MarketEntry(ResourceLocation good, ResourceLocation item, double buy, double sell, double buyProduced,
                          double sellDemanded, List<Climate> producedIn, double weight) {

    public MarketEntry {
        producedIn = List.copyOf(producedIn);
    }

    public static MarketEntry of(ResourceLocation id, TradeGood good, MarketParams p) {
        double base = good.basePrice();
        return new MarketEntry(id, good.item(),
                base * p.roleFactor(GoodRole.NEUTRAL) * p.buyMultiplier(),
                base * p.roleFactor(GoodRole.NEUTRAL) * p.sellMultiplier(),
                base * p.roleFactor(GoodRole.PRODUCES) * p.buyMultiplier(),
                base * p.roleFactor(GoodRole.DEMANDS) * p.sellMultiplier(),
                good.producedIn(), good.weight());
    }

    /**
     * Every good whose item exists ({@code itemExists}; goods naming a missing item are skipped as the market skips
     * them), sorted by item id.
     */
    public static List<MarketEntry> all(Map<ResourceLocation, TradeGood> goods, MarketParams p, Predicate<ResourceLocation> itemExists) {
        List<MarketEntry> out = new ArrayList<>();
        goods.forEach((id, good) -> {
            if (itemExists.test(good.item())) out.add(of(id, good, p));
        });
        out.sort(Comparator.comparing((MarketEntry e) -> e.item().toString()).thenComparing(e -> e.good().toString()));
        return out;
    }

    /** The text lines under the slots, top to bottom. */
    public List<TextLine> lines() {
        List<TextLine> out = new ArrayList<>();
        out.add(TextLine.of(CompatText.MARKET_BUY, price(buy)));
        out.add(TextLine.of(CompatText.MARKET_SELL, price(sell)));
        out.add(TextLine.of(CompatText.MARKET_PRODUCED, price(buyProduced)));
        out.add(TextLine.of(CompatText.MARKET_DEMANDED, price(sellDemanded)));
        if (producedIn.isEmpty()) {
            out.add(TextLine.of(CompatText.MARKET_PRODUCED_NOWHERE));
        } else {
            out.add(TextLine.of(CompatText.MARKET_PRODUCED_IN,
                    producedIn.stream().map(c -> TextLine.of(CompatText.climate(c))).toList()));
        }
        out.add(TextLine.of(CompatText.MARKET_WEIGHT, number(weight)));
        return out;
    }

    /** A price per unit with one decimal ("2.2"). */
    static String price(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** A number without trailing zeros ("0.25", "1"). */
    static String number(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }
}

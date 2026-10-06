package com.richardsenger.piratesnships.trade.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * One port market: its profile plus, per good, the <b>imbalance</b> {@code x} = units customers have bought from the
 * market minus units they sold to it, decaying towards 0 over time. Immutable.
 *
 * <h2>Price model</h2>
 * The mid price of a good is {@code base × roleFactor × e^(k·x)}, with {@code k} = {@link MarketParams#pressurePerUnit}.
 * Buying {@code q} units moves {@code x} up by {@code q} and costs the integral of the buy price over that move:
 * {@code buyMult × base × f × (e^(k(x+q)) − e^(kx)) / k}. Selling moves {@code x} down and pays the integral of the
 * sell price. Because the cost is an integral of a function of {@code x} alone:
 * <ul>
 *   <li>splitting an order into parts gives exactly the same raw total (only the rounding differs, and rounding is
 *       always against the customer: buy totals round up, sell totals down, so splitting never pays);</li>
 *   <li>any sequence of buys and sells without time passing that ends with the same goods in hand moves {@code x}
 *       back to where it started, so the bought and sold amounts cover the same price range, and the customer pays
 *       {@code buyMult} for it and receives {@code sellMult} &lt; {@code buyMult}: always a loss (no money loops).</li>
 * </ul>
 * Stock: a customer can buy until {@code x} reaches the stock cap and sell until {@code −x} reaches the absorb cap.
 * Recovery multiplies every {@code x} by {@link MarketParams#remainingAfter} the elapsed ticks, which is exact for any
 * split of the interval.
 */
public record Market(PortProfile profile, long lastUpdate, Map<ResourceLocation, Double> imbalance) {

    public static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> i.group(
            PortProfile.CODEC.fieldOf("profile").forGetter(Market::profile),
            Codec.LONG.fieldOf("last_update").forGetter(Market::lastUpdate),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("imbalance", Map.of()).forGetter(Market::imbalance)
    ).apply(i, Market::new));

    /** Deviations smaller than this are dropped after recovery. */
    static final double NEGLIGIBLE = 1e-6;
    /** Totals are capped here so a runaway price can't overflow. */
    static final double MAX_TOTAL = 1e15;

    public enum Side { BUY, SELL }

    public enum Outcome {
        OK,
        /** The port doesn't trade this good. */
        NOT_TRADED,
        /** Not enough stock (buy) or the port is saturated (sell); {@link Quote#available()} says how many fit. */
        LIMIT,
        /** Quantity below 1. */
        INVALID_QUANTITY
    }

    /**
     * A price for {@code quantity} units. {@code total} is in doubloons (what the customer pays for a buy, receives
     * for a sell); {@code available} is how many units the port would trade right now ({@link Integer#MAX_VALUE}
     * without limits).
     */
    public record Quote(ResourceLocation good, Side side, int quantity, long total, int available, Outcome outcome) {
        public boolean ok() {
            return outcome == Outcome.OK;
        }
    }

    /** A quote plus the market after the trade (unchanged unless {@code quote.ok()}). */
    public record Trade(Quote quote, Market market) {
    }

    public Market {
        imbalance = Map.copyOf(imbalance);
    }

    public static Market fresh(PortProfile profile, long now) {
        return new Market(profile, now, Map.of());
    }

    public double imbalance(ResourceLocation good) {
        return imbalance.getOrDefault(good, 0.0);
    }

    /** The market at {@code now}: every deviation decayed. Never goes back in time. */
    public Market recoverTo(long now, MarketParams p) {
        if (now <= lastUpdate) return this;
        double keep = p.remainingAfter(now - lastUpdate);
        Map<ResourceLocation, Double> out = new HashMap<>();
        imbalance.forEach((id, x) -> {
            double nx = x * keep;
            if (Math.abs(nx) >= NEGLIGIBLE) out.put(id, nx);
        });
        return new Market(profile, now, out);
    }

    /** Mid price of one unit (no spread), for display and contract rewards. */
    public double midPrice(ResourceLocation id, TradeGood good, MarketParams p) {
        GoodRole role = profile.role(id);
        return good.basePrice() * p.roleFactor(role) * Math.exp(p.pressurePerUnit(good, profile.kind()) * imbalance(id));
    }

    /** How many units a customer could buy or sell right now. */
    public int available(ResourceLocation id, TradeGood good, Side side, MarketParams p) {
        GoodRole role = profile.role(id);
        if (!role.traded()) return 0;
        if (!p.stockLimits()) return Integer.MAX_VALUE;
        double x = imbalance(id);
        double room = side == Side.BUY ? p.stockCap(role, good, profile.kind()) - x : p.absorbCap(role, good, profile.kind()) + x;
        // Tiny epsilon so a cap of exactly 48.0 minus a recovered 1e-12 still allows 48
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.floor(room + 1e-9)));
    }

    /** Raw (unrounded) total for a trade of {@code q} units; the integral described in the class comment. */
    public double rawTotal(ResourceLocation id, TradeGood good, Side side, int q, MarketParams p) {
        GoodRole role = profile.role(id);
        double scale = good.basePrice() * p.roleFactor(role);
        double k = p.pressurePerUnit(good, profile.kind());
        double x = imbalance(id);
        double integral;
        if (k <= 0) {
            integral = q;
        } else if (side == Side.BUY) {
            integral = Math.exp(k * x) * Math.expm1(k * q) / k;
        } else {
            integral = -Math.exp(k * x) * Math.expm1(-k * q) / k;
        }
        double mult = side == Side.BUY ? p.buyMultiplier() : p.sellMultiplier();
        return Math.min(MAX_TOTAL, scale * integral * mult);
    }

    /** Prices a trade without performing it. The market must already be recovered to now. */
    public Quote quote(ResourceLocation id, TradeGood good, Side side, int quantity, MarketParams p) {
        if (!profile.role(id).traded()) return new Quote(id, side, quantity, 0, 0, Outcome.NOT_TRADED);
        int available = available(id, good, side, p);
        if (quantity < 1) return new Quote(id, side, quantity, 0, available, Outcome.INVALID_QUANTITY);
        double raw = rawTotal(id, good, side, quantity, p);
        long total = side == Side.BUY ? (long) Math.ceil(raw) : (long) Math.floor(raw);
        Outcome outcome = quantity > available ? Outcome.LIMIT : Outcome.OK;
        return new Quote(id, side, quantity, total, available, outcome);
    }

    /** Customer buys {@code quantity} units from the port at time {@code now} (recovers first). */
    public Trade buy(ResourceLocation id, TradeGood good, int quantity, long now, MarketParams p) {
        return trade(id, good, Side.BUY, quantity, now, p);
    }

    /** Customer sells {@code quantity} units to the port at time {@code now} (recovers first). */
    public Trade sell(ResourceLocation id, TradeGood good, int quantity, long now, MarketParams p) {
        return trade(id, good, Side.SELL, quantity, now, p);
    }

    public Trade trade(ResourceLocation id, TradeGood good, Side side, int quantity, long now, MarketParams p) {
        Market current = recoverTo(now, p);
        Quote q = current.quote(id, good, side, quantity, p);
        if (!q.ok()) return new Trade(q, current);
        Map<ResourceLocation, Double> next = new HashMap<>(current.imbalance);
        double nx = current.imbalance(id) + (side == Side.BUY ? quantity : -quantity);
        if (Math.abs(nx) < NEGLIGIBLE) next.remove(id);
        else next.put(id, nx);
        return new Trade(q, new Market(profile, current.lastUpdate, next));
    }

    /** The same market with a profile extended by new goods (see {@link PortProfile#extendedWith}). */
    public Market withProfile(PortProfile newProfile) {
        return newProfile == profile ? this : new Market(newProfile, lastUpdate, imbalance);
    }
}

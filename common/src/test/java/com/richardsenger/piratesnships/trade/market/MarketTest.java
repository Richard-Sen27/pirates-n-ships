package com.richardsenger.piratesnships.trade.market;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.richardsenger.piratesnships.trade.TradeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketTest {

    @Test
    void buyingRaisesAndSellingLowersThePrice() {
        Market m = market(GoodRole.NEUTRAL);
        double mid = m.midPrice(SUGAR, SUGAR_DEF, P);
        assertEquals(SUGAR_DEF.basePrice(), mid, 1e-12);
        Market afterBuy = m.buy(SUGAR, SUGAR_DEF, 64, 0, P).market();
        assertEquals(mid * Math.exp(P.volatility()), afterBuy.midPrice(SUGAR, SUGAR_DEF, P), 1e-9, "one stack = volatility in log price");
        Market afterSell = m.sell(SUGAR, SUGAR_DEF, 64, 0, P).market();
        assertTrue(afterSell.midPrice(SUGAR, SUGAR_DEF, P) < mid);
        assertTrue(m.quote(SUGAR, SUGAR_DEF, Market.Side.BUY, 64, P).total()
                < afterBuy.quote(SUGAR, SUGAR_DEF, Market.Side.BUY, 64, P).total());
        assertTrue(m.quote(SUGAR, SUGAR_DEF, Market.Side.SELL, 64, P).total()
                > afterSell.quote(SUGAR, SUGAR_DEF, Market.Side.SELL, 64, P).total());
    }

    @Test
    void roleFactorsAndSpread() {
        double produced = market(GoodRole.PRODUCES).midPrice(SUGAR, SUGAR_DEF, P);
        double neutral = market(GoodRole.NEUTRAL).midPrice(SUGAR, SUGAR_DEF, P);
        double demanded = market(GoodRole.DEMANDS).midPrice(SUGAR, SUGAR_DEF, P);
        assertTrue(produced < neutral && neutral < demanded);
        Market m = market(GoodRole.NEUTRAL);
        double buy = m.rawTotal(SUGAR, SUGAR_DEF, Market.Side.BUY, 1, P);
        double sell = m.rawTotal(SUGAR, SUGAR_DEF, Market.Side.SELL, 1, P);
        assertTrue(buy > neutral && sell < neutral);
        assertEquals(P.spread(), (buy - sell) / neutral, 0.01, "gap = spread of the mid price for a tiny order");
    }

    @Test
    void roundingNeverFavorsTheCustomer() {
        Market m = market(GoodRole.NEUTRAL);
        for (int q = 1; q < 300; q++) {
            var b = m.quote(SUGAR, SUGAR_DEF, Market.Side.BUY, q, P);
            var s = m.quote(SUGAR, SUGAR_DEF, Market.Side.SELL, q, P);
            assertTrue(b.total() >= m.rawTotal(SUGAR, SUGAR_DEF, Market.Side.BUY, q, P));
            assertTrue(s.total() <= m.rawTotal(SUGAR, SUGAR_DEF, Market.Side.SELL, q, P));
            assertTrue(b.total() >= 1, "a buy is never free");
        }
    }

    @Test
    void splittingAnOrderNeverPays() {
        Random r = new Random(42);
        var p = unlimited();
        for (int trial = 0; trial < 500; trial++) {
            Market m = market(GoodRole.values()[r.nextInt(3)]);
            int total = 1 + r.nextInt(1500);
            Market.Side side = r.nextBoolean() ? Market.Side.BUY : Market.Side.SELL;
            long whole = m.quote(SUGAR, SUGAR_DEF, side, total, p).total();
            double wholeRaw = m.rawTotal(SUGAR, SUGAR_DEF, side, total, p);
            long split = 0;
            double splitRaw = 0;
            int left = total, parts = 0;
            Market cur = m;
            while (left > 0) {
                int q = Math.min(left, 1 + r.nextInt(40));
                splitRaw += cur.rawTotal(SUGAR, SUGAR_DEF, side, q, p);
                var t = cur.trade(SUGAR, SUGAR_DEF, side, q, 0, p);
                split += t.quote().total();
                cur = t.market();
                left -= q;
                parts++;
            }
            assertEquals(wholeRaw, splitRaw, 1e-9 * Math.max(1, wholeRaw), "raw totals equal for any split");
            if (side == Market.Side.BUY) {
                assertTrue(split >= whole, "split buy costs at least the whole buy");
                assertTrue(split - whole <= parts, "only rounding differs");
            } else {
                assertTrue(split <= whole, "split sale pays at most the whole sale");
                assertTrue(whole - split <= parts, "only rounding differs");
            }
            assertEquals(m.trade(SUGAR, SUGAR_DEF, side, total, 0, p).market().imbalance(SUGAR), cur.imbalance(SUGAR), 1e-9);
        }
    }

    @Test
    void stockLimits() {
        Market m = market(GoodRole.DEMANDS);
        int stock = m.available(SUGAR, SUGAR_DEF, Market.Side.BUY, P);
        assertEquals((int) (P.stockDemanded() * SUGAR_DEF.stockFactor()), stock);
        assertEquals(Market.Outcome.LIMIT, m.buy(SUGAR, SUGAR_DEF, stock + 1, 0, P).quote().outcome());
        var all = m.buy(SUGAR, SUGAR_DEF, stock, 0, P);
        assertTrue(all.quote().ok());
        assertEquals(0, all.market().available(SUGAR, SUGAR_DEF, Market.Side.BUY, P));
        assertSame(all.market(), all.market().buy(SUGAR, SUGAR_DEF, 1, 0, P).market(), "refused trade leaves the market as is");
        // Stock comes back with time
        assertTrue(all.market().recoverTo(5 * DAY, P).available(SUGAR, SUGAR_DEF, Market.Side.BUY, P) > stock / 2);

        Market producer = market(GoodRole.PRODUCES);
        int absorb = producer.available(SUGAR, SUGAR_DEF, Market.Side.SELL, P);
        assertEquals((int) P.absorbProduced(), absorb);
        assertEquals(Market.Outcome.LIMIT, producer.sell(SUGAR, SUGAR_DEF, absorb + 1, 0, P).quote().outcome());
        // Buying first makes room to sell more
        Market bought = producer.buy(SUGAR, SUGAR_DEF, 100, 0, P).market();
        assertEquals(absorb + 100, bought.available(SUGAR, SUGAR_DEF, Market.Side.SELL, P));
        // Smaller ports keep less
        assertTrue(market(PortKind.PIRATE_ISLAND, GoodRole.PRODUCES).available(SUGAR, SUGAR_DEF, Market.Side.BUY, P)
                < producer.available(SUGAR, SUGAR_DEF, Market.Side.BUY, P));
        // Without limits anything goes
        assertTrue(m.buy(SUGAR, SUGAR_DEF, 100_000, 0, unlimited()).quote().ok());
    }

    @Test
    void notTradedAndInvalidQuantities() {
        Market m = market(GoodRole.NOT_TRADED);
        assertEquals(Market.Outcome.NOT_TRADED, m.buy(SUGAR, SUGAR_DEF, 1, 0, P).quote().outcome());
        assertEquals(Market.Outcome.NOT_TRADED, market(GoodRole.NEUTRAL).quote(
                net.minecraft.resources.ResourceLocation.parse("x:unknown"), SUGAR_DEF, Market.Side.SELL, 1, P).outcome());
        assertEquals(Market.Outcome.INVALID_QUANTITY, market(GoodRole.NEUTRAL).buy(SUGAR, SUGAR_DEF, 0, 0, P).quote().outcome());
        assertEquals(Market.Outcome.INVALID_QUANTITY, market(GoodRole.NEUTRAL).sell(SUGAR, SUGAR_DEF, -5, 0, P).quote().outcome());
    }

    @Test
    void recoveryTowardsBaseline() {
        Market m = market(GoodRole.NEUTRAL).buy(SUGAR, SUGAR_DEF, 128, 0, P).market();
        double x0 = m.imbalance(SUGAR);
        assertEquals(x0 * (1 - P.recoveryPerDay()), m.recoverTo(DAY, P).imbalance(SUGAR), 1e-9);
        double price0 = m.midPrice(SUGAR, SUGAR_DEF, P);
        double price1 = m.recoverTo(DAY, P).midPrice(SUGAR, SUGAR_DEF, P);
        assertTrue(price1 < price0 && price1 > SUGAR_DEF.basePrice());
        Market far = m.recoverTo(200 * DAY, P);
        assertEquals(SUGAR_DEF.basePrice(), far.midPrice(SUGAR, SUGAR_DEF, P), 1e-6);
        assertTrue(far.imbalance().isEmpty(), "negligible deviations are dropped");
        // Selling recovers upwards
        Market sold = market(GoodRole.NEUTRAL).sell(SUGAR, SUGAR_DEF, 128, 0, P).market();
        assertTrue(sold.recoverTo(DAY, P).midPrice(SUGAR, SUGAR_DEF, P) > sold.midPrice(SUGAR, SUGAR_DEF, P));
        // Time never runs backwards; zero recovery keeps the deviation
        assertSame(m, m.recoverTo(-5, P));
        assertEquals(x0, m.recoverTo(10 * DAY, withVolatilityAndRecovery(P.volatility(), 0.0)).imbalance(SUGAR), 1e-12);
        assertTrue(m.recoverTo(1, withVolatilityAndRecovery(P.volatility(), 1.0)).imbalance().isEmpty(), "rate 1 = instant");
    }

    @Test
    void recoveryIsIndependentOfTickInterval() {
        Random r = new Random(7);
        for (int trial = 0; trial < 100; trial++) {
            Market m = market(GoodRole.NEUTRAL).buy(SUGAR, SUGAR_DEF, 1 + r.nextInt(500), 0, unlimited()).market();
            long total = 1 + r.nextInt((int) (20 * DAY));
            Market once = m.recoverTo(total, P);
            Market steps = m;
            long t = 0;
            while (t < total) {
                t = Math.min(total, t + 1 + r.nextInt(3000));
                steps = steps.recoverTo(t, P);
            }
            assertEquals(once.imbalance(SUGAR), steps.imbalance(SUGAR), 1e-9 * Math.max(1, Math.abs(once.imbalance(SUGAR))));
            assertEquals(once.lastUpdate(), steps.lastUpdate());
        }
    }
}

package com.richardsenger.piratesnships.trade.market;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.trade.TradeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Buying where a good is produced and selling where it is demanded. */
class TradeRouteTest {

    private record Run(long cost, long revenue, Market from, Market to) {
        long profit() {
            return revenue - cost;
        }
    }

    private static Run run(Market from, Market to, int q, long now) {
        var buy = from.buy(SUGAR, SUGAR_DEF, q, now, P);
        var sell = to.sell(SUGAR, SUGAR_DEF, q, now, P);
        assertTrue(buy.quote().ok() && sell.quote().ok(), "q=" + q);
        return new Run(buy.quote().total(), sell.quote().total(), buy.market(), sell.market());
    }

    @Test
    void producerToDemanderIsProfitableWithDiminishingReturns() {
        Market producer = market(GoodRole.PRODUCES);
        Market demander = market(PortKind.NAVY_OUTPOST, GoodRole.DEMANDS);
        double lastPerUnit = Double.MAX_VALUE;
        for (int q : new int[]{16, 64, 128, 256, 512}) {
            Run r = run(producer, demander, q, 0);
            assertTrue(r.profit() > 0, "profitable at q=" + q);
            double perUnit = r.profit() / (double) q;
            assertTrue(perUnit < lastPerUnit, "profit per unit falls with quantity, q=" + q);
            lastPerUnit = perUnit;
        }
        // Neutral to neutral loses (the spread)
        assertTrue(run(market(GoodRole.NEUTRAL), market(GoodRole.NEUTRAL), 64, 0).profit() < 0);
        // Demander to producer loses badly
        assertTrue(run(market(GoodRole.DEMANDS), market(GoodRole.PRODUCES), 16, 0).profit() < 0);
    }

    @Test
    void repeatedRunsPayLessUntilPricesRecover() {
        Market producer = market(GoodRole.PRODUCES);
        Market demander = market(PortKind.NAVY_OUTPOST, GoodRole.DEMANDS);
        Run first = run(producer, demander, 128, 0);
        Run second = run(first.from(), first.to(), 128, 0);
        Run third = run(second.from(), second.to(), 128, 0);
        assertTrue(second.profit() < first.profit());
        assertTrue(third.profit() < second.profit());
        Run fourthRightAway = run(third.from(), third.to(), 128, 0);
        Run afterOneDay = run(third.from(), third.to(), 128, DAY);
        assertTrue(afterOneDay.profit() > fourthRightAway.profit(), "partly recovered after a day");
        Run afterLongRest = run(third.from(), third.to(), 128, 60 * DAY);
        assertEquals(first.profit(), afterLongRest.profit(), 1, "fully recovered");
    }

    @Test
    void workedExample() {
        Market producer = market(GoodRole.PRODUCES);
        Market demander = market(PortKind.NAVY_OUTPOST, GoodRole.DEMANDS);
        int q = 256;
        StringBuilder sb = new StringBuilder("Worked example, sugar (base 2.0), " + q + " units\n");
        sb.append(String.format(java.util.Locale.ROOT, "  producer village: buy 1 = %d, mid %.3f%n", producer.quote(SUGAR, SUGAR_DEF, Market.Side.BUY, 1, P).total(), producer.midPrice(SUGAR, SUGAR_DEF, P)));
        sb.append(String.format(java.util.Locale.ROOT, "  demanding navy outpost: sell 1 = %d, mid %.3f%n", demander.quote(SUGAR, SUGAR_DEF, Market.Side.SELL, 1, P).total(), demander.midPrice(SUGAR, SUGAR_DEF, P)));
        Run r = run(producer, demander, q, 0);
        sb.append(String.format(java.util.Locale.ROOT, "  cost %d, revenue %d, profit %d%n", r.cost(), r.revenue(), r.profit()));
        sb.append(String.format(java.util.Locale.ROOT, "  after: producer mid %.3f, outpost mid %.3f%n", r.from().midPrice(SUGAR, SUGAR_DEF, P), r.to().midPrice(SUGAR, SUGAR_DEF, P)));
        sb.append(String.format(java.util.Locale.ROOT, "  second run right away: profit %d; after 1 day: %d%n", run(r.from(), r.to(), q, 0).profit(), run(r.from(), r.to(), q, DAY).profit()));
        System.out.println(sb);
        assertTrue(r.profit() > 0);
    }
}

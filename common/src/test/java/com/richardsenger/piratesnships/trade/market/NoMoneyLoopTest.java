package com.richardsenger.piratesnships.trade.market;

import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.richardsenger.piratesnships.trade.TradeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Randomized (seeded) proofs that trading within one port never makes money. */
class NoMoneyLoopTest {

    private static final List<ResourceLocation> IDS = new ArrayList<>(TradeGoods.DEFAULTS.keySet());

    private static Market randomMarket(Random r) {
        PortKind kind = PortKind.values()[r.nextInt(3)];
        return Market.fresh(ProfileDeriver.derive(kind, Climate.values()[r.nextInt(4)], r.nextLong(), GOODS), 0);
    }

    private static MarketParams randomParams(Random r) {
        return withVolatilityAndRecovery(0.001 + r.nextDouble() * 0.5, r.nextDouble());
    }

    @Test
    void buyThenSellLosesForAnyQuantity() {
        for (ResourceLocation id : IDS) {
            TradeGood g = GOODS.require(id);
            for (GoodRole role : new GoodRole[]{GoodRole.PRODUCES, GoodRole.NEUTRAL, GoodRole.DEMANDS}) {
                Market m = Market.fresh(new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, 0, java.util.Map.of(id, role)), 0);
                for (int q = 1; q <= 2000; q += (q < 100 ? 1 : 37)) {
                    var buy = m.buy(id, g, q, 0, unlimited());
                    var sell = buy.market().sell(id, g, q, 0, unlimited());
                    assertTrue(sell.quote().total() < buy.quote().total(), id + " " + role + " q=" + q);
                    // And sell first, then buy back
                    var s2 = m.sell(id, g, q, 0, unlimited());
                    var b2 = s2.market().buy(id, g, q, 0, unlimited());
                    assertTrue(s2.quote().total() < b2.quote().total(), id + " " + role + " q=" + q + " (sell first)");
                }
            }
        }
    }

    /** Any sequence of buys and sells without time passing that ends with the starting inventory loses money. */
    @Test
    void anyCycleWithoutTimeLoses() {
        Random r = new Random(1234);
        for (int trial = 0; trial < 2000; trial++) {
            MarketParams p = r.nextBoolean() ? P : randomParams(r);
            assertCycleLoses(r, randomMarket(r), p, false);
        }
    }

    /** Starting from equilibrium, waiting between trades doesn't help either (recovery only moves towards baseline). */
    @Test
    void anyCycleFromEquilibriumWithTimeLoses() {
        Random r = new Random(98765);
        for (int trial = 0; trial < 2000; trial++) {
            assertCycleLoses(r, randomMarket(r), randomParams(r), true);
        }
    }

    private static void assertCycleLoses(Random r, Market start, MarketParams p, boolean withTime) {
        ResourceLocation id = IDS.get(r.nextInt(IDS.size()));
        TradeGood g = GOODS.require(id);
        if (!start.profile().role(id).traded()) return;
        Market m = start;
        long money = 0;
        int held = 200 + r.nextInt(500); // starting inventory, so selling first is possible
        int initial = held;
        long now = 0;
        int steps = 1 + r.nextInt(12);
        boolean traded = false;
        for (int i = 0; i < steps || held != initial; i++) {
            if (withTime && r.nextInt(3) == 0) now += r.nextInt((int) (3 * DAY));
            int q;
            Market.Side side;
            if (i >= steps) {
                side = held < initial ? Market.Side.BUY : Market.Side.SELL;
                q = Math.abs(initial - held);
            } else {
                side = r.nextBoolean() ? Market.Side.BUY : Market.Side.SELL;
                q = 1 + r.nextInt(side == Market.Side.SELL ? Math.max(1, held) : 400);
                if (side == Market.Side.SELL && held == 0) continue;
                q = side == Market.Side.SELL ? Math.min(q, held) : q;
            }
            var t = m.trade(id, g, side, q, now, p);
            if (!t.quote().ok()) {
                // A limit refused it; try the largest allowed amount instead
                int a = t.quote().available();
                if (a < 1) { m = t.market(); continue; }
                t = m.trade(id, g, side, Math.min(q, a), now, p);
                q = Math.min(q, a);
            }
            m = t.market();
            traded = true;
            if (side == Market.Side.BUY) { money -= t.quote().total(); held += q; }
            else { money += t.quote().total(); held -= q; }
            if (i > 200) fail("cycle did not close");
        }
        if (traded) assertTrue(money < 0, "cycle made " + money + " on " + id + " in " + start.profile().kind());
    }
}

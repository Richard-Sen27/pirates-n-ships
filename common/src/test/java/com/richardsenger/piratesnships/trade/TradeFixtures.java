package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.HashMap;
import java.util.Map;

/** Shared fixtures for the trade unit tests. */
public final class TradeFixtures {

    public static final MarketParams P = MarketParams.DEFAULTS;
    public static final Definitions<TradeGood> GOODS = Definitions.of("trade_good", TradeGoods.DEFAULTS);
    public static final ResourceLocation SUGAR = TradeGoods.SUGAR;
    public static final TradeGood SUGAR_DEF = TradeGoods.DEFAULTS.get(SUGAR);
    public static final long DAY = MarketParams.TICKS_PER_DAY;

    private TradeFixtures() {
    }

    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A profile where every default good has {@code role}. */
    public static PortProfile allGoods(PortKind kind, GoodRole role) {
        Map<ResourceLocation, GoodRole> roles = new HashMap<>();
        for (ResourceLocation id : TradeGoods.DEFAULTS.keySet()) roles.put(id, role);
        return new PortProfile(kind, Climate.TEMPERATE, 0L, roles);
    }

    public static Market market(GoodRole sugarRole) {
        return market(PortKind.SEAFARER_VILLAGE, sugarRole);
    }

    public static Market market(PortKind kind, GoodRole sugarRole) {
        return Market.fresh(new PortProfile(kind, Climate.TROPICAL, 0L, Map.of(SUGAR, sugarRole)), 0L);
    }

    /** Defaults without stock limits, for pure price tests. */
    public static MarketParams unlimited() {
        return new MarketParams(P.volatility(), P.recoveryPerDay(), P.spread(), P.producedFactor(), P.neutralFactor(),
                P.demandedFactor(), false, P.stockProduced(), P.stockNeutral(), P.stockDemanded(), P.absorbProduced(),
                P.absorbNeutral(), P.absorbDemanded(), P.depthVillage(), P.depthNavy(), P.depthPirate());
    }

    public static MarketParams withVolatilityAndRecovery(double volatility, double recovery) {
        return new MarketParams(volatility, recovery, P.spread(), P.producedFactor(), P.neutralFactor(),
                P.demandedFactor(), false, P.stockProduced(), P.stockNeutral(), P.stockDemanded(), P.absorbProduced(),
                P.absorbNeutral(), P.absorbDemanded(), P.depthVillage(), P.depthNavy(), P.depthPirate());
    }
}

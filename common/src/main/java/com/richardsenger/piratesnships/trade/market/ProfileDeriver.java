package com.richardsenger.piratesnships.trade.market;

import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.TradeRandom;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives a port's profile from its kind, climate and a seed (design.md §10.3 "derived from its biome and type").
 * Every good is rolled on its own random stream (seed + good id), so the result doesn't depend on which other goods
 * exist, and adding a good later never changes the roles of the others.
 *
 * <p>Weights per good: produce = kind weight × (5 if the climate produces it, 1 if no climate does, else 0.3);
 * demand = kind weight × (0.2 if the climate produces it, else 2); neutral = 2; not traded = kind weight. Afterwards a
 * port that rolled nothing to produce gets its most likely product, and one with nothing in demand gets its most
 * likely demand, so every port has at least one cheap and one expensive good.
 */
public final class ProfileDeriver {

    static final double CLIMATE_PRODUCE = 5.0;
    static final double NO_CLIMATE_PRODUCE = 1.0;
    static final double OTHER_CLIMATE_PRODUCE = 0.3;
    static final double CLIMATE_DEMAND = 0.2;
    static final double OTHER_CLIMATE_DEMAND = 2.0;
    static final double NEUTRAL = 2.0;

    private ProfileDeriver() {
    }

    public static PortProfile derive(PortKind kind, Climate climate, long seed, Definitions<TradeGood> goods) {
        List<ResourceLocation> ids = new ArrayList<>(goods.ids());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        Map<ResourceLocation, GoodRole> roles = new HashMap<>();
        for (ResourceLocation id : ids) {
            roles.put(id, roll(kind, climate, seed, id, goods.require(id)));
        }
        if (!ids.isEmpty() && !roles.containsValue(GoodRole.PRODUCES)) {
            roles.put(best(ids, goods, kind, climate, true, roles), GoodRole.PRODUCES);
        }
        if (ids.size() > 1 && !roles.containsValue(GoodRole.DEMANDS)) {
            roles.put(best(ids, goods, kind, climate, false, roles), GoodRole.DEMANDS);
        }
        return new PortProfile(kind, climate, seed, roles);
    }

    /** The role of one good, from its own random stream. */
    static GoodRole roll(PortKind kind, Climate climate, long seed, ResourceLocation id, TradeGood good) {
        double p = produceWeight(kind, climate, good);
        double d = demandWeight(kind, climate, good);
        double n = NEUTRAL;
        double x = kind.notTradedWeight();
        double r = TradeRandom.unit(TradeRandom.mix(seed, id.toString())) * (p + d + n + x);
        if (r < p) return GoodRole.PRODUCES;
        if (r < p + d) return GoodRole.DEMANDS;
        if (r < p + d + n) return GoodRole.NEUTRAL;
        return GoodRole.NOT_TRADED;
    }

    static double produceWeight(PortKind kind, Climate climate, TradeGood good) {
        double climateWeight = good.producedIn(climate) ? CLIMATE_PRODUCE
                : good.producedIn().isEmpty() ? NO_CLIMATE_PRODUCE : OTHER_CLIMATE_PRODUCE;
        return kind.produceWeight(good.category()) * climateWeight;
    }

    static double demandWeight(PortKind kind, Climate climate, TradeGood good) {
        return kind.demandWeight(good.category()) * (good.producedIn(climate) ? CLIMATE_DEMAND : OTHER_CLIMATE_DEMAND);
    }

    private static ResourceLocation best(List<ResourceLocation> ids, Definitions<TradeGood> goods, PortKind kind, Climate climate,
                                         boolean produce, Map<ResourceLocation, GoodRole> roles) {
        ResourceLocation best = null;
        double bestWeight = -1;
        for (ResourceLocation id : ids) {
            if (!produce && roles.get(id) == GoodRole.PRODUCES) continue;
            TradeGood g = goods.require(id);
            double w = produce ? produceWeight(kind, climate, g) : demandWeight(kind, climate, g);
            if (w > bestWeight) {
                bestWeight = w;
                best = id;
            }
        }
        return best;
    }
}

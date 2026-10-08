package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convoys on the markets (WS2): a convoy buys its cargo at the origin when it departs (the origin's prices rise) and
 * sells it at the destination when it arrives (the destination's prices fall), through
 * {@link TradeService#npcTrade}. No coins move; the markets' stock limits cap the units.
 */
public final class VoyageEconomy {

    private VoyageEconomy() {
    }

    /** Buys {@code wanted} at {@code port}; returns the units actually bought per good (goods with none left out). */
    public static Map<ResourceLocation, Integer> load(MinecraftServer server, ResourceLocation port, Map<ResourceLocation, Integer> wanted) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        wanted.forEach((good, units) -> {
            Market.Quote q = TradeService.npcTrade(server, port, good, Market.Side.BUY, units);
            if (q.ok() && q.quantity() > 0) out.put(good, q.quantity());
        });
        return out;
    }

    /** Sells {@code cargo} at {@code port}; returns the units sold per good. Unsold units are lost with the voyage. */
    public static Map<ResourceLocation, Integer> unload(MinecraftServer server, ResourceLocation port, Map<ResourceLocation, Integer> cargo) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        cargo.forEach((good, units) -> {
            if (units < 1) return;
            Market.Quote q = TradeService.npcTrade(server, port, good, Market.Side.SELL, units);
            if (q.ok() && q.quantity() > 0) out.put(good, q.quantity());
        });
        return out;
    }
}

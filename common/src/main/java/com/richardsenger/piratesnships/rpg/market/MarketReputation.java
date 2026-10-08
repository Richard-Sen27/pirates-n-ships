package com.richardsenger.piratesnships.rpg.market;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRecord;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRules;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.LongUnaryOperator;

/**
 * Reputation at the markets (docs/design.md §10.3, §15), called in one line each from {@code trade.net.MarketBackend}:
 * <ul>
 *   <li>prices swing by up to {@code reputation.price_swing} with the villagers' score at seafarer villages and the
 *       pirates' score at pirate fences (liked: buy cheaper, sell dearer; disliked: the reverse); navy outposts don't
 *       swing. The market view and the trade use the same functions, so the quoted price is the price paid;</li>
 *   <li>village markets refuse a player below {@code reputation.villager_trade_threshold};</li>
 *   <li>a trade at a village is the {@link Deed#TRADE_VILLAGE} deed (at most {@code village_trade_daily_cap} a day),
 *       selling plunder to a fence the {@link Deed#FENCE_PLUNDER} deed.</li>
 * </ul>
 */
public final class MarketReputation {

    private MarketReputation() {
    }

    /** Whose reputation swings prices at a port of {@code kind}. Pure. */
    public static Optional<Faction> priceFaction(PortKind kind) {
        return switch (kind) {
            case SEAFARER_VILLAGE -> Optional.of(Faction.VILLAGERS);
            case PIRATE_ISLAND -> Optional.of(Faction.PIRATES);
            case NAVY_OUTPOST -> Optional.empty();
        };
    }

    private static Optional<PortKind> kindOf(Player player, ResourceLocation port) {
        if (player.getServer() == null) return Optional.empty();
        return TradeService.market(player.getServer(), port).map(m -> m.profile().kind());
    }

    /** The player's score that swings prices at {@code port}; 0 for no swing. */
    public static int priceScore(Player player, ResourceLocation port) {
        return kindOf(player, port).flatMap(MarketReputation::priceFaction).map(f -> Reputation.priceScore(player, f)).orElse(0);
    }

    /** What the player pays for goods whose market total is the operand. */
    public static LongUnaryOperator buyPrice(Player player, ResourceLocation port) {
        int score = priceScore(player, port);
        double swing = ReputationConfig.PRICE_SWING.get();
        return total -> ReputationRules.buyPrice(total, score, swing);
    }

    /** What the player receives for a sale whose payout is the operand. */
    public static LongUnaryOperator sellPrice(Player player, ResourceLocation port) {
        int score = priceScore(player, port);
        double swing = ReputationConfig.PRICE_SWING.get();
        return payout -> ReputationRules.sellPrice(payout, score, swing);
    }

    /** {@code quote} as this player sees it at {@code port} (the market view). */
    public static Market.Quote quoteFor(Player player, ResourceLocation port, Market.Quote quote) {
        if (!quote.ok()) return quote;
        LongUnaryOperator price = quote.side() == Market.Side.BUY ? buyPrice(player, port) : sellPrice(player, port);
        return new Market.Quote(quote.good(), quote.side(), quote.quantity(), price.applyAsLong(quote.total()), quote.available(), quote.outcome());
    }

    /** Whether the market at {@code port} refuses the player: a village, villager reputation below the threshold. */
    public static boolean refuses(Player player, ResourceLocation port) {
        return kindOf(player, port).orElse(null) == PortKind.SEAFARER_VILLAGE && Reputation.villagersRefuse(player);
    }

    /** The answer to a refused request. */
    public static TransactionResult refusal(ResourceLocation good) {
        return TransactionResult.failed(TransactionResult.Status.REPUTATION_REFUSED, good);
    }

    /** After a trade at {@code port}: the village trade and fence deeds. */
    public static void afterTrade(ServerPlayer player, ResourceLocation port, TransactionResult result) {
        if (!result.done() || !Reputation.enabled()) return;
        PortKind kind = kindOf(player, port).orElse(null);
        if (result.plunder() == PlunderRules.Outcome.FENCED) {
            Deeds.record(player, Deed.FENCE_PLUNDER, DeedContext.port(port, result.coins()));
        }
        if (kind == PortKind.SEAFARER_VILLAGE) {
            long day = Reputation.now(player) / ReputationRules.TICKS_PER_DAY;
            ReputationRecord counted = Reputation.record(player).countTradeDeed(day, ReputationConfig.VILLAGE_TRADE_DAILY_CAP.get());
            if (counted == null) return;
            Reputation.store(player, counted);
            Deeds.record(player, Deed.TRADE_VILLAGE, DeedContext.port(port, result.coins()));
        }
    }
}

package com.richardsenger.piratesnships.trade.stall;

import com.richardsenger.piratesnships.mob.village.VillagerConfig;
import com.richardsenger.piratesnships.rpg.market.MarketReputation;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server side of a village stall (LIFE1): the lines the market screen shows for a stall session and the trades
 * of its fixed-price wares. {@code trade.net.MarketBackend} opens a stall session ({@code openStall}) when a vendor is
 * talked to, lists {@link #lines} instead of the port's whole market, and hands a trade request to {@link #trade}
 * unless the ware goes through the port's market ({@link #viaMarket}), which then runs its usual path (stock, price
 * movement, containers, the reputation swing and the village trade deed).
 *
 * <p>Fixed-price wares: the player pays {@link StallPricing#unitBuy} per unit from the purse and gets the items in the
 * inventory (dropped when full); the vendor buys back for {@link StallPricing#unitSell} per unit from the inventory.
 * Both totals take the villagers' reputation swing ({@link MarketReputation#buyPrice}, {@link MarketReputation#sellPrice})
 * and count as village trade ({@link MarketReputation#afterTrade}). Stock is unlimited.
 */
public final class StallTrades {

    private StallTrades() {
    }

    /** Whether {@code ware} trades through {@code port}'s market: a trade good the port's profile trades. */
    public static boolean viaMarket(MinecraftServer server, ResourceLocation port, StallWares.Ware ware) {
        if (ware.good().isEmpty()) return false;
        return TradeService.market(server, port).map(m -> m.profile().role(ware.good().get()).traded()).orElse(false)
                && TradeService.goods(false).tradeable().get(ware.good().get()).isPresent();
    }

    /** The base price of a fixed-price ware: the trade good's own base price, else the ware's. */
    static double basePrice(StallWares.Ware ware) {
        if (ware.good().isPresent()) {
            Optional<TradeGood> good = TradeService.goods(false).tradeable().get(ware.good().get());
            if (good.isPresent()) return good.get().basePrice();
        }
        return ware.basePrice();
    }

    /** Doubloons per unit of a fixed-price ware before the reputation swing. */
    public static long unitBuy(StallWares.Ware ware) {
        return StallPricing.unitBuy(basePrice(ware), VillagerConfig.VENDOR_PRICE_SCALE.get());
    }

    public static long unitSell(StallWares.Ware ware) {
        return StallPricing.unitSell(unitBuy(ware), VillagerConfig.VENDOR_BUYBACK.get(), ware.buysBack());
    }

    /** The market lines of {@code stall} at {@code port} for {@code player}, quoted for {@code quantity} units. */
    public static List<MarketView.GoodLine> lines(ServerPlayer player, ResourceLocation port, StallKind stall, int quantity) {
        MinecraftServer server = player.server;
        List<MarketView.GoodLine> lines = new ArrayList<>();
        for (StallWares.Ware ware : StallWares.of(stall)) {
            if (viaMarket(server, port, ware)) {
                ResourceLocation good = ware.good().orElseThrow();
                GoodRole role = TradeService.market(server, port).map(m -> m.profile().role(good)).orElse(GoodRole.NEUTRAL);
                lines.add(new MarketView.GoodLine(good, ware.item(), role,
                        MarketView.Price.of(MarketReputation.quoteFor(player, port, TradeService.quote(server, port, good, Market.Side.BUY, quantity))),
                        MarketView.Price.of(MarketReputation.quoteFor(player, port, TradeService.quote(server, port, good, Market.Side.SELL, quantity)))));
                continue;
            }
            long buy = MarketReputation.buyPrice(player, port).applyAsLong(StallPricing.total(unitBuy(ware), quantity));
            long sellUnit = unitSell(ware);
            MarketView.Price sell = sellUnit > 0
                    ? new MarketView.Price(MarketReputation.sellPrice(player, port).applyAsLong(StallPricing.total(sellUnit, quantity)),
                    Integer.MAX_VALUE, Market.Outcome.OK)
                    : new MarketView.Price(0, 0, Market.Outcome.NOT_TRADED);
            lines.add(new MarketView.GoodLine(ware.lineId(), ware.item(), GoodRole.NEUTRAL,
                    new MarketView.Price(buy, Integer.MAX_VALUE, Market.Outcome.OK), sell));
        }
        return lines;
    }

    /**
     * Buys ({@code buy}) or sells {@code quantity} units of the fixed-price ware of line {@code lineId} at {@code stall}
     * for {@code player} at {@code port}. The caller checked the session, the quantity and the market path.
     */
    public static TransactionResult trade(ServerPlayer player, ResourceLocation port, StallKind stall, ResourceLocation lineId,
                                          boolean buy, int quantity) {
        Optional<StallWares.Ware> ware = StallWares.byLine(stall, lineId);
        if (ware.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, lineId);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ware.get().item());
        if (item.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, lineId);
        TransactionResult r;
        if (buy) {
            long total = MarketReputation.buyPrice(player, port).applyAsLong(StallPricing.total(unitBuy(ware.get()), quantity));
            if (!Wallet.take(player, total)) return TransactionResult.failed(TransactionResult.Status.NOT_ENOUGH_COINS, lineId);
            for (int i = 0; i < quantity; i++) player.getInventory().placeItemBackInInventory(new ItemStack(item.get()));
            r = new TransactionResult(TransactionResult.Status.OK, lineId, quantity, total, PlunderRules.Outcome.NORMAL, false, Optional.empty());
        } else {
            long unit = unitSell(ware.get());
            if (unit <= 0) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, lineId);
            if (count(player, item.get()) < quantity) return TransactionResult.failed(TransactionResult.Status.NOT_ENOUGH_GOODS, lineId);
            remove(player, item.get(), quantity);
            long total = MarketReputation.sellPrice(player, port).applyAsLong(StallPricing.total(unit, quantity));
            Wallet.give(player, total);
            r = new TransactionResult(TransactionResult.Status.OK, lineId, quantity, total, PlunderRules.Outcome.NORMAL, false, Optional.empty());
        }
        player.containerMenu.broadcastChanges();
        MarketReputation.afterTrade(player, port, r);
        return r;
    }

    static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void remove(ServerPlayer player, Item item, int quantity) {
        int left = quantity;
        for (ItemStack s : player.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(item)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
    }
}

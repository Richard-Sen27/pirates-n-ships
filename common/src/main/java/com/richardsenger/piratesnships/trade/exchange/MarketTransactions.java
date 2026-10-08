package com.richardsenger.piratesnships.trade.exchange;

import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongUnaryOperator;

/**
 * Market trades with real coins and items on top of {@link TradeService}, each all or nothing: everything is checked
 * first, then the market moves, then coins and goods. Goods come from or go to the player's main inventory or a
 * {@link CargoContainerBlockEntity} (the caller checks the container is near the player).
 *
 * <pre>{@code
 * TransactionResult r = MarketTransactions.buy(player, port, TradeGoods.SUGAR, 64, Holder.of(player));
 * TransactionResult s = MarketTransactions.sell(player, port, TradeGoods.SUGAR, 64, true, Holder.of(crate));
 * if (s.noticedPlunder()) LawService.reportCrime(player, ...);   // the law integration decides
 * MarketTransactions.acceptContract(player, contractId);
 * MarketTransactions.deliverContract(player, port, contractId, Holder.of(player));
 * }</pre>
 */
public final class MarketTransactions {

    private MarketTransactions() {
    }

    /** Where goods come from or go to. */
    public sealed interface Holder permits PlayerHolder, ContainerHolder {
        /** Units of {@code item} with the given plunder state. */
        int count(Item item, boolean plundered);

        int room(Item item);

        void remove(Item item, boolean plundered, int n);

        void add(Item item, int n);

        static Holder of(ServerPlayer player) {
            return new PlayerHolder(player.getInventory());
        }

        static Holder of(CargoContainerBlockEntity container) {
            return new ContainerHolder(container);
        }
    }

    record PlayerHolder(Inventory inv) implements Holder {
        private static boolean matches(ItemStack s, Item item, boolean plundered) {
            return !s.isEmpty() && s.is(item) && PlunderMark.isPlundered(s) == plundered;
        }

        @Override
        public int count(Item item, boolean plundered) {
            int n = 0;
            for (ItemStack s : inv.items) if (matches(s, item, plundered)) n += s.getCount();
            return n;
        }

        @Override
        public int room(Item item) {
            return TradePlan.roomFor(inv.items, new ItemStack(item));
        }

        @Override
        public void remove(Item item, boolean plundered, int n) {
            int left = n;
            for (int i = 0; i < inv.items.size() && left > 0; i++) {
                ItemStack s = inv.items.get(i);
                if (!matches(s, item, plundered)) continue;
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
            inv.setChanged();
        }

        @Override
        public void add(Item item, int n) {
            int max = new ItemStack(item).getMaxStackSize();
            for (int left = n; left > 0; ) {
                int c = Math.min(max, left);
                inv.placeItemBackInInventory(new ItemStack(item, c));
                left -= c;
            }
        }
    }

    record ContainerHolder(CargoContainerBlockEntity be) implements Holder {
        @Override
        public int count(Item item, boolean plundered) {
            ItemStack k = be.heldKind();
            return !k.isEmpty() && k.is(item) && PlunderMark.isPlundered(k) == plundered ? be.count() : 0;
        }

        @Override
        public int room(Item item) {
            return be.room(new ItemStack(item));
        }

        @Override
        public void remove(Item item, boolean plundered, int n) {
            be.extract(n);
        }

        @Override
        public void add(Item item, int n) {
            be.insertAll(new ItemStack(item), n);
        }
    }

    private static Optional<Item> itemOf(ResourceLocation good) {
        return TradeService.goods(false).tradeable().get(good).map(TradeGood::item)
                .map(BuiltInRegistries.ITEM::get).filter(i -> i != Items.AIR);
    }

    /** Buys {@code quantity} clean units into {@code to}, paid from the player's wallet. */
    public static TransactionResult buy(ServerPlayer player, ResourceLocation port, ResourceLocation good, int quantity, Holder to) {
        return buy(player, port, good, quantity, to, LongUnaryOperator.identity());
    }

    /**
     * Buys like {@link #buy(ServerPlayer, ResourceLocation, ResourceLocation, int, Holder)}, but the player pays
     * {@code price} of the market total (REP1: the reputation price swing); the market moves as for the plain total.
     */
    public static TransactionResult buy(ServerPlayer player, ResourceLocation port, ResourceLocation good, int quantity, Holder to,
                                        LongUnaryOperator price) {
        MinecraftServer server = player.server;
        if (TradeService.market(server, port).isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_MARKET, good);
        Optional<Item> item = itemOf(good);
        if (item.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, good);
        Market.Quote q = TradeService.quote(server, port, good, Market.Side.BUY, quantity);
        TransactionResult.Status s = TradePlan.buy(q.outcome(), Wallet.count(player), price.applyAsLong(q.total()), to.room(item.get()), quantity);
        if (s != TransactionResult.Status.OK) return TransactionResult.failed(s, good);
        Market before = TradeData.get(server).market(port).orElseThrow();
        Market.Quote bought = TradeService.buy(server, port, good, quantity);
        long paid = bought.ok() ? price.applyAsLong(bought.total()) : 0;
        if (!bought.ok() || !Wallet.take(player, paid)) {
            TradeData.get(server).setMarket(port, before); // roll back the price move
            return TransactionResult.failed(bought.ok() ? TransactionResult.Status.NOT_ENOUGH_COINS : TradePlan.ofQuote(bought.outcome()), good);
        }
        to.add(item.get(), quantity);
        return new TransactionResult(TransactionResult.Status.OK, good, quantity, paid, PlunderRules.Outcome.NORMAL, false, Optional.empty());
    }

    /**
     * Sells {@code quantity} units with plunder state {@code plundered} from {@code from}. Plundered goods are judged by
     * the port kind ({@link TradeService#sell}): fenced at a discount, unnoticed, noticed and sold, or confiscated.
     */
    public static TransactionResult sell(ServerPlayer player, ResourceLocation port, ResourceLocation good, int quantity,
                                         boolean plundered, Holder from) {
        return sell(player, port, good, quantity, plundered, from, LongUnaryOperator.identity());
    }

    /** Sells like the plain {@code sell}, but the player receives {@code price} of the payout (REP1: the price swing). */
    public static TransactionResult sell(ServerPlayer player, ResourceLocation port, ResourceLocation good, int quantity,
                                         boolean plundered, Holder from, LongUnaryOperator price) {
        MinecraftServer server = player.server;
        if (TradeService.market(server, port).isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_MARKET, good);
        Optional<Item> item = itemOf(good);
        if (item.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, good);
        Market.Quote q = TradeService.quote(server, port, good, Market.Side.SELL, quantity);
        if (!q.ok()) return TransactionResult.failed(TradePlan.ofQuote(q.outcome()), good);
        TransactionResult.Status s = TradePlan.sell(from.count(item.get(), plundered), quantity);
        if (s != TransactionResult.Status.OK) return TransactionResult.failed(s, good);
        TradeService.Sale sale = TradeService.sell(server, port, good, quantity, plundered, player.getRandom());
        if (!sale.unitsTaken()) return TransactionResult.failed(TradePlan.ofQuote(sale.quote().outcome()), good);
        from.remove(item.get(), plundered, quantity);
        long payout = price.applyAsLong(sale.payout());
        Wallet.give(player, payout);
        TransactionResult.Status status = sale.verdict().sold() ? TransactionResult.Status.OK : TransactionResult.Status.CONFISCATED;
        return new TransactionResult(status, good, quantity, payout, sale.verdict().outcome(), sale.verdict().noticed(), Optional.empty());
    }

    /** Accepts a contract offer and takes its deposit (nothing changes without the coins). */
    public static TransactionResult acceptContract(ServerPlayer player, UUID contractId) {
        MinecraftServer server = player.server;
        Optional<DeliveryContract> c = TradeService.contract(server, contractId);
        if (c.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_CONTRACT, net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"));
        ResourceLocation good = c.get().good();
        if (c.get().state() == DeliveryContract.State.OFFERED && !Wallet.has(player, c.get().deposit())) {
            return TransactionResult.failed(TransactionResult.Status.NOT_ENOUGH_COINS, good);
        }
        DeliveryContract.ContractResult r = TradeService.accept(server, contractId, player.getUUID()).orElseThrow();
        if (r.outcome() != DeliveryContract.Outcome.ACCEPTED) {
            return TransactionResult.contract(TransactionResult.Status.CONTRACT_REFUSED, good, 0, 0, r.outcome());
        }
        if (!Wallet.take(player, r.depositDue())) {
            TradeData.get(server).putContract(c.get()); // roll back to the offer
            return TransactionResult.failed(TransactionResult.Status.NOT_ENOUGH_COINS, good);
        }
        return TransactionResult.contract(TransactionResult.Status.OK, good, 0, r.depositDue(), r.outcome());
    }

    /** Delivers an accepted contract at {@code port} from {@code from} (clean goods only) and pays reward + deposit. */
    public static TransactionResult deliverContract(ServerPlayer player, ResourceLocation port, UUID contractId, Holder from) {
        MinecraftServer server = player.server;
        Optional<DeliveryContract> c = TradeService.contract(server, contractId);
        if (c.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_CONTRACT, ResourceLocation.withDefaultNamespace("air"));
        ResourceLocation good = c.get().good();
        Optional<Item> item = itemOf(good);
        if (item.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NOT_TRADED, good);
        int have = from.count(item.get(), false);
        DeliveryContract.ContractResult r = TradeService.deliver(server, contractId, player.getUUID(), port, have).orElseThrow();
        if (r.outcome() != DeliveryContract.Outcome.DELIVERED) {
            TransactionResult.Status st = r.outcome() == DeliveryContract.Outcome.NOT_ENOUGH
                    ? TransactionResult.Status.NOT_ENOUGH_GOODS : TransactionResult.Status.CONTRACT_REFUSED;
            return TransactionResult.contract(st, good, 0, 0, r.outcome());
        }
        from.remove(item.get(), false, r.consumed());
        Wallet.give(player, r.payout());
        return TransactionResult.contract(TransactionResult.Status.OK, good, r.consumed(), r.payout(), r.outcome());
    }
}

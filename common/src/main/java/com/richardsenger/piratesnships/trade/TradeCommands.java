package com.richardsenger.piratesnships.trade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlock;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.cargo.CargoWeighing;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.MarketTransactions;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Operator debug commands (permission 2) to try trading before ports exist. Test ports are
 * {@code pirates_n_ships:debug/<name>}; "container" means the cargo container the player looks at.
 * <pre>
 * /pirates trade port &lt;name&gt; &lt;kind&gt; &lt;climate&gt;      create (if new) and open a test port
 * /pirates trade open &lt;name&gt;                          open it again (market session for the protocol)
 * /pirates trade goods &lt;name&gt; [quantity]              goods with buy and sell totals
 * /pirates trade buy &lt;name&gt; &lt;good&gt; &lt;qty&gt; [container]
 * /pirates trade sell &lt;name&gt; &lt;good&gt; &lt;qty&gt; [plundered|container]
 * /pirates trade contracts &lt;from&gt; &lt;to&gt;               today's offers from one test port to another
 * /pirates trade contract list | accept &lt;id&gt; | deliver &lt;port&gt; &lt;id&gt; [container]
 * /pirates trade weight | plunder | coins
 * </pre>
 */
public final class TradeCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".trade.";
    private static final SimpleCommandExceptionType NO_PORT = new SimpleCommandExceptionType(Component.translatable(KEY + "no_port"));
    private static final SimpleCommandExceptionType NO_CONTAINER = new SimpleCommandExceptionType(Component.translatable(KEY + "no_container"));
    private static final SimpleCommandExceptionType EMPTY_HAND = new SimpleCommandExceptionType(Component.translatable(KEY + "empty_hand"));
    private static final SimpleCommandExceptionType BAD_ENUM = new SimpleCommandExceptionType(Component.translatable(KEY + "bad_kind"));

    private TradeCommands() {
    }

    public static ResourceLocation portId(String name) {
        return Constants.id("debug/" + name.toLowerCase(Locale.ROOT));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("trade")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("port").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> suggest(PortKind.values(), b))
                                .then(Commands.argument("climate", StringArgumentType.word()).suggests((c, b) -> suggest(Climate.values(), b))
                                        .executes(TradeCommands::createPort)))))
                .then(Commands.literal("open").then(name().executes(c -> openPort(c, port(c)))))
                .then(Commands.literal("goods").then(name().executes(c -> goods(c, 64))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1)).executes(c -> goods(c, IntegerArgumentType.getInteger(c, "quantity"))))))
                .then(Commands.literal("buy").then(name().then(good().then(qty()
                        .executes(c -> trade(c, true, false, false))
                        .then(Commands.literal("container").executes(c -> trade(c, true, false, true)))))))
                .then(Commands.literal("sell").then(name().then(good().then(qty()
                        .executes(c -> trade(c, false, false, false))
                        .then(Commands.literal("plundered").executes(c -> trade(c, false, true, false)))
                        .then(Commands.literal("container").executes(c -> trade(c, false, false, true)))))))
                .then(Commands.literal("contracts").then(Commands.argument("from", StringArgumentType.word())
                        .then(Commands.argument("to", StringArgumentType.word()).executes(TradeCommands::offers))))
                .then(Commands.literal("contract")
                        .then(Commands.literal("list").executes(TradeCommands::myContracts))
                        .then(Commands.literal("accept").then(Commands.argument("id", UuidArgument.uuid()).executes(TradeCommands::accept)))
                        .then(Commands.literal("deliver").then(name().then(Commands.argument("id", UuidArgument.uuid())
                                .executes(c -> deliver(c, false))
                                .then(Commands.literal("container").executes(c -> deliver(c, true)))))))
                .then(Commands.literal("weight").executes(TradeCommands::weight))
                .then(Commands.literal("plunder").executes(TradeCommands::plunder))
                .then(Commands.literal("coins").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.translatable(KEY + "coins", Wallet.count(p)), false);
                    return (int) Math.min(Integer.MAX_VALUE, Wallet.count(p));
                }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.word());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> good() {
        return Commands.argument("good", ResourceLocationArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(TradeService.goods(false).tradeableIds(), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> qty() {
        return Commands.argument("quantity", IntegerArgumentType.integer(1));
    }

    private static <E extends Enum<E>> java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            E[] values, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(Arrays.stream(values).map(v -> v.name().toLowerCase(Locale.ROOT)), b);
    }

    private static ResourceLocation port(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation id = portId(StringArgumentType.getString(c, "name"));
        if (TradeService.market(c.getSource().getServer(), id).isEmpty()) throw NO_PORT.create();
        return id;
    }

    private static int createPort(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        PortKind kind;
        Climate climate;
        try {
            kind = PortKind.valueOf(StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT));
            climate = Climate.valueOf(StringArgumentType.getString(c, "climate").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BAD_ENUM.create();
        }
        ResourceLocation id = portId(StringArgumentType.getString(c, "name"));
        var server = c.getSource().getServer();
        TradeService.openMarket(server, id, () -> TradeService.deriveProfile(server, kind, climate, id.hashCode()));
        return openPort(c, id);
    }

    private static int openPort(CommandContext<CommandSourceStack> c, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Market m = TradeService.market(player.server, id).orElseThrow(NO_PORT::create);
        MarketBackend.open(player, id, 64);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "port", id.toString(), m.profile().kind().getSerializedName(),
                m.profile().climate().getSerializedName()), false);
        return 1;
    }

    private static int goods(CommandContext<CommandSourceStack> c, int quantity) throws CommandSyntaxException {
        ResourceLocation id = port(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        MarketView view = MarketBackend.view(player, id, quantity).orElseThrow(NO_PORT::create);
        for (MarketView.GoodLine l : view.goods()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "good_line", l.good().getPath(), l.role().getSerializedName(),
                    quantity, price(l.buy()), price(l.sell())), false);
        }
        return view.goods().size();
    }

    private static String price(MarketView.Price p) {
        return p.outcome() == Market.Outcome.OK ? Long.toString(p.total()) : p.outcome().name().toLowerCase(Locale.ROOT) + " (" + p.available() + ")";
    }

    private static int trade(CommandContext<CommandSourceStack> c, boolean buy, boolean plundered, boolean useContainer) throws CommandSyntaxException {
        ResourceLocation id = port(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        ResourceLocation good = ResourceLocationArgument.getId(c, "good");
        int qty = IntegerArgumentType.getInteger(c, "quantity");
        MarketTransactions.Holder holder = MarketTransactions.Holder.of(player);
        if (useContainer) {
            CargoContainerBlockEntity be = lookedAt(player);
            holder = MarketTransactions.Holder.of(be);
            plundered = PlunderMark.isPlundered(be.heldKind());
        }
        TransactionResult r = buy ? MarketTransactions.buy(player, id, good, qty, holder)
                : MarketTransactions.sell(player, id, good, qty, plundered, holder);
        report(c, r);
        return r.done() ? 1 : 0;
    }

    private static void report(CommandContext<CommandSourceStack> c, TransactionResult r) {
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "result", Component.translatable(r.status().translationKey()),
                r.units(), r.coins(), r.plunder().name().toLowerCase(Locale.ROOT)), false);
        if (r.noticedPlunder()) c.getSource().sendSuccess(() -> Component.translatable(KEY + "noticed"), false);
    }

    private static int offers(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var server = c.getSource().getServer();
        ResourceLocation from = portId(StringArgumentType.getString(c, "from"));
        ResourceLocation to = portId(StringArgumentType.getString(c, "to"));
        Market toMarket = TradeService.market(server, to).orElseThrow(NO_PORT::create);
        if (TradeService.market(server, from).isEmpty()) throw NO_PORT.create();
        List<DeliveryContract> list = TradeService.offers(server, from,
                List.of(new ContractGenerator.Destination(to, toMarket.profile(), 1000.0, 0.5)));
        list.forEach(k -> line(c, k));
        return list.size();
    }

    private static void line(CommandContext<CommandSourceStack> c, DeliveryContract k) {
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "contract_line", k.id().toString(), k.quantity(), k.good().getPath(),
                k.destination().getPath(), k.deadlineDay(), k.reward(), k.deposit(), k.state().getSerializedName()), false);
    }

    private static int myContracts(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        List<DeliveryContract> list = TradeService.contractsOf(player.server, player.getUUID());
        list.forEach(k -> line(c, k));
        return list.size();
    }

    private static int accept(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        TransactionResult r = MarketTransactions.acceptContract(player, UuidArgument.getUuid(c, "id"));
        report(c, r);
        return r.done() ? 1 : 0;
    }

    private static int deliver(CommandContext<CommandSourceStack> c, boolean useContainer) throws CommandSyntaxException {
        ResourceLocation id = port(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        UUID contract = UuidArgument.getUuid(c, "id");
        MarketTransactions.Holder holder = useContainer ? MarketTransactions.Holder.of(lookedAt(player)) : MarketTransactions.Holder.of(player);
        TransactionResult r = MarketTransactions.deliverContract(player, id, contract, holder);
        report(c, r);
        return r.done() ? 1 : 0;
    }

    private static CargoContainerBlockEntity lookedAt(ServerPlayer player) throws CommandSyntaxException {
        BlockPos pos = lookedAtPos(player);
        if (player.level().getBlockEntity(pos) instanceof CargoContainerBlockEntity be) return be;
        throw NO_CONTAINER.create();
    }

    private static BlockPos lookedAtPos(ServerPlayer player) throws CommandSyntaxException {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1.0f).scale(TradeConfig.CONTAINER_REACH.get()));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) throw NO_CONTAINER.create();
        return hit.getBlockPos();
    }

    private static int weight(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos pos = lookedAtPos(player);
        double w = CargoWeighing.weigh(player.level(), List.of(pos));
        Component what = player.level().getBlockEntity(pos) instanceof CargoContainerBlockEntity be ? CargoContainerBlock.describe(be)
                : player.level().getBlockState(pos).getBlock().getName();
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "weight", what, String.format(Locale.ROOT, "%.2f", w)), false);
        return (int) Math.round(w);
    }

    private static int plunder(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) throw EMPTY_HAND.create();
        PlunderMark.mark(held);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "plundered", held.getHoverName()), false);
        return 1;
    }

    static void lang(LangBuilder lang) {
        lang.add(KEY + "no_port", "No such test port (create it with /pirates trade port)")
                .add(KEY + "no_container", "Look at a cargo container")
                .add(KEY + "empty_hand", "Hold the stack to mark")
                .add(KEY + "bad_kind", "Unknown port kind or climate")
                .add(KEY + "port", "Port %s (%s, %s) is open")
                .add(KEY + "good_line", "%s [%s]: buy %s for %s, sell for %s")
                .add(KEY + "result", "%s: %s units, %s doubloons (%s)")
                .add(KEY + "noticed", "The port noticed the plunder")
                .add(KEY + "contract_line", "%s: %s × %s to %s by day %s, reward %s, deposit %s (%s)")
                .add(KEY + "weight", "%s: cargo weight %s")
                .add(KEY + "plundered", "Marked %s as plundered")
                .add(KEY + "coins", "You carry %s doubloons");
        for (TransactionResult.Status s : TransactionResult.Status.values()) {
            lang.add(s.translationKey(), switch (s) {
                case OK -> "Done";
                case CONFISCATED -> "Confiscated";
                case NO_MARKET -> "No market here";
                case NOT_TRADED -> "Not traded here";
                case STOCK_LIMIT -> "Over the port's stock or demand";
                case INVALID_QUANTITY -> "Invalid quantity";
                case NOT_ENOUGH_COINS -> "Not enough doubloons";
                case NOT_ENOUGH_SPACE -> "Not enough space";
                case NOT_ENOUGH_GOODS -> "Not enough goods";
                case NO_CONTAINER -> "No cargo container in reach";
                case NO_CONTRACT -> "No such contract";
                case CONTRACT_REFUSED -> "Contract refused";
            });
        }
    }
}

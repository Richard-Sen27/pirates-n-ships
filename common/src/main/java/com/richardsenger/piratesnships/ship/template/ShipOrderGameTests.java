package com.richardsenger.piratesnships.ship.template;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Outcome;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.OrderPayloads;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shipwright orders (SW1) on a test harbor: a 40×40 basin with a pier along z in its middle and two berths beside
 * it, both pointing north; the port record is built directly and registered, the desk bound to it. A (mock) player
 * orders the basic sloop through the market backend, picks it up at berth 1 once an operator finished it, a second
 * ship goes to berth 2, and a third finds no free berth. The toggle and the order limit refuse. Registered through
 * {@link ShipTemplateGameTests#tests}.
 */
public final class ShipOrderGameTests {

    private static final String BATCH = "pirates_n_ships_ship_orders";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_ship_orders";
    private static final ResourceLocation BASIC = ShipTemplates.STARTER_SLOOP_BASIC_ID;
    /** Water y 2..7; the pier deck at y 8 over x 19..21; the berths at the surface beside it. */
    private static final int SURFACE = 7;
    private static final BlockPos BERTH_1 = new BlockPos(18, SURFACE, 20);
    private static final BlockPos BERTH_2 = new BlockPos(22, SURFACE, 20);
    private static final BlockPos DESK = new BlockPos(20, 9, 37);

    private ShipOrderGameTests() {
    }

    // ------------------------------------------------------------------ fixtures

    /** Basin (stone floor y 1, walls), water y 2..7, the pier deck, the barrier ceiling removed (masts reach above). */
    private static void harbor(GameTestHelper h) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                if (!wall && x >= 19 && x <= 21) h.setBlock(new BlockPos(x, 8, z), Blocks.SPRUCE_PLANKS);
                BlockPos ceiling = new BlockPos(x, 13, z);
                if (h.getBlockState(ceiling).is(Blocks.BARRIER)) h.setBlock(ceiling, Blocks.AIR);
            }
        }
    }

    /** A registered seafarer village over the test area with the two berths, and its desk on the pier. */
    private static Port port(GameTestHelper h) {
        BlockPos min = h.absolutePos(BlockPos.ZERO);
        BlockPos max = h.absolutePos(new BlockPos(39, 30, 39));
        BoundingBox box = BoundingBox.fromCorners(min, max);
        Port port = new Port(Constants.id("gametest/village_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.SEAFARER_VILLAGE,
                h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 8, 20)), box, Climate.TEMPERATE,
                List.of(new Berth(h.absolutePos(BERTH_1), Direction.NORTH), new Berth(h.absolutePos(BERTH_2), Direction.NORTH)));
        PortService.register(h.getLevel().getServer(), port);
        h.setBlock(DESK, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(DESK)).setPort(Optional.of(port.id()));
        return port;
    }

    private static ServerPlayer player(GameTestHelper h, long coins, int logs, int wool) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "order_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(DESK.north())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        give(p, Items.OAK_LOG, logs);
        give(p, Items.WHITE_WOOL, wool);
        MarketBackend.record(p.getUUID());
        return p;
    }

    private static void give(ServerPlayer p, Item item, int count) {
        while (count > 0) {
            int n = Math.min(count, 64);
            p.getInventory().placeItemBackInInventory(new ItemStack(item, n));
            count -= n;
        }
    }

    private static int countOf(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Opens the desk's market (the session the backend checks) by using the desk. */
    private static void openDesk(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(DESK);
        h.getBlockState(DESK).useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        h.assertTrue(MarketBackend.canUse(p, portOf(h, p)), "desk session open");
    }

    private static ResourceLocation portOf(GameTestHelper h, ServerPlayer p) {
        return ((HarborDeskBlockEntity) h.getBlockEntity(DESK)).port().orElseThrow();
    }

    private static OrderPayloads.Orders lastOrders(GameTestHelper h, ServerPlayer p) {
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        synchronized (sent) {
            List<OrderPayloads.Orders> orders = sent.stream().filter(OrderPayloads.Orders.class::isInstance).map(OrderPayloads.Orders.class::cast).toList();
            h.assertFalse(orders.isEmpty(), "an orders payload was sent");
            return orders.get(orders.size() - 1);
        }
    }

    /** Orders the basic sloop through the backend and returns the result. */
    private static OrderPayloads.OrderResult order(GameTestHelper h, ServerPlayer p, ResourceLocation port) {
        MarketBackend.handleOrder(p, new OrderPayloads.PlaceOrder(port, BASIC));
        return lastOrders(h, p).result().orElseThrow(() -> new AssertionError("no order result"));
    }

    /** Moves the newest receipt in the inventory into the main hand and returns it. */
    private static ItemStack receiptInHand(GameTestHelper h, ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ShipOrderContent.SHIP_RECEIPT.get())) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                p.setItemInHand(InteractionHand.MAIN_HAND, s);
                return s;
            }
        }
        throw new AssertionError("no receipt in the inventory");
    }

    private static List<ShipOrder> orders(GameTestHelper h, ResourceLocation port) {
        return PortRegistry.get(h.getLevel().getServer()).index().byId(port).orElseThrow().orders();
    }

    private static void finishByCommand(GameTestHelper h, ServerPlayer p, ShipOrder order) {
        try {
            int r = h.getLevel().getServer().getCommands().getDispatcher().execute("pirates ship orders finish " + order.shortId(),
                    p.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            h.assertTrue(r == 1, "orders finish returned " + r);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cleanup(GameTestHelper h, ServerPlayer p, Port port) {
        MarketBackend.close(p);
        MarketBackend.stopRecording(p.getUUID());
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
    }

    private static BlockPos expectedOrigin(GameTestHelper h, BlockPos berth, Direction side, ShipTemplatePlacer.Result r) {
        var size = ShipTemplatePlacer.structure(h.getLevel(), ShipTemplates.STARTER_SLOOP_BASIC).orElseThrow().getSize();
        return TemplatePlacement.berthOrigin(size, Rotation.NONE, Direction.NORTH, side, h.absolutePos(berth),
                ShipTemplatePlacer.BERTH_GAP, h.absolutePos(berth).getY(), ShipTemplates.STARTER_SLOOP_BASIC.waterlineFor(null));
    }

    // ------------------------------------------------------------------ tests

    /**
     * Order, refused early pickup, pickup at berth 1 after {@code orders finish}, a second ship at berth 2, and a third
     * order that finds both berths taken.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH, timeoutTicks = 400)
    public static void orderAndPickUpAtFreeBerths(GameTestHelper h) {
        harbor(h);
        Port port = port(h);
        ServerPlayer p = player(h, 1000, 128, 64);
        openDesk(h, p);
        OrderPayloads.OrdersView view = lastOrders(h, p).view().orElseThrow(() -> new AssertionError("no orders tab at a village desk"));
        OrderPayloads.OrderLine line = view.lines().stream().filter(l -> l.template().equals(BASIC)).findFirst()
                .orElseThrow(() -> new AssertionError("basic sloop not offered: " + view.lines()));
        h.assertValueEqual(line.price(), 300L, "price = template price × 1.0");
        h.assertValueEqual(line.logs(), 35, "logs for 682 blocks");
        h.assertValueEqual(line.wool(), 16, "wool for 16 yard blocks");
        h.assertTrue(Math.abs(line.buildDays() - 682 / 500.0) < 1e-9, "build days " + line.buildDays());

        // 1. order: coins and materials taken, an order and a receipt exist
        OrderPayloads.OrderResult r = order(h, p, port.id());
        h.assertTrue(r.done(), "order refused: " + r);
        h.assertValueEqual(Wallet.count(p), 700L, "coins after the order");
        h.assertValueEqual(countOf(p, Items.OAK_LOG), 128 - 35, "logs after the order");
        h.assertValueEqual(countOf(p, Items.WHITE_WOOL), 64 - 16, "wool after the order");
        h.assertValueEqual(orders(h, port.id()).size(), 1, "orders on the port");
        h.assertValueEqual(lastOrders(h, p).view().orElseThrow().mine().size(), 1, "the player's orders in the tab");
        ItemStack receipt = receiptInHand(h, p);
        ShipReceipt data = ShipReceiptItem.receipt(receipt).orElseThrow();
        ShipOrder first = orders(h, port.id()).get(0);
        h.assertValueEqual(data.order(), first.id(), "receipt names the order");
        h.assertValueEqual(data.port(), port.id(), "receipt names the port");

        // 2. pickup before the finish day refuses and keeps the receipt
        BlockPos abs = h.absolutePos(DESK);
        ItemInteractionResult early = h.getBlockState(DESK).useItemOn(receipt, h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        h.assertValueEqual(early, ItemInteractionResult.CONSUME, "desk took the receipt use");
        h.assertTrue(p.getMainHandItem().is(ShipOrderContent.SHIP_RECEIPT.get()), "receipt kept before the finish day");
        h.assertValueEqual(orders(h, port.id()).size(), 1, "order kept before the finish day");
        h.assertValueEqual(ShipOrders.pickup(p, abs, p.getMainHandItem()).orElseThrow().outcome(), ShipOrders.Pickup.NOT_READY, "early pickup");

        // 3. an operator finishes it; the pickup places the assembled ship at berth 1, bow north, the player owns it
        finishByCommand(h, p, first);
        ShipOrders.PickupResult picked = ShipOrders.pickup(p, abs, p.getMainHandItem()).orElseThrow();
        if (picked.ship() != null) ShipTestCleanup.track(h, picked.ship());
        h.assertValueEqual(picked.outcome(), ShipOrders.Pickup.PICKED_UP, "pickup: " + picked.message().getString());
        h.assertValueEqual(picked.berth(), 1, "first ship at berth 1");
        h.assertTrue(picked.ship() != null, "ship assembled: " + picked.message().getString());
        h.assertValueEqual(picked.placement().rotation(), Rotation.NONE, "bow along the berth (north)");
        h.assertValueEqual(picked.placement().origin(), expectedOrigin(h, BERTH_1, Direction.WEST, picked.placement()),
                "moored west of berth 1 (away from the pier)");
        Optional<ShipData> ship = ShipRegistry.get(h.getLevel().getServer()).find(picked.ship());
        h.assertTrue(ship.isPresent() && ship.get().owner().equals(Optional.of(p.getUUID())), "player owns the ship: " + ship);
        h.assertTrue(p.getMainHandItem().isEmpty(), "receipt consumed");
        h.assertTrue(orders(h, port.id()).isEmpty(), "order record removed");
        h.assertTrue(SableShips.byId(h.getLevel(), picked.ship()) != null, "ship body exists");

        // 4. a second order goes to berth 2
        h.assertTrue(order(h, p, port.id()).done(), "second order");
        receiptInHand(h, p);
        finishByCommand(h, p, orders(h, port.id()).get(0));
        ShipOrders.PickupResult second = ShipOrders.pickup(p, abs, p.getMainHandItem()).orElseThrow();
        if (second.ship() != null) ShipTestCleanup.track(h, second.ship());
        h.assertValueEqual(second.outcome(), ShipOrders.Pickup.PICKED_UP, "second pickup: " + second.message().getString());
        var body1 = SableShips.byId(h.getLevel(), picked.ship());
        h.assertValueEqual(second.berth(), 2, "berth 1 is taken (first ship " + body1.worldBounds() + ", plot "
                + java.util.Arrays.toString(body1.plotBounds()) + ", bounds " + ShipOrders.bounds(body1) + ", placed at "
                + picked.placement().origin() + "), so berth 2");
        h.assertValueEqual(second.placement().origin(), expectedOrigin(h, BERTH_2, Direction.EAST, second.placement()),
                "moored east of berth 2 (away from the pier)");

        // 5. both berths taken: the third pickup refuses and keeps the receipt and the order
        h.assertTrue(order(h, p, port.id()).done(), "third order");
        receiptInHand(h, p);
        finishByCommand(h, p, orders(h, port.id()).get(0));
        ShipOrders.PickupResult third = ShipOrders.pickup(p, abs, p.getMainHandItem()).orElseThrow();
        if (third.ship() != null) ShipTestCleanup.track(h, third.ship());
        h.assertValueEqual(third.outcome(), ShipOrders.Pickup.NO_BERTH, "third pickup: " + third.message().getString());
        h.assertTrue(p.getMainHandItem().is(ShipOrderContent.SHIP_RECEIPT.get()), "receipt kept without a free berth");
        h.assertValueEqual(orders(h, port.id()).size(), 1, "order kept without a free berth");

        cleanup(h, p, port);
        h.succeed();
    }

    /** {@code max_orders_per_port} (3) refuses a fourth order and takes nothing for it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH)
    public static void orderLimitRefusesAFourthOrder(GameTestHelper h) {
        harbor(h);
        Port port = port(h);
        ServerPlayer p = player(h, 2000, 192, 64);
        openDesk(h, p);
        for (int i = 0; i < 3; i++) {
            OrderPayloads.OrderResult r = order(h, p, port.id());
            h.assertTrue(r.done(), "order " + (i + 1) + " refused: " + r);
        }
        long coins = Wallet.count(p);
        OrderPayloads.OrderResult fourth = order(h, p, port.id());
        h.assertFalse(fourth.done(), "fourth order taken");
        h.assertValueEqual(fourth.key(), ShipOrders.KEY_TOO_MANY, "refusal");
        h.assertValueEqual(Wallet.count(p), coins, "no coins taken for the refused order");
        h.assertValueEqual(orders(h, port.id()).size(), 3, "three orders");
        h.assertValueEqual(countOf(p, ShipOrderContent.SHIP_RECEIPT.get()), 3, "three receipts");
        // a receipt for a port that is gone: the order was lost, the receipt stays
        ItemStack receipt = receiptInHand(h, p);
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        ShipOrders.PickupResult lost = ShipOrders.pickup(p, h.absolutePos(DESK), receipt).orElseThrow();
        h.assertValueEqual(lost.outcome(), ShipOrders.Pickup.LOST, "pickup without the port");
        h.assertTrue(p.getMainHandItem().is(ShipOrderContent.SHIP_RECEIPT.get()), "lost receipt kept");
        cleanup(h, p, port);
        h.succeed();
    }

    /** Without a village port (a navy outpost's desk) there is no Orders tab and ordering refuses. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void otherPortsTakeNoOrders(GameTestHelper h) {
        Port navy = new Port(Constants.id("gametest/navy_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.NAVY_OUTPOST,
                h.getLevel().dimension(), h.absolutePos(BlockPos.ZERO), BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO),
                h.absolutePos(new BlockPos(8, 8, 8))), Climate.TEMPERATE, List.of());
        PortService.register(h.getLevel().getServer(), navy);
        BlockPos desk = new BlockPos(4, 1, 4);
        h.setBlock(desk, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(desk)).setPort(Optional.of(navy.id()));
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "order_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        p.setPos(Vec3.atCenterOf(h.absolutePos(desk.north())));
        h.assertTrue(ShipOrders.view(p, navy.id()).isEmpty(), "no orders tab at a navy outpost");
        h.assertValueEqual(ShipOrders.place(p, navy.id(), BASIC).key(), ShipOrders.KEY_NOT_VILLAGE, "order at a navy outpost");
        PortRegistry.get(h.getLevel().getServer()).remove(navy.id());
        h.succeed();
    }

    /** {@code ships.shipwright_orders = false} refuses ordering and takes nothing; the tab says so. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = CONFIG_BATCH)
    public static void disabledOrdersRefuse(GameTestHelper h) {
        ConfigOverrides.during(h, ShipConfig.SHIPWRIGHT_ORDERS, false);
        harbor(h);
        Port port = port(h);
        ServerPlayer p = player(h, 1000, 64, 64);
        openDesk(h, p);
        OrderPayloads.OrdersView view = lastOrders(h, p).view().orElseThrow(() -> new AssertionError("no orders tab"));
        h.assertFalse(view.enabled(), "tab shows the shipwright closed");
        h.assertTrue(view.lines().isEmpty(), "no ships offered when disabled");
        OrderPayloads.OrderResult r = order(h, p, port.id());
        h.assertFalse(r.done(), "order taken while disabled");
        h.assertValueEqual(r.key(), ShipOrders.KEY_DISABLED, "refusal");
        h.assertValueEqual(Wallet.count(p), 1000L, "coins kept");
        h.assertValueEqual(countOf(p, Items.OAK_LOG), 64, "logs kept");
        h.assertTrue(orders(h, port.id()).isEmpty(), "no order recorded");
        h.assertValueEqual(countOf(p, ShipOrderContent.SHIP_RECEIPT.get()), 0, "no receipt");
        cleanup(h, p, port);
        h.succeed();
    }
}

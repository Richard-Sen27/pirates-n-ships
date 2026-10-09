package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.career.CareerRewards;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.LocalBlock;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Outcome;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Result;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.OrderPayloads;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Shipwright orders (design.md §4.1, SW1), server side: the Orders tab of a seafarer village's harbor master's desk
 * (and of a navy outpost's desk for navy captains, at their rank's price: CAR2, {@link #orderPort})
 * lists the ship templates with price, build time and materials ({@link ShipOrderMath}); {@link #place} takes the
 * coins and materials, records a {@link ShipOrder} on the port and hands out a {@link ShipReceiptItem};
 * {@link #pickup} (the desk used with the receipt) places the template, assembled, at the first free berth of the
 * port with the player as owner. Server thread only.
 */
public final class ShipOrders {

    static final String KEY = "message." + Constants.MOD_ID + ".ship_order.";
    public static final String KEY_ORDERED = KEY + "ordered";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_NOT_VILLAGE = KEY + "not_village";
    public static final String KEY_NO_SESSION = KEY + "no_session";
    public static final String KEY_UNKNOWN_TEMPLATE = KEY + "unknown_template";
    public static final String KEY_TOO_MANY = KEY + "too_many";
    public static final String KEY_NO_COINS = KEY + "no_coins";
    public static final String KEY_NO_LOGS = KEY + "no_logs";
    public static final String KEY_NO_WOOL = KEY + "no_wool";
    public static final String KEY_PICKED_UP = KEY + "picked_up";
    public static final String KEY_NOT_ASSEMBLED = KEY + "not_assembled";
    public static final String KEY_NOT_READY = KEY + "not_ready";
    public static final String KEY_NO_BERTH = KEY + "no_berth";
    public static final String KEY_LOST = KEY + "lost";
    public static final String KEY_WRONG_PORT = KEY + "wrong_port";

    public static final TagKey<Item> LOGS = ItemTags.LOGS;
    public static final TagKey<Item> WOOL = ItemTags.WOOL;

    /** A template's size: all blocks and its sail yard blocks. */
    public record Stats(int blocks, int yards) {
    }

    /** What the shipwright asks for a template right now (config applied). */
    public record Quote(ResourceLocation id, ShipTemplate template, long price, double buildDays, int logs, int wool) {
        public OrderPayloads.OrderLine line() {
            return new OrderPayloads.OrderLine(id, template.name(), price, buildDays, logs, wool);
        }
    }

    public enum Pickup { PICKED_UP, NOT_READY, NO_BERTH, LOST, WRONG_PORT }

    /** What a pickup did: {@code ship} is the new ship (null unless picked up and assembled), {@code berth} 1-based. */
    public record PickupResult(Pickup outcome, Component message, @Nullable UUID ship, int berth, @Nullable Result placement) {
    }

    private ShipOrders() {
    }

    // ------------------------------------------------------------------ numbers

    /** Today's world day (fractional), from the overworld's day time. */
    public static double today(MinecraftServer server) {
        return ShipOrderMath.day(server.overworld().getDayTime());
    }

    public static Optional<Stats> stats(ServerLevel level, ShipTemplate template) {
        Optional<StructureTemplate> structure = ShipTemplatePlacer.structure(level, template);
        if (structure.isEmpty() || structure.get().getSize().getX() < 1) return Optional.empty();
        List<LocalBlock> blocks = ShipTemplatePlacer.blocks(structure.get(), level.holderLookup(Registries.BLOCK));
        int yards = (int) blocks.stream().filter(b -> b.state().is(SailingBlocks.YARD.get())).count();
        return Optional.of(new Stats(blocks.size(), yards));
    }

    public static Optional<Quote> quote(ServerLevel level, ResourceLocation id, ShipTemplate template) {
        return quote(level, id, template, 1.0);
    }

    /** {@link #quote} with the price also multiplied by {@code priceFactor} (a navy officer's discount, CAR2). */
    public static Optional<Quote> quote(ServerLevel level, ResourceLocation id, ShipTemplate template, double priceFactor) {
        if (!template.orderable()) return Optional.empty(); // an NPC-only ship (WS4c's armed sloops)
        return stats(level, template).map(s -> new Quote(id, template,
                ShipOrderMath.price(template.price(), ShipConfig.ORDER_PRICE_FACTOR.get() * priceFactor),
                ShipOrderMath.buildDays(ShipConfig.buildDays(id.getPath()), s.blocks()),
                ShipOrderMath.logs(s.blocks(), ShipConfig.ORDER_MATERIALS_FACTOR.get()),
                ShipOrderMath.wool(s.yards(), ShipConfig.ORDER_MATERIALS_FACTOR.get())));
    }

    /** Every template the shipwright builds (its structure exists), by id. */
    public static List<Quote> quotes(ServerLevel level) {
        return quotes(level, 1.0);
    }

    /** {@link #quotes} with every price also multiplied by {@code priceFactor}. */
    public static List<Quote> quotes(ServerLevel level, double priceFactor) {
        List<Quote> out = new ArrayList<>();
        Map<ResourceLocation, ShipTemplate> all = ShipTemplates.TYPE.server().all();
        all.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> quote(level, id, all.get(id), priceFactor).ifPresent(out::add));
        return out;
    }

    /** The registered port {@code id} if it is a seafarer village (the only ports with a shipwright). */
    public static Optional<Port> villagePort(MinecraftServer server, ResourceLocation id) {
        return PortRegistry.get(server).index().byId(id).filter(p -> p.kind() == PortKind.SEAFARER_VILLAGE);
    }

    /**
     * The registered port {@code id} if its shipwright builds for {@code player}: a seafarer village, or a navy outpost
     * for a navy officer of {@code careers.rewards.navy_orders_min_rank} and up (the navy shipyard, CAR2).
     */
    public static Optional<Port> orderPort(ServerPlayer player, ResourceLocation id) {
        return PortRegistry.get(player.server).index().byId(id).filter(p -> p.kind() == PortKind.SEAFARER_VILLAGE
                || p.kind() == PortKind.NAVY_OUTPOST && CareerRewards.navyShipyard(player));
    }

    /** The price factor {@code player} gets at {@code port}: the navy officer's rank discount at an outpost (CAR2), else 1. */
    public static double priceFactor(ServerPlayer player, Port port) {
        return port.kind() == PortKind.NAVY_OUTPOST ? CareerRewards.shipPriceFactor(player) : 1.0;
    }

    // ------------------------------------------------------------------ the Orders tab

    /** The Orders tab of {@code port} for {@code player}; empty unless {@link #orderPort} builds for the player there. */
    public static Optional<OrderPayloads.OrdersView> view(ServerPlayer player, ResourceLocation port) {
        Optional<Port> p = orderPort(player, port);
        if (p.isEmpty()) return Optional.empty();
        boolean enabled = ShipConfig.SHIPWRIGHT_ORDERS.get();
        double factor = priceFactor(player, p.get());
        List<OrderPayloads.OrderLine> lines = enabled ? quotes(player.serverLevel(), factor).stream().map(Quote::line).toList() : List.of();
        List<OrderPayloads.MyOrder> mine = new ArrayList<>();
        for (ShipOrder o : p.get().orders()) {
            if (o.owner().equals(player.getUUID())) mine.add(new OrderPayloads.MyOrder(nameOf(o.template()), o.finishDay()));
        }
        return Optional.of(new OrderPayloads.OrdersView(port, enabled, p.get().orders().size(), ShipConfig.MAX_ORDERS_PER_PORT.get(),
                lines, mine));
    }

    /** The translation key of template {@code id}'s name (the id itself if the template is gone). */
    public static String nameOf(ResourceLocation id) {
        return ShipTemplates.TYPE.server().get(id).map(ShipTemplate::name).orElse(id.toString());
    }

    /**
     * Orders a ship of {@code templateId} from {@code portId}'s shipwright for {@code player}. The desk session
     * (reach, binding) is the caller's check ({@code MarketBackend.handleOrder}); this checks the toggle, the port's
     * kind, the template, the order limit, the coins and the materials, then takes them (all or nothing), records
     * the order and gives the receipt.
     */
    public static OrderPayloads.OrderResult place(ServerPlayer player, ResourceLocation portId, ResourceLocation templateId) {
        if (!ShipConfig.SHIPWRIGHT_ORDERS.get()) return refused(KEY_DISABLED);
        MinecraftServer server = player.server;
        Optional<Port> port = orderPort(player, portId);
        if (port.isEmpty()) return refused(KEY_NOT_VILLAGE);
        Optional<ShipTemplate> template = ShipTemplates.TYPE.server().get(templateId);
        double factor = priceFactor(player, port.get());
        Optional<Quote> quote = template.flatMap(t -> quote(player.serverLevel(), templateId, t, factor));
        if (quote.isEmpty()) return refused(KEY_UNKNOWN_TEMPLATE, templateId.toString());
        int max = ShipConfig.MAX_ORDERS_PER_PORT.get();
        if (!ShipOrderMath.takesOrder(port.get().orders().size(), max)) return refused(KEY_TOO_MANY, Integer.toString(max));
        Quote q = quote.get();
        if (!Wallet.has(player, q.price())) return refused(KEY_NO_COINS, Long.toString(q.price()));
        if (count(player.getInventory(), LOGS) < q.logs()) return refused(KEY_NO_LOGS, Integer.toString(q.logs()));
        if (count(player.getInventory(), WOOL) < q.wool()) return refused(KEY_NO_WOOL, Integer.toString(q.wool()));

        if (!Wallet.take(player, q.price())) return refused(KEY_NO_COINS, Long.toString(q.price()));
        take(player.getInventory(), LOGS, q.logs());
        take(player.getInventory(), WOOL, q.wool());
        player.containerMenu.broadcastChanges();

        double today = today(server);
        ShipOrder order = new ShipOrder(UUID.randomUUID(), templateId, player.getUUID(), today, ShipOrderMath.finishDay(today, q.buildDays()));
        List<ShipOrder> orders = new ArrayList<>(port.get().orders());
        orders.add(order);
        PortRegistry.get(server).update(port.get().withOrders(orders));
        player.getInventory().placeItemBackInInventory(ShipReceiptItem.stack(ShipReceipt.of(portId, order, q.template().name())));
        player.containerMenu.broadcastChanges();
        Constants.LOG.debug("Ship order {} of {} at {} for {}, ready on day {}", order.shortId(), templateId, portId,
                player.getGameProfile().getName(), order.finishDay());
        return new OrderPayloads.OrderResult(true, KEY_ORDERED, List.of(q.template().name(), ShipOrderMath.formatDays(q.buildDays())));
    }

    private static OrderPayloads.OrderResult refused(String key, String... args) {
        return new OrderPayloads.OrderResult(false, key, List.of(args));
    }

    /** Items of {@code tag} in a container. */
    public static int count(Container container, TagKey<Item> tag) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.is(tag)) n += s.getCount();
        }
        return n;
    }

    /** Removes up to {@code amount} items of {@code tag} (check {@link #count} first). */
    static void take(Container container, TagKey<Item> tag, int amount) {
        for (int i = 0; i < container.getContainerSize() && amount > 0; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || !s.is(tag)) continue;
            int n = Math.min(amount, s.getCount());
            container.removeItem(i, n);
            amount -= n;
        }
        container.setChanged();
    }

    // ------------------------------------------------------------------ pickup

    /**
     * {@code player} uses the harbor master's desk at {@code desk} with {@code stack} (a receipt). Picks the ship up
     * when the desk belongs to the receipt's port, the order still exists and its finish day has passed: places the
     * template, assembled, at the first free berth (no ship's bounds over the berth or the ship's footprint there,
     * nothing solid in the way), the player becomes the owner, the order record goes and the receipt is consumed.
     * Otherwise the receipt stays and the result says why. Returns empty if {@code stack} is no receipt.
     */
    public static Optional<PickupResult> pickup(ServerPlayer player, BlockPos desk, ItemStack stack) {
        Optional<ShipReceipt> receipt = ShipReceiptItem.receipt(stack);
        if (receipt.isEmpty()) return Optional.empty();
        ShipReceipt r = receipt.get();
        MinecraftServer server = player.server;
        Optional<ResourceLocation> deskPort = HarborDeskService.boundPort(player.level(), desk);
        if (deskPort.isEmpty() || !deskPort.get().equals(r.port())) {
            return Optional.of(result(Pickup.WRONG_PORT, Component.translatable(KEY_WRONG_PORT, MarketLines.portName(r.port()))));
        }
        Optional<Port> port = PortRegistry.get(server).index().byId(r.port());
        Optional<ShipOrder> order = port.flatMap(p -> p.orders().stream().filter(o -> o.id().equals(r.order())).findFirst());
        ServerLevel level = port.map(p -> server.getLevel(p.dimension())).orElse(null);
        if (order.isEmpty() || level == null) {
            return Optional.of(result(Pickup.LOST, Component.translatable(KEY_LOST)));
        }
        if (r.finishDay() != order.get().finishDay()) {
            // the record changed (an operator finished it): the receipt's tooltip follows
            stack.set(ShipOrderContent.RECEIPT_DATA.get(), r.withFinishDay(order.get().finishDay()));
        }
        double today = today(server);
        if (!ShipOrderMath.ready(order.get().finishDay(), today)) {
            return Optional.of(result(Pickup.NOT_READY, Component.translatable(KEY_NOT_READY,
                    ShipOrderMath.formatDays(ShipOrderMath.remaining(order.get().finishDay(), today)))));
        }
        Optional<Berthed> berthed = placeAtFreeBerth(level, port.get(), order.get().template(), player, "Ship order " + order.get().shortId());
        if (berthed.isEmpty()) return Optional.of(result(Pickup.NO_BERTH, Component.translatable(KEY_NO_BERTH)));
        Result placed = berthed.get().placed();
        int berth = berthed.get().berth();
        removeOrder(server, port.get().id(), order.get().id());
        stack.shrink(1);
        player.containerMenu.broadcastChanges();
        AssemblyResult a = placed.assembly();
        Component name = Component.translatable(placed.template().name());
        Component message = Component.translatable(KEY_PICKED_UP, name, berth);
        if (a == null || !a.success()) {
            message = Component.translatable(KEY_NOT_ASSEMBLED, name, berth);
            Constants.LOG.warn("Ship order {}: placed at berth {} of {} but not assembled: {}", order.get().shortId(), berth,
                    port.get().id(), a == null ? null : a.outcome());
        }
        return Optional.of(new PickupResult(Pickup.PICKED_UP, message, a == null ? null : a.shipId(), berth, placed));
    }

    /** A template placed at a berth: the placement (outcome {@link Outcome#PLACED}) and the berth, 1-based. */
    public record Berthed(Result placed, int berth) {
    }

    /**
     * Places template {@code template}, assembled with {@code owner} as owner, at the first free berth of {@code port}
     * (no ship's bounds over the berth or the ship's footprint there, nothing solid in the way): the shipwright's
     * pickup, also the delivery of a granted ship (SHP1). Empty when no berth is free; {@code what} names the
     * delivery in the log.
     */
    public static Optional<Berthed> placeAtFreeBerth(ServerLevel level, Port port, ResourceLocation template, ServerPlayer owner,
                                                     String what) {
        List<AABB> ships = SableShips.all(level).stream().filter(b -> !b.isRemoved()).map(ShipOrders::bounds).toList();
        List<Berth> berths = port.berths();
        for (int i = 0; i < berths.size(); i++) {
            Berth berth = berths.get(i);
            Result placed = ShipTemplatePlacer.placeAtBerth(level, template, berth.pos(), berth.bow(),
                    box -> ShipOrderMath.occupied(berth.pos(), box, ships), true, owner);
            if (placed.outcome() == Outcome.PLACED) return Optional.of(new Berthed(placed, i + 1));
            if (placed.outcome() != Outcome.OCCUPIED && placed.outcome() != Outcome.OBSTRUCTED) {
                Constants.LOG.warn("{}: berth {} of {} refused: {} at {}", what, i + 1, port.id(), placed.outcome(), placed.where());
            }
        }
        return Optional.empty();
    }

    /**
     * A ship's world bounds: Sable's bounding box together with its plot's block box carried into the world (a ship
     * assembled this tick may not have its world box yet).
     */
    static AABB bounds(ShipBody ship) {
        AABB box = ship.worldBounds();
        BlockPos[] plot = ship.plotBounds();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            net.minecraft.world.phys.Vec3 corner = ship.toWorld(new net.minecraft.world.phys.Vec3(
                    (i & 1) == 0 ? plot[0].getX() : plot[1].getX() + 1,
                    (i & 2) == 0 ? plot[0].getY() : plot[1].getY() + 1,
                    (i & 4) == 0 ? plot[0].getZ() : plot[1].getZ() + 1));
            minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
        }
        AABB plotBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return box.getSize() <= 0 ? plotBox : box.minmax(plotBox);
    }

    private static PickupResult result(Pickup outcome, Component message) {
        return new PickupResult(outcome, message, null, 0, null);
    }

    // ------------------------------------------------------------------ records

    /** Removes order {@code order} from port {@code port}; false if it is not there. */
    public static boolean removeOrder(MinecraftServer server, ResourceLocation port, UUID order) {
        PortRegistry registry = PortRegistry.get(server);
        Optional<Port> p = registry.index().byId(port);
        if (p.isEmpty()) return false;
        List<ShipOrder> left = p.get().orders().stream().filter(o -> !o.id().equals(order)).toList();
        if (left.size() == p.get().orders().size()) return false;
        return registry.update(p.get().withOrders(left));
    }

    /** An order on some port. */
    public record Located(Port port, ShipOrder order) {
    }

    /** Every order whose id is or starts with {@code typed} (at least four characters), on every port. */
    public static List<Located> find(MinecraftServer server, String typed) {
        List<Located> out = new ArrayList<>();
        for (Port p : PortRegistry.get(server).index().all()) {
            for (ShipOrder o : p.orders()) {
                if (o.matches(typed)) out.add(new Located(p, o));
            }
        }
        return out;
    }

    /** Sets the order's finish day to today (operators, playtests). False if the order is gone. */
    public static boolean finishNow(MinecraftServer server, ResourceLocation port, UUID order) {
        PortRegistry registry = PortRegistry.get(server);
        Optional<Port> p = registry.index().byId(port);
        if (p.isEmpty()) return false;
        double today = today(server);
        boolean found = false;
        List<ShipOrder> orders = new ArrayList<>();
        for (ShipOrder o : p.get().orders()) {
            if (o.id().equals(order)) {
                orders.add(o.withFinishDay(Math.min(o.finishDay(), today)));
                found = true;
            } else {
                orders.add(o);
            }
        }
        return found && registry.update(p.get().withOrders(orders));
    }
}

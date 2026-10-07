package com.richardsenger.piratesnships.ship.template;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Operator commands (permission 2) for shipwright orders (SW1):
 * <pre>
 * /pirates ship orders                  the orders of the port nearest to the caller
 * /pirates ship orders &lt;port&gt;           the orders of that port
 * /pirates ship orders finish &lt;order&gt;   sets the order's finish day to now (its id or the first 4+ characters of it)
 * </pre>
 */
public final class ShipOrderCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".ship_orders.";
    public static final String KEY_HEADER = KEY + "header";
    public static final String KEY_ENTRY_WAITING = KEY + "entry_waiting";
    public static final String KEY_ENTRY_READY = KEY + "entry_ready";
    public static final String KEY_NONE = KEY + "none";
    public static final String KEY_NO_PORT = KEY + "no_port";
    public static final String KEY_FINISHED = KEY + "finished";
    public static final String KEY_NO_ORDER = KEY + "no_order";
    public static final String KEY_AMBIGUOUS = KEY + "ambiguous";

    private ShipOrderCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates")
                .then(Commands.literal("ship").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("orders").executes(ShipOrderCommands::listNearest)
                                .then(Commands.literal("finish")
                                        .then(Commands.argument("order", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(PortRegistry.get(c.getSource().getServer())
                                                        .index().all().stream().flatMap(p -> p.orders().stream()).map(ShipOrder::shortId), b))
                                                .executes(ShipOrderCommands::finish)))
                                .then(Commands.argument("port", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(PortRegistry.get(c.getSource().getServer())
                                                .index().all().stream().map(Port::id), b))
                                        .executes(ShipOrderCommands::listGiven)))));
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY_HEADER, "%s ship orders at %s:")
                .add(KEY_ENTRY_WAITING, "%s: %s for %s, ready in %s days")
                .add(KEY_ENTRY_READY, "%s: %s for %s, ready for pickup")
                .add(KEY_NONE, "No ship orders at %s")
                .add(KEY_NO_PORT, "No such port")
                .add(KEY_FINISHED, "Order %s (%s at %s) is ready for pickup")
                .add(KEY_NO_ORDER, "No ship order %s")
                .add(KEY_AMBIGUOUS, "%s orders start with %s: type more of the id");
    }

    private static int listNearest(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Optional<Port> port = PortRegistry.get(s.getServer()).index().nearest(s.getLevel().dimension(), BlockPos.containing(s.getPosition()));
        return list(s, port);
    }

    private static int listGiven(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        return list(s, PortRegistry.get(s.getServer()).index().byId(ResourceLocationArgument.getId(c, "port")));
    }

    private static int list(CommandSourceStack s, Optional<Port> port) {
        if (port.isEmpty()) {
            s.sendFailure(Component.translatable(KEY_NO_PORT));
            return 0;
        }
        Port p = port.get();
        String portName = MarketLines.portName(p.id());
        if (p.orders().isEmpty()) {
            s.sendSuccess(() -> Component.translatable(KEY_NONE, portName), false);
            return 0;
        }
        double today = ShipOrders.today(s.getServer());
        s.sendSuccess(() -> Component.translatable(KEY_HEADER, p.orders().size(), portName), false);
        for (ShipOrder o : p.orders()) {
            Component ship = Component.translatable(ShipOrders.nameOf(o.template()));
            String owner = ownerName(s.getServer(), o.owner());
            Component line = ShipOrderMath.ready(o.finishDay(), today)
                    ? Component.translatable(KEY_ENTRY_READY, o.shortId(), ship, owner)
                    : Component.translatable(KEY_ENTRY_WAITING, o.shortId(), ship, owner,
                    ShipOrderMath.formatDays(ShipOrderMath.remaining(o.finishDay(), today)));
            s.sendSuccess(() -> line, false);
        }
        return p.orders().size();
    }

    private static String ownerName(MinecraftServer server, UUID owner) {
        var online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? owner.toString() : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
    }

    private static int finish(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        String typed = StringArgumentType.getString(c, "order");
        List<ShipOrders.Located> found = ShipOrders.find(s.getServer(), typed);
        if (found.isEmpty()) {
            s.sendFailure(Component.translatable(KEY_NO_ORDER, typed));
            return 0;
        }
        if (found.size() > 1) {
            s.sendFailure(Component.translatable(KEY_AMBIGUOUS, found.size(), typed));
            return 0;
        }
        ShipOrders.Located l = found.get(0);
        ShipOrders.finishNow(s.getServer(), l.port().id(), l.order().id());
        ResourceLocation template = l.order().template();
        s.sendSuccess(() -> Component.translatable(KEY_FINISHED, l.order().shortId(), Component.translatable(ShipOrders.nameOf(template)),
                MarketLines.portName(l.port().id())), true);
        return 1;
    }
}

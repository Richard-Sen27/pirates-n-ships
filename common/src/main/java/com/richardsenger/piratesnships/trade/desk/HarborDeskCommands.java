package com.richardsenger.piratesnships.trade.desk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.TradeCommands;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Operator commands (permission 2) for harbor master's desks, on the desk the player looks at:
 * <pre>
 * /pirates trade desk bind &lt;port&gt;    bind to a port: a full id, or a test port name ("cane" = pirates_n_ships:debug/cane)
 * /pirates trade desk unbind
 * /pirates trade desk info            which port the desk belongs to
 * </pre>
 */
public final class HarborDeskCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".trade.desk.";
    private static final SimpleCommandExceptionType NO_DESK = new SimpleCommandExceptionType(Component.translatable(KEY + "no_desk"));
    private static final SimpleCommandExceptionType NO_PORT = new SimpleCommandExceptionType(Component.translatable(KEY + "no_port"));

    private HarborDeskCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Brigadier merges these literals with the ones TradeCommands registered
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("trade")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("desk")
                        .then(Commands.literal("bind").then(Commands.argument("port", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                        TradeData.get(c.getSource().getServer()).snapshot().markets().keySet(), b))
                                .executes(HarborDeskCommands::bind)))
                        .then(Commands.literal("unbind").executes(HarborDeskCommands::unbind))
                        .then(Commands.literal("info").executes(HarborDeskCommands::info)))));
    }

    /** A known market id, or a test port name typed without namespace. */
    public static Optional<ResourceLocation> resolvePort(MinecraftServer server, ResourceLocation typed) {
        if (TradeService.market(server, typed).isPresent()) return Optional.of(typed);
        if (typed.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            ResourceLocation test = TradeCommands.portId(typed.getPath());
            if (TradeService.market(server, test).isPresent()) return Optional.of(test);
        }
        return Optional.empty();
    }

    private static int bind(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos pos = lookedAtDesk(player);
        ResourceLocation port = resolvePort(player.server, ResourceLocationArgument.getId(c, "port")).orElseThrow(NO_PORT::create);
        HarborDeskService.bind(player.level(), pos, Optional.of(port));
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "bound", port.toString()), true);
        return 1;
    }

    private static int unbind(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        HarborDeskService.bind(player.level(), lookedAtDesk(player), Optional.empty());
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "unbound"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos pos = lookedAtDesk(player);
        Optional<ResourceLocation> port = HarborDeskService.boundPort(player.level(), pos);
        if (port.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "info_unbound", pos.toShortString()), false);
            return 0;
        }
        Optional<Market> market = TradeService.market(player.server, port.get());
        Component kind = market.map(m -> Component.literal(m.profile().kind().getSerializedName()))
                .orElse(Component.translatable(KEY + "info_missing"));
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "info", pos.toShortString(), port.get().toString(), kind), false);
        return 1;
    }

    private static BlockPos lookedAtDesk(ServerPlayer player) throws CommandSyntaxException {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1.0f).scale(Math.max(5.0, player.blockInteractionRange())));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || HarborDeskService.desk(player.level(), hit.getBlockPos()).isEmpty()) throw NO_DESK.create();
        return hit.getBlockPos();
    }

    static void lang(LangBuilder lang) {
        lang.add(KEY + "no_desk", "Look at a harbor master's desk")
                .add(KEY + "no_port", "No such port (create a test port with /pirates trade port)")
                .add(KEY + "bound", "The desk now belongs to port %s")
                .add(KEY + "unbound", "The desk no longer belongs to a port")
                .add(KEY + "info", "Desk at %s belongs to port %s (%s)")
                .add(KEY + "info_unbound", "Desk at %s is not bound to a port")
                .add(KEY + "info_missing", "no market");
    }
}

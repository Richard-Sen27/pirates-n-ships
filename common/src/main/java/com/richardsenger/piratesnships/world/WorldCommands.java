package com.richardsenger.piratesnships.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortArguments;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.PortNameBook;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.treasure.TreasureMapCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /pirates world ports} lists the port registry; {@code /pirates world port nearest} shows the nearest port with
 * its berths and treasure sites, {@code /pirates world port <id or "name">} any one port (NAME3); {@code /pirates world treasure give [port]} gives a bound treasure map (TM1,
 * {@link TreasureMapCommands}) (operators only, permission 2).
 */
public final class WorldCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.";

    private WorldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> port = Commands.literal("port")
                .then(Commands.literal("nearest").executes(WorldCommands::nearest));
        PortArguments.addTo(port, "port", a -> a.executes(WorldCommands::show));
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("ports").executes(WorldCommands::list))
                .then(port)
                .then(TreasureMapCommands.node())));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        List<Port> ports = PortRegistry.get(source.getServer()).index().all();
        source.sendSuccess(() -> Component.translatable(KEY + "count", ports.size()), false);
        for (Port port : ports) source.sendSuccess(() -> line(port, source), false);
        return ports.size();
    }

    private static int nearest(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        BlockPos at = BlockPos.containing(source.getPosition());
        Optional<Port> port = PortRegistry.get(source.getServer()).index().nearest(source.getLevel().dimension(), at);
        if (port.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "none"));
            return 0;
        }
        Port p = port.get();
        int distance = (int) Math.round(Math.sqrt(PortIndex.horizontalDistanceSqr(p.centre(), at)));
        source.sendSuccess(() -> Component.translatable(KEY + "nearest", distance), false);
        details(p, source);
        return distance;
    }

    /** {@code /pirates world port <id or name>}: one port with its berths and treasure sites. */
    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Port p = PortArguments.get(c, "port");
        details(p, c.getSource());
        return 1;
    }

    private static void details(Port p, CommandSourceStack source) {
        source.sendSuccess(() -> line(p, source), false);
        for (Berth b : p.berths()) {
            source.sendSuccess(() -> Component.translatable(KEY + "berth", b.pos().toShortString(), b.bow().getName()), false);
        }
        for (TreasureSite t : p.treasures()) {
            source.sendSuccess(() -> Component.translatable(KEY + "treasure", t.pos().toShortString(),
                    Component.translatable(KEY + (t.looted() ? "treasure.looted" : "treasure.buried"))), false);
        }
    }

    private static Component line(Port p, CommandSourceStack source) {
        String berths = p.berths().stream().map(b -> b.pos().toShortString()).collect(Collectors.joining("; "));
        return Component.translatable(KEY + "port", PortNameBook.labelWithId(p.id()), p.kind().getSerializedName(), p.climate().getSerializedName(),
                p.dimension().location().toString(), p.centre().toShortString(), p.berths().size(), berths);
    }

    static void lang(LangBuilder lang) {
        lang.add(KEY + "count", "%s ports known")
                .add(KEY + "port", "%s (%s, %s) in %s at %s, %s berths [%s]")
                .add(KEY + "nearest", "Nearest port, %s blocks away:")
                .add(KEY + "berth", "Berth at %s, bow %s")
                .add(KEY + "treasure", "Treasure at %s (%s)")
                .add(KEY + "treasure.buried", "buried")
                .add(KEY + "treasure.looted", "looted")
                .add(KEY + "none", "No port known in this dimension");
    }
}

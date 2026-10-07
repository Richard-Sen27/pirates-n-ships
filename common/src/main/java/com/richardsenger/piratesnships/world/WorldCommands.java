package com.richardsenger.piratesnships.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /pirates world ports} lists the port registry; {@code /pirates world port nearest} shows the nearest port with
 * its berths and treasure sites (operators only, permission 2).
 */
public final class WorldCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.";

    private WorldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("ports").executes(WorldCommands::list))
                .then(Commands.literal("port").then(Commands.literal("nearest").executes(WorldCommands::nearest)))));
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
        source.sendSuccess(() -> line(p, source), false);
        for (Berth b : p.berths()) {
            source.sendSuccess(() -> Component.translatable(KEY + "berth", b.pos().toShortString(), b.bow().getName()), false);
        }
        for (TreasureSite t : p.treasures()) {
            source.sendSuccess(() -> Component.translatable(KEY + "treasure", t.pos().toShortString(),
                    Component.translatable(KEY + (t.looted() ? "treasure.looted" : "treasure.buried"))), false);
        }
        return distance;
    }

    private static Component line(Port p, CommandSourceStack source) {
        String berths = p.berths().stream().map(b -> b.pos().toShortString()).collect(Collectors.joining("; "));
        return Component.translatable(KEY + "port", p.id().toString(), p.kind().getSerializedName(), p.climate().getSerializedName(),
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

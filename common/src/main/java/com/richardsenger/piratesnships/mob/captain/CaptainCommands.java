package com.richardsenger.piratesnships.mob.captain;

import com.mojang.brigadier.CommandDispatcher;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /pirates mob captain spawn} and {@code /pirates mob captain list} (permission 2, BOS1).
 *
 * <ul>
 *   <li>{@code spawn}: a captain where the source stands, facing its way. Inside a pirate island he is that island's
 *       captain (refused while its captain lives); elsewhere he gets a post of his own (port id
 *       {@code pirates_n_ships:captain_<x>_<z>}), with a name, a bounty and a successor like any other.</li>
 *   <li>{@code list}: every island's captain: name, alive or lost on which day, post, bounty.</li>
 * </ul>
 */
public final class CaptainCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".captain.";
    public static final String KEY_SPAWNED = KEY + "spawned";
    public static final String KEY_REFUSED = KEY + "refused";
    public static final String KEY_NONE = KEY + "none";
    public static final String KEY_LINE_ALIVE = KEY + "line.alive";
    public static final String KEY_LINE_LOST = KEY + "line.lost";

    private CaptainCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("mob").requires(s -> s.hasPermission(2))
                .then(Commands.literal("captain")
                        .then(Commands.literal("spawn").executes(c -> spawn(c.getSource())))
                        .then(Commands.literal("list").executes(c -> list(c.getSource()))))));
    }

    /** The pirate island around {@code pos}, else a post of its own. */
    public static ResourceLocation portAt(ServerLevel level, BlockPos pos) {
        Optional<Port> island = PortRegistry.get(level.getServer()).index().all().stream()
                .filter(p -> p.kind() == PortKind.PIRATE_ISLAND && p.contains(level.dimension(), pos))
                .findFirst();
        return island.map(Port::id).orElseGet(() -> Constants.id("captain_" + pos.getX() + "_" + pos.getZ()));
    }

    private static int spawn(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Direction facing = Direction.fromYRot(source.getRotation().y);
        ResourceLocation port = portAt(level, pos);
        PirateCaptain captain = IslandCaptains.spawn(level, port, pos, facing, nextGeneration(level, port));
        if (captain == null) {
            source.sendFailure(Component.translatable(KEY_REFUSED, port.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY_SPAWNED, captain.getName(), port.toString(),
                LawService.bountyTotal(level.getServer(), captain.getUUID())), true);
        return 1;
    }

    private static int nextGeneration(ServerLevel level, ResourceLocation port) {
        return CaptainRegistry.get(level.getServer()).get(port).map(e -> e.generation() + 1).orElse(0);
    }

    private static int list(CommandSourceStack source) {
        var server = source.getServer();
        Map<ResourceLocation, CaptainEntry> all = CaptainRegistry.get(server).all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY_NONE), false);
            return 0;
        }
        all.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            CaptainEntry c = e.getValue();
            String post = c.post().toShortString();
            Component line = c.alive()
                    ? Component.translatable(KEY_LINE_ALIVE, c.name(), e.getKey().toString(), post,
                    LawService.bountyTotal(server, c.id()))
                    : Component.translatable(KEY_LINE_LOST, c.name(), e.getKey().toString(), post, c.diedDay());
            source.sendSuccess(() -> line, false);
        });
        return all.size();
    }
}

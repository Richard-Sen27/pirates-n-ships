package com.richardsenger.piratesnships.ship.assembly;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** {@code /pirates ship info}: id, name, origin and wreck flag of the ship the source stands on or next to (RS1). */
public final class ShipInfoCommand {

    static final String KEY = "commands." + Constants.MOD_ID + ".ship.info";
    static final String KEY_NONE = KEY + ".none";
    static final String KEY_SHIP = KEY + ".ship";
    static final String KEY_WRECK = KEY + ".wreck";
    static final String KEY_UNNAMED = KEY + ".unnamed";

    private ShipInfoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates")
                .then(Commands.literal("ship").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("info").executes(ShipInfoCommand::info))));
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        ShipBody ship = shipAt(s.getLevel(), s.getPosition());
        if (ship == null) {
            s.sendFailure(Component.translatable(KEY_NONE));
            return 0;
        }
        ShipSplits.Lineage line = ShipSplits.lineage(ship);
        String name = ShipRegistry.get(s.getServer()).find(ship.id()).map(ShipData::name).orElse("");
        Component shown = name.isEmpty() ? Component.translatable(KEY_UNNAMED) : Component.literal(name);
        if (line.wreck()) {
            Component of = line.wreckOf().isEmpty() ? Component.translatable(KEY_UNNAMED) : Component.literal(line.wreckOf());
            s.sendSuccess(() -> Component.translatable(KEY_WRECK, ship.id().toString(), of, line.origin().toString()), false);
        } else {
            s.sendSuccess(() -> Component.translatable(KEY_SHIP, ship.id().toString(), shown, line.origin().toString()), false);
        }
        return 1;
    }

    private static @Nullable ShipBody shipAt(ServerLevel level, Vec3 pos) {
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.worldBounds().inflate(0.5, 1.5, 0.5).contains(pos)) {
                return ship;
            }
        }
        return SableShips.containing(level, BlockPos.containing(pos));
    }
}

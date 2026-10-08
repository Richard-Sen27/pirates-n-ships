package com.richardsenger.piratesnships.mob.squad;

import com.mojang.brigadier.CommandDispatcher;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /pirates mob squad info|patrol|return} (permission 2, MOB2), for the squad of the nearest navy officer within
 * {@link #RANGE} blocks that leads one:
 * <ul>
 *     <li>{@code info}: state, waypoint, members, when the next patrol is due;</li>
 *     <li>{@code patrol}: sets off on patrol now (ignores the night until the patrol is over);</li>
 *     <li>{@code return}: back to the garrison posts now.</li>
 * </ul>
 */
public final class SquadCommands {

    public static final double RANGE = 64.0;

    public static final String KEY = "commands." + Constants.MOD_ID + ".squad.";
    public static final String KEY_NONE = KEY + "none";
    public static final String KEY_INFO = KEY + "info";
    public static final String KEY_PATROL = KEY + "patrol";
    public static final String KEY_PATROL_REFUSED = KEY + "patrol_refused";
    public static final String KEY_RETURN = KEY + "return";
    public static final String KEY_RETURN_REFUSED = KEY + "return_refused";

    private SquadCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("mob").requires(s -> s.hasPermission(2))
                .then(Commands.literal("squad")
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))
                        .then(Commands.literal("patrol").executes(c -> patrol(c.getSource())))
                        .then(Commands.literal("return").executes(c -> orderReturn(c.getSource()))))));
    }

    private static Squad nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Squad squad = SquadService.nearest(level, BlockPos.containing(source.getPosition()), RANGE);
        if (squad == null) source.sendFailure(Component.translatable(KEY_NONE, (int) RANGE));
        return squad;
    }

    private static int info(CommandSourceStack source) {
        Squad squad = nearest(source);
        if (squad == null) return 0;
        long now = source.getLevel().getGameTime();
        long next = squad.state() == SquadState.AT_POST && squad.nextPatrolAt() > 0 ? Math.max(0, (squad.nextPatrolAt() - now) / 20) : 0;
        source.sendSuccess(() -> Component.translatable(KEY_INFO, squad.leader().getName(), squad.state().getSerializedName(),
                Math.min(squad.waypoint() + 1, squad.route().size()), squad.route().size(), squad.members().size(), next), false);
        return 1;
    }

    private static int patrol(CommandSourceStack source) {
        Squad squad = nearest(source);
        if (squad == null) return 0;
        if (!squad.startPatrol(true)) {
            source.sendFailure(Component.translatable(KEY_PATROL_REFUSED, squad.leader().getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY_PATROL, squad.leader().getName(), squad.members().size(),
                squad.route().size()), true);
        return 1;
    }

    private static int orderReturn(CommandSourceStack source) {
        Squad squad = nearest(source);
        if (squad == null) return 0;
        if (!squad.orderReturn()) {
            source.sendFailure(Component.translatable(KEY_RETURN_REFUSED, squad.leader().getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY_RETURN, squad.leader().getName()), true);
        return 1;
    }
}

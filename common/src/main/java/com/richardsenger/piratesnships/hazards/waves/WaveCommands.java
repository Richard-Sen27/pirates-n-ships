package com.richardsenger.piratesnships.hazards.waves;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /pirates waves} shows the sea of the source's level; {@code /pirates waves set <calm|moderate|rough|storm>
 * [fromDegrees]} holds a state (at once, no easing) and optionally a direction the waves come from (compass, like
 * {@code /pirates wind set}); {@code /pirates waves clear} returns the sea to the weather. Set and clear need
 * permission 2.
 */
public final class WaveCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".waves.";
    public static final String KEY_INFO = KEY + "info";
    public static final String KEY_OVERRIDE = KEY + "override";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_SET = KEY + "set";
    public static final String KEY_CLEARED = KEY + "cleared";
    public static final String KEY_UNKNOWN = KEY + "unknown";

    private WaveCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("waves")
                .executes(WaveCommands::info)
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(SeaState.values()).map(SeaState::id), b))
                                .executes(c -> set(c, null))
                                .then(Commands.argument("from", DoubleArgumentType.doubleArg(0, 360))
                                        .executes(c -> set(c, DoubleArgumentType.getDouble(c, "from"))))))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2))
                        .executes(WaveCommands::clear))));
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        if (!HazardConfig.WAVES_ENABLED.get()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY_DISABLED), false);
            return 0;
        }
        WaveField f = SeaStates.field(level);
        SeaState now = SeaStates.current(level);
        SeaState target = SeaStates.target(level);
        boolean fixed = SeaStates.override(level) != null;
        c.getSource().sendSuccess(() -> Component.translatable(fixed ? KEY_OVERRIDE : KEY_INFO, now.id(), target.id(),
                String.format("%.2f", f.amplitude()),
                String.format("%.0f", com.richardsenger.piratesnships.sailing.wind.WindSample.normalizeDegrees(f.directionDegrees() + 180.0))),
                false);
        return now.ordinal() + 1;
    }

    private static int set(CommandContext<CommandSourceStack> c, @Nullable Double fromDegrees) {
        String id = StringArgumentType.getString(c, "state");
        Optional<SeaState> state = SeaState.byId(id);
        if (state.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(KEY_UNKNOWN, id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        SeaStates.set(level, state.get(), fromDegrees == null ? null : fromDegrees + 180.0, SeaStateModel.Tracker.FOREVER);
        c.getSource().sendSuccess(() -> Component.translatable(KEY_SET, state.get().id(),
                fromDegrees == null ? "-" : String.format("%.0f", fromDegrees)), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        SeaStates.clear(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.translatable(KEY_CLEARED), true);
        return 1;
    }
}

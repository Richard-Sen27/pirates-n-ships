package com.richardsenger.piratesnships.rpg.reputation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Reputation commands (docs/design.md §15):
 * <pre>
 * /pirates rep                                       (your own reputation, any player)
 * /pirates rep &lt;player&gt;                              (operators)
 * /pirates rep set &lt;player&gt; &lt;faction&gt; &lt;value&gt;      (operators; value -100..100)
 * </pre>
 */
public final class ReputationCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".rep.";

    private static final SimpleCommandExceptionType UNKNOWN_FACTION =
            new SimpleCommandExceptionType(Component.translatable(KEY + "unknown_faction"));

    private ReputationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("rep")
                .executes(c -> show(c, c.getSource().getPlayerOrException()))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Faction.values()).map(Faction::id), b))
                                        .then(Commands.argument("value", IntegerArgumentType.integer((int) ReputationRules.MIN, (int) ReputationRules.MAX))
                                                .executes(ReputationCommands::set)))))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> show(c, EntityArgument.getPlayer(c, "player"))))));
    }

    private static int show(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        ReputationRecord r = Reputation.record(player);
        Component line = Component.translatable(KEY + (Reputation.enabled() ? "show" : "show.off"), player.getDisplayName(),
                r.display(Faction.NAVY), r.display(Faction.PIRATES), r.display(Faction.VILLAGERS))
                .append(". ").append(com.richardsenger.piratesnships.rpg.career.Careers.describe(player));
        c.getSource().sendSuccess(() -> line, false);
        return r.display(Faction.NAVY);
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Faction faction = Faction.byId(StringArgumentType.getString(c, "faction")).orElseThrow(UNKNOWN_FACTION::create);
        int value = Reputation.set(player, faction, IntegerArgumentType.getInteger(c, "value"), "command");
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "set", player.getDisplayName(),
                Component.translatable(faction.nameKey()), value), true);
        return value;
    }

    /** Lang entries of the commands (from {@code RpgModule.gatherData}). */
    public static void lang(LangBuilder lang) {
        lang.add(KEY + "show", "Reputation of %s: navy %s, pirates %s, villagers %s")
                .add(KEY + "show.off", "Reputation of %s: navy %s, pirates %s, villagers %s (reputation is off on this server)")
                .add(KEY + "set", "Set the %2$s reputation of %1$s to %3$s")
                .add(KEY + "unknown_faction", "Unknown faction (navy, pirates or villagers)");
    }
}

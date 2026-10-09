package com.richardsenger.piratesnships.rpg.career;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Career commands (docs/design.md §15, CAR1):
 * <pre>
 * /pirates career                                  (your own career, any player)
 * /pirates career &lt;player&gt;                         (operators; with the counters)
 * /pirates career set &lt;player&gt; navy &lt;rank&gt;        (operators; none ends the service without desertion)
 * /pirates career set &lt;player&gt; infamy &lt;rank&gt;      (operators; buccaneer or more in service is desertion)
 * /pirates career letter &lt;player&gt; grant|void       (operators; grant skips the checks and the fee)
 * </pre>
 */
public final class CareerCommands {

    private static final SimpleCommandExceptionType UNKNOWN_RANK =
            new SimpleCommandExceptionType(Component.translatable(CareerText.UNKNOWN_RANK));

    private CareerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("career")
                .executes(c -> show(c, c.getSource().getPlayerOrException(), false))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("navy").then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(NavyRank.values()).map(NavyRank::id), b))
                                        .executes(CareerCommands::setNavy)))
                                .then(Commands.literal("infamy").then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(InfamyRank.values()).map(InfamyRank::id), b))
                                        .executes(CareerCommands::setInfamy)))))
                .then(Commands.literal("letter").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("grant").executes(c -> letter(c, true)))
                                .then(Commands.literal("void").executes(c -> letter(c, false)))))
                .then(Commands.literal("ship_grant").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("reset").executes(CareerCommands::resetShipGrants))))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> show(c, EntityArgument.getPlayer(c, "player"), true)))));
    }

    /** SHP1, operators and playtests: forgets the player's ship grants and redemptions; a held rank grants again at once. */
    private static int resetShipGrants(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int granted = ShipGrants.reset(player);
        c.getSource().sendSuccess(() -> Component.translatable(ShipGrants.KEY_CMD_RESET, player.getDisplayName(), granted), true);
        return granted;
    }

    private static int show(CommandContext<CommandSourceStack> c, ServerPlayer player, boolean counters) {
        CareerRecord r = Careers.record(player);
        Component line = Component.translatable(Careers.enabled() ? CareerText.SHOW : CareerText.SHOW_OFF, player.getDisplayName(),
                Careers.navyName(r), Component.translatable(r.infamy().nameKey()), Component.translatable(r.letter().nameKey()),
                r.prizeMoney());
        c.getSource().sendSuccess(() -> line, false);
        if (counters) {
            for (CareerCounter counter : CareerCounter.values()) {
                long n = r.count(counter);
                if (n == 0) continue;
                Component text = Component.translatable(CareerText.COUNTERS,
                        Component.translatable(counter.nameKey()).append(": " + n));
                c.getSource().sendSuccess(() -> text, false);
            }
        }
        return r.navy().ordinal();
    }

    private static int setNavy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        NavyRank rank = NavyRank.byId(StringArgumentType.getString(c, "rank")).orElseThrow(UNKNOWN_RANK::create);
        Careers.setNavy(player, rank);
        c.getSource().sendSuccess(() -> Component.translatable(CareerText.SET_NAVY, player.getDisplayName(),
                Component.translatable(rank.nameKey())), true);
        return rank.ordinal();
    }

    private static int setInfamy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        InfamyRank rank = InfamyRank.byId(StringArgumentType.getString(c, "rank")).orElseThrow(UNKNOWN_RANK::create);
        Careers.setInfamy(player, rank);
        c.getSource().sendSuccess(() -> Component.translatable(CareerText.SET_INFAMY, player.getDisplayName(),
                Component.translatable(rank.nameKey())), true);
        return rank.ordinal();
    }

    private static int letter(CommandContext<CommandSourceStack> c, boolean grant) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        if (grant) Careers.forceLetter(player);
        else Careers.voidLetter(player);
        LetterState state = Careers.record(player).letter();
        c.getSource().sendSuccess(() -> Component.translatable(CareerText.LETTER_SET, player.getDisplayName(),
                Component.translatable(state.nameKey())), true);
        return state.ordinal();
    }
}

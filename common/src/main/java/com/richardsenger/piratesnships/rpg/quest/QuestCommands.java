package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Quest commands (docs/design.md §15, QST1):
 * <pre>
 * /pirates quest list                          (your active quests with their ids)
 * /pirates quest abandon &lt;id&gt;                  (drop one of yours; counts as failed)
 * /pirates quest offer &lt;port&gt; &lt;type&gt;           (operators: one more offer of that type at the port)
 * /pirates quest complete &lt;id&gt;                 (operators: complete one of your active quests, with its reward)
 * </pre>
 * Ids are the first eight hex digits the list shows.
 */
public final class QuestCommands {

    private static final SimpleCommandExceptionType UNKNOWN_ID = new SimpleCommandExceptionType(Component.translatable(QuestText.UNKNOWN_ID));
    private static final SimpleCommandExceptionType UNKNOWN_TYPE = new SimpleCommandExceptionType(Component.translatable(QuestText.UNKNOWN_TYPE));

    private QuestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("quest")
                .then(Commands.literal("list").executes(QuestCommands::list))
                .then(Commands.literal("abandon").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(c.getSource()), b))
                        .executes(QuestCommands::abandon)))
                .then(Commands.literal("offer").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("port", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ports(c.getSource().getServer()), b))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(QuestType.values()).filter(QuestType::available).map(QuestType::id), b))
                                        .executes(QuestCommands::offer))))
                .then(Commands.literal("complete").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(c.getSource()), b))
                                .executes(QuestCommands::complete)))));
    }

    private static Stream<String> ids(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        return p == null ? Stream.empty() : Quests.log(p).active().stream().map(Quest::shortId);
    }

    private static Stream<ResourceLocation> ports(MinecraftServer server) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Port p : PortRegistry.get(server).index().all()) out.add(p.id());
        out.addAll(TradeData.get(server).snapshot().markets().keySet());
        return out.stream();
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        QuestLog log = Quests.log(player);
        c.getSource().sendSuccess(() -> Component.translatable(QuestText.LIST_HEADER, player.getDisplayName(), log.active().size(),
                QuestConfig.MAX_ACTIVE.get(), log.completedTotal()), false);
        if (log.active().isEmpty()) c.getSource().sendSuccess(() -> Component.translatable(QuestText.LIST_NONE), false);
        for (Quest q : log.active()) {
            c.getSource().sendSuccess(() -> Component.translatable(QuestText.LIST_LINE, q.shortId(), QuestText.title(q, false),
                    q.progress(), q.needed(), q.deadlineDay(), q.rewardCoins()), false);
        }
        return log.active().size();
    }

    private static Quest find(ServerPlayer player, String id) throws CommandSyntaxException {
        return Quests.log(player).findByPrefix(id).orElseThrow(UNKNOWN_ID::create);
    }

    private static int abandon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Quest q = find(player, StringArgumentType.getString(c, "id"));
        Quests.Result r = Quests.abandon(player, q.id());
        c.getSource().sendSuccess(() -> Component.translatable(QuestText.result(r.key()), QuestText.title(q, false)), false);
        return r.done() ? 1 : 0;
    }

    private static int offer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation port = ResourceLocationArgument.getId(c, "port");
        QuestType type = QuestType.byId(StringArgumentType.getString(c, "type")).filter(QuestType::available).orElseThrow(UNKNOWN_TYPE::create);
        Optional<Quest> q = Quests.offerOfType(c.getSource().getServer(), port, type);
        if (q.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(QuestText.CANT_OFFER, QuestText.portName(port)));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(QuestText.OFFERED, q.get().shortId(), QuestText.portName(port),
                QuestText.title(q.get(), false)), true);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Quest q = find(player, StringArgumentType.getString(c, "id"));
        Quests.applyTo(player, q.id(), new QuestEvent.Complete());
        c.getSource().sendSuccess(() -> Component.translatable(QuestText.FORCED, q.shortId(), player.getDisplayName()), true);
        return 1;
    }
}

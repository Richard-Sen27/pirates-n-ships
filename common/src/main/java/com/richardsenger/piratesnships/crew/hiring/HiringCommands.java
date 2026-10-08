package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Operator hiring commands (CRW1, docs/design.md §7.5):
 * <pre>
 * /pirates crew hire &lt;sailor|pirate|navy&gt; [port]   (a new recruit of that kind signs on for you, free and without the
 *                                                  standing check; your ship must be moored at the port with a free
 *                                                  bunk; the port defaults to the one you stand in, else the nearest)
 * /pirates crew dismiss &lt;crew&gt;                       (that crew member becomes a neutral sailor, whoever hired it)
 * </pre>
 */
public final class HiringCommands {

    private static final DynamicCommandExceptionType UNKNOWN_KIND =
            new DynamicCommandExceptionType(k -> Component.literal("Unknown crew kind: " + k));
    private static final SimpleCommandExceptionType NO_PORT =
            new SimpleCommandExceptionType(Component.translatable(HiringText.result(HiringText.NO_PORT)));

    private HiringCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("crew").requires(s -> s.hasPermission(2))
                .then(Commands.literal("hire").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CandidateKind.values()).map(CandidateKind::id), b))
                                .executes(c -> hire(c, Optional.empty()))
                                .then(Commands.argument("port", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                PortRegistry.get(c.getSource().getServer()).index().all().stream().map(Port::id), b))
                                        .executes(c -> hire(c, Optional.of(ResourceLocationArgument.getId(c, "port")))))))
                .then(Commands.literal("dismiss").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("crew", EntityArgument.entity())
                                .executes(HiringCommands::dismiss)))));
    }

    private static int hire(CommandContext<CommandSourceStack> c, Optional<ResourceLocation> portId) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String kindId = StringArgumentType.getString(c, "kind");
        Optional<CandidateKind> found = CandidateKind.byId(kindId);
        if (found.isEmpty()) throw UNKNOWN_KIND.create(kindId);
        CandidateKind kind = found.get();
        PortIndex index = PortRegistry.get(c.getSource().getServer()).index();
        BlockPos at = player.blockPosition();
        Port port = (portId.isPresent() ? index.byId(portId.get())
                : index.containing(player.level().dimension(), at).or(() -> index.nearest(player.level().dimension(), at)))
                .orElseThrow(NO_PORT::create);
        Hiring.Result r = Hiring.hireNew(player, port, kind);
        if (r.done()) {
            c.getSource().sendSuccess(r::message, true);
            return 1;
        }
        c.getSource().sendFailure(r.message());
        return 0;
    }

    private static int dismiss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "crew");
        if (!(e instanceof CrewMember crew)) {
            c.getSource().sendFailure(Component.translatable(HiringText.result(HiringText.NOT_CREW), e.getDisplayName()));
            return 0;
        }
        Hiring.Result r = Hiring.forceDismiss(crew);
        c.getSource().sendSuccess(r::message, true);
        return r.done() ? 1 : 0;
    }
}

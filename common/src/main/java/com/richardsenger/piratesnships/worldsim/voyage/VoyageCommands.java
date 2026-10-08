package com.richardsenger.piratesnships.worldsim.voyage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /pirates world voyages [list]} lists the active voyages; {@code spawn convoy <from> <to>} sends a convoy now;
 * {@code advance <id> <blocks>} moves one (arriving at the end); {@code lane <from> <to>} finds the lane between two
 * ports now and prints its length, waypoints and cost (operators only, permission 2).
 */
public final class VoyageCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.voyages.";

    private static final SuggestionProvider<CommandSourceStack> PORTS = (c, b) -> SharedSuggestionProvider.suggestResource(
            PortRegistry.get(c.getSource().getServer()).index().all().stream().map(Port::id), b);
    private static final SuggestionProvider<CommandSourceStack> IDS = (c, b) -> SharedSuggestionProvider.suggest(
            Voyages.active(c.getSource().getServer()).stream().map(Voyage::shortId), b);

    private VoyageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> voyages = Commands.literal("voyages")
                .requires(s -> s.hasPermission(2))
                .executes(VoyageCommands::list)
                .then(Commands.literal("list").executes(VoyageCommands::list))
                .then(Commands.literal("spawn").then(Commands.literal("convoy")
                        .then(Commands.argument("from", ResourceLocationArgument.id()).suggests(PORTS)
                                .then(Commands.argument("to", ResourceLocationArgument.id()).suggests(PORTS)
                                        .executes(VoyageCommands::spawnConvoy)))))
                .then(Commands.literal("advance")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0))
                                        .executes(VoyageCommands::advance))))
                .then(Commands.literal("lane")
                        .then(Commands.argument("from", ResourceLocationArgument.id()).suggests(PORTS)
                                .then(Commands.argument("to", ResourceLocationArgument.id()).suggests(PORTS)
                                        .executes(VoyageCommands::lane))));
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world").requires(s -> s.hasPermission(2)).then(voyages)));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        List<Voyage> all = Voyages.active(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "count", all.size(), Lanes.queued()), false);
        for (Voyage v : all) source.sendSuccess(() -> line(v), false);
        return all.size();
    }

    static Component line(Voyage v) {
        Lane.Position p = v.position();
        String cargo = v.cargo().isEmpty() ? "-" : v.cargo().entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().getPath()).collect(Collectors.joining(", "));
        return Component.translatable(KEY + "line", v.shortId(), v.kind().getSerializedName(), v.from().toString(), v.to().toString(),
                Math.round(v.progress()), Math.round(v.length()), Math.round(p.x()), Math.round(p.z()),
                v.state().getSerializedName(), cargo);
    }

    private static int spawnConvoy(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ResourceLocation from = ResourceLocationArgument.getId(c, "from");
        ResourceLocation to = ResourceLocationArgument.getId(c, "to");
        Optional<Voyage> v = Voyages.spawnConvoy(source.getServer(), from, to, source.getLevel().getRandom());
        if (v.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "spawn_failed", from.toString(), to.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "spawned"), true);
        source.sendSuccess(() -> line(v.get()), false);
        return 1;
    }

    private static int advance(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        String id = StringArgumentType.getString(c, "id");
        Optional<Voyage> v = Voyages.find(source.getServer(), id);
        if (v.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown", id));
            return 0;
        }
        Optional<Voyage> moved = Voyages.advance(source.getServer(), v.get().id(), DoubleArgumentType.getDouble(c, "blocks"));
        if (moved.isPresent() && moved.get().arrived() && Voyages.get(source.getServer(), v.get().id()).isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "arrived", v.get().shortId(), v.get().to().toString()), true);
        } else {
            moved.ifPresent(m -> source.sendSuccess(() -> line(m), false));
        }
        return 1;
    }

    private static int lane(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ResourceLocation from = ResourceLocationArgument.getId(c, "from");
        ResourceLocation to = ResourceLocationArgument.getId(c, "to");
        Lanes.Computed r = Lanes.compute(source.getServer(), from, to);
        long ms = r.nanos() / 1_000_000;
        if (r.lane().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_lane", from.toString(), to.toString(),
                    r.result().status().name().toLowerCase(java.util.Locale.ROOT), r.result().expanded(), ms));
            return 0;
        }
        Lane lane = r.lane().get();
        source.sendSuccess(() -> Component.translatable(KEY + "lane", from.toString(), to.toString(), Math.round(lane.length()),
                lane.waypoints().size(), r.result().expanded(), r.biomeSamples(), ms), false);
        String points = lane.waypoints().stream().map(p -> p.x() + " " + p.z()).collect(Collectors.joining("; "));
        source.sendSuccess(() -> Component.literal(points), false);
        return (int) Math.round(lane.length());
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "count", "%s voyages under way (%s lanes queued)")
                .add(KEY + "line", "%s %s %s -> %s: %s/%s blocks at %s %s, %s, cargo %s")
                .add(KEY + "spawned", "A convoy sets sail")
                .add(KEY + "spawn_failed", "No convoy from %s to %s: unknown port, market not open, or no sea lane")
                .add(KEY + "unknown", "No single voyage with an id starting %s")
                .add(KEY + "arrived", "Voyage %s arrived at %s")
                .add(KEY + "no_lane", "No lane from %s to %s (%s after %s cells, %s ms)")
                .add(KEY + "lane", "Lane %s -> %s: %s blocks, %s waypoints, %s cells searched, %s biome samples, %s ms");
    }
}

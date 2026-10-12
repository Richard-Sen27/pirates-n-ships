package com.richardsenger.piratesnships.worldsim.voyage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortArguments;
import com.richardsenger.piratesnships.world.port.PortNameBook;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /pirates world voyages [list]} lists the active voyages; {@code spawn convoy <from> <to>} sends a convoy now;
 * {@code advance <id> <blocks>} moves one (arriving at the end); {@code lane <from> <to>} finds the lane between two
 * ports now and prints its length, waypoints and cost (operators only, permission 2). A port is given by its id or its
 * name in quotes ({@link PortArguments}), and the replies name ports by their names (NAME3).
 */
public final class VoyageCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.voyages.";

    private static final SuggestionProvider<CommandSourceStack> IDS = (c, b) -> SharedSuggestionProvider.suggest(
            Voyages.active(c.getSource().getServer()).stream().map(Voyage::shortId), b);
    /** Extra words for a voyage's line (BOS2: the captain aboard), added by other packages. */
    private static final List<java.util.function.BiFunction<net.minecraft.server.MinecraftServer, Voyage, Optional<Component>>> LABELS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private VoyageCommands() {
    }

    /** Adds {@code label} to the line of every voyage it answers for in {@code /pirates world voyages}. */
    public static void label(java.util.function.BiFunction<net.minecraft.server.MinecraftServer, Voyage, Optional<Component>> label) {
        LABELS.add(label);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // both ports by id or by name (NAME3): <from> | <from_name>, then <to> | <to_name>
        LiteralArgumentBuilder<CommandSourceStack> convoy = Commands.literal("convoy");
        PortArguments.addTo(convoy, "from", f -> PortArguments.addTo(f, "to", t -> t.executes(VoyageCommands::spawnConvoy)));
        LiteralArgumentBuilder<CommandSourceStack> lane = Commands.literal("lane");
        PortArguments.addTo(lane, "from", f -> PortArguments.addTo(f, "to", t -> t.executes(VoyageCommands::lane)));
        LiteralArgumentBuilder<CommandSourceStack> voyages = Commands.literal("voyages")
                .requires(s -> s.hasPermission(2))
                .executes(VoyageCommands::list)
                .then(Commands.literal("list").executes(VoyageCommands::list))
                .then(Commands.literal("spawn").then(convoy))
                .then(Commands.literal("advance")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0))
                                        .executes(VoyageCommands::advance))))
                .then(lane);
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world").requires(s -> s.hasPermission(2)).then(voyages)));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        List<Voyage> all = Voyages.active(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "count", all.size(), Lanes.queued()), false);
        for (Voyage v : all) source.sendSuccess(() -> line(source.getServer(), v), false);
        return all.size();
    }

    static Component line(net.minecraft.server.MinecraftServer server, Voyage v) {
        Lane.Position p = v.position();
        String cargo = v.cargo().isEmpty() ? "-" : v.cargo().entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().getPath()).collect(Collectors.joining(", "));
        net.minecraft.network.chat.MutableComponent line = Component.translatable(KEY + "line", v.shortId(), v.kind().getSerializedName(),
                PortNameBook.label(v.from()), PortNameBook.label(v.to()), Math.round(v.progress()), Math.round(v.length()), Math.round(p.x()), Math.round(p.z()),
                v.state().getSerializedName(), cargo);
        for (var label : LABELS) label.apply(server, v).ifPresent(l -> line.append(", ").append(l));
        return line;
    }

    private static int spawnConvoy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ResourceLocation from = PortArguments.get(c, "from").id();
        ResourceLocation to = PortArguments.get(c, "to").id();
        Optional<Voyage> v = Voyages.spawnConvoy(source.getServer(), from, to, source.getLevel().getRandom());
        if (v.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "spawn_failed", PortNameBook.label(from), PortNameBook.label(to)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "spawned"), true);
        source.sendSuccess(() -> line(source.getServer(), v.get()), false);
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
            source.sendSuccess(() -> Component.translatable(KEY + "arrived", v.get().shortId(), PortNameBook.label(v.get().to())), true);
        } else {
            moved.ifPresent(m -> source.sendSuccess(() -> line(source.getServer(), m), false));
        }
        return 1;
    }

    private static int lane(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        Port pf = PortArguments.get(c, "from"), pt = PortArguments.get(c, "to");
        ResourceLocation from = pf.id(), to = pt.id();
        if (!pf.dimension().equals(pt.dimension())) {
            source.sendFailure(Component.translatable(KEY + "no_lane.unknown", PortNameBook.label(from), PortNameBook.label(to)));
            return 0;
        }
        Lanes.Computed r = Lanes.compute(source.getServer(), from, to);
        long ms = r.nanos() / 1_000_000;
        if (r.lane().isEmpty()) {
            source.sendFailure(noLane(from, to, r, ms));
            return 0;
        }
        Lane lane = r.lane().get();
        source.sendSuccess(() -> Component.translatable(KEY + "lane", PortNameBook.label(from), PortNameBook.label(to), Math.round(lane.length()),
                lane.waypoints().size(), r.result().expanded(), r.result().budget(), r.biomeSamples(), ms), false);
        String points = lane.waypoints().stream().map(p -> p.x() + " " + p.z()).collect(Collectors.joining("; "));
        source.sendSuccess(() -> Component.literal(points), false);
        return (int) Math.round(lane.length());
    }

    /** Why there is no lane, in words: no sea at a berth, separate seas, or the budget; ports by their names (NAME3). */
    public static Component noLane(ResourceLocation from, ResourceLocation to, Lanes.Computed r, long ms) {
        com.richardsenger.piratesnships.worldsim.lane.LanePathfinder.Result res = r.result();
        String status = res.status().name().toLowerCase(java.util.Locale.ROOT);
        String f = PortNameBook.label(from), t = PortNameBook.label(to);
        return switch (res.status()) {
            case NO_START -> Component.translatable(KEY + "no_lane.no_start", f, t, status,
                    VoyageConfig.ENDPOINT_RADIUS_CELLS.get(), f, ms);
            case NO_END -> Component.translatable(KEY + "no_lane.no_start", f, t, status,
                    VoyageConfig.ENDPOINT_RADIUS_CELLS.get(), t, ms);
            case NO_PATH -> {
                int cell = VoyageConfig.CELL_BLOCKS.get();
                var b = res.searched();
                yield Component.translatable(KEY + "no_lane.no_path", f, t, status, res.expanded(),
                        f, b == null ? "-" : b.minX() * cell, b == null ? "-" : b.maxX() * cell + cell - 1,
                        b == null ? "-" : b.minZ() * cell, b == null ? "-" : b.maxZ() * cell + cell - 1, t, ms);
            }
            case BUDGET -> Component.translatable(KEY + "no_lane.budget", f, t, status, res.expanded(),
                    res.budget(), Math.round(r.straight()), ms);
            case FOUND -> Component.translatable(KEY + "no_lane", f, t, status, res.expanded(), ms);
        };
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "count", "%s voyages under way (%s lanes queued)")
                .add(KEY + "line", "%s %s %s -> %s: %s/%s blocks at %s %s, %s, cargo %s")
                .add(KEY + "spawned", "A convoy sets sail")
                .add(KEY + "spawn_failed", "No convoy from %s to %s: unknown port, market not open, or no sea lane")
                .add(KEY + "unknown", "No single voyage with an id starting %s")
                .add(KEY + "arrived", "Voyage %s arrived at %s")
                .add(KEY + "no_lane", "No lane from %s to %s (%s after %s cells, %s ms)")
                .add(KEY + "no_lane.unknown", "No lane from %s to %s: unknown port, or the two are in different dimensions")
                .add(KEY + "no_lane.no_start", "No lane from %s to %s (%s): no sea within %s cells of the berth of %s (%s ms)")
                .add(KEY + "no_lane.no_path", "No lane from %s to %s (%s after %s cells): the sea around %s (x %s..%s, z %s..%s) is closed and never reaches %s, the two lie on separate seas (%s ms)")
                .add(KEY + "no_lane.budget", "No lane from %s to %s (%s after %s cells): the search budget of %s cells for %s blocks ran out (%s ms)")
                .add(KEY + "lane", "Lane %s -> %s: %s blocks, %s waypoints, %s of %s cells searched, %s biome samples, %s ms");
    }
}

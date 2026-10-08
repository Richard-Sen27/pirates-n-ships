package com.richardsenger.piratesnships.worldsim.raid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /pirates world raid <port>} starts a raid on a settlement now (ignoring the chance, the cooldown, the target
 * rule and a missing pirate island); {@code /pirates world raid chance} lists the settlements with a presence count,
 * the one the source stands in and the raids under way (operators only, permission 2).
 */
public final class RaidCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.raid.";

    private static final SuggestionProvider<CommandSourceStack> PORTS = (c, b) -> SharedSuggestionProvider.suggestResource(
            PortRegistry.get(c.getSource().getServer()).index().all().stream().map(Port::id), b);

    private RaidCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> raid = Commands.literal("raid")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("chance").executes(RaidCommands::chance))
                .then(Commands.argument("port", ResourceLocationArgument.id()).suggests(PORTS).executes(RaidCommands::start));
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world").requires(s -> s.hasPermission(2)).then(raid)));
    }

    private static int start(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        MinecraftServer server = source.getServer();
        ResourceLocation id = ResourceLocationArgument.getId(c, "port");
        Optional<Port> port = PortRegistry.get(server).index().byId(id);
        if (port.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown", id.toString()));
            return 0;
        }
        Optional<RaidPlanner.Started> started = RaidPlanner.start(server, port.get(), true, source.getLevel().getRandom());
        if (started.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "refused", id.toString()));
            return 0;
        }
        RaidPlanner.Started s = started.get();
        String ids = s.voyages().stream().map(Voyage::shortId).collect(Collectors.joining(", "));
        BlockPos t = s.target();
        source.sendSuccess(() -> Component.translatable(KEY + "started", id.toString(), s.voyages().size(), ids, t.getX(), t.getZ(),
                Component.translatable(KEY + (s.viaLane() ? "via_lane" : "from_sea")), s.bells().size()), true);
        return s.voyages().size();
    }

    private static int chance(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        MinecraftServer server = source.getServer();
        RaidData data = RaidData.get(server);
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        PortRegistry.get(server).index().containing(source.getLevel().dimension(), BlockPos.containing(source.getPosition()))
                .ifPresent(p -> ids.add(p.id()));
        ids.addAll(data.counted());
        RaidRules.Params p = RaidConfig.params(server);
        source.sendSuccess(() -> Component.translatable(KEY + "params", fmt(p.growth()), fmt(p.multiplier()), fmt(p.cap()),
                fmt(p.cooldownDays())), false);
        for (ResourceLocation id : ids) {
            boolean target = PortRegistry.get(server).index().byId(id).map(RaidPlanner::target).orElse(false);
            source.sendSuccess(() -> Component.translatable(KEY + "line", id.toString(), data.minutes(id),
                    fmt(RaidTracker.chance(server, id) * 100.0), fmt(RaidTracker.cooldownLeft(server, id)),
                    Component.translatable(KEY + (target ? "target" : "not_target"))), false);
        }
        for (RaidData.ActiveRaid r : data.raids()) {
            String ships = r.ships().entrySet().stream()
                    .map(e -> e.getKey().toString().substring(0, 8) + " " + e.getValue().phase().getSerializedName())
                    .collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.translatable(KEY + "active", r.port().toString(), ships), false);
        }
        if (ids.isEmpty() && data.raids().isEmpty()) source.sendSuccess(() -> Component.translatable(KEY + "none"), false);
        return ids.size();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "unknown", "No port %s")
                .add(KEY + "refused", "No raid on %s: a pirate island, a raid already under way there, or its dimension is not loaded")
                .add(KEY + "started", "Raid on %s: %s ship(s) (%s) making for %s %s %s; %s bell(s) ringing")
                .add(KEY + "via_lane", "along the lane from the nearest pirate island")
                .add(KEY + "from_sea", "straight in from the sea")
                .add(KEY + "params", "Growth %s per minute x %s (retaliation), cap %s, cooldown %s days")
                .add(KEY + "line", "%s: %s minute(s) of presence, raid chance %s%% per minute, cooldown %s days left, %s")
                .add(KEY + "target", "raided on its own")
                .add(KEY + "not_target", "not raided on its own")
                .add(KEY + "active", "Raid under way on %s: %s")
                .add(KEY + "none", "No player at a settlement and no raid under way");
    }
}

package com.richardsenger.piratesnships.worldsim.faction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /pirates world factions} prints one line per faction (aggression, wealth, tension with the other two);
 * {@code /pirates world factions report <event>} reports a world event (playtests); {@code /pirates world factions
 * reset} puts the start state back (operators only, permission 2). WS1, design.md §10.4.
 */
public final class FactionCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".world.factions.";
    public static final String KEY_LINE = KEY + "line";

    private FactionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("factions").executes(FactionCommands::show)
                        .then(Commands.literal("report").then(Commands.argument("event", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(FactionEvent.values()).map(FactionEvent::id), b))
                                .executes(FactionCommands::report)))
                        .then(Commands.literal("reset").executes(FactionCommands::reset)))));
    }

    private static int show(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        FactionState state = Factions.state(source.getServer());
        for (Faction f : Faction.values()) {
            Component line = line(state, f);
            source.sendSuccess(() -> line, false);
        }
        return Faction.values().length;
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        String id = StringArgumentType.getString(c, "event");
        FactionEvent event;
        try {
            event = FactionEvent.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(KEY + "unknown", id));
            return 0;
        }
        if (!Factions.report(source.getServer(), event)) {
            source.sendFailure(Component.translatable(KEY + "unchanged", event.id()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "reported", event.id()), true);
        return show(c);
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        Factions.set(c.getSource().getServer(), FactionState.INITIAL);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "reset"), true);
        return 1;
    }

    /** One faction's line. */
    static Component line(FactionState state, Faction f) {
        Faction[] others = Arrays.stream(Faction.values()).filter(o -> o != f).toArray(Faction[]::new);
        return Component.translatable(KEY_LINE, name(f), fmt(state.aggression(f)), state.wealth(f),
                name(others[0]), fmt(state.tension(f, others[0])), name(others[1]), fmt(state.tension(f, others[1])));
    }

    public static Component name(Faction f) {
        return Component.translatable(KEY + "faction." + f.name().toLowerCase(Locale.ROOT));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static void lang(LangBuilder lang) {
        lang.add(KEY_LINE, "%s: aggression %s, wealth %s, tension with %s %s, with %s %s")
                .add(KEY + "faction.navy", "Navy")
                .add(KEY + "faction.pirates", "Pirates")
                .add(KEY + "faction.merchants", "Merchants")
                .add(KEY + "reported", "Reported %s")
                .add(KEY + "unchanged", "%s changed nothing (faction state disabled or event scale 0)")
                .add(KEY + "unknown", "Unknown faction event: %s")
                .add(KEY + "reset", "Faction state reset");
    }
}

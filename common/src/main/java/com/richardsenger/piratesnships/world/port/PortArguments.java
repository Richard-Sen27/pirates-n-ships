package com.richardsenger.piratesnships.world.port;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A port argument that takes the id or the name (NAME3): two sibling arguments, {@code <arg>} (a resource location:
 * {@code pirates_n_ships:village_1192_1530}, or a bare word such as {@code greyhaven}) and {@code <arg>_name} (a
 * quoted string: {@code "Port Marlow"}). Brigadier keeps whichever parses; {@link #get} resolves both through
 * {@link PortLookup}, and a name's start that fits several ports fails with the list of them.
 */
public final class PortArguments {

    public static final String KEY = "commands." + Constants.MOD_ID + ".port_argument.";
    public static final String UNKNOWN = KEY + "unknown";
    public static final String AMBIGUOUS = KEY + "ambiguous";

    private static final DynamicCommandExceptionType UNKNOWN_PORT =
            new DynamicCommandExceptionType(q -> Component.translatable(UNKNOWN, q));
    private static final DynamicCommandExceptionType AMBIGUOUS_PORT =
            new DynamicCommandExceptionType(list -> Component.translatable(AMBIGUOUS, list));

    private static final SuggestionProvider<CommandSourceStack> IDS = (c, b) -> SharedSuggestionProvider.suggestResource(
            PortRegistry.get(c.getSource().getServer()).index().all().stream().map(Port::id), b);
    private static final SuggestionProvider<CommandSourceStack> NAMES = (c, b) -> SharedSuggestionProvider.suggest(
            PortRegistry.get(c.getSource().getServer()).index().all().stream().filter(Port::named)
                    .map(p -> StringArgumentType.escapeIfRequired(p.name())), b);

    private PortArguments() {
    }

    /** The id half: {@code <arg>}. */
    public static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> id(String arg) {
        return Commands.argument(arg, ResourceLocationArgument.id()).suggests(IDS);
    }

    /** The name half: {@code <arg>_name}, a quoted string. */
    public static RequiredArgumentBuilder<CommandSourceStack, String> name(String arg) {
        return Commands.argument(arg + "_name", StringArgumentType.string()).suggests(NAMES);
    }

    /**
     * Adds both halves of {@code arg} under {@code parent}; {@code finish} completes each half (its {@code executes} or
     * the next arguments) before it is added.
     */
    public static void addTo(ArgumentBuilder<CommandSourceStack, ?> parent, String arg, Consumer<ArgumentBuilder<CommandSourceStack, ?>> finish) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> byId = id(arg);
        finish.accept(byId);
        parent.then(byId);
        RequiredArgumentBuilder<CommandSourceStack, String> byName = name(arg);
        finish.accept(byName);
        parent.then(byName);
    }

    /** The port {@code arg} names, by id or by name. */
    public static Port get(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        PortIndex index = PortRegistry.get(c.getSource().getServer()).index();
        List<PortLookup.Entry> entries = index.all().stream().map(p -> new PortLookup.Entry(p.id(), p.name())).toList();
        String typed;
        PortLookup.Result r;
        ResourceLocation id = optional(c, arg, ResourceLocation.class);
        if (id != null) {
            typed = id.toString();
            r = PortLookup.find(entries, id);
        } else {
            typed = c.getArgument(arg + "_name", String.class);
            r = PortLookup.find(entries, typed);
        }
        return switch (r) {
            case PortLookup.Result.Found f -> index.byId(f.id()).orElseThrow(() -> UNKNOWN_PORT.create(typed));
            case PortLookup.Result.Ambiguous a -> throw AMBIGUOUS_PORT.create(a.candidates().stream()
                    .map(e -> e.name() + " (" + e.id() + ")").collect(Collectors.joining(", ")));
            case PortLookup.Result.None n -> throw UNKNOWN_PORT.create(typed);
        };
    }

    private static <T> T optional(CommandContext<CommandSourceStack> c, String arg, Class<T> type) {
        try {
            return c.getArgument(arg, type);
        } catch (IllegalArgumentException absent) {
            return null;
        }
    }

    public static void lang(LangBuilder lang) {
        lang.add(UNKNOWN, "No port %s (give its id or its name in quotes)")
                .add(AMBIGUOUS, "Several ports fit, which one? %s");
    }
}

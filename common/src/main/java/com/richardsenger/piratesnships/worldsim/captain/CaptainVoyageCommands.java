package com.richardsenger.piratesnships.worldsim.captain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.mob.captain.CaptainEntry;
import com.richardsenger.piratesnships.mob.captain.CaptainRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * {@code /pirates mob captain voyage <island>} (permission 2, BOS2): the island's captain puts to sea now, whatever his
 * schedule and chance (not while voyages are switched off, he is lost, at sea or on his way home).
 */
public final class CaptainVoyageCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".captain.voyage.";

    private static final SuggestionProvider<CommandSourceStack> ISLANDS = (c, b) -> SharedSuggestionProvider.suggestResource(
            CaptainRegistry.get(c.getSource().getServer()).all().keySet().stream(), b);

    private CaptainVoyageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("mob").requires(s -> s.hasPermission(2))
                .then(Commands.literal("captain").then(Commands.literal("voyage")
                        .then(Commands.argument("island", ResourceLocationArgument.id()).suggests(ISLANDS)
                                .executes(CaptainVoyageCommands::voyage))))));
    }

    private static int voyage(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ResourceLocation island = ResourceLocationArgument.getId(c, "island");
        CaptainVoyages.Departure d = CaptainVoyages.depart(source.getServer(), island, source.getLevel().getRandom());
        if (!d.outcome().sailed() || d.voyage().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + d.outcome().name().toLowerCase(Locale.ROOT), island.toString()));
            return 0;
        }
        String name = CaptainRegistry.get(source.getServer()).get(island).map(CaptainEntry::name).orElse("?");
        source.sendSuccess(() -> Component.translatable(KEY + "sailed", name, island.toString(), d.voyage().get().shortId(),
                d.voyage().get().to().toString(), Math.round(d.voyage().get().length())), true);
        return 1;
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "sailed", "Captain %s of %s puts to sea (voyage %s toward %s, %s blocks out and back)")
                .add(KEY + "disabled", "No voyage for %s: captains' voyages are off (world_simulation.captain.enabled, world_simulation.enabled, mobs.captain.enabled)")
                .add(KEY + "no_captain", "No voyage for %s: it has no living captain")
                .add(KEY + "away", "No voyage for %s: its captain is at sea, on his way home, or a prisoner")
                .add(KEY + "no_island", "No voyage for %s: it is not a known port")
                .add(KEY + "no_route", "No voyage for %s: no open sea to sail from its berth");
    }
}

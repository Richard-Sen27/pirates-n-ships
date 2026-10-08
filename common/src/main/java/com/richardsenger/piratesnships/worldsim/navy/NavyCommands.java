package com.richardsenger.piratesnships.worldsim.navy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;

/**
 * {@code /pirates world patrols}: every navy patrol at sea with its position, state and chase (operators, permission 2;
 * for playtests).
 */
public final class NavyCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.patrols.";

    private NavyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world").requires(s -> s.hasPermission(2))
                .then(Commands.literal("patrols").requires(s -> s.hasPermission(2)).executes(NavyCommands::list))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        MinecraftServer server = source.getServer();
        List<Voyage> patrols = Voyages.active(server).stream().filter(v -> v.kind() == VoyageKind.PATROL).toList();
        source.sendSuccess(() -> Component.translatable(KEY + "count", patrols.size()), false);
        long now = server.overworld().getGameTime();
        for (Voyage v : patrols) {
            Lane.Position p = v.position();
            Component chase = chase(server, v, now);
            source.sendSuccess(() -> Component.translatable(KEY + "line", v.shortId(), v.state().getSerializedName(),
                    Math.round(p.x()), Math.round(p.z()), chase), false);
        }
        return patrols.size();
    }

    private static Component chase(MinecraftServer server, Voyage v, long now) {
        if (v.pursuit().isEmpty()) return Component.translatable(KEY + "on_route", v.from().toString(), v.to().toString());
        String target = v.pursuit().get().toString().substring(0, 8);
        Optional<NavyData.Pursuit> p = NavyData.get(server).get(v.id());
        if (p.isPresent() && p.get().surrenderedUntil() > 0) {
            return Component.translatable(KEY + "shadowing", target, Math.max(0, p.get().surrenderedUntil() - now));
        }
        long since = p.map(x -> now - x.lastContact()).orElse(0L);
        return Component.translatable(KEY + "chasing", target, since);
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "count", "%s navy patrols at sea")
                .add(KEY + "line", "%s %s at %s %s: %s")
                .add(KEY + "on_route", "on patrol from %s to %s")
                .add(KEY + "chasing", "chasing ship %s (%s ticks since contact)")
                .add(KEY + "shadowing", "shadowing ship %s that struck its colours (%s ticks left)");
    }
}

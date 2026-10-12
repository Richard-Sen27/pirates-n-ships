package com.richardsenger.piratesnships.world.port;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.ship.template.ShipTemplateCommands;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /pirates port spot <template>} (STR7, operators): where a ship of {@code template} would appear at the nearest
 * port right now ({@link ShipSpots#atPort}): the footprint, heading, the berth it was found from and how far out, or
 * why there is none. Nothing is placed.
 */
public final class ShipSpotCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".port.spot.";

    private ShipSpotCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("port").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spot").then(Commands.argument("template", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ShipTemplates.TYPE.server().ids(), b))
                        .executes(ShipSpotCommands::spot)));
    }

    private static int spot(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ResourceLocation template = ShipTemplateCommands.resolve(ResourceLocationArgument.getId(c, "template"));
        BlockPos at = BlockPos.containing(source.getPosition());
        Optional<Port> port = PortRegistry.get(source.getServer()).index().nearest(source.getLevel().dimension(), at);
        if (port.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_port"));
            return 0;
        }
        Optional<ShipSpots.Found> found = ShipSpots.atPort(source.getLevel(), port.get(), template);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown", template.toString(), port.get().id().toString()));
            return 0;
        }
        ShipSpots.Found f = found.get();
        if (f.spot().isEmpty()) {
            source.sendFailure(Component.translatable(KEY + (f.search().failure() == ShipSpot.Failure.OCCUPIED ? "occupied" : "none"),
                    port.get().id().toString(), f.search().tried()));
            return 0;
        }
        ShipSpot.Spot s = f.spot().get();
        ShipSpot.Rect r = s.footprint();
        String centre = String.format(Locale.ROOT, "%.1f %d %.1f", r.centreX(), f.surfaceY(), r.centreZ());
        source.sendSuccess(() -> Component.translatable(KEY + "found", port.get().id().toString(), centre,
                s.heading().getName(), s.anchor() + 1, s.distance(), r.minX() + " " + r.minZ() + " .. " + r.maxX() + " " + r.maxZ(),
                f.search().tried()), false);
        return Math.max(1, s.distance());
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "no_port", "No port known in this dimension")
                .add(KEY + "unknown", "No ship template %s, or %s has no berths")
                .add(KEY + "none", "No deep water near the berths of %s (%s footprints tried)")
                .add(KEY + "occupied", "Deep water near the berths of %s is all taken by ships (%s footprints tried)")
                .add(KEY + "found", "At %s: centre %s, bow %s, from berth %s, %s blocks out; footprint %s (%s footprints tried)");
    }
}

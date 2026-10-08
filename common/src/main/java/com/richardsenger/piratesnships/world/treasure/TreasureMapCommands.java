package com.richardsenger.piratesnships.world.treasure;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * {@code /pirates world treasure give [port]} (TM1, operators through {@code /pirates world}): gives the executing
 * player a map bound to the given port's first unfound treasure, or without a port to the nearest pirate island with
 * one (any distance). Works with treasure maps switched off.
 */
public final class TreasureMapCommands {

    private TreasureMapCommands() {
    }

    /** The {@code treasure} node under {@code /pirates world}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("treasure").then(Commands.literal("give")
                .executes(c -> give(c, Optional.empty()))
                .then(Commands.argument("port", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(PortRegistry.get(c.getSource().getServer()).index().all()
                                .stream().filter(p -> p.kind() == PortKind.PIRATE_ISLAND).map(Port::id), b))
                        .executes(c -> give(c, Optional.of(ResourceLocationArgument.getId(c, "port"))))));
    }

    private static int give(CommandContext<CommandSourceStack> c, Optional<ResourceLocation> portId) {
        CommandSourceStack source = c.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.translatable(TreasureMapText.CMD_NO_PLAYER));
            return 0;
        }
        var index = PortRegistry.get(source.getServer()).index();
        Optional<TreasureBinding.Choice> choice;
        if (portId.isPresent()) {
            Optional<Port> port = index.byId(portId.get());
            Optional<TreasureSite> site = port.flatMap(TreasureBinding::firstUnlooted);
            if (site.isEmpty()) {
                source.sendFailure(Component.translatable(TreasureMapText.CMD_NO_PORT, portId.get().toString()));
                return 0;
            }
            choice = Optional.of(new TreasureBinding.Choice(port.get(), site.get()));
        } else {
            choice = TreasureBinding.choose(index.all(), source.getLevel().dimension(), BlockPos.containing(source.getPosition()), Integer.MAX_VALUE);
            if (choice.isEmpty()) {
                source.sendFailure(Component.translatable(TreasureMapText.CMD_NONE));
                return 0;
            }
        }
        TreasureBinding.Choice ch = choice.get();
        ItemStack map = TreasureMapService.boundMap(source.getLevel(), ch.port(), ch.site());
        if (!player.getInventory().add(map)) player.drop(map, false);
        source.sendSuccess(() -> Component.translatable(TreasureMapText.CMD_GIVEN, player.getDisplayName(), ch.port().id().toString(),
                ch.site().pos().toShortString()), true);
        return 1;
    }
}

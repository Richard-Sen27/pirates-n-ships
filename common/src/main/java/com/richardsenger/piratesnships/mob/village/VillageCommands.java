package com.richardsenger.piratesnships.mob.village;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Operators (permission 2, LIFE1): {@code /pirates village spawn <role>} puts a villager of {@code role} at the source
 * position (its workstation and home the spot it stands on, no bed, its role's stall without a port to sell for);
 * {@code /pirates village respawn} fills every village slot that is due now ({@code respawn_days}) and loaded.
 */
public final class VillageCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".village.";
    public static final String SPAWNED = KEY + "spawned";
    public static final String UNKNOWN = KEY + "unknown";
    public static final String DISABLED = KEY + "disabled";
    public static final String RESPAWNED = KEY + "respawned";

    private VillageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("village").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(VillageRole.values()).map(VillageRole::id), b))
                        .executes(VillageCommands::spawn)))
                .then(Commands.literal("respawn").executes(c -> {
                    int n = Residents.check(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.translatable(RESPAWNED, n), true);
                    return n;
                }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "role");
        Optional<VillageRole> role = VillageRole.byId(id);
        if (role.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(UNKNOWN, id));
            return 0;
        }
        if (!VillagerConfig.enabled()) {
            c.getSource().sendFailure(Component.translatable(DISABLED));
            return 0;
        }
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        Direction facing = Direction.fromYRot(c.getSource().getRotation().y);
        VillageResident v = Residents.spawn(c.getSource().getLevel(), null, ResidentPlan.standalone(role.get(), at, facing));
        if (v == null) {
            c.getSource().sendFailure(Component.translatable(DISABLED));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(SPAWNED, v.getDisplayName(),
                Component.translatable(ResidentInteractions.roleKey(role.get()))), true);
        return 1;
    }
}

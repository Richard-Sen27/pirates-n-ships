package com.richardsenger.piratesnships.hazards;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@code /pirates hazard spawn <waterspout|whirlpool> [x y z]} places a hazard with its base at the position (default:
 * the source's), refused when that kind is disabled; {@code /pirates hazard clear [radius]} removes the hazards of the
 * source's level, all of them or those within {@code radius} blocks. Both need permission 2.
 */
public final class HazardCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".hazard.";
    public static final String KEY_SPAWNED = KEY + "spawned";
    public static final String KEY_UNKNOWN = KEY + "unknown";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_FAILED = KEY + "failed";
    public static final String KEY_CLEARED = KEY + "cleared";

    private HazardCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("hazard").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HazardKind.values()).map(HazardKind::id), b))
                        .executes(c -> spawn(c, c.getSource().getPosition()))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> spawn(c, Vec3Argument.getVec3(c, "pos"))))))
                .then(Commands.literal("clear")
                        .executes(c -> clear(c, -1))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0))
                                .executes(c -> clear(c, DoubleArgumentType.getDouble(c, "radius")))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Vec3 at) {
        String id = StringArgumentType.getString(c, "type");
        Optional<HazardKind> kind = HazardKind.byId(id);
        if (kind.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(KEY_UNKNOWN, id));
            return 0;
        }
        if (!enabled(kind.get())) {
            c.getSource().sendFailure(Component.translatable(KEY_DISABLED, id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        HazardEntity e = HazardSpawner.spawn(level, kind.get(), at, level.getRandom());
        if (e == null) {
            c.getSource().sendFailure(Component.translatable(KEY_FAILED, id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(KEY_SPAWNED, id,
                String.format("%.1f", at.x), String.format("%.1f", at.y), String.format("%.1f", at.z)), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c, double radius) {
        int n = clear(c.getSource().getLevel(), c.getSource().getPosition(), radius);
        c.getSource().sendSuccess(() -> Component.translatable(KEY_CLEARED, n), true);
        return n;
    }

    /** Removes the hazards within {@code radius} of {@code at} (a negative radius: all loaded ones); returns how many. */
    public static int clear(ServerLevel level, Vec3 at, double radius) {
        List<? extends HazardEntity> found = radius < 0
                ? collectAll(level)
                : level.getEntitiesOfClass(HazardEntity.class, new AABB(at, at).inflate(radius),
                        e -> e.position().distanceTo(at) <= radius);
        found.forEach(HazardEntity::discard);
        return found.size();
    }

    private static List<HazardEntity> collectAll(ServerLevel level) {
        List<HazardEntity> out = new java.util.ArrayList<>();
        for (HazardKind kind : HazardKind.values()) {
            out.addAll(level.getEntities(HazardsContent.type(kind), e -> true));
        }
        return out;
    }

    public static boolean enabled(HazardKind kind) {
        return switch (kind) {
            case WATERSPOUT -> HazardsConfig.WATERSPOUTS_ENABLED.get();
            case WHIRLPOOL -> HazardsConfig.WHIRLPOOLS_ENABLED.get();
        };
    }
}

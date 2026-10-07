package com.richardsenger.piratesnships.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code /pirates mob spawn <pirate|sailor|navy_soldier|navy_officer> [count]} (permission 2): spawns mobs around the
 * source position, up to {@link #MAX_COUNT} at once. Disabled types ({@code mobs.<type>.enabled}) are refused. Natural
 * spawning waits for the world structures (docs/design.md §10.1).
 */
public final class MobCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".mob.";
    public static final String KEY_SPAWNED = KEY + "spawned";
    public static final String KEY_UNKNOWN = KEY + "unknown";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final int MAX_COUNT = 64;
    private static final double SPREAD = 1.5;

    private MobCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("mob").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MobKind.values()).map(MobKind::id), b))
                        .executes(c -> spawn(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "count"))))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, int count) {
        String id = StringArgumentType.getString(c, "type");
        Optional<MobKind> kind = MobKind.byId(id);
        if (kind.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(KEY_UNKNOWN, id));
            return 0;
        }
        if (!MobConfig.enabled(kind.get()).get()) {
            c.getSource().sendFailure(Component.translatable(KEY_DISABLED, id));
            return 0;
        }
        int n = spawn(c.getSource().getLevel(), kind.get(), c.getSource().getPosition(), count);
        c.getSource().sendSuccess(() -> Component.translatable(KEY_SPAWNED, n, id), true);
        return n;
    }

    /** Spawns {@code count} mobs of {@code kind} around {@code at}; returns how many were added. */
    public static int spawn(ServerLevel level, MobKind kind, Vec3 at, int count) {
        RandomSource random = level.getRandom();
        int added = 0;
        for (int i = 0; i < count; i++) {
            SeafarerMob mob = MobContent.type(kind).create(level);
            if (mob == null) continue;
            double x = at.x + (count == 1 ? 0 : (random.nextDouble() * 2 - 1) * SPREAD);
            double z = at.z + (count == 1 ? 0 : (random.nextDouble() * 2 - 1) * SPREAD);
            mob.moveTo(x, at.y, z, random.nextFloat() * 360f, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, at.y, z)), MobSpawnType.COMMAND, null);
            if (level.addFreshEntity(mob)) added++;
        }
        return added;
    }
}

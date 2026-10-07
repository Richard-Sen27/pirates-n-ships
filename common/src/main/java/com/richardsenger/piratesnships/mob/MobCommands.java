package com.richardsenger.piratesnships.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.ai.DuelistDebug;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code /pirates mob spawn <pirate|sailor|navy_soldier|navy_officer|shark> [count]} (permission 2): spawns mobs
 * around the source position, up to {@link #MAX_COUNT} at once. Disabled types ({@code mobs.<type>.enabled}) are
 * refused. The humanoids' natural spawning waits for the world structures (docs/design.md §10.1); sharks also spawn
 * naturally in oceans.
 *
 * <p>{@code /pirates mob debug <on|off>} (permission 2) traces the fighting mobs near the source ({@link #debug}).
 */
public final class MobCommands {

    public static final String KEY = "commands." + Constants.MOD_ID + ".mob.";
    public static final String KEY_SPAWNED = KEY + "spawned";
    public static final String KEY_UNKNOWN = KEY + "unknown";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final int MAX_COUNT = 64;
    /** The shark's command argument (not a {@link MobKind}: it has no faction, duelist skill or humanoid rig). */
    public static final String SHARK = "shark";
    private static final double SPREAD = 1.5;

    private MobCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("mob").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Stream.concat(Arrays.stream(MobKind.values()).map(MobKind::id), Stream.of(SHARK)), b))
                        .executes(c -> spawn(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(c -> debug(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> debug(c.getSource(), false))))));
    }

    /**
     * {@code /pirates mob debug <on|off>}: traces the state changes of fighting mobs within {@link DuelistDebug#RANGE}
     * blocks of the source (the duelist goal, the target goal, the brain's decision) to the server log, and to the
     * player's chat at a throttled rate. A debug aid for playtests, so the lines are plain English (not translated).
     */
    private static int debug(CommandSourceStack source, boolean on) {
        Object key = source.getPlayer() != null ? source.getPlayer().getUUID() : "source:" + source.getTextName();
        if (on) {
            if (source.getPlayer() != null) DuelistDebug.listen(source.getPlayer());
            else DuelistDebug.listen(key, source.getLevel().dimension(), source.getPosition());
            source.sendSuccess(() -> Component.literal("Mob debug on: state changes of mobs within " + (int) DuelistDebug.RANGE
                    + " blocks go to the server log (and to your chat, at most " + DuelistDebug.CHAT_LINES_PER_SECOND
                    + " lines a second). Difficulty: " + source.getLevel().getDifficulty().getKey()), false);
        } else {
            boolean was = DuelistDebug.unlisten(key);
            source.sendSuccess(() -> Component.literal(was ? "Mob debug off" : "Mob debug was not on"), false);
        }
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, int count) {
        String id = StringArgumentType.getString(c, "type");
        if (SHARK.equals(id)) {
            if (!MobConfig.SHARK_ENABLED.get()) {
                c.getSource().sendFailure(Component.translatable(KEY_DISABLED, id));
                return 0;
            }
            int n = spawn(c.getSource().getLevel(), MobContent.SHARK.get(), c.getSource().getPosition(), count);
            c.getSource().sendSuccess(() -> Component.translatable(KEY_SPAWNED, n, id), true);
            return n;
        }
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
        return spawn(level, MobContent.type(kind), at, count);
    }

    /** Spawns {@code count} mobs of {@code type} around {@code at}; returns how many were added. */
    public static int spawn(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, int count) {
        RandomSource random = level.getRandom();
        int added = 0;
        for (int i = 0; i < count; i++) {
            Mob mob = type.create(level);
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

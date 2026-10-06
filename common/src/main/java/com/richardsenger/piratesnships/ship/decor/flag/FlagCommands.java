package com.richardsenger.piratesnships.ship.decor.flag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.UUID;

/**
 * Operator debug commands (permission 2), without the hoisting delay:
 * <pre>
 * /pirates flag get &lt;pos&gt;
 * /pirates flag set &lt;pos&gt; none|merchant|navy|jolly_roger|custom
 * /pirates flag strike &lt;pos&gt;
 * /pirates flag raise &lt;pos&gt;
 * </pre>
 * {@code set} drops the old flag item at the pole; {@code custom} hoists a plain white banner.
 */
public final class FlagCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".flag.";

    private static final SimpleCommandExceptionType NOT_A_FLAGPOLE = new SimpleCommandExceptionType(Component.translatable(KEY + "not_a_flagpole"));
    private static final SimpleCommandExceptionType UNKNOWN_KIND = new SimpleCommandExceptionType(Component.translatable(KEY + "unknown_kind"));
    private static final SimpleCommandExceptionType NO_FLAG = new SimpleCommandExceptionType(Component.translatable(KEY + "no_flag"));

    private FlagCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("flag")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("get").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(FlagCommands::get)))
                .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(FlagKind.values()).map(FlagKind::getSerializedName), b))
                                .executes(FlagCommands::set))))
                .then(Commands.literal("strike").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> strike(c, true))))
                .then(Commands.literal("raise").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> strike(c, false))))));
    }

    private static FlagpoleBlockEntity pole(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (c.getSource().getLevel().getBlockEntity(pos) instanceof FlagpoleBlockEntity be) return be;
        throw NOT_A_FLAGPOLE.create();
    }

    private static int get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        FlagpoleBlockEntity be = pole(c);
        FlagReading r = be.reading();
        String hoister = be.state().hoistedBy().map(UUID::toString).orElse("-");
        String pending = be.state().pending().map(p -> p.action().getSerializedName() + " in "
                + Math.max(0, p.finishTick() - c.getSource().getLevel().getGameTime()) + "t").orElse("-");
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "get", Component.translatable(FlagpoleBlockEntity.kindKey(r.kind())),
                Component.translatable(KEY + "status." + r.status().name().toLowerCase(java.util.Locale.ROOT)), hoister, pending), false);
        return r.status().ordinal();
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        FlagpoleBlockEntity be = pole(c);
        String id = StringArgumentType.getString(c, "kind");
        FlagKind kind = Arrays.stream(FlagKind.values()).filter(k -> k.getSerializedName().equals(id)).findFirst()
                .orElseThrow(UNKNOWN_KIND::create);
        Entity e = c.getSource().getEntity();
        be.commandSet(kind, false, e == null ? null : e.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "set", Component.translatable(FlagpoleBlockEntity.kindKey(kind))), true);
        return 1;
    }

    private static int strike(CommandContext<CommandSourceStack> c, boolean struck) throws CommandSyntaxException {
        FlagpoleBlockEntity be = pole(c);
        if (!be.state().hasFlag()) throw NO_FLAG.create();
        be.commandStrike(struck);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + (struck ? "struck" : "raised")), true);
        return 1;
    }
}

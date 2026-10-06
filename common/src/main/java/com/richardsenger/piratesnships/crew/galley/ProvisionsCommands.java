package com.richardsenger.piratesnships.crew.galley;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Operator debug commands for provisions before crew NPCs exist. They work on a <em>galley group</em>: the pantry or
 * water barrel you look at (or at {@code pos}) plus every pantry and water barrel within {@link #GROUP_RADIUS} blocks,
 * fed through the same {@link ShipProvisions} entry point the crew system will use. Every run starts from a fresh
 * {@link ProvisioningState} (no carried-over credit, hunger or scurvy clock).
 *
 * <pre>
 * /pirates provisions show &lt;crew&gt; [pos]
 * /pirates provisions advance &lt;days&gt; &lt;crew&gt; [prisoners] [rum] [pos]
 * </pre>
 */
public final class ProvisionsCommands {

    public static final int GROUP_RADIUS = 4;
    private static final SimpleCommandExceptionType NOT_A_CONTAINER =
            new SimpleCommandExceptionType(Component.translatable(GalleyText.NOT_A_CONTAINER));

    private ProvisionsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var show = Commands.literal("show").then(Commands.argument("crew", IntegerArgumentType.integer(0, 1000))
                .executes(c -> show(c, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> show(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))));
        var rum = Commands.argument("rum", DoubleArgumentType.doubleArg(0, 10))
                .executes(c -> advance(c, IntegerArgumentType.getInteger(c, "prisoners"), DoubleArgumentType.getDouble(c, "rum"), null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> advance(c, IntegerArgumentType.getInteger(c, "prisoners"), DoubleArgumentType.getDouble(c, "rum"),
                                BlockPosArgument.getLoadedBlockPos(c, "pos"))));
        var prisoners = Commands.argument("prisoners", IntegerArgumentType.integer(0, 1000))
                .executes(c -> advance(c, IntegerArgumentType.getInteger(c, "prisoners"), 1.0, null))
                .then(rum);
        var advance = Commands.literal("advance").then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 1000))
                .then(Commands.argument("crew", IntegerArgumentType.integer(0, 1000))
                        .executes(c -> advance(c, 0, 1.0, null))
                        .then(prisoners)));
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("provisions")
                .requires(s -> s.hasPermission(2))
                .then(show)
                .then(advance)));
    }

    private static int show(CommandContext<CommandSourceStack> c, BlockPos at) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> group = group(level, target(c.getSource(), at));
        int crew = IntegerArgumentType.getInteger(c, "crew");
        for (Component line : showLines(level, group, crew)) {
            c.getSource().sendSuccess(() -> line, false);
        }
        return group.size();
    }

    private static int advance(CommandContext<CommandSourceStack> c, int prisoners, double rum, BlockPos at) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> group = group(level, target(c.getSource(), at));
        double days = DoubleArgumentType.getDouble(c, "days");
        int crew = IntegerArgumentType.getInteger(c, "crew");
        for (Component line : advanceLines(level, group, days, new CrewHeadcount(crew, prisoners, rum))) {
            c.getSource().sendSuccess(() -> line, true);
        }
        return group.size();
    }

    /** The report of {@code show}. */
    public static List<Component> showLines(ServerLevel level, List<BlockPos> group, int crew) {
        ProvisionSettings s = ProvisionsConfig.settings();
        List<Component> lines = new ArrayList<>();
        lines.add(groupLine(level, group));
        ProvisionStore store = ShipProvisions.store(level, group);
        lines.addAll(GalleyText.pantryInfo(PantryInfo.of(store, s)));
        lines.add(GalleyText.days(crew, ProvisionRules.suppliesLeft(store, ProvisioningState.INITIAL, CrewHeadcount.crew(crew), s)));
        return lines;
    }

    /** Runs {@code advance} and returns its report. */
    public static List<Component> advanceLines(ServerLevel level, List<BlockPos> group, double days, CrewHeadcount crew) {
        List<Component> lines = new ArrayList<>();
        if (!ProvisionsConfig.CONSUMPTION_ENABLED.get()) {
            lines.add(Component.translatable(GalleyText.CMD_DISABLED));
        }
        long ticks = Math.round(days * ProvisionSettings.TICKS_PER_DAY);
        ShipProvisions.Result r = ShipProvisions.simulate(level, group, ProvisioningState.INITIAL, crew, ticks);
        lines.add(groupLine(level, group));
        lines.add(Component.translatable(GalleyText.CMD_ADVANCED, GalleyText.number(days), crew.crew(), crew.prisoners(),
                GalleyText.number(crew.rumRations())));
        lines.addAll(GalleyText.outcome(r.update().outcome()));
        lines.add(GalleyText.days(crew.crew(), ShipProvisions.suppliesLeft(level, group, r.state(), CrewHeadcount.crew(crew.crew()))));
        return lines;
    }

    private static Component groupLine(ServerLevel level, List<BlockPos> group) {
        int pantries = 0, barrels = 0;
        for (BlockPos p : group) {
            if (level.getBlockEntity(p) instanceof PantryBlockEntity) pantries++;
            else if (level.getBlockEntity(p) instanceof WaterBarrelBlockEntity) barrels++;
        }
        return Component.translatable(GalleyText.CMD_CONTAINERS, pantries, barrels, GROUP_RADIUS);
    }

    private static BlockPos target(CommandSourceStack source, BlockPos at) throws CommandSyntaxException {
        if (at != null) {
            return at;
        }
        Entity e = source.getEntity();
        if (e != null && e.pick(8.0, 0.0f, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        throw NOT_A_CONTAINER.create();
    }

    /** The target container plus all pantries and water barrels around it. */
    public static List<BlockPos> group(ServerLevel level, BlockPos target) throws CommandSyntaxException {
        if (!(level.getBlockEntity(target) instanceof ProvisionContainer)) {
            throw NOT_A_CONTAINER.create();
        }
        List<BlockPos> out = new ArrayList<>();
        out.add(target.immutable());
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-GROUP_RADIUS, -GROUP_RADIUS, -GROUP_RADIUS),
                target.offset(GROUP_RADIUS, GROUP_RADIUS, GROUP_RADIUS))) {
            if (!p.equals(target) && level.getBlockEntity(p) instanceof ProvisionContainer) {
                out.add(p.immutable());
            }
        }
        return out;
    }
}

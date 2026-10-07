package com.richardsenger.piratesnships.station;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Operator commands for crew stations (permission 2), for tests and playtests:
 * {@code /pirates crew spawn}, {@code assign <crew> <station pos>}, {@code release <crew>},
 * {@code order <hoist|reef|furl|pump> [crew]}. A station position may be the block's world position (as seen in game,
 * F3) or its plot position.
 */
public final class StationCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".crew.";
    public static final String KEY_UNKNOWN_ORDER = KEY + "unknown_order";
    public static final String KEY_ORDERED = KEY + "ordered";

    private StationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("crew").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").executes(StationCommands::spawn))
                .then(Commands.literal("assign").then(Commands.argument("crew", EntityArgument.entity())
                        .then(Commands.argument("station", BlockPosArgument.blockPos()).executes(StationCommands::assign))))
                .then(Commands.literal("release").then(Commands.argument("crew", EntityArgument.entities()).executes(StationCommands::release)))
                .then(Commands.literal("order").then(Commands.argument("order", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CrewOrder.ids(), b))
                        .executes(c -> order(c, null))
                        .then(Commands.argument("crew", EntityArgument.entities()).executes(c -> order(c, EntityArgument.getEntities(c, "crew"))))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Vec3 p = c.getSource().getPosition();
        CrewMember crew = StationContent.CREW_MEMBER.get().create(level);
        if (crew == null) return 0;
        crew.moveTo(p.x, p.y, p.z, c.getSource().getRotation().y, 0);
        crew.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.COMMAND, null);
        level.addFreshEntity(crew);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "spawned"), true);
        return 1;
    }

    private static int assign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        Entity e = EntityArgument.getEntity(c, "crew");
        if (!(e instanceof CrewMember crew)) {
            c.getSource().sendFailure(Component.translatable(KEY + "not_crew"));
            return 0;
        }
        BlockPos plot = stationPlotPos(level, BlockPosArgument.getBlockPos(c, "station"));
        CrewStations.AssignResult r = plot == null ? CrewStations.AssignResult.NOT_A_STATION : CrewStations.assign(level, crew, plot);
        Component msg = CaptainsWhistleItem.assignMessage(r, crew);
        if (r == CrewStations.AssignResult.ASSIGNED) {
            c.getSource().sendSuccess(() -> msg, true);
            return 1;
        }
        c.getSource().sendFailure(msg);
        return 0;
    }

    private static int release(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "crew")) {
            if (e instanceof CrewMember crew && crew.assignment() != null) {
                CrewStations.release(c.getSource().getLevel(), crew);
                n++;
            }
        }
        int released = n;
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "released", released), true);
        return n;
    }

    /**
     * Without a crew argument the order goes to the assigned crew within {@code order_radius} whose station takes it
     * (as the whistle's orders do); named crew members at another kind of station refuse it.
     */
    private static int order(CommandContext<CommandSourceStack> c, @Nullable Collection<? extends Entity> targets) {
        CrewOrder order = CrewOrder.byId(StringArgumentType.getString(c, "order").toLowerCase(Locale.ROOT)).orElse(null);
        if (order == null) {
            c.getSource().sendFailure(Component.translatable(KEY_UNKNOWN_ORDER));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        List<CrewMember> crew = new ArrayList<>();
        if (targets == null) {
            double r = StationConfig.ORDER_RADIUS.get();
            crew.addAll(level.getEntitiesOfClass(CrewMember.class, new AABB(c.getSource().getPosition(), c.getSource().getPosition()).inflate(r),
                    m -> CrewStations.takes(level, m, order)));
        } else {
            for (Entity e : targets) if (e instanceof CrewMember m && m.assignment() != null) crew.add(m);
        }
        int n = 0;
        for (CrewMember m : crew) {
            if (CrewStations.order(level, m, order) == Stations.OrderResult.STARTED) n++;
        }
        int started = n;
        c.getSource().sendSuccess(() -> Component.translatable(KEY_ORDERED, Component.translatable(order.nameKey()), started, crew.size()), true);
        return n;
    }

    /** Plot position of the station at {@code pos}: {@code pos} itself if it is in a plot, else the ship block seen there. */
    static @Nullable BlockPos stationPlotPos(ServerLevel level, BlockPos pos) {
        if (SableShips.containing(level, pos) != null) {
            return pos;
        }
        for (ShipBody ship : SableShips.all(level)) {
            if (CrewStations.worldBox(ship, 1).contains(Vec3.atCenterOf(pos))) {
                BlockPos plot = BlockPos.containing(ship.toPlot(Vec3.atCenterOf(pos)));
                if (level.getBlockState(plot).getBlock() instanceof StationBlock) return plot;
            }
        }
        return null;
    }
}

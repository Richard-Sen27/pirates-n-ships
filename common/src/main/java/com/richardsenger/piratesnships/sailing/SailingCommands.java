package com.richardsenger.piratesnships.sailing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.ForceContribution;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Operator commands (permission 2):
 * <pre>
 * /pirates wind get                          the wind at the caller (and whether it is overridden)
 * /pirates wind set &lt;fromDegrees&gt; &lt;strength&gt;   fixed wind for this dimension: blowing FROM the compass bearing
 *                                            (0 = from the north, 90 = from the east), in blocks/s, until cleared
 * /pirates wind clear
 * /pirates ship forces                       last sail/keel force breakdown of the ship the caller stands on
 * </pre>
 * The force printout is in the ship frame: {@code fwd} toward the bow, {@code port} to the left, {@code up}.
 */
public final class SailingCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".";

    private SailingCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates")
                .then(Commands.literal("wind").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("get").executes(SailingCommands::windGet))
                        .then(Commands.literal("set").then(Commands.argument("fromDegrees", DoubleArgumentType.doubleArg(-360, 720))
                                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0, 100)).executes(SailingCommands::windSet))))
                        .then(Commands.literal("clear").executes(SailingCommands::windClear)))
                .then(Commands.literal("ship").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("forces").executes(SailingCommands::forces))));
    }

    private static String dim(CommandSourceStack s) {
        return s.getLevel().dimension().location().toString();
    }

    private static int windGet(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        WindSample w = WindService.sample(s.getLevel(), s.getPosition());
        boolean fixed = WindOverride.get(dim(s), s.getLevel().getGameTime()) != null;
        s.sendSuccess(() -> Component.translatable(KEY + "wind.get", fmt(w.fromDegrees()), fmt(w.strength()),
                Component.translatable(KEY + (fixed ? "wind.fixed" : "wind.natural"))), false);
        return 1;
    }

    private static int windSet(CommandContext<CommandSourceStack> c) {
        double from = DoubleArgumentType.getDouble(c, "fromDegrees");
        double strength = DoubleArgumentType.getDouble(c, "strength");
        WindOverride.set(dim(c.getSource()), from, strength, WindOverride.FOREVER);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "wind.set", fmt(WindSample.normalizeDegrees(from)), fmt(strength)), true);
        return 1;
    }

    private static int windClear(CommandContext<CommandSourceStack> c) {
        WindOverride.clear(dim(c.getSource()));
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "wind.clear"), true);
        return 1;
    }

    private static int forces(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        ShipBody ship = shipAt(s.getLevel(), s.getPosition());
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (ship == null || rt == null) {
            s.sendFailure(Component.translatable(KEY + "ship.none"));
            return 0;
        }
        Vector3d v = rt.shipFrameVelocity(ship, new Vector3d());
        WindSample w = WindService.sample(s.getLevel(), ship.worldBounds().getCenter());
        double heading = rt.headingDegrees(ship);
        // apparent wind angle off the bow (0 = from ahead), from true wind minus hull velocity, deck plane
        Vector3d lin = new Vector3d();
        ship.velocities(lin, new Vector3d());
        Vector3d apparent = w.velocity(new Vector3d()).sub(lin);
        double awa = Math.abs(WindSample.normalizeDegrees(Math.toDegrees(Math.atan2(-apparent.x, apparent.z)) - heading + 180.0) - 180.0);
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        s.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "speed %.2f m/s (fwd %.2f, port %.2f), heading %.0f°, bow %s, wind from %.0f° at %.1f, apparent wind %.0f° off the bow, submerged %.2f, sails %d (%d set)",
                speed, v.z, v.x, heading, rt.bow().name(), w.fromDegrees(), w.strength(), awa, rt.lastSubmerged(),
                rt.sailCount(), rt.unfurledCount())), false);
        ForceBreakdown f = rt.lastBreakdown();
        if (f == null) {
            s.sendSuccess(() -> Component.translatable(KEY + "ship.idle"), false);
            return 1;
        }
        for (ForceContribution fc : f.contributions()) {
            s.sendSuccess(() -> Component.literal(line(fc.source(), fc.force(), fc.torque())), false);
        }
        s.sendSuccess(() -> Component.literal(line("total", f.force(), f.torque())), false);
        return 1;
    }

    private static String line(String name, Vector3dc force, Vector3dc torque) {
        return String.format(Locale.ROOT, "%s: force fwd %.1f port %.1f up %.1f | torque roll %.1f pitch %.1f yaw %.1f",
                name, force.z(), force.x(), force.y(), torque.z(), torque.x(), torque.y());
    }

    /** The ship whose world bounds contain {@code pos} or the block below it (standing on deck). */
    private static @Nullable ShipBody shipAt(ServerLevel level, Vec3 pos) {
        for (ShipBody ship : SableShips.all(level)) {
            AABB b = ship.worldBounds().inflate(0.5, 1.5, 0.5);
            if (b.contains(pos)) {
                return ship;
            }
        }
        ShipBody inPlot = SableShips.containing(level, BlockPos.containing(pos));
        return inPlot;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }
}

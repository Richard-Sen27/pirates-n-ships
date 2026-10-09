package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The climbing assist of the ratline nets (RL2, {@code rigging.net_assist}). Vanilla climbs a climbable block only
 * straight up while the climber's feet are in it, so on a diagonal net a climber rises out of the cell column and
 * falls back; this rule carries a player who climbs inside a net cell along the net's line instead
 * ({@link RatlineNetRules#assist}): forward or jump climbs up, back climbs down, sneaking holds, nothing slides down
 * slowly as on a ladder.
 *
 * <p>It runs at the end of each player tick ({@code CommonEvents.PLAYER_TICK_END}) on the side that moves the player:
 * the client for the local player (vanilla movement is the client's; the server only checks it), the server for a
 * player the server moves itself (a GameTest mock player). The velocity it sets is the one the next tick moves by.
 * On a ship the net, the cells and the climber's feet meet in the ship's plot frame: Sable's
 * {@code climbing_sub_levels} mixin hands {@code onClimbable} the plot cell ({@code LivingEntityMixin#sable$redirectPos},
 * so vanilla stores it as {@code lastClimbablePos}); the feet go into the plot and the velocity back out through the
 * ship's pose ({@link ShipBody#toPlot} and {@link ShipBody#orientation} on the server, a client frame installed by
 * {@code RiggingClient} on the client).
 */
public final class RatlineNetAssist {

    /** Climbing speed along the line's height [blocks/tick]: vanilla's ladder climb (0.2 less gravity, after drag). */
    public static final double CLIMB_SPEED = 0.1176;
    /** Vanilla's fastest slide down a climbable block [blocks/tick]. */
    public static final double SLIDE_SPEED = 0.15;

    /** A ship's plot frame: world positions into the plot, plot directions out to the world and back. */
    public interface Frame {
        Vec3 toLocal(Vec3 world);

        Vec3 dirToWorld(Vec3 local);

        Vec3 dirToLocal(Vec3 world);
    }

    /** Finds the frame of the ship whose plot holds {@code plotPos}; null in the world. */
    @FunctionalInterface
    public interface Frames {
        @Nullable Frame at(Level level, BlockPos plotPos);
    }

    private static volatile Frames clientFrames = (level, pos) -> null;

    private RatlineNetAssist() {
    }

    /** Physical client: how the client finds a ship's frame (its render pose; the server's {@link ShipBody} is server-only). */
    public static void installClientFrames(Frames frames) {
        clientFrames = frames;
    }

    /** {@code PLAYER_TICK_END}, both sides. */
    public static void onPlayerTick(Player player) {
        Level level = player.level();
        boolean movesHere = level.isClientSide ? player.isLocalPlayer() : !(player instanceof ServerPlayer);
        if (!movesHere || !RiggingConfig.NET_ASSIST.get() || player.isSpectator() || player.getAbilities().flying
                || player.isPassenger() || !player.onClimbable()) {
            return;
        }
        BlockPos cell = player.getLastClimbablePos().orElse(null);
        if (cell == null) {
            return;
        }
        Net net = netAt(level, cell);
        if (net == null) {
            return;
        }
        Frame frame = frame(level, cell);
        Vec3 feet = frame == null ? player.position() : frame.toLocal(player.position());
        Vec3 v = frame == null ? player.getDeltaMovement() : frame.dirToLocal(player.getDeltaMovement());
        double[] out = RatlineNetRules.assist(net.ringA(), net.ringB(), new Point(feet.x, feet.y, feet.z),
                climb(v.y, player.zza, player.isShiftKeyDown()));
        Vec3 local = new Vec3(out[0], out[1], out[2]);
        player.setDeltaMovement(frame == null ? local : frame.dirToWorld(local));
    }

    /**
     * The vertical speed along the net for a climber whose vertical velocity is {@code vy} after vanilla's tick and
     * whose forward input is {@code forward}: forward (or vanilla's jump climb) up, back down, sneaking holds, else
     * vanilla's slow slide.
     */
    public static double climb(double vy, float forward, boolean sneaking) {
        if (forward > 0 || vy > 0.05) {
            return CLIMB_SPEED;
        }
        if (forward < 0) {
            return -SLIDE_SPEED;
        }
        if (sneaking) {
            return 0;
        }
        return Math.max(vy, -SLIDE_SPEED);
    }

    /**
     * The net a climber at the climbable block {@code pos} hangs on: the net of a net cell, or the net a fitting holds
     * (the fittings are climbable too, so a climber gets onto the net from the chainplate's block and off it into the
     * hoop's); in a fill cell of a fan (RL4) the line through the cell along its climb direction to the hoop
     * ({@link RatlineNets#climbLine}).
     */
    static @Nullable Net netAt(Level level, BlockPos pos) {
        return RatlineNets.climbLine(level, pos);
    }

    private static @Nullable Frame frame(Level level, BlockPos cell) {
        if (level instanceof ServerLevel server) {
            ShipBody ship = SableShips.containing(server, cell);
            return ship == null ? null : new ShipFrame(ship);
        }
        return clientFrames.at(level, cell);
    }

    /** The server's frame of a ship: {@link ShipBody#toPlot} and its orientation. */
    private record ShipFrame(ShipBody ship) implements Frame {
        @Override
        public Vec3 toLocal(Vec3 world) {
            return ship.toPlot(world);
        }

        @Override
        public Vec3 dirToWorld(Vec3 local) {
            return rotate(ship.orientation(), local);
        }

        @Override
        public Vec3 dirToLocal(Vec3 world) {
            return rotate(ship.orientation().conjugate(), world);
        }
    }

    /** {@code v} turned by {@code q}. */
    public static Vec3 rotate(Quaterniond q, Vec3 v) {
        Vector3d d = q.transform(new Vector3d(v.x, v.y, v.z));
        return new Vec3(d.x, d.y, d.z);
    }
}

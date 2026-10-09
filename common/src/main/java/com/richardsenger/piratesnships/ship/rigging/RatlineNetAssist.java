package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
 *
 * <p><b>A ship under way (RL3).</b> The velocity the assist sets is the climber's motion relative to the net; the ship's
 * own motion reaches the climber as a carry of its position ({@link #move}). Sable carries an entity it tracks, one
 * that landed on the ship's blocks and has stayed among them since, net cells included: its collision moves the entity
 * by the ship's pose change in every substep while any non-air plot block lies around it
 * ({@code sublevel/entity_collision/SubLevelEntityCollision#collide}, l.236-259), and
 * {@code mixin/entity/entity_sublevel_collision/EntityMixin#sable$collideRedirect} keeps the tracked ship (it sets one
 * only on a collision from below, l.140-148). That carry moves the position, not the velocity, so overwriting the
 * velocity does not cancel it, and the assist adds nothing then. A climber Sable does not track (one that got onto the
 * net without landing on the ship) gets the ship's motion at its feet from the assist itself: {@link Frame#velocityAt},
 * Sable's velocity of a plot point.
 *
 * <p><b>Off the top (RL3).</b> Over the higher ring the pull lets go of a climber that climbs on: it rises straight up,
 * keeps its own horizontal velocity, and is lifted on past the top fitting's block by up to {@link #EXIT_LIFT}
 * ({@link #liftingOff}), so forward walks it off onto whatever stands beside or over the top. Holding or climbing down
 * at the top it is pulled back onto the line; a player stepping off a platform into the top fitting is caught there.
 */
public final class RatlineNetAssist {

    /** Climbing speed along the line's height [blocks/tick]: vanilla's ladder climb (0.2 less gravity, after drag). */
    public static final double CLIMB_SPEED = 0.1176;
    /** Vanilla's fastest slide down a climbable block [blocks/tick]. */
    public static final double SLIDE_SPEED = 0.15;
    /** Game ticks per second: Sable's velocities are per second, the climber's per tick. */
    public static final double TICKS_PER_SECOND = 20.0;
    /** Sable's lift of the feet when it looks up the block an entity is in ({@code EntityMixin#getInBlockState}). */
    private static final double FEET_LIFT = 0.001;
    /**
     * How far over the top fitting's block the climb carries a climber that climbs on at the top of a net [blocks]:
     * vanilla's step height, so forward then walks it off onto a floor, a deck or a crow's nest beside or over the top.
     */
    public static final double EXIT_LIFT = 0.6;

    /** A ship's plot frame: world positions into the plot, plot directions out to the world and back, its motion. */
    public interface Frame {
        Vec3 toLocal(Vec3 world);

        Vec3 dirToWorld(Vec3 local);

        Vec3 dirToLocal(Vec3 world);

        /** World-frame velocity of the ship at plot point {@code local} [blocks/tick]: linear plus angular × radius. */
        Vec3 velocityAt(Vec3 local);

        /** True when Sable carries {@code entity} with this ship itself (it tracks it, see the class comment). */
        boolean carries(Entity entity);
    }

    /** Finds the frame of the ship whose plot holds {@code plotPos}; null in the world. */
    @FunctionalInterface
    public interface Frames {
        @Nullable Frame at(Level level, BlockPos plotPos);
    }

    /**
     * One tick of the assist on a ship: the velocity to set (the climb, in the world) and the distance to carry the
     * climber's position by (the ship's motion at its feet over the tick; none when Sable carries it already).
     */
    public record Move(Vec3 velocity, Vec3 carry) {
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
                || player.isPassenger()) {
            return;
        }
        // off a climbable block only the lift off the top can apply: climbing on, in the air, just over the top fitting
        boolean climbing = player.onClimbable();
        if (!climbing && (player.zza <= 0 || player.onGround())) {
            return;
        }
        BlockPos cell = player.getLastClimbablePos().orElse(null);
        if (cell == null) {
            return;
        }
        Net net = netAt(level, cell);
        Frame frame = frame(level, cell);
        if (net == null && frame != null && climbing) {
            // while Sable tracks the climber on a moving ship its climbing redirect can hand vanilla a stale cell, e.g.
            // the deck plank under a climber at the chainplate (climbing_sub_levels/LivingEntityMixin#sable$redirectPos
            // reads sable$getInBlockStatePos before getInBlockState, EntityMixin l.264, refreshes it): use the feet's cell
            net = netAt(level, BlockPos.containing(frame.toLocal(player.position()).add(0, FEET_LIFT, 0)));
        }
        if (net == null) {
            return;
        }
        Vec3 feet = frame == null ? player.position() : frame.toLocal(player.position());
        if (!climbing && !liftingOff(top(net), feet)) {
            return;
        }
        Vec3 v = frame == null ? player.getDeltaMovement() : frame.dirToLocal(player.getDeltaMovement());
        double[] out = RatlineNetRules.assist(net.ringA(), net.ringB(), new Point(feet.x, feet.y, feet.z),
                climb(v.y, player.zza, player.isShiftKeyDown()), v.x, v.z);
        Vec3 local = new Vec3(out[0], out[1], out[2]);
        if (frame == null) {
            player.setDeltaMovement(local);
            return;
        }
        Move move = move(frame, local, feet, frame.carries(player));
        player.setDeltaMovement(move.velocity());
        if (move.carry().lengthSqr() > 0) {
            player.setPos(player.position().add(move.carry()));
        }
    }

    /** The net's top fitting: the one of the higher ring. */
    static BlockPos top(Net net) {
        return net.ringA().y() <= net.ringB().y() ? net.b() : net.a();
    }

    /**
     * True while a climber whose feet are at {@code feet} (the net's frame) is lifting off the top of a net (RL3): in the
     * column over the top fitting {@code top}, its feet out of the fitting's block and less than {@link #EXIT_LIFT}
     * above it. The fitting is the last climbable block there, so without this the climber would drop back into it.
     */
    public static boolean liftingOff(BlockPos top, Vec3 feet) {
        return Mth.floor(feet.x) == top.getX() && Mth.floor(feet.z) == top.getZ()
                && feet.y >= top.getY() + 1 && feet.y < top.getY() + 1 + EXIT_LIFT;
    }

    /**
     * The assist's tick on a ship (RL3): the climb {@code climbLocal} (plot frame, relative to the net) turned into the
     * world as the climber's velocity, and the ship's motion at the feet {@code feetLocal} as a carry of the position,
     * unless Sable carries the climber already ({@code sableCarries}). The velocity stays the motion relative to the
     * net, so vanilla's limits on a climbable block ({@code LivingEntity#handleOnClimbable}, 0.15 blocks/tick across) and
     * the climb input ({@link #climb}) only ever see that, however fast the ship sails.
     */
    public static Move move(Frame frame, Vec3 climbLocal, Vec3 feetLocal, boolean sableCarries) {
        return new Move(frame.dirToWorld(climbLocal), sableCarries ? Vec3.ZERO : frame.velocityAt(feetLocal));
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

    /**
     * The server's frame of a ship: {@link ShipBody#toPlot}, its orientation, {@link ShipBody#velocityAt} (per second,
     * so ÷ 20) and whether Sable tracks the entity on it ({@link ShipEntities#standingOrRiding}; a climber rides nothing).
     */
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

        @Override
        public Vec3 velocityAt(Vec3 local) {
            return ship.velocityAt(local).scale(1.0 / TICKS_PER_SECOND);
        }

        @Override
        public boolean carries(Entity entity) {
            ShipBody on = ShipEntities.standingOrRiding(entity);
            return on != null && on.id().equals(ship.id());
        }
    }

    /** {@code v} turned by {@code q}. */
    public static Vec3 rotate(Quaterniond q, Vec3 v) {
        Vector3d d = q.transform(new Vector3d(v.x, v.y, v.z));
        return new Vec3(d.x, d.y, d.z);
    }
}

package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of steering by the wheel (docs/design.md §5.3, HELM1). Using the helm of an assembled ship starts a
 * session for that player ({@link #steer}, installed as the helm's steering handler); while it lasts, the client sends
 * wheel deltas ({@link HelmWheelPayload}) that {@link #turn} clamps to {@code helm.wheel.max_degrees_per_tick} per
 * tick and to the locks, integrates into the wheel angle of the helm's block entity and hands to the ship's runtime
 * ({@link ShipControls#setWheel}), whose rudder follows linearly. The session ends when the client lets go of use
 * ({@link HelmReleasePayload}), or on the server when the player leaves, dies, walks more than
 * {@code helm.wheel.session_reach} blocks from the helm, the ship is disassembled or wheel steering is switched off
 * ({@link SessionRules}). With {@code helm.wheel.drag_steering} off, using the helm runs spike 3's click steps.
 */
public final class HelmService {

    public static final String KEY = Constants.MOD_ID + ".helm.";
    public static final String KEY_HOLDING = KEY + "holding";
    public static final String KEY_RUDDER = KEY + "rudder";
    public static final String KEY_BUSY = KEY + "busy";

    /** One helmsman at one wheel. */
    static final class Session {
        final Player player;
        final ServerLevel level;
        final BlockPos pos;
        long tick = Long.MIN_VALUE;
        double used;

        Session(Player player, ServerLevel level, BlockPos pos) {
            this.player = player;
            this.level = level;
            this.pos = pos.immutable();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HelmService() {
    }

    /** {@link HelmBlock.SteeringHandler}: takes the wheel, or runs the click steps when wheel steering is off. */
    public static Component steer(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
        if (!SailingConfig.STEERING_ENABLED.get()) {
            return Component.translatable(ShipControls.KEY_STEERING_OFF);
        }
        if (!HelmConfig.DRAG_STEERING.get()) {
            return ShipControls.steer(level, pos, state, player, hit);
        }
        return start(level, pos, player);
    }

    /** {@code player} takes the wheel of the helm at {@code pos} (plot). Refused while another player holds it. */
    public static Component start(ServerLevel level, BlockPos pos, Player player) {
        for (Session s : SESSIONS.values()) {
            if (s.player != player && s.level == level && s.pos.equals(pos) && present(s.player)) {
                return Component.translatable(KEY_BUSY);
            }
        }
        Session previous = SESSIONS.put(player.getUUID(), new Session(player, level, pos));
        if (previous != null && (previous.level != level || !previous.pos.equals(pos))) {
            notify(previous.player, previous.pos, false, 0f); // the old wheel of this player is let go
        }
        float wheel = wheel(level, pos);
        // a helm from before HELM1 gets its block entity now: send it, so the client draws the wheel
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        notify(player, pos, true, wheel);
        return Component.translatable(KEY_HOLDING, rudderMessage(rudderAngle(wheel)));
    }

    /**
     * Applies a wheel delta from {@code player} to the helm at {@code pos}: only for the player's own session at that
     * helm, limited per tick and by the locks. Returns false when the player holds no wheel there.
     */
    public static boolean turn(Player player, BlockPos pos, double delta) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.player != player || !s.pos.equals(pos) || !(player.level() == s.level)) {
            return false;
        }
        long now = s.level.getGameTime();
        if (s.tick != now) {
            s.tick = now;
            s.used = 0.0;
        }
        WheelMath.Step step = WheelMath.turn(wheel(s.level, pos), s.used, delta, HelmConfig.MAX_DEGREES_PER_TICK.get(),
                HelmConfig.lockAngle());
        s.used = step.usedThisTick();
        ShipControls.setWheel(s.level, pos, step.wheel());
        return true;
    }

    /** The client let go of use: ends {@code player}'s session at {@code pos}. False when there was none. */
    public static boolean release(Player player, BlockPos pos) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.pos.equals(pos)) {
            return false;
        }
        end(player.getUUID(), s, SessionRules.End.RELEASED);
        return true;
    }

    /** The helm {@code player} holds right now (plot position), or null. */
    public static @Nullable BlockPos session(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null || s.player != player ? null : s.pos;
    }

    /** Whether a player holds the wheel of the helm at {@code pos} (plot) in {@code level} right now (WS3a: the NPC helmsman yields). */
    public static boolean isHeld(ServerLevel level, BlockPos pos) {
        for (Session s : SESSIONS.values()) {
            if (s.level == level && s.pos.equals(pos) && present(s.player)) {
                return true;
            }
        }
        return false;
    }

    /** The wheel angle of the helm at {@code pos}, 0 without a block entity. */
    public static float wheel(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HelmBlockEntity be ? be.wheel() : 0f;
    }

    /** Rudder angle for a wheel angle at the current config. */
    public static double rudderAngle(double wheel) {
        return WheelMath.rudderAngle(wheel, HelmConfig.lockAngle(), SailingConfig.MAX_RUDDER_ANGLE.get());
    }

    /** "Rudder 12° starboard" or "Rudder midships". */
    public static Component rudderMessage(double rudderAngle) {
        WheelMath.Side side = WheelMath.side(rudderAngle);
        if (side == WheelMath.Side.MIDSHIPS) {
            return Component.translatable(ShipControls.KEY_RUDDER_MIDSHIPS);
        }
        return Component.translatable(KEY_RUDDER, String.format(Locale.ROOT, "%.0f", Math.abs(rudderAngle)),
                Component.translatable(side == WheelMath.Side.STARBOARD ? ShipControls.KEY_STARBOARD : ShipControls.KEY_PORT));
    }

    /** Level tick: ends the sessions in {@code level} whose rules say so. */
    public static void onLevelTick(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        boolean enabled = HelmConfig.DRAG_STEERING.get() && SailingConfig.STEERING_ENABLED.get();
        double reach = HelmConfig.SESSION_REACH.get();
        for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
            Session s = e.getValue();
            if (s.level != level) {
                continue;
            }
            SessionRules.End end = check(s, enabled, reach);
            if (end != SessionRules.End.NONE) {
                end(e.getKey(), s, end);
            }
        }
    }

    private static SessionRules.End check(Session s, boolean enabled, double reach) {
        boolean present = present(s.player) && s.player.level() == s.level;
        ShipBody ship = SableShips.containing(s.level, s.pos);
        boolean onShip = ship != null && !ship.isRemoved() && s.level.getBlockState(s.pos).getBlock() instanceof HelmBlock;
        double distance = Double.POSITIVE_INFINITY;
        if (present && onShip) {
            Vec3 p = ship.toPlot(s.player.position());
            distance = SessionRules.distanceToBlock(p.x, p.y, p.z, s.pos.getX(), s.pos.getY(), s.pos.getZ());
        }
        return SessionRules.check(enabled, present, onShip, distance, reach);
    }

    private static boolean present(Player p) {
        return !p.isRemoved() && p.isAlive() && !p.isSpectator()
                && !(p instanceof ServerPlayer sp && sp.hasDisconnected());
    }

    private static void end(UUID id, Session s, SessionRules.End why) {
        SESSIONS.remove(id, s);
        notify(s.player, s.pos, false, wheel(s.level, s.pos));
    }

    private static void notify(Player player, BlockPos pos, boolean active, float wheel) {
        if (player instanceof ServerPlayer sp && !sp.hasDisconnected()) {
            Services.NETWORK.sendToPlayer(sp, new HelmSessionPayload(pos, active, wheel));
        }
    }

    /** Logout: the player's session ends without a message. */
    public static void onLogout(Player player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void onServerStopped() {
        SESSIONS.clear();
    }

    // ---- payload handlers (server main thread) ----------------------------------------------------------------------

    static void handleWheel(HelmWheelPayload payload, Player player) {
        if (player != null && player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())) {
            turn(player, payload.pos(), payload.delta());
        }
    }

    static void handleRelease(HelmReleasePayload payload, Player player) {
        if (player != null) {
            release(player, payload.pos());
        }
    }
}

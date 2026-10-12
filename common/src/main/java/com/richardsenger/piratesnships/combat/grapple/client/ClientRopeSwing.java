package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.RopeSwing;
import com.richardsenger.piratesnships.combat.grapple.RopeSwingService;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The local player's side of hanging on their own hand-held rope (GR7, {@link RopeSwingService}): the client moves its
 * own player, so it runs the swing ({@link RopeSwingService#apply}) at the end of every local player tick with the
 * rope's length predicted by the same rule as the server's ({@link RopeSwing#track}), adopting the server's synced length
 * whenever it changes (a pull, the server's view of leaving the ground). A pull sent to the server is predicted at
 * once ({@link #predictPull}) so the climb starts without waiting for the round trip. Physical client only.
 *
 * <p>The hook's pivot on a ship is its plot point through the ship's pose of the last tick ({@link ClientShipPoses#toWorld}
 * at partial tick 1); its motion is Sable's velocity of that point ({@code ActiveSableCompanion#getVelocity(Level, Vec3)},
 * l.369, per second), and Sable carries the player itself when it tracks them on that ship
 * ({@code ActiveSableCompanion#getTrackingSubLevel}, l.434, against {@code #getContaining(Level, Position)}, l.97), the
 * calls {@code ship.rigging.client.RiggingClient} uses for RL3.
 */
public final class ClientRopeSwing {

    /** Network id of the hook the state below belongs to, -1 for none. */
    private static int hookId = -1;
    /** The predicted rope length (NaN: free). */
    private static double length = Double.NaN;
    /** The synced length last seen (NaN: free); a change means the server decided. */
    private static double lastSynced = Double.NaN;

    private ClientRopeSwing() {
    }

    /** {@code PLAYER_TICK_END} (registered on the physical client): the local player's swing. */
    public static void onPlayerTick(Player player) {
        if (!player.level().isClientSide || !player.isLocalPlayer()) {
            return;
        }
        GrapplingHookEntity hook = ownHook(player);
        if (hook == null) {
            hookId = -1;
            length = Double.NaN;
            lastSynced = Double.NaN;
            return;
        }
        if (hook.getId() != hookId) {
            hookId = hook.getId();
            length = Double.NaN;
            lastSynced = Double.NaN;
        }
        double synced = hook.syncedSwingLength();
        if (!same(synced, lastSynced)) {
            length = synced;
            lastSynced = synced;
        }
        RopeSwingService.Pivot pivot = pivot(hook);
        Vec3 hand = RopeSwingService.hand(player);
        double d = hand.distanceTo(pivot.position());
        length = RopeSwing.track(length, d, RopeSwingService.holds(player), player.onGround(), pivot.position().y > hand.y,
                GrappleConfig.MUSKET_ROPE_LENGTH.get());
        if (RopeSwing.holding(length)) {
            RopeSwingService.apply(player, pivot, length);
        }
    }

    /** The predicted length of {@code hook}'s rope for drawing: the local player's own, else the synced one (NaN: free). */
    public static double length(GrapplingHookEntity hook) {
        return hook.getId() == hookId ? length : hook.syncedSwingLength();
    }

    /** Whether the local player hangs on {@code hook}'s rope now (the rope holds at a length). */
    public static boolean hanging(GrapplingHookEntity hook) {
        return hook.getId() == hookId && RopeSwing.holding(length);
    }

    /** A pull was sent for {@code hook}: the rope is taken in at once, as the server will ({@link RopeSwing#pull}). */
    public static void predictPull(GrapplingHookEntity hook, Player player) {
        if (hook.getId() != hookId) {
            return;
        }
        RopeSwingService.Pivot pivot = pivot(hook);
        double d = RopeSwingService.hand(player).distanceTo(pivot.position());
        length = RopeSwing.pull(length, d, GrappleConfig.ROPE_CLIMB_STEP.get());
    }

    /** The local player's latched hook whose near end is in their hand, or null. */
    static @Nullable GrapplingHookEntity ownHook(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GrapplingHookEntity hook && !hook.isRemoved() && hook.getOwner() == player
                    && hook.state() == GrapplingHookEntity.State.LATCHED && hook.syncedTiedRing().isEmpty()) {
                return hook;
            }
        }
        return null;
    }

    /** The client's pivot of {@code hook}: on its ship's pose of the last tick, or the fixed world point it holds. */
    static RopeSwingService.Pivot pivot(GrapplingHookEntity hook) {
        Level level = hook.level();
        Vec3 plot = hook.syncedPlotPos();
        Vec3 onShip = ClientShipPoses.toWorld(level, plot, 1.0f);
        Vec3 at = ClientRopes.hookPos(hook, 1.0f);
        return new RopeSwingService.Pivot() {
            @Override
            public Vec3 position() {
                return at;
            }

            @Override
            public Vec3 velocity() {
                return onShip == null ? Vec3.ZERO : Sable.HELPER.getVelocity(level, plot).scale(1.0 / 20.0);
            }

            @Override
            public boolean carries(Player player) {
                if (onShip == null) {
                    return false;
                }
                SubLevel ship = Sable.HELPER.getContaining(level, plot);
                return ship != null && Sable.HELPER.getTrackingSubLevel(player) == ship;
            }
        };
    }

    private static boolean same(double a, double b) {
        return Double.isNaN(a) ? Double.isNaN(b) : !Double.isNaN(b) && Math.abs(a - b) < 1.0e-4;
    }
}

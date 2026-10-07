package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the grappling hook (docs/design.md §8.3, §8.4, G11): throwing, one hook per thrower, release, and the
 * rope force that hauls two ships together.
 *
 * <p><b>The rope force</b> is applied every physics substep ({@link SableShips#onPhysicsTick}) to both bodies through
 * our own Sable force group ({@link ShipBody#applyGrappleImpulse}: {@code QueuedForceGroup#applyAndRecordPointForce}):
 * the tension from {@link GrappleRules#tension} along the horizontal line between the rope's ends, pulling the hooked
 * ship toward the anchor (the thrower ship's block nearest the hook) and the thrower's ship toward the hook, equal and
 * opposite. Each force acts at its rope end moved to the height of that ship's center of mass, so it turns the ships
 * about the vertical axis but does not heel or pitch them (small hulls have almost no righting moment,
 * docs/sable-notes.md §9.0d). The world force is turned into the body frame and multiplied by the substep length, as
 * Sable expects an impulse (sable-notes §3.2). A thrower on land pulls only the hooked ship, toward the thrower, with
 * {@code shore_haul_force}. When the rope is tied to a mooring ring (GR1), the ring's ship hauls from the ring instead
 * of the thrower's ship from its nearest block, and a ring on land pulls like a thrower on land.
 */
public final class GrappleService {

    /** The live hook of each thrower (server thread only). */
    private static final Map<UUID, GrapplingHookEntity> ACTIVE = new HashMap<>();

    private GrappleService() {
    }

    // ------------------------------------------------------------------ throwing

    /**
     * Throws a hook from {@code player}'s eyes along its view with {@code throw_velocity} (Sable's projectile mixin adds
     * the velocity of the deck the player stands on). A hook the player already has out is released first.
     *
     * @param hook     the thrown item (one), carried by the hook and returned on release
     * @param consumed whether the item was taken from the player (not in creative), so it must be given back
     */
    public static GrapplingHookEntity throwHook(ServerLevel level, Player player, ItemStack hook, boolean consumed) {
        return launchHook(level, player, hook, consumed, GrappleLaunch.Mode.THROW);
    }

    /**
     * Launches a hook from {@code player}'s eyes along its view (GR1): thrown, shot from a crossbow or fired from a
     * musket, with the mode's speed ({@link GrappleConfig#speed}) and rope length ({@link GrappleConfig#ropeLength}).
     * The weapon's own sound and effects are the caller's; a throw plays the bobber throw.
     */
    public static GrapplingHookEntity launchHook(ServerLevel level, Player player, ItemStack hook, boolean consumed, GrappleLaunch.Mode mode) {
        GrapplingHookEntity entity = new GrapplingHookEntity(level, player, hook, consumed);
        entity.setRopeLength(GrappleConfig.ropeLength(mode));
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, (float) GrappleConfig.speed(mode),
                GrappleLaunch.inaccuracy(mode));
        return launch(level, player, entity, mode == GrappleLaunch.Mode.THROW);
    }

    /** Launches a hook from {@code pos} with {@code velocity} (blocks per tick); used by tests. */
    public static GrapplingHookEntity launch(ServerLevel level, Player player, Vec3 pos, Vec3 velocity, ItemStack hook, boolean consumed) {
        return launch(level, player, new GrapplingHookEntity(level, player, pos, velocity, hook, consumed), true);
    }

    private static GrapplingHookEntity launch(ServerLevel level, Player player, GrapplingHookEntity entity, boolean throwSound) {
        release(player);
        ACTIVE.put(player.getUUID(), entity);
        level.addFreshEntity(entity);
        if (throwSound) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS,
                    0.6f, 0.6f);
        }
        return entity;
    }

    // ------------------------------------------------------------------ mooring rings (GR1)

    public static final String TIED_KEY = "message." + Constants.MOD_ID + ".grapple.tied";
    public static final String TIE_SAME_SHIP_KEY = "message." + Constants.MOD_ID + ".grapple.tie_same_ship";
    public static final String TIE_TOO_FAR_KEY = "message." + Constants.MOD_ID + ".grapple.tie_too_far";
    public static final String ALREADY_TIED_KEY = "message." + Constants.MOD_ID + ".grapple.already_tied";

    /**
     * The player used the mooring ring at {@code ringPos} (plot position for a ring on a ship): ties the near end of
     * their hook's rope to it ({@link GrappleRules#tie}). PASS without a hook out, so an item in hand is used as usual.
     */
    public static InteractionResult tieOff(ServerLevel level, Player player, BlockPos ringPos) {
        GrapplingHookEntity hook = hookOf(player);
        if (hook == null) {
            return InteractionResult.PASS;
        }
        if (ringPos.equals(hook.tiedRing())) {
            player.displayClientMessage(Component.translatable(ALREADY_TIED_KEY), true);
            return InteractionResult.SUCCESS;
        }
        ShipBody ringShip = SableShips.containing(level, ringPos);
        Vec3 ringPlot = MooringRingBlock.ringCenter(level, ringPos);
        Vec3 ringWorld = ringShip != null ? ringShip.toWorld(ringPlot) : ringPlot;
        UUID hookShip = hook.state() == GrapplingHookEntity.State.LATCHED ? hook.shipId() : null;
        GrappleRules.Tie tie = GrappleRules.tie(true, ringShip == null ? null : ringShip.id(), hookShip,
                ringWorld.distanceTo(hook.position()), hook.ropeLength());
        switch (tie) {
            case SAME_SHIP -> player.displayClientMessage(Component.translatable(TIE_SAME_SHIP_KEY), true);
            case TOO_FAR -> player.displayClientMessage(Component.translatable(TIE_TOO_FAR_KEY), true);
            case OK -> {
                hook.tieTo(ringPos, ringShip == null ? null : ringShip.id());
                level.playSound(null, ringWorld.x, ringWorld.y, ringWorld.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
                player.displayClientMessage(Component.translatable(TIED_KEY), true);
            }
            default -> { }
        }
        return InteractionResult.SUCCESS;
    }

    /** Client prediction of {@link #tieOff}: whether the player has a hook out among the entities the client knows. */
    public static boolean hasHookOutClientSide(Level level, Player player) {
        return !level.getEntitiesOfClass(GrapplingHookEntity.class, player.getBoundingBox().inflate(160.0),
                h -> h.getOwner() == player).isEmpty();
    }

    /** A ring found near a hook's path: its ship, block (plot position) and middle (plot coordinates). */
    record RingCatch(ShipBody ship, BlockPos block, Vec3 center) {
    }

    /**
     * The mooring ring on a ship other than {@code exclude} whose middle lies nearest to the segment {@code from}-{@code to}
     * (world coordinates), within {@code radius}, or null. Searches only ships whose world bounds come close to the
     * segment, and in each only the plot blocks around the segment.
     */
    static @Nullable RingCatch findRing(ServerLevel level, Vec3 from, Vec3 to, double radius, @Nullable UUID exclude) {
        if (radius <= 0) {
            return null;
        }
        AABB path = new AABB(from, to).inflate(radius + 1.0);
        RingCatch best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.id().equals(exclude) || !ship.worldBounds().intersects(path)) {
                continue;
            }
            Vec3 a = ship.toPlot(from);
            Vec3 b = ship.toPlot(to);
            double r = radius + 0.5;
            BlockPos min = BlockPos.containing(Math.min(a.x, b.x) - r, Math.min(a.y, b.y) - r, Math.min(a.z, b.z) - r);
            BlockPos max = BlockPos.containing(Math.max(a.x, b.x) + r, Math.max(a.y, b.y) + r, Math.max(a.z, b.z) + r);
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                BlockState state = level.getBlockState(p);
                if (!(state.getBlock() instanceof MooringRingBlock)) {
                    continue;
                }
                Vec3 c = MooringRingBlock.ringCenter(state, p);
                double d = GrappleRules.segmentDistance(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z);
                if (GrappleRules.ringCatches(d, radius) && d < bestD) {
                    bestD = d;
                    best = new RingCatch(ship, p.immutable(), c);
                }
            }
        }
        return best;
    }

    /** The player's hook that is out, or null. */
    public static @Nullable GrapplingHookEntity hookOf(Player player) {
        GrapplingHookEntity h = ACTIVE.get(player.getUUID());
        return h != null && !h.isRemoved() ? h : null;
    }

    /** Releases the player's hook (reeled in, returned). True when there was one. */
    public static boolean release(Player player) {
        GrapplingHookEntity h = hookOf(player);
        if (h == null) {
            return false;
        }
        h.release(GrappleRules.Release.NONE);
        return true;
    }

    /** From the release payload: sneak + use with an empty hand. */
    public static void onReleaseRequest(Player player) {
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            release(player);
        }
    }

    /** A thrower logging out takes the hook back with them (hooks are not saved). */
    public static void onLogout(Player player) {
        GrapplingHookEntity h = hookOf(player);
        if (h != null) {
            h.giveBackTo(player);
            h.discard();
        }
    }

    /** Called by a ticking hook: a hook not in the table (e.g. after a change of owner) is entered. */
    static void adopt(GrapplingHookEntity hook) {
        Entity owner = hook.getOwner();
        if (owner == null) {
            return;
        }
        GrapplingHookEntity current = ACTIVE.get(owner.getUUID());
        if (current == null || current.isRemoved()) {
            ACTIVE.put(owner.getUUID(), hook);
        } else if (current != hook) {
            hook.release(GrappleRules.Release.NONE); // one hook per thrower: the older one goes
        }
    }

    static void forget(GrapplingHookEntity hook) {
        ACTIVE.values().removeIf(h -> h == hook);
    }

    /**
     * End of every server tick: a hook that vanished without being released (its chunk unloaded) gives its item back
     * to its online thrower.
     */
    public static void onServerTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, GrapplingHookEntity> e : List.copyOf(ACTIVE.entrySet())) {
            GrapplingHookEntity h = e.getValue();
            if (h.isRemoved()) {
                if (!h.finished()) {
                    h.giveBackTo(server.getPlayerList().getPlayer(e.getKey()));
                }
                ACTIVE.remove(e.getKey(), h);
            }
        }
    }

    public static void onServerStopped() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ ships

    /**
     * The ship the entity stands on or rides in ({@link ShipEntities#standingOrRiding}); failing that, a ship whose world
     * bounds hold the entity with a ship block at most two blocks below its feet (for entities Sable does not track).
     */
    public static @Nullable ShipBody shipOf(ServerLevel level, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        ShipBody s = ShipEntities.standingOrRiding(entity);
        if (s != null) {
            return s;
        }
        for (ShipBody b : SableShips.all(level)) {
            if (b.worldBounds().inflate(1.0, 2.0, 1.0).contains(entity.position())) {
                BlockPos local = BlockPos.containing(b.toPlot(entity.position()));
                for (int dy = 0; dy <= 2; dy++) {
                    if (!level.getBlockState(local.below(dy)).isAir()) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /** Center (plot coordinates) of the ship's block nearest to {@code worldPos}, or null for an empty ship. */
    static @Nullable Vec3 nearestBlockCenter(ShipBody ship, Vec3 worldPos) {
        Vec3 local = ship.toPlot(worldPos);
        Vec3 best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (BlockPos p : ship.plotBlocks()) {
            Vec3 c = Vec3.atCenterOf(p);
            double d = c.distanceToSqr(local);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ physics

    /** Physics substep: every latched hook in this level pulls on its ship(s). */
    public static void onPhysicsTick(ServerLevel level, double timeStep) {
        if (ACTIVE.isEmpty() || !GrappleConfig.ENABLED.get()) {
            return;
        }
        for (GrapplingHookEntity h : ACTIVE.values()) {
            if (h.isRemoved() || h.level() != level || h.state() != GrapplingHookEntity.State.LATCHED) {
                continue;
            }
            try {
                haul(level, h, timeStep);
            } catch (RuntimeException e) {
                Constants.LOG.error("Grappling rope force failed", e);
            }
        }
    }

    private static void haul(ServerLevel level, GrapplingHookEntity h, double dt) {
        UUID targetId = h.shipId();
        Vec3 hookPlot = h.plotPos();
        if (targetId == null || hookPlot == null || h.throwerAboardTarget()) {
            return;
        }
        ShipBody target = SableShips.byId(level, targetId);
        if (target == null) {
            return;
        }
        Vec3 hookWorld = target.toWorld(hookPlot);
        double hold = GrappleConfig.holdLength();
        double damping = GrappleConfig.ROPE_DAMPING.get();
        UUID throwerId = h.throwerShipId();
        Vec3 anchorPlot = h.anchorPlot();
        if (throwerId != null && anchorPlot != null) {
            ShipBody thrower = SableShips.byId(level, throwerId);
            if (thrower == null) {
                return;
            }
            Vec3 anchorWorld = thrower.toWorld(anchorPlot);
            double dx = hookWorld.x - anchorWorld.x;
            double dz = hookWorld.z - anchorWorld.z;
            double d = Math.hypot(dx, dz);
            if (d < 1.0e-6) {
                return;
            }
            Vec3 u = new Vec3(dx / d, 0, dz / d);
            double extension = target.velocityAt(hookPlot).subtract(thrower.velocityAt(anchorPlot)).dot(u);
            if (h.holding().update(d, hold)) {
                return; // the hulls lie together: the rope holds without pulling
            }
            double t = GrappleRules.tension(d, hold, extension, GrappleConfig.HAUL_FORCE.get(), damping);
            if (t > 0) {
                push(target, hookWorld, u.scale(-t), dt);
                push(thrower, anchorWorld, u.scale(t), dt);
            }
        } else {
            Vec3 shore = h.throwerPos();
            if (shore == null) {
                return;
            }
            double dx = hookWorld.x - shore.x;
            double dz = hookWorld.z - shore.z;
            double d = Math.hypot(dx, dz);
            if (d < 1.0e-6) {
                return;
            }
            Vec3 u = new Vec3(dx / d, 0, dz / d);
            double extension = target.velocityAt(hookPlot).dot(u);
            if (h.holding().update(d, hold)) {
                return; // the ship lies at the shore: the rope holds without pulling
            }
            double t = GrappleRules.tension(d, hold, extension, GrappleConfig.SHORE_HAUL_FORCE.get(), damping);
            if (t > 0) {
                push(target, hookWorld, u.scale(-t), dt);
            }
        }
    }

    /**
     * Records the world force {@code force} [kpg·m/s²] for one substep at {@code worldPoint} lowered or raised to the
     * ship's center of mass height.
     */
    private static void push(ShipBody ship, Vec3 worldPoint, Vec3 force, double dt) {
        Vector3d com = new Vector3d();
        if (!ship.centerOfMass(com)) {
            return;
        }
        Vector3d comWorld = ship.toWorld(com, new Vector3d());
        Vec3 at = ship.toPlot(new Vec3(worldPoint.x, comWorld.y, worldPoint.z));
        Vector3d local = ship.orientation().transformInverse(new Vector3d(force.x * dt, force.y * dt, force.z * dt));
        ship.applyGrappleImpulse(new Vector3d(at.x, at.y, at.z), local);
    }
}

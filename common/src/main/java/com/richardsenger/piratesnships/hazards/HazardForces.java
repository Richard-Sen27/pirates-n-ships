package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/**
 * Applies a hazard's field to the world each server tick (docs/design.md §12): entities get the acceleration of
 * {@link HazardField} added to their velocity; players (and the boats they steer) move on their own client, so they get
 * it through {@link HazardPushPayload}; Sable ships get an impulse at five points of the hull (the centre of mass and
 * the middle of each side), so a field that differs across the hull, like the whirlpool's spin at its centre, also turns
 * the ship. Ship forces are computed here once per game tick and recorded every physics substep by
 * {@link HazardShipForces} in our sea hazards force group (the grappling rope's path).
 */
public final class HazardForces {

    /** How far below the water line a waterspout reaches. */
    static final double SPOUT_BELOW = 2.0;
    /** How far below and above the water line a whirlpool reaches. */
    static final double POOL_BELOW = 6.0;
    static final double POOL_ABOVE = 3.0;

    private HazardForces() {
    }

    /** A field: acceleration at an offset from the hazard's base, for a ship hull point. */
    @FunctionalInterface
    interface ShipField {
        HazardField.Vec at(double dx, double dy, double dz);
    }

    /** Whether an entity feels hazard fields at all. Passengers move with their vehicle, which is pushed instead. */
    static boolean affected(Entity e) {
        return e.isAlive() && !(e instanceof HazardEntity) && !e.isSpectator() && !e.isPassenger() && !e.noPhysics
                && !e.isNoGravity() && !(e instanceof Player p && p.getAbilities().flying);
    }

    public static AABB spoutBox(Vec3 base, HazardField.Spout p) {
        return new AABB(base.x - p.radius(), base.y - SPOUT_BELOW, base.z - p.radius(),
                base.x + p.radius(), base.y + p.funnelHeight(), base.z + p.radius());
    }

    public static AABB poolBox(Vec3 base, HazardField.Pool p) {
        return new AABB(base.x - p.radius(), base.y - POOL_BELOW, base.z - p.radius(),
                base.x + p.radius(), base.y + POOL_ABOVE, base.z + p.radius());
    }

    /** One tick of a waterspout: pull and lift on entities and ships in its cylinder. */
    static void waterspout(ServerLevel level, HazardEntity spout, HazardField.Spout p, double maxLift) {
        Vec3 base = spout.position();
        AABB box = spoutBox(base, p);
        for (Entity e : level.getEntities(spout, box, HazardForces::affected)) {
            HazardField.Vec a = HazardField.waterspout(e.getX() - base.x, e.getY() - base.y, e.getZ() - base.z, p);
            push(e, a, maxLift);
        }
        ShipField field = (dx, dy, dz) -> dy < -SPOUT_BELOW ? HazardField.Vec.ZERO : HazardField.waterspout(dx, dy, dz, p);
        List<HazardShipForces.PointForce> forces = HazardShipForces.newList();
        for (ShipBody ship : SableShips.all(level)) {
            if (!ship.isRemoved() && ship.worldBounds().intersects(box)) {
                pushShip(ship, base, field, forces);
            }
        }
        HazardShipForces.set(level, spout.getUUID(), forces);
    }

    /** One tick of a whirlpool: pull and spin on entities and ships, drag-down on boats and swimmers. */
    static void whirlpool(ServerLevel level, HazardEntity pool, HazardField.Pool p) {
        Vec3 base = pool.position();
        AABB box = poolBox(base, p);
        for (Entity e : level.getEntities(pool, box, HazardForces::affected)) {
            boolean dragged = e instanceof Boat || e instanceof LivingEntity && e.isInWater();
            HazardField.Vec a = HazardField.whirlpool(e.getX() - base.x, e.getZ() - base.z, dragged, p);
            push(e, a, Double.MAX_VALUE);
        }
        ShipField field = (dx, dy, dz) -> HazardField.whirlpool(dx, dz, false, p);
        List<HazardShipForces.PointForce> forces = HazardShipForces.newList();
        for (ShipBody ship : SableShips.all(level)) {
            if (!ship.isRemoved() && ship.worldBounds().intersects(box)) {
                pushShip(ship, base, field, forces);
            }
        }
        HazardShipForces.set(level, pool.getUUID(), forces);
    }

    /**
     * Adds {@code a} to an entity's velocity. Players and player-steered vehicles are moved by their client, so the
     * push is sent to that client ({@link HazardPushPayload}); everything else is moved here.
     */
    static void push(Entity e, HazardField.Vec a, double maxLift) {
        if (a.x() == 0 && a.y() == 0 && a.z() == 0) {
            return;
        }
        if (a.y() > 0) {
            e.resetFallDistance(); // only the fall after leaving the funnel counts
        }
        if (e instanceof ServerPlayer player) {
            Services.NETWORK.sendToPlayer(player, new HazardPushPayload((float) a.x(), (float) a.y(), (float) a.z(), (float) Math.min(maxLift, Float.MAX_VALUE), false));
            return;
        }
        if (e.getControllingPassenger() instanceof ServerPlayer driver) {
            Services.NETWORK.sendToPlayer(driver, new HazardPushPayload((float) a.x(), (float) a.y(), (float) a.z(), (float) Math.min(maxLift, Float.MAX_VALUE), true));
            return;
        }
        addVelocity(e, a.x(), a.y(), a.z(), maxLift);
    }

    /** Adds an acceleration to an entity's velocity, the upward part capped by {@link HazardField#applyLift}. */
    static void addVelocity(Entity e, double ax, double ay, double az, double maxLift) {
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x + ax, HazardField.applyLift(v.y, ay, maxLift), v.z + az);
        e.hasImpulse = true;
    }

    /**
     * The field on a ship: at the centre of mass and the middle of each side of the plot bounds (at centre-of-mass
     * height, so lift adds no pitch), each point carrying a fifth of {@link HazardField#shipForce}, in the body frame.
     */
    static void pushShip(ShipBody ship, Vec3 base, ShipField field, List<HazardShipForces.PointForce> out) {
        double mass = ship.mass();
        Vector3d com = new Vector3d();
        if (mass <= 0 || !ship.centerOfMass(com)) {
            return;
        }
        double scale = HazardsConfig.SHIP_FORCE_SCALE.get();
        double cap = HazardsConfig.MAX_SHIP_MASS_EFFECT.get();
        if (scale <= 0) {
            return;
        }
        BlockPos[] b = ship.plotBounds();
        double hx = (b[1].getX() - b[0].getX() + 1) / 2.0;
        double hz = (b[1].getZ() - b[0].getZ() + 1) / 2.0;
        Vector3d[] points = {
                new Vector3d(com), new Vector3d(com).add(hx, 0, 0), new Vector3d(com).sub(hx, 0, 0),
                new Vector3d(com).add(0, 0, hz), new Vector3d(com).sub(0, 0, hz)};
        Quaterniond q = ship.orientation(new Quaterniond());
        Vector3d world = new Vector3d();
        int before = out.size();
        for (Vector3d point : points) {
            ship.toWorld(point, world);
            HazardField.Vec a = field.at(world.x - base.x, world.y - base.y, world.z - base.z);
            if (a.x() == 0 && a.y() == 0 && a.z() == 0) {
                continue;
            }
            HazardField.Vec force = HazardField.shipForce(a, mass, cap, scale).scale(1.0 / points.length);
            Vector3d local = q.transformInverse(new Vector3d(force.x(), force.y(), force.z()));
            out.add(new HazardShipForces.PointForce(ship.id(), new Vector3d(point), local));
        }
        if (out.size() > before) {
            // Sable wakes a sleeping body only when a queued group's total changes (ForceTotal#applyForces l.25-30),
            // so a steady hazard force would never wake a ship that fell asleep at rest; adding zero velocity wakes it
            // (RigidBodyHandle#addLinearAndAngularVelocity, RapierPhysicsPipeline l.478: wakeUp true)
            ship.addVelocity(new Vector3d(), new Vector3d());
        }
    }

    /**
     * Once per second: every set sail of a ship whose sail block lies inside the funnel rolls {@code chance} and, on a
     * hit, loses one trim step ({@link SailTear}) with a tearing sound. Crew orders are left alone.
     */
    static void tearSails(ServerLevel level, HazardEntity spout, HazardField.Spout p, double chance) {
        if (chance <= 0) {
            return;
        }
        Vec3 base = spout.position();
        AABB box = spoutBox(base, p);
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved() || !ship.worldBounds().intersects(box)) {
                continue;
            }
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
            if (rt == null) {
                continue;
            }
            for (BlockPos sail : rt.sailPositions()) {
                SailTrim trim = rt.trimAt(sail);
                if (trim == null) {
                    continue;
                }
                Vec3 w = ship.toWorld(Vec3.atCenterOf(sail));
                double dx = w.x - base.x, dz = w.z - base.z, dy = w.y - base.y;
                if (dx * dx + dz * dz > p.radius() * p.radius() || dy < -SPOUT_BELOW || dy > p.funnelHeight()) {
                    continue;
                }
                if (!SailTear.tears(trim, chance, level.random.nextDouble())) {
                    continue;
                }
                SailTrim torn = SailTear.torn(trim);
                if (torn != null && SailingRuntimes.setTrim(level, sail, torn)) {
                    level.playSound(null, w.x, w.y, w.z, SoundEvents.WOOL_BREAK, SoundSource.WEATHER, 1.5f, 0.6f);
                }
            }
        }
    }
}

package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A server-side ship body: our view of a Sable {@code ServerSubLevel} ({@code sublevel/ServerSubLevel.java}). Cheap to
 * create; holds no state of its own. Do not keep it across ticks, look the ship up again by {@link #id()}.
 */
public final class ShipBody {

    private final ServerSubLevel sub;

    ShipBody(ServerSubLevel sub) {
        this.sub = sub;
    }

    ServerSubLevel raw() {
        return sub;
    }

    /** Stable sub-level UUID, persists across save/load ({@code sublevel/SubLevel.java#getUniqueId}, l.210). */
    public UUID id() {
        return sub.getUniqueId();
    }

    public ServerLevel level() {
        return sub.getLevel();
    }

    public boolean isRemoved() {
        return sub.isRemoved();
    }

    /** Sable's display name ({@code ServerSubLevel#setName}, l.411), shown by {@code /sable} commands. */
    public void setName(@Nullable String name) {
        sub.setName(name);
    }

    /** Plot position to world position ({@code SubLevel#logicalPose}, companion {@code Pose3dc#transformPosition}). */
    public Vec3 toWorld(Vec3 plotPos) {
        return sub.logicalPose().transformPosition(plotPos);
    }

    /** World position to plot position ({@code Pose3dc#transformPositionInverse}). */
    public Vec3 toPlot(Vec3 worldPos) {
        return sub.logicalPose().transformPositionInverse(worldPos);
    }

    /** Body to world rotation (copy). */
    public Quaterniond orientation() {
        return new Quaterniond(sub.logicalPose().orientation());
    }

    /** World-space bounds of the ship ({@code SubLevel#boundingBox}, l.167). */
    public AABB worldBounds() {
        BoundingBox3dc b = sub.boundingBox();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** Plot-space block bounds, inclusive ({@code sublevel/plot/LevelPlot.java#getBoundingBox}, l.342). */
    public BlockPos[] plotBounds() {
        BoundingBox3ic b = sub.getPlot().getBoundingBox();
        return new BlockPos[] {new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ())};
    }

    /** World-frame linear velocity in m/s ({@code api/physics/handle/RigidBodyHandle.java#getLinearVelocity}, l.160). */
    public Vector3d linearVelocity() {
        RigidBodyHandle h = RigidBodyHandle.of(sub);
        return h == null || !h.isValid() ? new Vector3d() : h.getLinearVelocity(new Vector3d());
    }

    /** World-frame angular velocity in rad/s ({@code RigidBodyHandle#getAngularVelocity}, l.168). */
    public Vector3d angularVelocity() {
        RigidBodyHandle h = RigidBodyHandle.of(sub);
        return h == null || !h.isValid() ? new Vector3d() : h.getAngularVelocity(new Vector3d());
    }

    /** Adds world-frame velocity (game tick safe, {@code RigidBodyHandle#addLinearAndAngularVelocity}, l.186). */
    public void addVelocity(Vector3d linear, Vector3d angular) {
        RigidBodyHandle h = RigidBodyHandle.of(sub);
        if (h != null && h.isValid()) {
            h.addLinearAndAngularVelocity(linear, angular);
        }
    }

    /**
     * Sets the orientation in place, keeping the world position of the rotation point
     * ({@code api/physics/PhysicsPipeline.java#teleport}, l.149, as {@code assembleBlocks} does).
     */
    public void setOrientation(Quaterniondc orientation) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(sub.getLevel());
        if (container == null) {
            return;
        }
        sub.logicalPose().orientation().set(orientation);
        container.physicsSystem().getPipeline().teleport(sub, sub.logicalPose().position(), sub.logicalPose().orientation());
        sub.updateLastPose();
    }

    /**
     * Teleports the ship so that the plot point {@code plotPoint} lands on {@code world} with the body-to-world rotation
     * {@code orientation}. The pose maps {@code world = orientation · (plot − rotationPoint) + position}
     * (docs/sable-notes.md §1.3), so the new position is {@code world − orientation · (plotPoint − rotationPoint)};
     * applied like {@link #setOrientation} through {@code api/physics/PhysicsPipeline.java#teleport} (l.149), as
     * {@code api/SubLevelAssemblyHelper.java#assembleBlocks} does (l.131-135; the rotation point, l.119).
     */
    public void placeAt(Vec3 plotPoint, Vec3 world, Quaterniondc orientation) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(sub.getLevel());
        if (container == null) {
            return;
        }
        var pose = sub.logicalPose();
        Vector3d arm = new Vector3d(plotPoint.x, plotPoint.y, plotPoint.z).sub(pose.rotationPoint());
        new Quaterniond(orientation).transform(arm);
        pose.orientation().set(orientation);
        pose.position().set(world.x - arm.x, world.y - arm.y, world.z - arm.z);
        container.physicsSystem().getPipeline().teleport(sub, pose.position(), pose.orientation());
        sub.updateLastPose();
    }

    /**
     * True if the plot position lies in this ship's plot area, where its blocks may go
     * ({@code sublevel/plot/LevelPlot.java#contains(double, double)}, l.198; the plot spans {@code 1 << logSize} chunks).
     */
    public boolean plotContains(BlockPos plotPos) {
        return sub.getPlot().contains(plotPos.getX() + 0.5, plotPos.getZ() + 0.5);
    }

    /**
     * Every non-air block in the ship's loaded plot chunks, in plot coordinates
     * ({@code LevelPlot#getLoadedChunks}, l.298; {@code PlotChunkHolder#getBoundingBox}, l.153, chunk-local x/z).
     */
    public List<BlockPos> plotBlocks() {
        List<BlockPos> out = new ArrayList<>();
        ServerLevel level = sub.getLevel();
        for (PlotChunkHolder chunk : sub.getPlot().getLoadedChunks()) {
            BoundingBox3ic b = chunk.getBoundingBox();
            if (b == null || b == BoundingBox3i.EMPTY) {
                continue;
            }
            int bx = chunk.getPos().getMinBlockX();
            int bz = chunk.getPos().getMinBlockZ();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        BlockPos p = new BlockPos(bx + x, y, bz + z);
                        if (!level.getBlockState(p).isAir()) {
                            out.add(p);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** World up (0, 1, 0) expressed in the body / plot frame ({@code Pose3dc#orientation}, inverse rotation). */
    public Vector3d localUp() {
        return sub.logicalPose().orientation().transformInverse(new Vector3d(0, 1, 0));
    }

    /**
     * Players the ship is currently sent to ({@code ServerSubLevel#getTrackingPlayers}, l.151, maintained by
     * {@code sublevel/system/SubLevelTrackingSystem.java#tick}, l.138). Copy.
     */
    public List<UUID> trackingPlayers() {
        return List.copyOf(sub.getTrackingPlayers());
    }

    /**
     * Records an impulse (force × time step, body frame) at a plot position in our buoyancy force group. Only legal during
     * a physics substep: groups are reset at the start of each substep and applied after the pre-physics event
     * ({@code ServerSubLevel#getOrCreateQueuedForceGroup}, l.395; {@code api/physics/force/QueuedForceGroup.java#applyAndRecordPointForce},
     * l.25; tick order in docs/sable-notes.md §1.5).
     */
    public void applyBuoyancyImpulse(Vector3d plotPoint, Vector3d localImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.buoyancy();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(plotPoint, localImpulse);
        }
    }

    // ---------------------------------------------------------------- sailing (spike 3) reads and writes

    /**
     * Total mass [kpg], 0 when the mass data is invalid ({@code ServerSubLevel#getMassTracker}, l.489;
     * {@code api/physics/mass/MassData.java#getMass}, l.13). Merged bodies included.
     */
    public double mass() {
        var m = sub.getMassTracker();
        return m == null || m.isInvalid() ? 0.0 : m.getMass();
    }

    /**
     * Writes the center of mass in <b>plot</b> coordinates into {@code dest} and returns true, or false when Sable has no
     * valid mass data ({@code MassData#getCenterOfMass}, l.34, nullable, plot coordinates per docs/sable-notes.md §3.3).
     */
    public boolean centerOfMass(Vector3d dest) {
        var m = sub.getMassTracker();
        if (m == null || m.isInvalid()) {
            return false;
        }
        dest.set(m.getCenterOfMass());
        return true;
    }

    /** Plot position to world position without allocation ({@code Pose3dc#transformPosition(Vector3dc, Vector3d)}). */
    public Vector3d toWorld(Vector3dc plotPos, Vector3d dest) {
        return sub.logicalPose().transformPosition(plotPos, dest);
    }

    /** Body (plot) to world rotation, written into {@code dest} ({@code Pose3dc#orientation}). */
    public Quaterniond orientation(Quaterniond dest) {
        return dest.set(sub.logicalPose().orientation());
    }

    /**
     * World-frame linear velocity of the center of mass [m/s] and angular velocity [rad/s] from the physics engine,
     * into the given vectors; false (and zeros) when the body has no valid handle
     * ({@code api/physics/handle/RigidBodyHandle.java#of(ServerSubLevel)} l.56, {@code #getLinearVelocity(Vector3d)} l.160,
     * {@code #getAngularVelocity(Vector3d)} l.168, {@code #isValid} l.205).
     */
    public boolean velocities(Vector3d linear, Vector3d angular) {
        RigidBodyHandle h = RigidBodyHandle.of(sub);
        if (h == null || !h.isValid()) {
            linear.zero();
            angular.zero();
            return false;
        }
        h.getLinearVelocity(linear);
        h.getAngularVelocity(angular);
        return true;
    }

    /**
     * Records a linear impulse and an angular impulse about the center of mass, both in the body (plot) frame, in our
     * sailing force group. Physics substep only, like {@link #applyBuoyancyImpulse}
     * ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395, {@code api/physics/force/QueuedForceGroup.java#getForceTotal}
     * l.21, {@code api/physics/force/ForceTotal.java#applyLinearAndAngularImpulse} l.63; the torque convention is
     * {@code (pos − COM) × force} in the local frame, {@code ForceTotal#applyImpulseAtPoint} l.101-105).
     */
    public void applySailingImpulse(Vector3dc localImpulse, Vector3dc localAngularImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.sailing();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).getForceTotal().applyLinearAndAngularImpulse(localImpulse, localAngularImpulse);
        }
    }

    // ---------------------------------------------------------------- cannons (G9)

    /**
     * World-frame velocity [m/s] of a plot point, including the ship's rotation: {@code ω × (p − pose position) + v}
     * from the physics handle ({@code ActiveSableCompanion.java#getVelocity(Level, SubLevelAccess, Vector3dc, Vector3d)},
     * l.379-397; {@code Sable.HELPER}, {@code Sable.java} l.35).
     */
    public Vec3 velocityAt(Vec3 plotPos) {
        Vector3d v = dev.ryanhcode.sable.Sable.HELPER.getVelocity(sub.getLevel(), sub,
                new Vector3d(plotPos.x, plotPos.y, plotPos.z), new Vector3d());
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * Applies an impulse [kpg·m/s] in the body (plot) frame at a plot position, at once and outside a physics substep
     * ({@code api/physics/handle/RigidBodyHandle.java#applyImpulseAtPoint(Vector3dc, Vector3dc)} l.78, which goes straight
     * to {@code PhysicsPipeline#applyImpulse}). Sable itself calls it from game-tick code for dispenser recoil
     * ({@code mixin/recoil/ProjectileDispenseBehaviorMixin.java} l.40-43) and for arrows hitting a ship
     * ({@code mixin/entity/arrows_hit_blocks/AbstractArrowMixin.java} l.56-59). Does nothing without a valid handle.
     */
    public void applyImpulseNow(Vec3 plotPos, Vec3 localImpulse) {
        RigidBodyHandle h = RigidBodyHandle.of(sub);
        if (h != null && h.isValid()) {
            h.applyImpulseAtPoint(new Vector3d(plotPos.x, plotPos.y, plotPos.z), new Vector3d(localImpulse.x, localImpulse.y, localImpulse.z));
        }
    }

    // ---------------------------------------------------------------- grappling hook (G11)

    /**
     * Records an impulse (force × time step, body frame) at a plot position in our grappling rope force group; the
     * point force becomes a force plus the torque {@code (pos − COM) × force} about the center of mass. Physics substep
     * only, like {@link #applyBuoyancyImpulse} ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395;
     * {@code api/physics/force/QueuedForceGroup.java#applyAndRecordPointForce} l.25;
     * {@code api/physics/force/ForceTotal.java#applyImpulseAtPoint} l.101-105).
     */
    public void applyGrappleImpulse(Vector3dc plotPoint, Vector3dc localImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.grapple();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(plotPoint, localImpulse);
        }
    }

    /**
     * Records an impulse (force × time step, body frame) at a plot position in our rope hauling force group (GR5), like
     * {@link #applyGrappleImpulse}: the point force becomes a force plus the torque {@code (pos − COM) × force}. Physics
     * substep only ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395;
     * {@code api/physics/force/QueuedForceGroup.java#applyAndRecordPointForce} l.25;
     * {@code api/physics/force/ForceTotal.java#applyImpulseAtPoint} l.101-105).
     */
    public void applyHaulImpulse(Vector3dc plotPoint, Vector3dc localImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.haul();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(plotPoint, localImpulse);
        }
    }

    // ---------------------------------------------------------------- sea hazards (H1)

    /**
     * Records an impulse (force × time step, body frame) at a plot position in our sea hazards force group, like
     * {@link #applyGrappleImpulse}: the point force becomes a force plus the torque {@code (pos − COM) × force}. Physics
     * substep only ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395;
     * {@code api/physics/force/QueuedForceGroup.java#applyAndRecordPointForce} l.25;
     * {@code api/physics/force/ForceTotal.java#applyImpulseAtPoint} l.101-105).
     */
    public void applyHazardImpulse(Vector3dc plotPoint, Vector3dc localImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.hazards();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(plotPoint, localImpulse);
        }
    }

    // ---------------------------------------------------------------- anchor chain (AN2a)

    /**
     * Records a linear impulse and an angular impulse about the center of mass, both in the body (plot) frame, in our
     * anchor chain force group, like {@link #applySailingImpulse} (the caller scales the heeling part of the moment).
     * Physics substep only ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395,
     * {@code api/physics/force/QueuedForceGroup.java#getForceTotal} l.21,
     * {@code api/physics/force/ForceTotal.java#applyLinearAndAngularImpulse} l.63).
     */
    public void applyAnchorImpulse(Vector3dc localImpulse, Vector3dc localAngularImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.anchor();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).getForceTotal().applyLinearAndAngularImpulse(localImpulse, localAngularImpulse);
        }
    }

    // ---------------------------------------------------------------- cargo weight (CW1)

    /**
     * Records an impulse (force × time step, body frame) at a plot position in our cargo force group, like
     * {@link #applyGrappleImpulse}: the point force becomes a force plus the torque {@code (pos − COM) × force}. Physics
     * substep only ({@code ServerSubLevel#getOrCreateQueuedForceGroup} l.395;
     * {@code api/physics/force/QueuedForceGroup.java#applyAndRecordPointForce} l.25;
     * {@code api/physics/force/ForceTotal.java#applyImpulseAtPoint} l.101-105).
     */
    public void applyCargoImpulse(Vector3dc plotPoint, Vector3dc localImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.cargo();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(plotPoint, localImpulse);
        }
    }

    // ---------------------------------------------------------------- waves (WV1)

    /**
     * Records an angular impulse (torque × time step) about the center of mass, in the body (plot) frame, in our waves
     * force group. Physics substep only, like {@link #applySailingImpulse} ({@code ServerSubLevel#getOrCreateQueuedForceGroup}
     * l.395, {@code api/physics/force/QueuedForceGroup.java#getForceTotal} l.21,
     * {@code api/physics/force/ForceTotal.java#applyLinearAndAngularImpulse} l.63).
     */
    public void applyWaveImpulse(Vector3dc localAngularImpulse) {
        applyWaveImpulse(new Vector3d(), localAngularImpulse);
    }

    /**
     * Records a linear impulse (the heave, WAV2) and an angular impulse about the center of mass, both in the body (plot)
     * frame, in our waves force group. Physics substep only, like {@link #applySailingImpulse}
     * ({@code api/physics/force/ForceTotal.java#applyLinearAndAngularImpulse} l.63: both are local).
     */
    public void applyWaveImpulse(Vector3dc localImpulse, Vector3dc localAngularImpulse) {
        dev.ryanhcode.sable.api.physics.force.ForceGroup group = ShipForces.waves();
        if (group != null) {
            sub.getOrCreateQueuedForceGroup(group).getForceTotal().applyLinearAndAngularImpulse(localImpulse, localAngularImpulse);
        }
    }

    /**
     * The level's gravity vector in the world frame [m/s²], {@code (0, -11, 0)} by default
     * ({@code physics/config/dimension_physics/DimensionPhysicsData.java#getGravity(Level)}, l.49-60; the same call
     * Sable's physics system makes, {@code sublevel/system/SubLevelPhysicsSystem.java} l.168).
     */
    public Vector3d gravity() {
        return dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(sub.getLevel());
    }

    /**
     * Every block entity in the ship's loaded plot chunks (plot positions), without scanning blocks
     * ({@code LevelPlot#getLoadedChunks}, l.298; {@code sublevel/plot/PlotChunkHolder.java#getChunk}, l.146; vanilla
     * {@code LevelChunk#getBlockEntities}). Copy.
     */
    public List<net.minecraft.world.level.block.entity.BlockEntity> plotBlockEntities() {
        List<net.minecraft.world.level.block.entity.BlockEntity> out = new ArrayList<>();
        for (PlotChunkHolder chunk : sub.getPlot().getLoadedChunks()) {
            net.minecraft.world.level.chunk.LevelChunk c = chunk.getChunk();
            if (c != null) {
                out.addAll(c.getBlockEntities().values());
            }
        }
        return out;
    }

    /** Our sub-tree of the sub-level's persisted user data ({@code ServerSubLevel#getUserDataTag}, l.548). */
    public CompoundTag userData(String key) {
        CompoundTag root = sub.getUserDataTag();
        return root != null && root.contains(key) ? root.getCompound(key).copy() : new CompoundTag();
    }

    /** Writes our sub-tree of the user data ({@code ServerSubLevel#setUserDataTag}, l.557). */
    public void setUserData(String key, CompoundTag data) {
        CompoundTag root = sub.getUserDataTag();
        root = root == null ? new CompoundTag() : root.copy();
        root.put(key, data);
        sub.setUserDataTag(root);
    }
}

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

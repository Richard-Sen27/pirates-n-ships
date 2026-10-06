package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/**
 * Client-side ship poses for rendering (physical client only; never touch it from server code). Backed by
 * {@code ActiveSableCompanion#getContaining(Level, Position)} (l.97, works for client levels) and
 * {@code sublevel/ClientSubLevel#renderPose(float)} (l.313, the pose interpolated for the partial tick, the same one
 * Sable renders the ship and its plot entities with, {@code mixin/entity/entity_rendering/LevelRendererMixin} l.76).
 */
public final class ClientShipPoses {

    private ClientShipPoses() {
    }

    /** World position of plot position {@code plotPos} at render time, or null when no client ship contains it. */
    public static @Nullable Vec3 toWorld(Level level, Vec3 plotPos, float partialTick) {
        Pose3dc pose = pose(level, plotPos, partialTick);
        return pose == null ? null : pose.transformPosition(plotPos);
    }

    /** Plot-to-world rotation of the ship containing {@code plotPos} at render time (copy), or null. */
    public static @Nullable Quaterniond orientation(Level level, Vec3 plotPos, float partialTick) {
        Pose3dc pose = pose(level, plotPos, partialTick);
        return pose == null ? null : new Quaterniond(pose.orientation());
    }

    private static @Nullable Pose3dc pose(Level level, Vec3 plotPos, float partialTick) {
        SubLevel sub = Sable.HELPER.getContaining(level, plotPos);
        return sub instanceof ClientSubLevel c && !c.isRemoved() ? c.renderPose(partialTick) : null;
    }
}

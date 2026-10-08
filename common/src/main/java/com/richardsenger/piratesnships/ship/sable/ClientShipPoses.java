package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
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

    /**
     * True when the entity stands on or rides in a client ship. {@code ActiveSableCompanion#getTrackingOrVehicleSubLevel}
     * (l.449): the sub-level the entity stands on, else the one containing its vehicle. Sable sets the tracked
     * sub-level in its {@code Entity.move} redirect ({@code mixin/entity/entity_sublevel_collision/EntityMixin} l.109
     * to 180; it runs for the local player on the client) and clears it when the entity leaves the deck, e.g. while
     * jumping; the vehicle part is updated every tick ({@code EntityMixin#sable$tickInject}, l.219).
     */
    public static boolean onShip(Entity entity) {
        SubLevel sub = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        return sub instanceof ClientSubLevel c && !c.isRemoved();
    }

    /**
     * Plot-to-world rotation (copy) at render time of the client ship the entity stands on or rides in, or null
     * (WV1 camera sway). Same lookup as {@link #onShip}: {@code ActiveSableCompanion#getTrackingOrVehicleSubLevel}
     * (l.449) and {@code ClientSubLevel#renderPose(float)} (l.313).
     */
    public static @Nullable Quaterniond shipOrientation(Entity entity, float partialTick) {
        SubLevel sub = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        return sub instanceof ClientSubLevel c && !c.isRemoved() ? new Quaterniond(c.renderPose(partialTick).orientation()) : null;
    }

    private static @Nullable Pose3dc pose(Level level, Vec3 plotPos, float partialTick) {
        SubLevel sub = Sable.HELPER.getContaining(level, plotPos);
        return sub instanceof ClientSubLevel c && !c.isRemoved() ? c.renderPose(partialTick) : null;
    }
}

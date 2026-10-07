package com.richardsenger.piratesnships.mob.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a mob to face a point at once. The melee sweep and the firearm read the mob's own rotation
 * ({@code getViewVector}, {@code getXRot}/{@code getYRot}), not its head, so attacks set it explicitly.
 */
public final class MobAim {

    private MobAim() {
    }

    public static void face(Mob mob, Vec3 point) {
        Vec3 eye = mob.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(pitch);
    }

    /** Faces the middle of {@code target}'s body. */
    public static void face(Mob mob, Entity target) {
        face(mob, target.position().add(0, target.getBbHeight() * 0.6, 0));
    }

    /** Whether {@code viewer} looks towards {@code other} (within 90° either side, horizontally). */
    public static boolean facing(Entity viewer, Entity other) {
        Vec3 look = viewer.getViewVector(1.0f);
        Vec3 to = other.position().subtract(viewer.position());
        return look.x * to.x + look.z * to.z > 0;
    }
}

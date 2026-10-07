package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.MooringRingBlock;
import com.richardsenger.piratesnships.combat.grapple.RopeRiderEntity;
import com.richardsenger.piratesnships.combat.grapple.RopeSlide;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a grappling rope runs on the client (GR1, GR2): its ends at render time through the ships' render poses, the
 * points where riders hang on it, and the pick of a rope by the look ray. Physical client only.
 */
public final class ClientRopes {

    private ClientRopes() {
    }

    /**
     * The rope's fixed near end at render time: the mooring ring it is tied to or the pin (GR2), through the ship's
     * render pose when it is on a ship; null when the thrower holds it.
     */
    public static @Nullable Vec3 fixedNearEnd(GrapplingHookEntity hook, float partialTick) {
        BlockPos ring = hook.syncedTiedRing().orElse(null);
        Vec3 local = ring != null ? MooringRingBlock.ringCenter(hook.level(), ring) : hook.syncedPin().orElse(null);
        if (local == null) {
            return null;
        }
        Vec3 onShip = ClientShipPoses.toWorld(hook.level(), local, partialTick);
        return onShip != null ? onShip : local;
    }

    /** The rope's near end for picking: the fixed end, else the thrower's hand below the eyes; null without a thrower. */
    public static @Nullable Vec3 nearEnd(GrapplingHookEntity hook, float partialTick) {
        Vec3 fixed = fixedNearEnd(hook, partialTick);
        if (fixed != null) {
            return fixed;
        }
        Entity owner = hook.getOwner();
        return owner != null ? owner.getEyePosition(partialTick).subtract(0, GrapplingHookEntity.HAND_BELOW_EYES, 0) : null;
    }

    /** The hook at render time: on its ship's render pose while latched, else interpolated. */
    public static Vec3 hookPos(GrapplingHookEntity hook, float partialTick) {
        if (hook.state() == GrapplingHookEntity.State.LATCHED) {
            Vec3 onShip = ClientShipPoses.toWorld(hook.level(), hook.syncedPlotPos(), partialTick);
            if (onShip != null) {
                return onShip;
            }
        }
        return new Vec3(Mth.lerp(partialTick, hook.xOld, hook.getX()), Mth.lerp(partialTick, hook.yOld, hook.getY()),
                Mth.lerp(partialTick, hook.zOld, hook.getZ()));
    }

    /**
     * Where the riders on {@code hook}'s rope from {@code from} to {@code to} grip it (world, render time), ordered from
     * {@code from} to {@code to}; empty when nobody rides.
     */
    public static List<Vec3> grips(GrapplingHookEntity hook, Vec3 from, Vec3 to, float partialTick) {
        double hang = GrappleConfig.HANG_OFFSET.get();
        AABB box = new AABB(from, to).inflate(1.0).expandTowards(0, -hang - 1.0, 0);
        List<Vec3> grips = new ArrayList<>();
        for (RopeRiderEntity r : hook.level().getEntitiesOfClass(RopeRiderEntity.class, box, r -> r.hookId() == hook.getId())) {
            grips.add(new Vec3(Mth.lerp(partialTick, r.xOld, r.getX()), Mth.lerp(partialTick, r.yOld, r.getY()) + hang,
                    Mth.lerp(partialTick, r.zOld, r.getZ())));
        }
        grips.sort(Comparator.comparingDouble(g -> RopeSlide.parameter(from, to, g)));
        return grips;
    }

    /** A rope hit by the look ray: the hook and the pick. */
    public record RopeHit(GrapplingHookEntity hook, RopeSlide.Pick pick) {
    }

    /**
     * The latched rope the look ray from {@code eye} along {@code look} passes closest to, within {@code board_reach}
     * and {@code board_pick_radius} ({@link RopeSlide#pick}), or null.
     */
    public static @Nullable RopeHit pick(ClientLevel level, Vec3 eye, Vec3 look) {
        double reach = GrappleConfig.BOARD_REACH.get();
        double radius = GrappleConfig.BOARD_PICK_RADIUS.get();
        RopeHit best = null;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof GrapplingHookEntity hook) || hook.isRemoved() || hook.state() != GrapplingHookEntity.State.LATCHED) {
                continue;
            }
            Vec3 a = nearEnd(hook, 1.0f);
            if (a == null) {
                continue;
            }
            RopeSlide.Pick p = RopeSlide.pick(eye, look, reach, a, hookPos(hook, 1.0f), radius);
            if (p != null && (best == null || p.rayDistance() < best.pick().rayDistance())) {
                best = new RopeHit(hook, p);
            }
        }
        return best;
    }
}

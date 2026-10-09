package com.richardsenger.piratesnships.ship.rigging.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetAssist;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/** Client setup of the rigging module (physical client only, from {@code RiggingModule.initClient()}). */
public final class RiggingClient {

    private RiggingClient() {
    }

    public static void init() {
        // RL2: the net is drawn by its fitting A
        ClientEvents.registerBlockEntityRenderer(RiggingContent.NET_FITTING_BLOCK_ENTITY, RatlineNetRenderer::new);
        RatlineNetAssist.installClientFrames(RiggingClient::frame);
    }

    /**
     * The client's frame of the ship whose plot holds {@code plotPos}: its current pose ({@link ClientShipPoses#renderPose}
     * at partial tick 1, the pose of the last tick, which Sable's climbing mixin reads as {@code logicalPose}); its
     * {@code transformPositionInverse} and {@code orientation} are {@code companion.math.Pose3dc} methods
     * (docs/sable-notes.md §7, checked with javap on sable-companion 1.6.0).
     *
     * <p>Its motion (RL3): the ship's velocity at a plot point is Sable's {@code ActiveSableCompanion#getVelocity(Level,
     * Vec3)} (l.369; for a client sub-level the pose change of the last tick times 20, l.394-396, the very change Sable's
     * own carry moves a tracked entity by), and Sable carries the climber itself when it tracks it on this ship
     * ({@code ActiveSableCompanion#getTrackingSubLevel}, l.434, against {@code #getContaining(Level, Position)}, l.97).
     */
    private static RatlineNetAssist.@Nullable Frame frame(Level level, BlockPos plotPos) {
        Pose3dc pose = ClientShipPoses.renderPose(level, Vec3.atCenterOf(plotPos), 1f);
        if (pose == null) {
            return null;
        }
        Pose3dc copy = pose;
        Vec3 centre = Vec3.atCenterOf(plotPos);
        Quaterniond q = new Quaterniond(copy.orientation());
        Vec3 origin = copy.transformPositionInverse(Vec3.ZERO);
        // the pose is Sable's own object: read what is needed now, keep only copies
        Quaterniond inv = new Quaterniond(q).conjugate();
        return new RatlineNetAssist.Frame() {
            @Override
            public Vec3 toLocal(Vec3 world) {
                return RatlineNetAssist.rotate(inv, world).add(origin);
            }

            @Override
            public Vec3 dirToWorld(Vec3 local) {
                return RatlineNetAssist.rotate(q, local);
            }

            @Override
            public Vec3 dirToLocal(Vec3 world) {
                return RatlineNetAssist.rotate(inv, world);
            }

            @Override
            public Vec3 velocityAt(Vec3 local) {
                return Sable.HELPER.getVelocity(level, local).scale(1.0 / RatlineNetAssist.TICKS_PER_SECOND);
            }

            @Override
            public boolean carries(Entity entity) {
                SubLevel ship = Sable.HELPER.getContaining(level, centre);
                return ship != null && Sable.HELPER.getTrackingSubLevel(entity) == ship;
            }
        };
    }
}

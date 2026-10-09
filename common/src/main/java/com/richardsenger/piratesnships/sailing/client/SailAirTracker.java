package com.richardsenger.piratesnships.sailing.client;

import com.richardsenger.piratesnships.sailing.force.EfficiencyCurve;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.SailAir;
import com.richardsenger.piratesnships.sailing.sail.SailShape;
import com.richardsenger.piratesnships.sailing.sail.SailVisualsConfig;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * The client half of VIS1b for one sail renderer: keeps a {@link SailShape.Look} per sail (weakly, by its head block
 * entity, so no block entity changes), and once per frame works out the apparent wind on the sail and eases the look.
 * Render thread only; the scratch vectors are reused, so a frame allocates nothing per sail but the
 * {@code Vec3.atCenterOf} key Sable's lookup needs.
 *
 * <p>The apparent wind is the synced wind ({@link ClientWind}) minus the sail's own motion, which is the smoothed
 * difference of its world position between frames (Sable's render pose, {@link ClientShipPoses#renderPose}; its
 * {@code transformPosition(Vector3dc, Vector3d)} and {@code orientation()} are {@code companion.math.Pose3dc}
 * defaults, sable-companion 1.6.0 l.25 and l.47). It is turned into the plot frame with the pose's orientation, where
 * {@link SailAir} reads how the sail draws.
 */
final class SailAirTracker {

    private final Map<BlockEntity, SailShape.Look> looks = new WeakHashMap<>();
    private final Vector3d plot = new Vector3d();
    private final Vector3d world = new Vector3d();
    private final Vector3d wind = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final Vector3d outAxis = new Vector3d();

    /** The look of a sail, created on its first frame (a stay's bow defaults to its tack). */
    SailShape.Look look(BlockEntity be, int side, int defaultBow) {
        SailShape.Look look = looks.get(be);
        if (look == null) {
            look = new SailShape.Look(side, defaultBow);
            looks.put(be, look);
        }
        return look;
    }

    /**
     * Updates the sail's look for this frame and returns the side its cloth should belly to ({@link ClothSide}, with
     * the apparent wind).
     *
     * @param bowX       x of the sail's bow axis in the plot frame (unit, horizontal; sign from the look's bow)
     * @param bowZ       z of it
     * @param normalX    x of the cloth's normal in the plot frame (unit, horizontal)
     * @param normalZ    z of it
     * @param drop       the full drop of the sail [blocks]
     * @param currentSide the side shown so far
     */
    int update(SailShape.Look look, Level level, Vec3 center, float partialTick, double now, EfficiencyCurve curve,
               SailTrim trim, double bowX, double bowZ, double normalX, double normalZ, float drop, int currentSide) {
        Pose3dc pose = ClientShipPoses.renderPose(level, center, partialTick);
        Quaterniondc q = pose == null ? null : pose.orientation();
        if (pose != null) {
            pose.transformPosition(plot.set(center.x, center.y, center.z), world);
            look.trackPosition(world.x, world.z, now);
        } else {
            look.still();
        }
        WindSample w = ClientWind.hasData() ? ClientWind.sample(now) : WindSample.CALM;
        double strength = Math.max(0.0, w.strength());
        wind.set(w.dirX() * strength - look.velocityX, 0.0, w.dirZ() * strength - look.velocityZ);
        double speed = Math.hypot(wind.x, wind.z);
        int side = currentSide;
        if (speed > 0.01) {
            outAxis.set(normalX, 0.0, normalZ);
            side = ClothSide.side(currentSide, outAxis, q, wind.x / speed, wind.z / speed);
        }
        // into the plot frame, where the bow and the cloth's normal are known
        velocity.set(look.velocityX, 0.0, look.velocityZ);
        if (q != null) {
            q.transformInverse(wind);
            q.transformInverse(velocity);
        }
        look.bowSign = SailAir.bowSign(look.bowSign, velocity.x * bowX + velocity.z * bowZ);
        double beta = SailAir.beta(wind.x, wind.z, bowX, bowZ, look.bowSign);
        double square = SailAir.squareness(wind.x, wind.z, normalX, normalZ);
        float fill = SailAir.fill(curve, beta, square);
        float maxBelly = SailVisualsConfig.MAX_BELLY.get().floatValue();
        float flutter = SailVisualsConfig.FLUTTER_AMPLITUDE.get().floatValue();
        look.approach(SailShape.bellyTarget(trim, drop, speed, fill, maxBelly),
                SailShape.flutterTarget(trim, drop, speed, fill, flutter), side, fill, now);
        return side;
    }
}

package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.sailing.force.AnchorState;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the anchor lies (GR4, docs/design.md §5.3): pure rules from its phase and chain, shared by the server (which syncs
 * the {@link Kind}) and the renderer (which builds the rotation from the synced kind, the hawse and the ship's heading).
 *
 * <p><b>The anchor's frame.</b> Origin at the crown (the anchor's position, {@link AnchorMotion}), {@code +Y} along the
 * shank from the crown to the ring, {@code ±X} along the arms, {@code +Z} along the stock ({@code X × Y}). The ring,
 * where the chain is shackled, is {@code (0, HEIGHT, 0)}.
 *
 * <ul>
 *   <li>{@link Kind#STOWED}: in the ship's plot under the hawse, upright, the arms along the plot axis (the old look).</li>
 *   <li>{@link Kind#DROPPING}: falling flukes down, the shank along the chain toward the hawse (at most
 *       {@link #MAX_DROP_TILT} from upright), the arms turned with the ship's heading.</li>
 *   <li>{@link Kind#HANGING}: at the chain's end, or heaved up off the bottom: upright under the hawse like the stowed
 *       anchor, turned with the ship's heading.</li>
 *   <li>{@link Kind#HOLDING}: on the bottom: the shank along the seabed pointing at the ship, rising
 *       {@link #HOLD_RISE_SLACK} (up to {@link #MAX_HOLD_RISE} toward a taut chain), the arms upright so one fluke digs
 *       in (rolled {@link #FLUKE_ROLL}), the stock flat; {@link #HOLD_LIFT} above the crown's point so the crown rests on
 *       the bed.</li>
 * </ul>
 */
public final class AnchorPose {

    public enum Kind { STOWED, DROPPING, HANGING, HOLDING }

    /** Most a falling anchor leans toward the chain [degrees]. */
    public static final float MAX_DROP_TILT = 60f;
    /** The shank's rise off the bed with a slack chain, and its most toward a taut one [degrees]. */
    public static final float HOLD_RISE_SLACK = 4f, MAX_HOLD_RISE = 30f;
    /** The arms' lean off upright on the bottom [degrees]. */
    public static final float FLUKE_ROLL = 15f;
    /** The lying anchor's lift above its crown point [blocks]: the crown's half height. */
    public static final float HOLD_LIFT = 0.1f;
    /** Time constant of the turn from one pose to the next [ticks]. */
    public static final float TURN_TICKS = 4f;

    private AnchorPose() {
    }

    /** The pose of an anchor {@code out} of its ship (or stowed) in {@code phase}, resting on the bottom, its chain taut. */
    public static Kind of(boolean out, AnchorState.Phase phase, boolean resting, boolean taut) {
        if (!out || phase == AnchorState.Phase.RAISED) {
            return Kind.STOWED;
        }
        if (resting) {
            return Kind.HOLDING;
        }
        return phase == AnchorState.Phase.RAISING || taut ? Kind.HANGING : Kind.DROPPING;
    }

    /**
     * The rotation of {@code kind} from the anchor's frame to the world; {@code toHawse}: from the crown to the hawse
     * (world), {@code arms}: the ship's arm axis (world; its horizontal part counts). {@link Kind#STOWED} is upright with
     * the arms along {@code arms} (in the frame {@code arms} is given in).
     */
    public static Quaternionf orientation(Kind kind, Vector3f toHawse, Vector3f arms) {
        Vector3f up = new Vector3f(0f, 1f, 0f);
        return switch (kind) {
            case STOWED, HANGING -> frame(up, arms);
            case DROPPING -> frame(leanToward(toHawse, MAX_DROP_TILT), arms);
            case HOLDING -> {
                Vector3f h = new Vector3f(toHawse.x, 0f, toHawse.z);
                if (h.lengthSquared() < 1.0e-8f) {
                    h.set(-arms.z, 0f, arms.x); // the hawse straight above: along the ship
                    if (h.lengthSquared() < 1.0e-8f) h.set(0f, 0f, 1f);
                }
                h.normalize();
                float elevation = (float) Math.toDegrees(Math.atan2(toHawse.y, Math.sqrt(toHawse.x * toHawse.x + toHawse.z * toHawse.z)));
                float rise = Mth.clamp(elevation, HOLD_RISE_SLACK, MAX_HOLD_RISE);
                float r = rise * Mth.DEG_TO_RAD;
                Vector3f shank = new Vector3f(h).mul(Mth.cos(r)).add(0f, Mth.sin(r), 0f);
                // the arms upright, leaned by the fluke roll about the shank: one fluke down into the bed
                Vector3f armsUp = new Quaternionf().rotationAxis(FLUKE_ROLL * Mth.DEG_TO_RAD, shank).transform(new Vector3f(up));
                yield frame(shank, armsUp);
            }
        };
    }

    /** The anchor's offset from its crown point to its frame's origin in {@code kind} (the lying anchor's lift). */
    public static Vector3f lift(Kind kind) {
        return new Vector3f(0f, kind == Kind.HOLDING ? HOLD_LIFT : 0f, 0f);
    }

    /** The ring (where the chain is shackled) relative to the crown point, in {@code rotation} and {@code kind}. */
    public static Vector3f ring(Quaternionf rotation, Kind kind) {
        return rotation.transform(new Vector3f(0f, (float) AnchorTravel.HEIGHT, 0f)).add(lift(kind));
    }

    /**
     * The render transform from the code-built model ({@code AnchorRenderer.createLayer}: origin at the ring's top, y
     * down, in blocks as {@code ModelPart} draws pixel/16) to the world relative to the crown point:
     * {@code translate(lift) · R · translate(0, HEIGHT, 0) · scale(−1, −1, 1)}.
     */
    public static Matrix4f modelMatrix(Quaternionf rotation, Kind kind) {
        Vector3f l = lift(kind);
        return new Matrix4f().translation(l).rotate(rotation).translate(0f, (float) AnchorTravel.HEIGHT, 0f).scale(-1f, -1f, 1f);
    }

    /** How far a pose turns toward its target in {@code dtTicks} ({@link #TURN_TICKS}): 0 for none, toward 1. */
    public static float turn(float dtTicks) {
        return dtTicks <= 0f ? 0f : 1f - (float) Math.exp(-dtTicks / TURN_TICKS);
    }

    /** {@code dir} (from the crown) as a unit shank, leaned at most {@code maxTilt} degrees off upright; upright when unknown. */
    static Vector3f leanToward(Vector3f dir, float maxTilt) {
        if (dir.lengthSquared() < 1.0e-8f) {
            return new Vector3f(0f, 1f, 0f);
        }
        Vector3f d = new Vector3f(dir).normalize();
        float tilt = (float) Math.acos(Mth.clamp(d.y, -1f, 1f));
        float max = maxTilt * Mth.DEG_TO_RAD;
        if (tilt <= max) {
            return d;
        }
        Vector3f h = new Vector3f(d.x, 0f, d.z);
        if (h.lengthSquared() < 1.0e-8f) {
            return new Vector3f(0f, 1f, 0f); // straight down the chain: hang upright
        }
        h.normalize();
        return h.mul(Mth.sin(max)).add(0f, Mth.cos(max), 0f);
    }

    /** The rotation with {@code +Y} along {@code shank} and {@code +X} along {@code arms} made square to it. */
    static Quaternionf frame(Vector3f shank, Vector3f arms) {
        Vector3f y = new Vector3f(shank).normalize();
        Vector3f x = new Vector3f(arms).sub(new Vector3f(y).mul(arms.dot(y)));
        if (x.lengthSquared() < 1.0e-8f) {
            x = Math.abs(y.x) < 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 0f, 1f);
            x.sub(new Vector3f(y).mul(x.dot(y)));
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix3f(x, y, z).getNormalizedRotation(new Quaternionf());
    }
}

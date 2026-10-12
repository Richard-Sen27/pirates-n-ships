package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the grappling hook lies (GR4, docs/design.md §8.3): pure rules from the hook's velocity, the face it bit into and
 * its state, shared by the server (which syncs the latched pose) and the renderer.
 *
 * <p><b>The hook's frame.</b> Origin at the crown (the bite point, where the flukes meet the shank), {@code +Y} along
 * the shank from the crown to the eye, the four flukes along {@code ±X} and {@code ±Z}. The point (where the hook
 * leads, where it bites) is {@code −Y}. A pose is the rotation from this frame to the world (or to a ship's plot for a
 * hook latched on a ship; the renderer puts the ship's rotation in front).
 *
 * <ul>
 *   <li>{@link Kind#FLYING}: point first along the velocity, like an arrow: yaw {@code atan2(vx, vz)}, pitch
 *       {@code atan2(vy, horizontal)} (vanilla's projectile convention, positive up), {@link #flight}.</li>
 *   <li>{@link Kind#HANGING}: on a rail, a ring or a cleat: the flukes over the hold, the shank hanging straight down
 *       from it, turned to the flight's heading.</li>
 *   <li>{@link Kind#FLAT}: on a deck or the ground (a face whose normal points up): lying on its side, the point along
 *       the flight's heading, dipped {@link #FLAT_DIP} degrees into the planks.</li>
 *   <li>{@link Kind#BITING}: on a wall, a cliff or a ceiling: the point into the face, the shank sticking out along its
 *       normal.</li>
 *   <li>{@link Kind#FALLING}: dropped free (retracting): point down, turned to its last heading.</li>
 * </ul>
 *
 * <p><b>The model.</b> {@code art/models/grappling_hook.bbmodel}, a vanilla item model (not GeckoLib): shaft along the
 * model's {@code +y}, crown at pixel y 2, eye ring centred at pixel y 14. {@link #modelMatrix} is the whole render
 * transform from the item model's block space (as {@code ItemDisplayContext.NONE} draws it, centred on the origin) into
 * the hook's frame placed by a pose; the rope ties on at {@link #eye}.
 */
public final class HookPose {

    public enum Kind { FLYING, HANGING, FLAT, BITING, FALLING }

    /** Blocks per model block (the item model's 16 pixels): the hook is half a block long. */
    public static final float SCALE = 0.5f;
    /** The crown in the item model, block units centred on the model's middle (pixel y 2). */
    public static final float CROWN_Y = 2f / 16f - 0.5f;
    /** The middle of the eye ring in the item model (pixel y 14), where the rope is tied. */
    public static final float EYE_Y = 14f / 16f - 0.5f;
    /** From the crown to the eye along the shank [blocks]. */
    public static final float EYE_FROM_CROWN = (EYE_Y - CROWN_Y) * SCALE;
    /** How far a hook lying on a deck dips its point into the planks [degrees]. */
    public static final float FLAT_DIP = 10f;
    /** A face normal with a y component above this is a deck (or the ground), below its negative a ceiling. */
    public static final double DECK_NORMAL_Y = 0.7;
    /** The settle after latching: first tilt [degrees], decay time and swing rate [ticks, radians per tick], length. */
    public static final float SETTLE_DEGREES = 18f, SETTLE_DECAY = 3f, SETTLE_RATE = 1.2f, SETTLE_TICKS = 12f;

    private HookPose() {
    }

    // ------------------------------------------------------------------ rules

    /**
     * The pose of a hook latched on a face with normal {@code (nx, ny, nz)} (in the frame of the block it bit:
     * a ship's plot or the world); {@code hangs}: the block is a rail, ring or cleat the hook hangs from.
     */
    public static Kind latchKind(double nx, double ny, double nz, boolean hangs) {
        if (hangs) {
            return Kind.HANGING;
        }
        return ny > DECK_NORMAL_Y ? Kind.FLAT : Kind.BITING;
    }

    /** Vanilla's projectile yaw of a velocity [degrees]: {@code atan2(vx, vz)}. */
    public static float yaw(double vx, double vz) {
        return (float) (Mth.atan2(vx, vz) * Mth.RAD_TO_DEG);
    }

    /** Vanilla's projectile pitch of a velocity [degrees], positive up: {@code atan2(vy, horizontal)}. */
    public static float pitch(double vx, double vy, double vz) {
        return (float) (Mth.atan2(vy, Math.sqrt(vx * vx + vz * vz)) * Mth.RAD_TO_DEG);
    }

    /**
     * The pose whose point leads along yaw {@code yRot} and pitch {@code xRot} (degrees, projectile convention):
     * {@code Ry(yRot)·Rx(−(xRot + 90°))}, which turns the point {@code −Y} first to {@code +Z}, then up by the pitch,
     * then about the vertical by the yaw.
     */
    public static Quaternionf fromYawPitch(float yRot, float xRot) {
        return new Quaternionf().rotationY(yRot * Mth.DEG_TO_RAD).rotateX(-(xRot + 90f) * Mth.DEG_TO_RAD);
    }

    /** {@link Kind#FLYING}: point first along the velocity. A standing hook ({@code v = 0}) points down. */
    public static Quaternionf flight(double vx, double vy, double vz) {
        if (vx * vx + vy * vy + vz * vz < 1.0e-12) {
            return falling(0f);
        }
        return fromYawPitch(yaw(vx, vz), pitch(vx, vy, vz));
    }

    /**
     * The latched pose of {@code kind} on a face with normal {@code n}, the hook having flown along {@code v} (both in
     * the frame of the block it bit). {@link Kind#FLYING} and {@link Kind#FALLING} are no latched poses: they give
     * {@link #flight} and {@link #falling}.
     */
    public static Quaternionf latched(Kind kind, double nx, double ny, double nz, double vx, double vy, double vz) {
        float heading = Math.abs(vx) + Math.abs(vz) > 1.0e-9 ? yaw(vx, vz) : yaw(-nx, -nz);
        return switch (kind) {
            // Rx(180°) turns the shank down from the crown, the yaw keeps the flight's heading
            case HANGING -> new Quaternionf().rotationY(heading * Mth.DEG_TO_RAD).rotateX(Mth.PI);
            case FLAT -> fromYawPitch(heading, -FLAT_DIP);
            case BITING -> Math.abs(nx) + Math.abs(nz) > 1.0e-9 ? fromYawPitch(yaw(-nx, -nz), pitch(-nx, -ny, -nz))
                    : fromYawPitch(heading, pitch(-nx, -ny, -nz));
            case FLYING -> flight(vx, vy, vz);
            case FALLING -> falling(heading);
        };
    }

    /** {@link Kind#FALLING}: point down (the model upright), turned to {@code yRot}. */
    public static Quaternionf falling(float yRot) {
        return new Quaternionf().rotationY(yRot * Mth.DEG_TO_RAD);
    }

    /** The settle of a freshly latched hook: a tilt about its {@code X} axis decaying to 0 within {@link #SETTLE_TICKS}. */
    public static float settleDegrees(float ticksSinceLatch) {
        if (ticksSinceLatch < 0f || ticksSinceLatch >= SETTLE_TICKS) {
            return 0f;
        }
        float fade = 1f - ticksSinceLatch / SETTLE_TICKS; // reaches 0 at the end, no jump
        return SETTLE_DEGREES * (float) Math.exp(-ticksSinceLatch / SETTLE_DECAY) * Mth.cos(SETTLE_RATE * ticksSinceLatch) * fade;
    }

    /** {@code pose} with the settle of {@code ticksSinceLatch} applied in the hook's own frame (about the crown). */
    public static Quaternionf settled(Quaternionf pose, float ticksSinceLatch) {
        float deg = settleDegrees(ticksSinceLatch);
        return deg == 0f ? new Quaternionf(pose) : new Quaternionf(pose).rotateX(deg * Mth.DEG_TO_RAD);
    }

    // ------------------------------------------------------------------ geometry

    /** Where the hook points (its {@code −Y}) in {@code pose}. */
    public static Vector3f point(Quaternionf pose) {
        return pose.transform(new Vector3f(0f, -1f, 0f));
    }

    /** The eye, where the rope is tied, relative to the crown (the hook's position) in {@code pose} [blocks]. */
    public static Vector3f eye(Quaternionf pose) {
        return pose.transform(new Vector3f(0f, EYE_FROM_CROWN, 0f));
    }

    /**
     * The render transform from the item model (as drawn with {@code ItemDisplayContext.NONE}: block units centred on
     * the origin) into world space relative to the crown: {@code R · scale(SCALE) · translate(0, −CROWN_Y, 0)}.
     */
    public static Matrix4f modelMatrix(Quaternionf pose) {
        return new Matrix4f().rotation(pose).scale(SCALE).translate(0f, -CROWN_Y, 0f);
    }
}

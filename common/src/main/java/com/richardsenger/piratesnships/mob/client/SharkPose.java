package com.richardsenger.piratesnships.mob.client;

/**
 * The shark's code-driven pitch (GL1), pure maths without world access. Input is Minecraft's pitch (the entity's view
 * x rotation, degrees, positive = nose down); output is GeckoLib bone rotations (radians). GeckoLib bones are not in
 * vanilla's flipped model space (art/README.md, "GeckoLib bone rotations in code", rule 1): a positive x rotation lifts
 * the shark's nose (it faces −z), so the Minecraft pitch is negated once here and nowhere else.
 * <p>
 * In water the {@code root} bone pitches the whole body with the swimming direction (clamped to ±{@link #BODY_LIMIT}°).
 * The head is a child of the body, so it only takes what the body could not (beyond the body's clamp, at most
 * ±{@link #HEAD_LIMIT}°); on land the body stays level and the head takes the look pitch within its limit.
 */
public final class SharkPose {

    /** Largest body pitch in water, degrees. */
    public static final float BODY_LIMIT = 60f;
    /** Largest head pitch and yaw against the body, degrees. */
    public static final float HEAD_LIMIT = 30f;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private SharkPose() {
    }

    /** The body's pitch in Minecraft degrees (positive = nose down) for a view pitch. */
    static float bodyPitchDeg(float viewXRotDeg, boolean inWater) {
        return inWater ? clamp(viewXRotDeg, BODY_LIMIT) : 0f;
    }

    /** {@code root}'s x rotation (radians, GeckoLib bone space) for a Minecraft view pitch. */
    public static float rootRotX(float viewXRotDeg, boolean inWater) {
        return -bodyPitchDeg(viewXRotDeg, inWater) * DEG_TO_RAD;
    }

    /** {@code head}'s x rotation (radians, bone space, relative to the pitched body) for a Minecraft view pitch. */
    public static float headRotX(float viewXRotDeg, boolean inWater) {
        return -clamp(viewXRotDeg - bodyPitchDeg(viewXRotDeg, inWater), HEAD_LIMIT) * DEG_TO_RAD;
    }

    private static float clamp(float v, float limit) {
        return Math.max(-limit, Math.min(limit, v));
    }
}

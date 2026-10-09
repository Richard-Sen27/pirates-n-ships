package com.richardsenger.piratesnships.apparel;

/**
 * How a worn coat's tails hang (ART9), without client classes: the pitch of one tail (a child of the body part, hinged
 * at the body's bottom back edge) from the pitch of the body and of the leg on the same side, in vanilla model angles
 * (radians, positive x rotation swings the lower end backwards).
 *
 * <p>The tail keeps a world angle of {@link #REST} plus the leg's backward swing {@code b} as {@code b + CURVE * b^2}:
 * the leg turns about the hip, 3 px in front of the tail's hinge, so its back face sweeps back faster than the swing
 * angle alone and the tail has to lead it a little more the further it goes. A leg stepping forward only flares its
 * tail a little ({@link #FLARE}). The body's own pitch (crouching) is mostly taken out so the tails hang nearly straight
 * instead of sticking out behind. Vanilla's walk and sprint swing the legs up to about 0.8 rad (walk animation speed
 * at most about 0.55 of {@code HumanoidModel}'s 1.4); within that the leg stays in front of the tail's lower 5 px
 * ({@code CoatArmorModelTest}). Used by {@code apparel.client.CoatArmorModel}.
 */
public final class CoatTails {

    /** World pitch of a tail at rest: hanging a little away from the legs. */
    public static final float REST = 0.08f;
    /** The extra lead per squared radian of backward leg swing. */
    public static final float CURVE = 0.5f;
    /** Flare per radian of the leg swinging forward. */
    public static final float FLARE = 0.12f;
    /** The tails never swing further back than this (world pitch, radians). */
    public static final float MAX = 1.6f;
    /** At most this much of the body's pitch is taken out (crouching leans the body 0.5 forward). */
    public static final float MAX_BODY_COMPENSATION = 0.4f;

    private CoatTails() {
    }

    /**
     * The tail's own x rotation, relative to the body.
     *
     * @param legPitch  x rotation of the leg on the tail's side (vanilla: positive = foot backwards)
     * @param bodyPitch x rotation of the body (vanilla crouch: 0.5)
     */
    public static float pitch(float legPitch, float bodyPitch) {
        float back = Math.max(0f, legPitch);
        float world = Math.min(MAX, REST + back + CURVE * back * back + FLARE * Math.max(0f, -legPitch));
        float compensation = Math.max(-MAX_BODY_COMPENSATION, Math.min(MAX_BODY_COMPENSATION, bodyPitch));
        return world - compensation;
    }
}

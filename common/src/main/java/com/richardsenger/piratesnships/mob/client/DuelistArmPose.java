package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.mob.MeleePose;
import org.jetbrains.annotations.Nullable;

/**
 * The procedural sword-arm pose that telegraphs a duelist's melee phase on the GeckoLib rig (no attack animations
 * exist in the shared animation file yet; Blockbench ones can replace this in M3-art). Since MEL3 the crew shows the same
 * poses ({@code MeleeTelegraph}), and a bare-handed vanilla swing gets {@link #punch}. Pure: angles in degrees in the
 * animation-file convention of the rig contract (negative x swings an arm forward, positive x leans the waist
 * forward); the model converts them to GeckoLib's code rotations.
 *
 * <ul>
 *   <li>Slash: wind-up raises the sword arm high and cocks it out (the telegraph), the hit frames sweep it down and
 *       across, recovery lets it sink.</li>
 *   <li>Thrust: wind-up draws the arm back and leans back, the hit frames punch it straight forward with a lean in.</li>
 *   <li>Guard: blade held across the body; parry: blade raised high across; stagger: arm flung out, leaning back.</li>
 * </ul>
 */
public final class DuelistArmPose {

    /**
     * Main (sword) arm x/y/z, waist x and the main hand's x (the wrist, which tips the held blade; MEL3), in degrees.
     */
    public record Angles(float armX, float armY, float armZ, float waistX, float handX) {
        /** A pose with a straight wrist. */
        public Angles(float armX, float armY, float armZ, float waistX) {
            this(armX, armY, armZ, waistX, 0);
        }
    }

    /**
     * MEL3: the thrust's wrist. A held blade points forward and a little down off a hanging arm (the item's third-person
     * display), so the arm's swing alone would aim it at the ground when drawn back and at the sky on the lunge; the
     * wrist levels it (negative tips the blade up, positive down, like the arm's x). Proven by {@code CrewMeleePoseTest}.
     */
    static final float THRUST_DRAWN_WRIST = -35, THRUST_LUNGE_WRIST = 50;

    private DuelistArmPose() {
    }

    /** The pose, or {@code null} when the duelist is idle (the body animations play unchanged). */
    public static @Nullable Angles of(MeleePose pose, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        boolean thrust = pose.attack() == AttackKind.THRUST;
        return switch (pose.phase()) {
            case IDLE -> null;
            case WINDUP -> thrust
                    ? lerp(new Angles(-30, 0, 0, 0), new Angles(25, 10, 5, -8, THRUST_DRAWN_WRIST), t)
                    : lerp(new Angles(-30, 0, 0, 0), new Angles(-160, 35, -20, -4), t);
            case ACTIVE -> thrust
                    ? lerp(new Angles(25, 10, 5, -8, THRUST_DRAWN_WRIST), new Angles(-90, 0, 0, 12, THRUST_LUNGE_WRIST), t)
                    : lerp(new Angles(-160, 35, -20, -4), new Angles(-40, -45, 10, 10), t);
            case RECOVERY -> thrust
                    ? lerp(new Angles(-90, 0, 0, 12, THRUST_LUNGE_WRIST), new Angles(-30, 0, 0, 2), t)
                    : lerp(new Angles(-40, -45, 10, 10), new Angles(-25, -10, 0, 2), t);
            case GUARDING -> new Angles(-70, -45, 0, 0);
            case PARRYING -> new Angles(-115, -50, 0, -3);
            case STAGGERED -> new Angles(20, 20, 30, -12);
        };
    }

    /** Peak of the bare-handed punch: the arm straight forward at shoulder height, turned a little inwards (degrees). */
    public static final Angles PUNCH = new Angles(-85, -15, 0, 6);

    /**
     * The bare-handed (or vanilla-sword) swing of a mob whose vanilla melee called {@code swing(hand)} (MEL3: a crew
     * member punching, BRD3's {@code unarmed_damage}), from vanilla's attack progress {@code attackAnim} (0..1,
     * {@code LivingEntity#getAttackAnim}): the arm punches out and comes back on a sine. Null at rest (0 or 1), so the
     * animations play unchanged. Pure.
     */
    public static @Nullable Angles punch(float attackAnim) {
        if (attackAnim <= 0f || attackAnim >= 1f) return null;
        float s = (float) Math.sin(Math.PI * attackAnim);
        return lerp(new Angles(0, 0, 0, 0), PUNCH, s);
    }

    static Angles lerp(Angles a, Angles b, float t) {
        return new Angles(a.armX() + (b.armX() - a.armX()) * t, a.armY() + (b.armY() - a.armY()) * t,
                a.armZ() + (b.armZ() - a.armZ()) * t, a.waistX() + (b.waistX() - a.waistX()) * t,
                a.handX() + (b.handX() - a.handX()) * t);
    }
}

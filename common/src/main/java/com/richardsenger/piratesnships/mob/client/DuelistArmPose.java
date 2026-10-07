package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.mob.MeleePose;
import org.jetbrains.annotations.Nullable;

/**
 * The procedural sword-arm pose that telegraphs a duelist's melee phase on the GeckoLib rig (no attack animations
 * exist in the shared animation file yet; Blockbench ones can replace this in M3-art). Pure: angles in degrees in the
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

    /** Main (sword) arm x/y/z and waist x, in degrees. */
    public record Angles(float armX, float armY, float armZ, float waistX) {
    }

    private DuelistArmPose() {
    }

    /** The pose, or {@code null} when the duelist is idle (the body animations play unchanged). */
    public static @Nullable Angles of(MeleePose pose, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        boolean thrust = pose.attack() == AttackKind.THRUST;
        return switch (pose.phase()) {
            case IDLE -> null;
            case WINDUP -> thrust
                    ? lerp(new Angles(-30, 0, 0, 0), new Angles(25, 10, 5, -8), t)
                    : lerp(new Angles(-30, 0, 0, 0), new Angles(-160, 35, -20, -4), t);
            case ACTIVE -> thrust
                    ? lerp(new Angles(25, 10, 5, -8), new Angles(-90, 0, 0, 12), t)
                    : lerp(new Angles(-160, 35, -20, -4), new Angles(-40, -45, 10, 10), t);
            case RECOVERY -> thrust
                    ? lerp(new Angles(-90, 0, 0, 12), new Angles(-30, 0, 0, 2), t)
                    : lerp(new Angles(-40, -45, 10, 10), new Angles(-25, -10, 0, 2), t);
            case GUARDING -> new Angles(-70, -45, 0, 0);
            case PARRYING -> new Angles(-115, -50, 0, -3);
            case STAGGERED -> new Angles(20, 20, 30, -12);
        };
    }

    static Angles lerp(Angles a, Angles b, float t) {
        return new Angles(a.armX() + (b.armX() - a.armX()) * t, a.armY() + (b.armY() - a.armY()) * t,
                a.armZ() + (b.armZ() - a.armZ()) * t, a.waistX() + (b.waistX() - a.waistX()) * t);
    }
}

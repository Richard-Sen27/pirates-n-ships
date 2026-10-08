package com.richardsenger.piratesnships.combat.grapple;

/**
 * Pure maths of hauling a hooked ship by hand (GR5, docs/design.md §8.3 "Hauling"): the frozen rope's excess length,
 * its spring-damper tension, the cap and the slip. No world access; tested in JUnit ({@code HaulMathTest}).
 *
 * <p>Units match the other rope forces ({@link GrappleConfig}): distances in blocks (one block is one metre), forces in
 * kpg·m/s², stiffness in kpg/s² (force per block of excess), damping in kpg/s (force per m/s of stretching).
 */
public final class HaulMath {

    private HaulMath() {
    }

    /** How far the rope's ends are beyond the frozen length [blocks], never negative (a rope never pushes). */
    public static double excess(double distance, double frozenLength) {
        return distance > frozenLength ? distance - frozenLength : 0.0;
    }

    /**
     * Rope tension [kpg·m/s²]: {@code stiffness × excess + damping × extensionRate}, clamped to {@code [0, maxForce]};
     * zero without excess.
     *
     * @param excess        {@link #excess} [blocks]
     * @param extensionRate rate at which the distance between the ends grows [m/s], negative while they approach
     */
    public static double tension(double excess, double extensionRate, double stiffness, double damping, double maxForce) {
        if (!(excess > 0) || maxForce <= 0 || stiffness <= 0) {
            return 0.0;
        }
        double t = stiffness * excess + damping * extensionRate;
        return Math.max(0.0, Math.min(maxForce, t));
    }

    /**
     * The frozen length after a slip: when the spring part of the tension ({@code stiffness × excess}) would exceed
     * {@code maxForce}, the rope runs through the hand until it holds exactly the cap, so the length grows to
     * {@code distance − maxForce / stiffness}; otherwise it stays. It never shrinks: a slip only pays out rope.
     */
    public static double slip(double distance, double frozenLength, double stiffness, double maxForce) {
        if (stiffness <= 0 || maxForce <= 0) {
            return Math.max(frozenLength, distance); // no grip at all: the rope runs freely
        }
        double maxExcess = maxForce / stiffness;
        return distance - frozenLength > maxExcess ? distance - maxExcess : frozenLength;
    }

    /** Whether the rope slips now ({@link #slip} would grow the frozen length). */
    public static boolean slips(double distance, double frozenLength, double stiffness, double maxForce) {
        return slip(distance, frozenLength, stiffness, maxForce) > frozenLength;
    }

    /**
     * Velocity [blocks per tick] added each game tick to an airborne or swimming player pulled by the rope: the
     * tension's share of the cap times {@code fullPull}.
     */
    public static double playerPull(double tension, double maxForce, double fullPull) {
        if (!(tension > 0) || maxForce <= 0) {
            return 0.0;
        }
        return fullPull * Math.min(1.0, tension / maxForce);
    }

    /**
     * Whether a player hauls now: the toggle, a hook latched on a ship, the near end in the player's hand (not tied to
     * a cleat or ring), the player sneaking, not riding anything (a rope pull or slide), and not standing on the hooked
     * ship itself (a rope from a ship to itself pulls nothing).
     */
    public static boolean hauls(boolean enabled, boolean hookOnShip, boolean nearEndInHand, boolean sneaking,
                                boolean riding, boolean aboardHookedShip) {
        return enabled && hookOnShip && nearEndInHand && sneaking && !riding && !aboardHookedShip;
    }
}

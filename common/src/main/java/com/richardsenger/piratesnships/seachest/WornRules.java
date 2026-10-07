package com.richardsenger.piratesnships.seachest;

/**
 * What a sea chest worn on the back does to its wearer (docs/design.md §11 "Carried on the back"). Pure: the tick
 * adapter {@link SeaChestWearing} turns the result into attribute modifiers, flags and velocity.
 *
 * <ul>
 *   <li>no jumping: a {@code JUMP_STRENGTH} modifier of −100 % (operation {@code ADD_MULTIPLIED_TOTAL});</li>
 *   <li>slower walking: a {@code MOVEMENT_SPEED} modifier of {@code speedMultiplier − 1} (same operation);</li>
 *   <li>no sprinting and no swimming: both flags are cleared every tick;</li>
 *   <li>the chest drags the wearer down: {@code sinkPull} blocks per tick are added downward while in water (not
 *       while flying).</li>
 * </ul>
 */
public final class WornRules {

    /** −100 %: jump strength 0. */
    public static final double JUMP_MODIFIER = -1.0;

    /**
     * @param enabled         config {@code sea_chest.enabled}; off = the chest is inert when worn
     * @param speedMultiplier walking speed while worn, as a factor of the normal speed (config {@code worn_speed_multiplier})
     * @param sinkPull        blocks per tick added downward in water (config {@code sink_pull})
     */
    public record Params(boolean enabled, double speedMultiplier, double sinkPull) {
    }

    /**
     * @param active        the restrictions apply (wearing and enabled); when false every modifier must be removed
     * @param jumpModifier  amount of the jump strength modifier ({@code ADD_MULTIPLIED_TOTAL})
     * @param speedModifier amount of the movement speed modifier ({@code ADD_MULTIPLIED_TOTAL})
     * @param clearSprint   set sprinting to false
     * @param clearSwim     set swimming to false
     * @param extraVy       added to the vertical velocity this tick (negative = down)
     */
    public record Effects(boolean active, double jumpModifier, double speedModifier, boolean clearSprint,
                          boolean clearSwim, double extraVy) {
        public static final Effects NONE = new Effects(false, 0.0, 0.0, false, false, 0.0);
    }

    private WornRules() {
    }

    public static Effects effects(boolean wearing, boolean inWater, boolean flying, boolean sprinting, boolean swimming, Params p) {
        if (!wearing || !p.enabled()) {
            return Effects.NONE;
        }
        double speed = clamp(p.speedMultiplier(), 0.0, 1.0) - 1.0;
        double vy = inWater && !flying && p.sinkPull() > 0.0 ? -p.sinkPull() : 0.0;
        return new Effects(true, JUMP_MODIFIER, speed, sprinting, swimming, vy);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

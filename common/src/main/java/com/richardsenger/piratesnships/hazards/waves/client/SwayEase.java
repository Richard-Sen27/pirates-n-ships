package com.richardsenger.piratesnships.hazards.waves.client;

/**
 * How much of the camera sway is applied (CAM1). Pure. A weight in [0, 1] walks toward 1 while the player is on a
 * ship and toward 0 otherwise, by {@code 1 / easeTicks} per client tick, and is shaped with a smoothstep, so boarding,
 * landing and leaving fade in and out instead of snapping. The weight scales the sway fraction, not the angle: the
 * roll itself still follows the deck's current pose every frame, so the deck motion is never lagged.
 */
public final class SwayEase {

    private double previous;
    private double current;

    /**
     * One client tick.
     *
     * @param target    true while the player is on a ship (the sway should be applied)
     * @param easeTicks ticks a full fade in or out takes; 0 or less is instant
     */
    public void tick(boolean target, int easeTicks) {
        double goal = target ? 1.0 : 0.0;
        if (easeTicks <= 0) {
            previous = current = goal;
            return;
        }
        previous = current;
        double step = 1.0 / easeTicks;
        // a little slack so the rounding of n steps of 1/n still lands on the goal in n ticks
        current = Math.abs(goal - current) <= step + 1e-9 ? goal : current + Math.signum(goal - current) * step;
    }

    /** The shaped weight for a frame, interpolated between the last two ticks, in [0, 1]. */
    public double weight(float partialTick) {
        double w = previous + (current - previous) * partialTick;
        return w * w * (3.0 - 2.0 * w);
    }

    /** True while nothing of the sway is applied or fading. */
    public boolean idle() {
        return previous == 0.0 && current == 0.0;
    }

    public void reset() {
        previous = current = 0.0;
    }
}

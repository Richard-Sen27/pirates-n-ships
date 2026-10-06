package com.richardsenger.piratesnships.sailing.ship;

/**
 * Pure rudder step logic of the helm (docs/design.md §5.3). The rudder has {@code stepsPerSide} steps to port and to
 * starboard; a step is a signed integer, positive = starboard (the sign convention of
 * {@link com.richardsenger.piratesnships.sailing.force.RudderModel}). The helm block stores it as the block state
 * property {@code rudder} = {@code step + MAX_STEPS} (0..2·MAX_STEPS, {@link #MAX_STEPS} = midships), so a config change
 * of the step count never makes a stored state invalid.
 *
 * <p><b>Click scheme</b> (no client code): the helmsman stands on the helm's {@code FACING} side, looking the other way
 * (towards the bow). The hit point on the block is projected onto the helmsman's right-hand direction: the right third
 * of the wheel steers one step to starboard, the left third one step to port, the middle third puts the rudder
 * midships.
 */
public final class RudderSteps {

    /** Largest number of steps per side the block state can hold. */
    public static final int MAX_STEPS = 5;

    /** Width of the middle (midships) zone on either side of the wheel's center, in blocks. */
    static final double MIDDLE_HALF_WIDTH = 1.0 / 6.0;

    public enum Click { PORT, MIDSHIPS, STARBOARD }

    private RudderSteps() {
    }

    /**
     * Which part of the wheel was clicked.
     *
     * @param dx       hit x minus the block center x
     * @param dz       hit z minus the block center z
     * @param facingX  x step of the helm's {@code FACING}
     * @param facingZ  z step of the helm's {@code FACING}
     */
    public static Click click(double dx, double dz, int facingX, int facingZ) {
        // the helmsman looks along -FACING; his right hand is (FACING.z, -FACING.x)
        double right = dx * facingZ - dz * facingX;
        if (Math.abs(right) < MIDDLE_HALF_WIDTH) {
            return Click.MIDSHIPS;
        }
        return right > 0 ? Click.STARBOARD : Click.PORT;
    }

    /** The step after a click, limited to ±{@code stepsPerSide}. */
    public static int apply(int step, Click click, int stepsPerSide) {
        int limit = limit(stepsPerSide);
        int s = Math.max(-limit, Math.min(limit, step));
        return switch (click) {
            case MIDSHIPS -> 0;
            case STARBOARD -> Math.min(limit, s + 1);
            case PORT -> Math.max(-limit, s - 1);
        };
    }

    /** Rudder angle in degrees (positive = starboard) of a step: {@code step / stepsPerSide · maxAngle}, clamped. */
    public static double angle(int step, int stepsPerSide, double maxAngleDeg) {
        int limit = limit(stepsPerSide);
        int s = Math.max(-limit, Math.min(limit, step));
        return maxAngleDeg * s / limit;
    }

    public static int toProperty(int step) {
        return Math.max(-MAX_STEPS, Math.min(MAX_STEPS, step)) + MAX_STEPS;
    }

    public static int fromProperty(int value) {
        return value - MAX_STEPS;
    }

    private static int limit(int stepsPerSide) {
        return Math.max(1, Math.min(MAX_STEPS, stepsPerSide));
    }
}

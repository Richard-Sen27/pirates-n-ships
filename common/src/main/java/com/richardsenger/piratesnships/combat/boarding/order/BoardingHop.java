package com.richardsenger.piratesnships.combat.boarding.order;

/**
 * The jump of a boarder over a gap (BRD3): the take-off velocity that carries a mob from the edge of one deck onto a
 * deck {@code distance} blocks away and up to one block higher or lower, under vanilla's mob physics: per tick the
 * position moves by the velocity, then gravity ({@link #GRAVITY}) is taken off the vertical speed and it is damped by
 * {@link #VERTICAL_DRAG}; in the air the horizontal speed is damped by {@link #AIR_DRAG} (the boarder gives no walking
 * input while it flies). Pure.
 */
public final class BoardingHop {

    /** Vanilla's jump speed ({@code LivingEntity#getJumpPower} with no boost). */
    public static final double JUMP = 0.42;
    /** A stronger take-off for a deck one block up, so the feet clear its edge with room to spare. */
    public static final double JUMP_UP = 0.5;
    public static final double GRAVITY = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    public static final double AIR_DRAG = 0.91;
    /** The longest flight simulated. */
    static final int MAX_TICKS = 60;

    /** The take-off velocity: horizontal speed along the line to the landing, vertical speed; and the flight's ticks. */
    public record Launch(double horizontal, double vertical, int ticks) {
    }

    private BoardingHop() {
    }

    /**
     * The launch that lands {@code distance} blocks away (horizontally) at {@code rise} blocks above the take-off
     * ({@code -1..1}): the jump speed for the rise, the ticks until the falling feet reach the landing height, and the
     * horizontal speed that covers the distance in exactly those ticks.
     */
    public static Launch launch(double distance, double rise) {
        double vy0 = rise > 0.01 ? JUMP_UP : JUMP;
        int n = flightTicks(vy0, rise);
        double sum = 0.0;
        double f = 1.0;
        for (int i = 0; i < n; i++) {
            sum += f;
            f *= AIR_DRAG;
        }
        return new Launch(sum <= 0 ? 0.0 : Math.max(0.0, distance) / sum, vy0, n);
    }

    /** Ticks of the flight from the take-off at vertical speed {@code vy0} until the falling feet reach {@code height}. */
    public static int flightTicks(double vy0, double height) {
        double y = 0.0;
        double vy = vy0;
        for (int t = 1; t <= MAX_TICKS; t++) {
            y += vy;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            if (vy < 0 && y <= height) return t;
        }
        return MAX_TICKS;
    }

    /** Height of the feet over the take-off after {@code ticks} ticks of a flight started at {@code vy0}. */
    public static double heightAfter(double vy0, int ticks) {
        double y = 0.0;
        double vy = vy0;
        for (int t = 0; t < ticks; t++) {
            y += vy;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
        }
        return y;
    }

    /** Horizontal distance flown after {@code ticks} ticks at take-off speed {@code v0}. */
    public static double distanceAfter(double v0, int ticks) {
        double d = 0.0;
        double v = v0;
        for (int t = 0; t < ticks; t++) {
            d += v;
            v *= AIR_DRAG;
        }
        return d;
    }
}

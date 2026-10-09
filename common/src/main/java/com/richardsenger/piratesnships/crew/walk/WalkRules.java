package com.richardsenger.piratesnships.crew.walk;

/**
 * The pure rules of a crew member's walk (WALK1): when it has arrived, when it gives up and is seated anyway, and when
 * it looks for a new path. No world access; {@link CrewWalk} feeds it ship-space distances and tick counts.
 */
public final class WalkRules {

    /** Speed (blocks per second) above which a ship counts as moving: its walkers re-path every interval. */
    public static final double MOVING_SPEED = 0.05;
    /** Turn rate (radians per second) above which a ship counts as moving. */
    public static final double MOVING_TURN = 0.02;

    /** What a walk does this tick. */
    public enum Step {
        /** Keep walking. */
        WALK,
        /** Close enough: take the place. */
        ARRIVED,
        /** Walked too long: seated at once (the fallback). */
        TIMED_OUT
    }

    private WalkRules() {
    }

    /**
     * The walk's step after {@code elapsed} ticks at {@code distance} blocks (ship space) from its spot: arrived within
     * {@code arriveDistance}, timed out at {@code timeoutTicks}, else walking. Arrival wins over the timeout.
     */
    public static Step step(double distance, double arriveDistance, long elapsed, int timeoutTicks) {
        if (distance <= arriveDistance) {
            return Step.ARRIVED;
        }
        return elapsed >= timeoutTicks ? Step.TIMED_OUT : Step.WALK;
    }

    /**
     * Whether the walker looks for a new path this tick: when it has none (never found, or the last one ran out short
     * of the spot), and every {@code repathTicks} while the ship moves (the deck turns under the path).
     */
    public static boolean repath(boolean hasPath, boolean shipMoving, long elapsed, int repathTicks) {
        if (!hasPath) {
            return true;
        }
        return shipMoving && elapsed > 0 && repathTicks > 0 && elapsed % repathTicks == 0;
    }

    /** Whether a ship with linear speed {@code speed} (blocks/s) and turn rate {@code turn} (rad/s) counts as moving. */
    public static boolean moving(double speed, double turn) {
        return speed > MOVING_SPEED || turn > MOVING_TURN;
    }
}

package com.richardsenger.piratesnships.mob.kraken;

/**
 * The kraken's state machine (docs/design.md §12), pure. One {@link #tick} per server tick:
 * <ul>
 *   <li><b>LURK</b>: deep below the surface; drifts toward the nearest ship or swimmer within {@code detection_range}.
 *       Rises ({@code SURFACE}) once the target is within {@link #SURFACE_RANGE} blocks.</li>
 *   <li><b>SURFACE</b>: rises next to the target and spreads its tentacles; {@code ATTACK} once it is at its attack
 *       spot. Back to {@code LURK} after {@link #LOSE_TARGET_TICKS} without a near target or {@link #SURFACE_TIMEOUT_TICKS}
 *       without reaching the spot.</li>
 *   <li><b>ATTACK</b>: the tentacles work (grab, mast strike, deck swipe, hold swimmers). Back to {@code LURK} after
 *       {@link #LOSE_TARGET_TICKS} without a target; {@code RETREAT} once it has attacked for
 *       {@code attack_duration_ticks} in all.</li>
 *   <li><b>RETREAT</b>: lets go of everything, submerges and sinks away; {@link #finished()} after {@link #RETREAT_TICKS}
 *       (the entity then leaves the world). Entered from every state when the health falls below
 *       {@code retreat_health_fraction}. Final.</li>
 * </ul>
 * {@code peaceful} keeps it in {@code LURK} (and sends an attacking kraken back there).
 */
public final class KrakenBrain {

    public enum State { LURK, SURFACE, ATTACK, RETREAT }

    /** Horizontal blocks to the target within which a lurking kraken rises. */
    public static final double SURFACE_RANGE = 14.0;
    public static final int LOSE_TARGET_TICKS = 60;
    public static final int SURFACE_TIMEOUT_TICKS = 400;
    public static final int RETREAT_TICKS = 200;

    /** Config: {@code peaceful}, {@code retreat_health_fraction}, {@code attack_duration_ticks}. */
    public record Params(boolean peaceful, double retreatHealthFraction, int attackDurationTicks) { }

    /**
     * One tick's view: health as a fraction of the maximum, whether there is a target at all (within detection range),
     * whether it is within {@link #SURFACE_RANGE}, and whether the kraken is at its attack spot.
     */
    public record Input(double healthFraction, boolean hasTarget, boolean targetNear, boolean atAttackSpot) { }

    private State state = State.LURK;
    private int ticksInState;
    private int ticksWithoutTarget;
    private int attackTicks;

    public State state() {
        return state;
    }

    public int ticksInState() {
        return ticksInState;
    }

    /** Ticks spent in {@code ATTACK} so far, over all attacks. */
    public int attackTicks() {
        return attackTicks;
    }

    /** True once a retreat has run its course. */
    public boolean finished() {
        return state == State.RETREAT && ticksInState >= RETREAT_TICKS;
    }

    /** Advances one tick; returns the state after it. */
    public State tick(Input in, Params p) {
        ticksInState++;
        if (state == State.RETREAT) {
            return state;
        }
        if (p.retreatHealthFraction() > 0 && in.healthFraction() < p.retreatHealthFraction()) {
            return enter(State.RETREAT);
        }
        if (p.peaceful()) {
            return state == State.LURK ? state : enter(State.LURK);
        }
        ticksWithoutTarget = in.hasTarget() && in.targetNear() ? 0 : ticksWithoutTarget + 1;
        switch (state) {
            case LURK -> {
                if (in.hasTarget() && in.targetNear()) enter(State.SURFACE);
            }
            case SURFACE -> {
                if (ticksWithoutTarget >= LOSE_TARGET_TICKS || ticksInState >= SURFACE_TIMEOUT_TICKS) enter(State.LURK);
                else if (in.atAttackSpot()) enter(State.ATTACK);
            }
            case ATTACK -> {
                attackTicks++;
                if (attackTicks >= p.attackDurationTicks()) enter(State.RETREAT);
                else if (ticksWithoutTarget >= LOSE_TARGET_TICKS) enter(State.LURK);
            }
            default -> { }
        }
        return state;
    }

    private State enter(State next) {
        if (next != state) {
            state = next;
            ticksInState = 0;
            ticksWithoutTarget = 0;
        }
        return state;
    }

    /** Restores a saved brain (unknown names start lurking). */
    public void load(String stateName, int ticksInState, int attackTicks) {
        State s = State.LURK;
        for (State v : State.values()) if (v.name().equals(stateName)) s = v;
        this.state = s;
        this.ticksInState = Math.max(0, ticksInState);
        this.attackTicks = Math.max(0, attackTicks);
        this.ticksWithoutTarget = 0;
    }
}

package com.richardsenger.piratesnships.sailing.force;

/**
 * The anchor (capstan) state machine (docs/design.md §5.3): {@code RAISED → DROPPING → HOLDING → RAISING → RAISED}.
 * Immutable; advance it once per game tick with {@link #tick}.
 *
 * <p>{@code hold} (0..1) is how strongly the anchor holds. It ramps up over {@code dropTicks} while dropping and down
 * over {@code raiseTicks} while raising (the anchor drags until it breaks free). Commands reverse a ramp from its
 * current value, so the holding force never jumps.
 *
 * @param phase current phase
 * @param hold  holding factor, 0..1
 */
public record AnchorState(Phase phase, double hold) {

    public enum Phase { RAISED, DROPPING, HOLDING, RAISING }

    public static final AnchorState RAISED = new AnchorState(Phase.RAISED, 0.0);

    public AnchorState {
        hold = Math.min(Math.max(0.0, hold), 1.0);
    }

    /** Command: let the anchor go. No effect when it is already dropping or holding. */
    public AnchorState drop() {
        return switch (phase) {
            case RAISED, RAISING -> new AnchorState(Phase.DROPPING, hold);
            case DROPPING, HOLDING -> this;
        };
    }

    /** Command: heave the anchor in. No effect when it is already raising or raised. */
    public AnchorState raise() {
        return switch (phase) {
            case HOLDING, DROPPING -> new AnchorState(Phase.RAISING, hold);
            case RAISING, RAISED -> this;
        };
    }

    /** Advances one game tick. */
    public AnchorState tick(SailingParams.AnchorParams p) {
        return switch (phase) {
            case DROPPING -> {
                double h = hold + 1.0 / p.dropTicks();
                yield h >= 1.0 - 1e-9 ? new AnchorState(Phase.HOLDING, 1.0) : new AnchorState(Phase.DROPPING, h);
            }
            case RAISING -> {
                double h = hold - 1.0 / p.raiseTicks();
                yield h <= 1e-9 ? RAISED : new AnchorState(Phase.RAISING, h);
            }
            case RAISED, HOLDING -> this;
        };
    }

    /** Whether the anchor is out of its stowed position (it may not hold fully yet). */
    public boolean isOut() {
        return phase != Phase.RAISED;
    }
}

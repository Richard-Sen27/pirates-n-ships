package com.richardsenger.piratesnships.sailing.force;

/**
 * The anchor (capstan) state machine (docs/design.md §5.2, AN2a): {@code RAISED → DROPPING → HOLDING → RAISING → RAISED}.
 * Immutable. The phases follow the anchor's body ({@code sailing.anchor.AnchorMotion}), not a timer:
 *
 * <ul>
 *   <li>{@link Phase#DROPPING}: the anchor is a free body, falling through the air and sinking through the water, or
 *       hanging at the end of the chain.</li>
 *   <li>{@link Phase#HOLDING}: it rests on the seabed; the chain pulls the ship whenever it is taut.</li>
 *   <li>{@link Phase#RAISING}: the capstan winds the chain in and pulls the anchor along the seabed and up.</li>
 * </ul>
 *
 * @param phase current phase
 */
public record AnchorState(Phase phase) {

    public enum Phase { RAISED, DROPPING, HOLDING, RAISING }

    public static final AnchorState RAISED = new AnchorState(Phase.RAISED);
    public static final AnchorState DROPPING = new AnchorState(Phase.DROPPING);
    public static final AnchorState HOLDING = new AnchorState(Phase.HOLDING);
    public static final AnchorState RAISING = new AnchorState(Phase.RAISING);

    public static AnchorState of(Phase phase) {
        return switch (phase) {
            case RAISED -> RAISED;
            case DROPPING -> DROPPING;
            case HOLDING -> HOLDING;
            case RAISING -> RAISING;
        };
    }

    /** Command: let the anchor go. No effect when it is already dropping or holding. */
    public AnchorState drop() {
        return switch (phase) {
            case RAISED, RAISING -> DROPPING;
            case DROPPING, HOLDING -> this;
        };
    }

    /** Command: heave the anchor in. No effect when it is already raising or raised. */
    public AnchorState raise() {
        return switch (phase) {
            case HOLDING, DROPPING -> RAISING;
            case RAISING, RAISED -> this;
        };
    }

    /** The falling anchor came to rest on the ground. */
    public AnchorState landed() {
        return phase == Phase.DROPPING ? HOLDING : this;
    }

    /** The resting anchor lost its ground (dragged off a ledge, the block below broken): it falls again. */
    public AnchorState lifted() {
        return phase == Phase.HOLDING ? DROPPING : this;
    }

    /** Whether the anchor is out of its stowed position. */
    public boolean isOut() {
        return phase != Phase.RAISED;
    }
}

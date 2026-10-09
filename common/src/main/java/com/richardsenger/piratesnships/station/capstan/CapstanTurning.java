package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.sailing.force.AnchorState;
import org.jetbrains.annotations.Nullable;

/**
 * Which way the capstan's bars turn (CAP2, CAP3, docs/design.md §6 "Capstan in motion", "Capstan turns for players
 * too"): synced from the server's
 * {@link CapstanBlockEntity} to clients, which draw the bars turning ({@code client/CapstanBarsRenderer}); the crew
 * member working the capstan walks round the drum the same way ({@link CapstanWalk}). Pure.
 */
public enum CapstanTurning {
    /** The capstan stands still: no work, no anchor moving on it (or {@code crew_stations.capstan.animation} is off). */
    NONE(0),
    /** Heaving the anchor in: clockwise seen from above. */
    RAISING(-1),
    /** Letting the anchor go: anticlockwise seen from above. */
    DROPPING(1);

    private final int sign;

    CapstanTurning(int sign) {
        this.sign = sign;
    }

    /**
     * The direction of the turn about the vertical axis: +1 is a positive right-handed turn about +y (anticlockwise seen
     * from above, {@code Axis.YP} with a positive angle), -1 the other way, 0 standing still.
     */
    public int sign() {
        return sign;
    }

    /** The turn while the capstan station works on {@code order}: raising one way, dropping the other. */
    public static CapstanTurning of(@Nullable Object order) {
        if (order == AnchorOrder.RAISE_ANCHOR) return RAISING;
        if (order == AnchorOrder.DROP_ANCHOR) return DROPPING;
        return NONE;
    }

    /**
     * CAP3: the turn of a capstan, whoever works it. A crew member's order in progress at the capstan
     * ({@code workingOrder}, null when no crew order runs with its occupant present) turns it as CAP2 did, so the bars
     * turn during a drop order's work before the anchor goes; otherwise the anchor hanging from this capstan does
     * ({@link #of(AnchorState.Phase, boolean)}), whether a player's use, a crew order or the automatic raise moves it.
     *
     * @param anchorPhase the phase of the ship's anchor when it hangs from this capstan, else null
     * @param hanging     the dropping anchor hangs at the end of its chain (the chain no longer runs out)
     */
    public static CapstanTurning of(@Nullable Object workingOrder, AnchorState.@Nullable Phase anchorPhase, boolean hanging) {
        CapstanTurning crew = of(workingOrder);
        return crew != NONE ? crew : of(anchorPhase, hanging);
    }

    /**
     * CAP3: the turn that follows the anchor's motion: {@link #RAISING} while the chain comes in, {@link #DROPPING} while
     * it runs out (the anchor falling, not {@code hanging} at the chain's end), {@link #NONE} while it holds, hangs or is
     * stowed (null: no anchor on this capstan).
     */
    public static CapstanTurning of(AnchorState.@Nullable Phase anchorPhase, boolean hanging) {
        if (anchorPhase == AnchorState.Phase.RAISING) return RAISING;
        if (anchorPhase == AnchorState.Phase.DROPPING && !hanging) return DROPPING;
        return NONE;
    }

    /** The turning with this {@link #ordinal()}, {@link #NONE} for anything unknown (a synced byte). */
    public static CapstanTurning byId(int id) {
        CapstanTurning[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}

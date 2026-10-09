package com.richardsenger.piratesnships.station.capstan;

import org.jetbrains.annotations.Nullable;

/**
 * Which way the capstan's bars turn (CAP2, docs/design.md §6 "Capstan in motion"): synced from the server's
 * {@link CapstanBlockEntity} to clients, which draw the bars turning ({@code client/CapstanBarsRenderer}); the crew
 * member working the capstan walks round the drum the same way ({@link CapstanWalk}). Pure.
 */
public enum CapstanTurning {
    /** Nobody works the capstan (or {@code crew_stations.capstan.animation} is off). */
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

    /** The turning with this {@link #ordinal()}, {@link #NONE} for anything unknown (a synced byte). */
    public static CapstanTurning byId(int id) {
        CapstanTurning[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}

package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;

/**
 * What a flagpole (or a whole ship) shows (docs/design.md §4.7). The three statuses are distinct on purpose: a pole
 * without a flag is not the same as a ship that has struck its colors.
 *
 * @param kind   the flag on the pole, also while it is struck ({@link FlagKind#NONE} only for {@link Status#NO_FLAG})
 * @param status whether a flag is there and whether it is up
 */
public record FlagReading(FlagKind kind, Status status) {

    public static final FlagReading NO_FLAG = new FlagReading(FlagKind.NONE, Status.NO_FLAG);

    public enum Status {
        /** No flag on the pole. */
        NO_FLAG,
        /** The flag is up. */
        FLYING,
        /** The pole keeps the flag but it is lowered: the ship has surrendered (§4.7 "striking colors"). */
        STRUCK
    }

    public FlagReading {
        if (status == Status.NO_FLAG) kind = FlagKind.NONE;
        if (kind == FlagKind.NONE) status = Status.NO_FLAG;
    }

    public static FlagReading flying(FlagKind kind) {
        return new FlagReading(kind, Status.FLYING);
    }

    public static FlagReading struck(FlagKind kind) {
        return new FlagReading(kind, Status.STRUCK);
    }

    /**
     * The flag an observer sees, as input for {@code FlagLaw.react}: the flag kind while it flies, otherwise
     * {@link FlagKind#NONE}.
     */
    public FlagKind shown() {
        return status == Status.FLYING ? kind : FlagKind.NONE;
    }

    public boolean isStruck() {
        return status == Status.STRUCK;
    }

    public boolean hasFlag() {
        return status != Status.NO_FLAG;
    }
}

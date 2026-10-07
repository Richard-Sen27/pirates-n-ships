package com.richardsenger.piratesnships.ship.decor.flag;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.law.flag.FlagKind;

import java.util.Locale;

/**
 * What a flagpole (or a whole ship) shows (docs/design.md §4.7). The three statuses are distinct on purpose: a pole
 * without a flag is not the same as a ship that has struck its colors.
 *
 * @param kind   the flag on the pole, also while it is struck ({@link FlagKind#NONE} only for {@link Status#NO_FLAG})
 * @param status whether a flag is there and whether it is up
 */
public record FlagReading(FlagKind kind, Status status) {

    public static final FlagReading NO_FLAG = new FlagReading(FlagKind.NONE, Status.NO_FLAG);

    private static final String STRUCK_PREFIX = "struck:";

    /**
     * A reading as one string, for {@code ShipData.flag} (FL2): {@code ""} for no flag, the kind id while it flies
     * ({@code "jolly_roger"}), {@code "struck:<kind>"} while it is struck. Strings it does not know read as
     * {@link #NO_FLAG}, so the placeholder strings saved before FL2 load as "no flag".
     */
    public static final Codec<FlagReading> STRING_CODEC = Codec.STRING.xmap(FlagReading::fromId, FlagReading::id);

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

    /** See {@link #STRING_CODEC}. */
    public String id() {
        return switch (status) {
            case NO_FLAG -> "";
            case FLYING -> kind.getSerializedName();
            case STRUCK -> STRUCK_PREFIX + kind.getSerializedName();
        };
    }

    /** See {@link #STRING_CODEC}; unknown strings give {@link #NO_FLAG}. */
    public static FlagReading fromId(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        boolean struck = s.startsWith(STRUCK_PREFIX);
        String kindId = struck ? s.substring(STRUCK_PREFIX.length()) : s;
        for (FlagKind k : FlagKind.values()) {
            if (k != FlagKind.NONE && k.getSerializedName().equals(kindId)) return struck ? struck(k) : flying(k);
        }
        return NO_FLAG;
    }
}

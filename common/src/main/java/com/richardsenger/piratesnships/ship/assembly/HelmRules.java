package com.richardsenger.piratesnships.ship.assembly;

import org.jetbrains.annotations.Nullable;

/**
 * Which helm of a ship steers it (HL1, docs/design.md §4.1). Pure rules, no world access.
 *
 * <p>A ship remembers one <b>steering helm</b> (its plot position, in the ship pointer of the sub-level's user data).
 * Breaking it leaves the ship <b>helmless</b>: the record, name, crew and stations stay, nothing steers and nothing
 * disassembles it. The first helm that is placed on (or used on) a helmless ship becomes its steering helm. While the
 * steering helm stands, any other helm on the ship is a <b>second</b> helm: it neither steers nor disassembles.
 */
public final class HelmRules {

    /** What a helm at some position is to the ship it stands on. */
    public enum Role {
        /** The ship's steering helm. */
        STEERING,
        /** The ship was helmless: this helm becomes its steering helm now. */
        ATTACHES,
        /** The ship has another steering helm; this one does nothing. */
        SECOND,
        /** The body is not one of our ships (no ship pointer). */
        NOT_A_SHIP
    }

    private HelmRules() {
    }

    /**
     * The role of the helm at {@code candidate}.
     *
     * @param ourShip       whether the body carries our ship pointer
     * @param recorded      the remembered steering helm (any position type with {@code equals}), null if none
     * @param recordedValid whether a helm block still stands at {@code recorded}
     * @param candidate     the helm being placed or used
     */
    public static <P> Role role(boolean ourShip, @Nullable P recorded, boolean recordedValid, P candidate) {
        if (!ourShip) {
            return Role.NOT_A_SHIP;
        }
        if (recorded == null || !recordedValid) {
            return Role.ATTACHES;
        }
        return recorded.equals(candidate) ? Role.STEERING : Role.SECOND;
    }

    /** Whether a helm with this role may steer and disassemble (after attaching, if it attaches). */
    public static boolean steers(Role role) {
        return role == Role.STEERING || role == Role.ATTACHES;
    }
}

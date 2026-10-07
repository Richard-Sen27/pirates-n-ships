package com.richardsenger.piratesnships.ship.hull.pump;

/**
 * Where a hull patch may go (docs/design.md §4.5), pure: only into a breach, the position of a destroyed hull block
 * that the ship's hull runtime tracks (and that is still open: air or another replaceable block).
 */
public final class PatchTarget {

    public enum Result {
        /** The position is an open breach: the patch goes in. */
        OK,
        /** Patching is switched off by config. */
        DISABLED,
        /** The position is not on an assembled ship with a hull runtime. */
        NOT_ON_SHIP,
        /** The position is on a ship, but not a breach. */
        NOT_A_BREACH,
        /** A breach, but a block that cannot be replaced is in it. */
        BLOCKED
    }

    private PatchTarget() {
    }

    public static Result check(boolean enabled, boolean onShip, boolean breach, boolean replaceable) {
        if (!enabled) {
            return Result.DISABLED;
        }
        if (!onShip) {
            return Result.NOT_ON_SHIP;
        }
        if (!breach) {
            return Result.NOT_A_BREACH;
        }
        return replaceable ? Result.OK : Result.BLOCKED;
    }
}

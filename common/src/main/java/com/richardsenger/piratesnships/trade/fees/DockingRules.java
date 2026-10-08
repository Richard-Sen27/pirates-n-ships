package com.richardsenger.piratesnships.trade.fees;

/**
 * The pure rules of harbor dues (PRT1b, design.md §10.3 "Port fees"): when a ship counts as docked, when a docked ship
 * is due again, and who pays. No world access; {@link DockingFees} feeds them.
 */
public final class DockingRules {

    /**
     * @param checkIntervalTicks ticks between two looks at the ships in ports
     * @param berthRadius        horizontal distance (blocks) from a berth to the hull's bounds within which a ship lies
     *                           at that berth
     * @param berthMaxSpeed      speed (m/s) below which a ship at a berth counts as tied up
     * @param periodDays         days between two charges of the same ship at the same port
     * @param chargeShipChest    the ship's own coins pay when the owner's wallet cannot
     * @param refuseDeskWhenOwed the port's harbor desk refuses a captain who owes dues there
     */
    public record Params(int checkIntervalTicks, double berthRadius, double berthMaxSpeed, int periodDays,
                         boolean chargeShipChest, boolean refuseDeskWhenOwed) {
        public static final Params DEFAULTS = new Params(100, 6.0, 0.1, 1, true, true);
    }

    /** Who pays a charge. */
    public enum Payer {
        /** The fee is 0 for this captain (rank or reputation waiver). */
        WAIVED,
        /** The owner's wallet. */
        WALLET,
        /** The coins aboard the ship. */
        SHIP,
        /** Nobody could: the fee is owed at the port. */
        OWED
    }

    private DockingRules() {
    }

    /**
     * Whether a ship in a port's box is docked: anchored, or lying within {@code berth_radius} of a berth slower than
     * {@code berth_max_speed}. A ship sailing or drifting through the harbor is not.
     *
     * @param anchored      the ship's anchor holds and the ship is at rest ({@code SailingRuntime.isAnchored})
     * @param berthDistance horizontal distance from the nearest berth to the hull's bounds, infinite without berths
     * @param speed         the ship's speed in m/s
     */
    public static boolean docked(boolean anchored, double berthDistance, double speed, Params p) {
        if (anchored) return true;
        return berthDistance <= p.berthRadius() && speed < p.berthMaxSpeed();
    }

    /**
     * Whether a docked ship is charged now: never charged at this port, or the last charge is at least
     * {@code fee_period_days} ago (a day count that went backwards, as after {@code /time set}, charges again too).
     *
     * @param lastDay the day of the last charge at this port, or {@code null} for none
     */
    public static boolean due(Long lastDay, long day, Params p) {
        if (lastDay == null) return true;
        long since = day - lastDay;
        return since < 0 || since >= Math.max(1, p.periodDays());
    }

    /**
     * Who pays {@code fee}: nobody when it is 0, the wallet when it holds the fee, else the ship's coins when that is
     * allowed and enough, else it is owed. Wallet and ship never split one fee.
     */
    public static Payer payer(int fee, long wallet, long shipCoins, Params p) {
        if (fee <= 0) return Payer.WAIVED;
        if (wallet >= fee) return Payer.WALLET;
        if (p.chargeShipChest() && shipCoins >= fee) return Payer.SHIP;
        return Payer.OWED;
    }

    /**
     * Horizontal distance from the point ({@code x}, {@code z}) to the box [{@code minX}, {@code maxX}] ×
     * [{@code minZ}, {@code maxZ}]; 0 inside it.
     */
    public static double horizontalDistanceToBox(double x, double z, double minX, double minZ, double maxX, double maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }
}

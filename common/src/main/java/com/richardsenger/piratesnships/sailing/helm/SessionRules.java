package com.richardsenger.piratesnships.sailing.helm;

/**
 * Pure rules for when a steering session at the helm ends (HELM1). A session lasts while the helmsman holds use; the
 * server also ends it when the player is gone or dead, walks more than {@code reach} blocks away from the helm block,
 * the helm is no longer on an assembled ship, or wheel steering is switched off.
 */
public final class SessionRules {

    /** Why a session ended, or {@link #NONE} while it goes on. */
    public enum End { NONE, RELEASED, GONE, TOO_FAR, NOT_ON_SHIP, DISABLED }

    private SessionRules() {
    }

    /**
     * Distance from a point to the unit block at {@code (bx, by, bz)} (its nearest face, edge or corner; 0 inside),
     * all in the same frame (on a ship: the plot).
     */
    public static double distanceToBlock(double px, double py, double pz, int bx, int by, int bz) {
        double dx = Math.max(Math.max(bx - px, 0.0), px - (bx + 1.0));
        double dy = Math.max(Math.max(by - py, 0.0), py - (by + 1.0));
        double dz = Math.max(Math.max(bz - pz, 0.0), pz - (bz + 1.0));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The first reason that ends the session this tick, in order of precedence; {@link End#NONE} keeps it going. */
    public static End check(boolean enabled, boolean playerPresent, boolean onShip, double distance, double reach) {
        if (!enabled) {
            return End.DISABLED;
        }
        if (!playerPresent) {
            return End.GONE;
        }
        if (!onShip) {
            return End.NOT_ON_SHIP;
        }
        if (!(distance <= reach)) {
            return End.TOO_FAR;
        }
        return End.NONE;
    }
}

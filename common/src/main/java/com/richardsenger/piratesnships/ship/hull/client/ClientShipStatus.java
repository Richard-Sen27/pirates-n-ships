package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Client store of the last {@link ShipStatusPayload} (HUD1). No client classes, so the common payload registration can
 * name its handler and JUnit can test the rules. Counted in client ticks: a status is fresh for {@link #FRESH_TICKS}
 * after it arrived; the player counts as aboard for {@link #ABOARD_GRACE_TICKS} after Sable last reported them on a ship
 * (Sable clears its tracking while the player jumps). The heading shown eases toward the received one, so the needle
 * turns smoothly between the once-a-second updates.
 */
public final class ClientShipStatus {

    /** A status older than this (3 s) is not shown. */
    public static final int FRESH_TICKS = 60;
    public static final int ABOARD_GRACE_TICKS = 10;
    /** Share of the remaining heading difference the needle turns per tick. */
    static final double EASE = 0.25;

    private static @Nullable ShipStatusPayload status;
    private static long ticks;
    private static long receivedAt = Long.MIN_VALUE / 2;
    private static long aboardAt = Long.MIN_VALUE / 2;
    private static double heading, previousHeading;

    private ClientShipStatus() {
    }

    /** Payload handler (client main thread). */
    public static void accept(ShipStatusPayload payload) {
        boolean jump = status == null || !status.ship().equals(payload.ship()) || ticks - receivedAt > FRESH_TICKS;
        status = payload;
        receivedAt = ticks;
        if (jump) {
            heading = previousHeading = payload.heading();
        }
    }

    /** Client tick end: advances time, notes whether the player is on a ship and eases the needle. */
    public static void tick(boolean onShip) {
        ticks++;
        if (onShip) {
            aboardAt = ticks;
        }
        previousHeading = heading;
        if (status != null) {
            heading = ease(heading, status.heading(), EASE);
        }
    }

    /** The status to draw, or null when there is none, it is stale or the player is not aboard. */
    public static @Nullable ShipStatusPayload shown() {
        if (status == null || ticks - receivedAt >= FRESH_TICKS || ticks - aboardAt > ABOARD_GRACE_TICKS) {
            return null;
        }
        return status;
    }

    /** The needle's heading for this frame [degrees, 0..360). */
    public static double heading(float partialTick) {
        return normalize(previousHeading + shortest(previousHeading, heading) * partialTick);
    }

    public static void reset() {
        status = null;
        ticks = 0;
        receivedAt = aboardAt = Long.MIN_VALUE / 2;
        heading = previousHeading = 0;
    }

    /** Turns {@code from} toward {@code to} by {@code f} of the shortest way round, in [0, 360). */
    static double ease(double from, double to, double f) {
        return normalize(from + shortest(from, to) * f);
    }

    /** The signed shortest turn from {@code a} to {@code b}, in (−180, 180]. */
    static double shortest(double a, double b) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return d == -180 ? 180 : d;
    }

    static double normalize(double d) {
        double r = d % 360;
        return r < 0 ? r + 360 : r;
    }
}

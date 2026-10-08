package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Client store of the last {@link ShipStatusPayload} (HUD1, HUD2). No client classes, so the common payload
 * registration can name its handler and JUnit can test the rules. Counted in client ticks.
 *
 * <ul>
 *   <li><b>Fresh:</b> a status is shown for {@link ShipStatusPayload#freshTicks()} after it arrived (the server picks
 *   it from its sync interval, three keepalive periods and a second, 140 ticks at the defaults; never under
 *   {@link ShipStatusPayload#MIN_FRESH_TICKS}).</li>
 *   <li><b>Aboard:</b> the player counts as aboard while Sable reports them on a ship (standing on it or riding a seat
 *   on it), and for {@link #ABOARD_GRACE_TICKS} after. On the client Sable drops that tracking on every tick the
 *   player does not touch the ship from above: mid-jump, on a ladder, falling. So while the player is
 *   <i>holding</i> (climbing, or in the air and not in water) the aboard time is carried on, up to
 *   {@link #MAX_HOLD_TICKS}; in water or on other ground the grace runs out and the HUD hides.</li>
 * </ul>
 * The heading shown eases toward the received one, so the needle turns smoothly between the updates.
 */
public final class ClientShipStatus {

    /** After Sable last saw the player on a ship (1.5 s): the HUD stays this long, then hides. */
    public static final int ABOARD_GRACE_TICKS = 30;
    /** Longest climb or flight (20 s) that carries the aboard time on without touching the ship. */
    public static final int MAX_HOLD_TICKS = 400;
    /** Share of the remaining heading difference the needle turns per tick. */
    static final double EASE = 0.25;

    private static @Nullable ShipStatusPayload status;
    private static long ticks;
    private static long receivedAt = Long.MIN_VALUE / 2;
    private static long aboardAt = Long.MIN_VALUE / 2;
    private static long touchedAt = Long.MIN_VALUE / 2;
    private static double heading, previousHeading;

    private ClientShipStatus() {
    }

    /** Payload handler (client main thread). */
    public static void accept(ShipStatusPayload payload) {
        boolean jump = status == null || !status.ship().equals(payload.ship()) || !fresh();
        status = payload;
        receivedAt = ticks;
        if (jump) {
            heading = previousHeading = payload.heading();
        }
    }

    /** Client tick end without a hold (tests, and a player with no level). */
    public static void tick(boolean onShip) {
        tick(onShip, false);
    }

    /**
     * Client tick end: advances time, notes whether the player is aboard and eases the needle.
     *
     * @param onShip  Sable reports the player standing on or riding in a ship this tick
     * @param holding the player is climbing, or in the air and not in water (Sable cannot see them on the ship then)
     */
    public static void tick(boolean onShip, boolean holding) {
        ticks++;
        if (onShip) {
            aboardAt = touchedAt = ticks;
        } else if (holding && ticks - aboardAt <= ABOARD_GRACE_TICKS && ticks - touchedAt <= MAX_HOLD_TICKS) {
            aboardAt = ticks;
        }
        previousHeading = heading;
        if (status != null) {
            heading = ease(heading, status.heading(), EASE);
        }
    }

    /** The status to draw, or null when there is none, it is stale or the player is not aboard. */
    public static @Nullable ShipStatusPayload shown() {
        if (status == null || !fresh() || ticks - aboardAt > ABOARD_GRACE_TICKS) {
            return null;
        }
        return status;
    }

    private static boolean fresh() {
        return status != null && ticks - receivedAt < Math.max(ShipStatusPayload.MIN_FRESH_TICKS, status.freshTicks());
    }

    /** The needle's heading for this frame [degrees, 0..360). */
    public static double heading(float partialTick) {
        return normalize(previousHeading + shortest(previousHeading, heading) * partialTick);
    }

    public static void reset() {
        status = null;
        ticks = 0;
        receivedAt = aboardAt = touchedAt = Long.MIN_VALUE / 2;
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

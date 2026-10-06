package com.richardsenger.piratesnships.sailing.wind;

/**
 * Client-side holder of the last synced wind, read by the HUD wind indicator and flag/sail rendering. Contains no
 * client-only classes, so it is safe to load on a dedicated server. Updated on the client main thread by the
 * {@link WindSyncPayload} handler.
 */
public final class ClientWind {

    private static volatile WindBlend blend = WindBlend.CALM;
    private static volatile boolean received;

    private ClientWind() {
    }

    /** The wind to display at client game time {@code time} (e.g. {@code level.getGameTime() + partialTick}). */
    public static WindSample sample(double time) {
        return blend.at(time);
    }

    /** Whether any wind sync has arrived since the last {@link #reset()}. */
    public static boolean hasData() {
        return received;
    }

    /** Called by the payload handler. The first sample after a reset is shown immediately. */
    public static void accept(WindSyncPayload payload, double clientTime) {
        WindSample s = payload.toSample();
        blend = received ? blend.next(s, clientTime, payload.intervalTicks()) : new WindBlend(s, s, clientTime, 1.0);
        received = true;
    }

    /** Forget the wind (e.g. on disconnect). */
    public static void reset() {
        blend = WindBlend.CALM;
        received = false;
    }
}

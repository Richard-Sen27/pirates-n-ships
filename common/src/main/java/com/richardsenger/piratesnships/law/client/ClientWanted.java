package com.richardsenger.piratesnships.law.client;

import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.sync.WantedSyncPayload;

/**
 * Client-side copy of the local player's wanted level and score, for a later HUD. Filled by
 * {@link WantedSyncPayload}, emptied on disconnect. Plain Java without client classes, so the payload handler that
 * writes it can be registered on both sides; only the client ever receives the payload.
 */
public final class ClientWanted {

    private static volatile WantedSyncPayload current = new WantedSyncPayload(WantedLevel.CLEAN, 0);
    private static volatile boolean known;

    private ClientWanted() {
    }

    public static WantedLevel level() {
        return current.level();
    }

    public static int score() {
        return current.score();
    }

    /** Whether the server has sent a value since joining. */
    public static boolean known() {
        return known;
    }

    public static void accept(WantedSyncPayload payload) {
        current = payload;
        known = true;
    }

    public static void reset() {
        current = new WantedSyncPayload(WantedLevel.CLEAN, 0);
        known = false;
    }
}

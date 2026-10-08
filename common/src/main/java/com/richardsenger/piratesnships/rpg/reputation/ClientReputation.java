package com.richardsenger.piratesnships.rpg.reputation;

/**
 * Client-side copy of the local player's shown reputation, for a later HUD or career screen. Filled by
 * {@link ReputationSyncPayload}. Plain Java without client classes (the {@code law.client.ClientWanted} pattern), so
 * the payload handler that writes it can be registered on both sides; only the client ever receives the payload.
 */
public final class ClientReputation {

    private static volatile ReputationSyncPayload current = ReputationSyncPayload.NEUTRAL;
    private static volatile boolean known;

    private ClientReputation() {
    }

    public static int get(Faction faction) {
        return current.get(faction);
    }

    /** Whether the server has sent a value since joining. */
    public static boolean known() {
        return known;
    }

    public static void accept(ReputationSyncPayload payload) {
        current = payload;
        known = true;
    }

    public static void reset() {
        current = ReputationSyncPayload.NEUTRAL;
        known = false;
    }
}

package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.AlmanacPayloads;
import com.richardsenger.piratesnships.platform.Services;

/**
 * Client side of reading the Captain's Almanac (ALM1, ALM3, docs/design.md §3.4). Uses no client-only classes itself,
 * so the payload handler may reference it on both sides; the screen is reached only when {@link #open} runs.
 *
 * <p>The server sends {@link AlmanacPayloads.Open} when the player reads the placed tome ({@code tome} present) or the
 * book in hand ({@code tome} empty); {@link #open} shows {@link AlmanacScreen} at the page the player last read. The
 * screen calls {@link #closed} with the same payload from its {@code removed()}, so a placed tome closes again.
 */
public final class AlmanacClient {

    private AlmanacClient() {
    }

    /** The server opened the almanac for this player: show the book screen. */
    public static void open(AlmanacPayloads.Open open) {
        AlmanacScreen.show(open);
    }

    /** The reader is done with the almanac opened by {@code open}: tell the server, so a placed tome closes. */
    public static void closed(AlmanacPayloads.Open open) {
        Services.NETWORK.sendToServer(new AlmanacPayloads.Close(open.tome()));
    }
}

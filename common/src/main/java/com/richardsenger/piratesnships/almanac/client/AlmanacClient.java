package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.AlmanacPayloads;
import com.richardsenger.piratesnships.platform.Services;

/**
 * Client side of reading the Captain's Almanac (ALM1, docs/design.md §3.4). Uses no client-only classes, so the
 * payload handler may reference it on both sides.
 *
 * <p>{@link #open} is the single hook the almanac screen (ALM3) replaces: the server sends
 * {@link AlmanacPayloads.Open} when the player reads the placed tome ({@code tome} present) or the book in hand
 * ({@code tome} empty). Whatever opens must call {@link #closed} with the same payload once the reader is done (the
 * screen's {@code removed()}), so a placed tome closes again. Until ALM3 there is no screen: it logs and counts as
 * closed at once, and the tome lies open for {@code almanac.open_ticks}.
 */
public final class AlmanacClient {

    private AlmanacClient() {
    }

    /** The server opened the almanac for this player. ALM3: open the screen here. */
    public static void open(AlmanacPayloads.Open open) {
        Constants.LOG.debug("Captain's Almanac opened ({}); no screen until ALM3", open.tome().map(Object::toString).orElse("in hand"));
        closed(open);
    }

    /** The reader is done with the almanac opened by {@code open}: tell the server, so a placed tome closes. */
    public static void closed(AlmanacPayloads.Open open) {
        Services.NETWORK.sendToServer(new AlmanacPayloads.Close(open.tome()));
    }
}

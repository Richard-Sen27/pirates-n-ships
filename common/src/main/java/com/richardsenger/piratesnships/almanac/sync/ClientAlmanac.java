package com.richardsenger.piratesnships.almanac.sync;

import com.richardsenger.piratesnships.almanac.data.AlmanacBook;
import com.richardsenger.piratesnships.almanac.data.AlmanacProgress;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's copy of the Almanac (ALM2): the last synced book and the page the player last read. A client that got no
 * sync (or left the world) has {@link AlmanacBook#EMPTY} and {@link AlmanacProgress#NONE}. Read by the screen (ALM3)
 * on the client main thread. Uses no client-only classes, so it loads on a dedicated server too.
 */
public final class ClientAlmanac {

    private static volatile AlmanacBook book = AlmanacBook.EMPTY;
    private static volatile AlmanacProgress progress = AlmanacProgress.NONE;
    /** Whether the book has been opened since joining (ALM8: the first opening shows the index spread). */
    private static volatile boolean opened;

    private ClientAlmanac() {
    }

    /** The last synced book. */
    public static AlmanacBook book() {
        return book;
    }

    /** The page the Almanac should open at. */
    public static AlmanacProgress progress() {
        return progress;
    }

    /**
     * The screen calls this when the player turns to a page: remembers it locally and reports it to the server (which
     * stores it when {@code almanac.remember_page} is on). Does nothing when the page did not change.
     */
    public static void reportPage(ResourceLocation chapter, int page) {
        AlmanacProgress now = AlmanacProgress.at(chapter, page);
        if (now.equals(progress)) return;
        progress = now;
        Services.NETWORK.sendToServer(new AlmanacPagePayload.Report(now));
    }

    /** True on the first call since joining (the book then opens on its index spread), false after. */
    public static boolean takeFirstOpening() {
        boolean first = !opened;
        opened = true;
        return first;
    }

    static void onSync(AlmanacSyncPayload payload) {
        book = payload.book();
    }

    static void onRestore(AlmanacPagePayload.Restore payload) {
        progress = payload.progress();
    }

    /** {@code ClientEvents.CLIENT_DISCONNECT}: forget the server's book and page. */
    public static void reset() {
        book = AlmanacBook.EMPTY;
        progress = AlmanacProgress.NONE;
        opened = false;
    }
}

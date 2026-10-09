package com.richardsenger.piratesnships.almanac.sync;

import com.richardsenger.piratesnships.almanac.data.AlmanacAttachments;
import com.richardsenger.piratesnships.almanac.data.AlmanacBook;
import com.richardsenger.piratesnships.almanac.data.AlmanacDataConfig;
import com.richardsenger.piratesnships.almanac.data.AlmanacProgress;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the Almanac's sync (docs/design.md §3.4, ALM2): the book to a player on login and to everyone after a
 * reload, the stored page on login, and the pages players report. The payload lists are built by pure-ish methods
 * ({@link #payloads}, {@link #store}) so GameTests capture them without a network.
 */
public final class AlmanacSync {

    private AlmanacSync() {
    }

    /** {@code CommonEvents.DATAPACK_SYNC}: once per player on login ({@code joined}) and for every player after a reload. */
    public static void onDatapackSync(ServerPlayer player, boolean joined) {
        for (CustomPacketPayload payload : payloads(player, joined, AlmanacBook.server())) {
            Services.NETWORK.sendToPlayer(player, payload);
        }
    }

    /**
     * What a player gets at a datapack sync: always the whole {@code book}; on login also the stored page
     * ({@link AlmanacPagePayload.Restore}), unless {@code almanac.remember_page} is off.
     */
    public static List<CustomPacketPayload> payloads(Player player, boolean joined, AlmanacBook book) {
        List<CustomPacketPayload> out = new ArrayList<>(2);
        out.add(new AlmanacSyncPayload(book));
        if (joined && AlmanacDataConfig.REMEMBER_PAGE.get()) {
            out.add(new AlmanacPagePayload.Restore(progress(player)));
        }
        return out;
    }

    /** Server handler of {@link AlmanacPagePayload.Report}. */
    public static void onReport(AlmanacPagePayload.Report payload, Player player) {
        store(player, payload.progress(), AlmanacBook.server());
    }

    /**
     * Stores {@code progress} as the player's last page. Nothing is stored while {@code almanac.remember_page} is off,
     * nor for a chapter that {@code book} does not hold (a stale client after a reload). {@link AlmanacProgress#NONE}
     * clears it.
     *
     * @return whether it was stored
     */
    public static boolean store(Player player, AlmanacProgress progress, AlmanacBook book) {
        if (!AlmanacDataConfig.REMEMBER_PAGE.get()) return false;
        if (progress.chapter().isPresent() && book.chapter(progress.chapter().get()).isEmpty()) return false;
        Services.ATTACHMENTS.set(player, AlmanacAttachments.PROGRESS, progress);
        return true;
    }

    /** The player's stored last page, {@link AlmanacProgress#NONE} if none. */
    public static AlmanacProgress progress(Player player) {
        return Services.ATTACHMENTS.get(player, AlmanacAttachments.PROGRESS);
    }
}

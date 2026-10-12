package com.richardsenger.piratesnships.chart.held;

import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chart held open (CHART3): while a player has their chart screen open the server sets {@link #READING} on them,
 * a synced, unsaved player attachment, so every client that sees the player unrolls the chart in their hands and plays
 * the reading pose ({@code chart/held/client}). {@code ChartBackend} sets it when the chart opens and clears it when
 * the screen closes or the session ends. With {@code chart.held_open} off it is never set.
 */
public final class ChartHeld {

    /** Whether the player reads their chart right now (synced to everyone who sees them, never saved). */
    public static final AttachmentKey<Boolean> READING = AttachmentKey.builder("chart_reading", () -> Boolean.FALSE)
            .synced(ByteBufCodecs.BOOL).build();

    private ChartHeld() {
    }

    /**
     * Players outside the server's player list (GameTest players, whose mock connection has no negotiated channels and
     * rejects the loaders' attachment sync payload): their flag is kept here instead of in the synced attachment, as
     * {@code CareerSync} syncs only online players.
     */
    private static final Set<UUID> OFFLINE_READING = ConcurrentHashMap.newKeySet();

    /** Sets or clears the flag; a change only (no sync for an unchanged value), and never set while the option is off. */
    public static void setReading(ServerPlayer player, boolean reading) {
        boolean value = reading && ChartConfig.HELD_OPEN.get();
        if (isReading(player) == value) return;
        if (!online(player)) {
            if (value) OFFLINE_READING.add(player.getUUID());
            else OFFLINE_READING.remove(player.getUUID());
            return;
        }
        Services.ATTACHMENTS.set(player, READING, value);
    }

    /** The flag on either side; on the client only what the server synced (nothing is created there). */
    public static boolean isReading(Player player) {
        if (player instanceof ServerPlayer sp && !online(sp)) return OFFLINE_READING.contains(sp.getUUID());
        return Services.ATTACHMENTS.has(player, READING) && Services.ATTACHMENTS.get(player, READING);
    }

    private static boolean online(ServerPlayer player) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /** The hand holding a chart (the main hand first), or null: a chart opened with the key and empty hands shows no pose. */
    public static @Nullable InteractionHand chartHand(Player player) {
        if (player.getMainHandItem().is(ChartContent.CHART.get())) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(ChartContent.CHART.get())) return InteractionHand.OFF_HAND;
        return null;
    }
}

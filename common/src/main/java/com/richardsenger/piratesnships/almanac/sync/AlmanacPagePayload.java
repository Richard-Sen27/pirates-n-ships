package com.richardsenger.piratesnships.almanac.sync;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.data.AlmanacProgress;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The page a player reads, both ways (ALM2). One payload type per direction, because a payload id can be registered
 * for one direction only on NeoForge: {@link Report} (client → server, the screen reports the page it shows) and
 * {@link Restore} (server → client, the stored page, sent on login with the book).
 */
public sealed interface AlmanacPagePayload extends CustomPacketPayload {

    AlmanacProgress progress();

    /** Client → server: the player now reads this page. Stored when {@code almanac.remember_page} is on. */
    record Report(AlmanacProgress progress) implements AlmanacPagePayload {

        public static final Type<Report> TYPE = new Type<>(Constants.id("almanac_page_report"));

        public static final StreamCodec<ByteBuf, Report> CODEC = AlmanacProgress.STREAM_CODEC.map(Report::new, Report::progress);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the player's stored page, so the Almanac opens there. */
    record Restore(AlmanacProgress progress) implements AlmanacPagePayload {

        public static final Type<Restore> TYPE = new Type<>(Constants.id("almanac_page_restore"));

        public static final StreamCodec<ByteBuf, Restore> CODEC = AlmanacProgress.STREAM_CODEC.map(Restore::new, Restore::progress);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

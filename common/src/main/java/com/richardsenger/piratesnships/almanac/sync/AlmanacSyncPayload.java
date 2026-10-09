package com.richardsenger.piratesnships.almanac.sync;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.data.AlmanacBook;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the whole Almanac (every chapter), sent to a player on login and to every player after a datapack
 * reload ({@code CommonEvents.DATAPACK_SYNC}). Chapters are small (lang keys, ids, numbers), so the book is one payload.
 */
public record AlmanacSyncPayload(AlmanacBook book) implements CustomPacketPayload {

    public static final Type<AlmanacSyncPayload> TYPE = new Type<>(Constants.id("almanac_sync"));

    public static final StreamCodec<ByteBuf, AlmanacSyncPayload> CODEC =
            AlmanacBook.STREAM_CODEC.map(AlmanacSyncPayload::new, AlmanacSyncPayload::book);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

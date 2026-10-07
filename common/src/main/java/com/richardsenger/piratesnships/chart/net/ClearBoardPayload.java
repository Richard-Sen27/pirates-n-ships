package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (work package MAP3): blank every tile of the map board the tile at {@code tilePos} belongs to.
 * Checked like a draw ({@link com.richardsenger.piratesnships.chart.tile.MapTileService#clear}); costs no ink.
 */
public record ClearBoardPayload(BlockPos tilePos) implements CustomPacketPayload {

    public static final Type<ClearBoardPayload> TYPE = new Type<>(Constants.id("chart_clear_board"));
    public static final StreamCodec<ByteBuf, ClearBoardPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ClearBoardPayload::tilePos,
            ClearBoardPayload::new);

    @Override
    public Type<ClearBoardPayload> type() {
        return TYPE;
    }
}

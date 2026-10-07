package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (work package MAP2): draw the player's own chart onto the map tile at {@code tilePos}, the area of
 * {@code chart.tiles.tile_cells} cells square whose top-left cell is {@code (minCx, minCz)}, with the player's markers
 * in it if {@code includeMarkers}. A drawn tile is replaced as a whole (a full redraw). The server checks everything
 * again ({@link com.richardsenger.piratesnships.chart.tile.MapTileService#draw}).
 */
public record DrawTilePayload(BlockPos tilePos, int minCx, int minCz, boolean includeMarkers) implements CustomPacketPayload {

    public static final Type<DrawTilePayload> TYPE = new Type<>(Constants.id("chart_draw_tile"));
    public static final StreamCodec<ByteBuf, DrawTilePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DrawTilePayload::tilePos,
            ByteBufCodecs.VAR_INT, DrawTilePayload::minCx,
            ByteBufCodecs.VAR_INT, DrawTilePayload::minCz,
            ByteBufCodecs.BOOL, DrawTilePayload::includeMarkers,
            DrawTilePayload::new);

    @Override
    public Type<DrawTilePayload> type() {
        return TYPE;
    }
}

package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (work packages MAP2, MAP3): draw the player's own chart onto the map board the tile at
 * {@code tilePos} belongs to. A draw covers the area whose top-left cell is {@code (minCx, minCz)} at zoom
 * {@code zoom} ({@code chart.tiles.tile_cells * zoom} cells per tile and side) and replaces whatever the board showed;
 * an {@code update} re-draws the board's own area and zoom (the area fields are ignored then) and merges the new
 * knowledge in. With {@code includeMarkers} the player's markers in the area are stamped. The server checks
 * everything again ({@link com.richardsenger.piratesnships.chart.tile.MapTileService#draw}).
 */
public record DrawTilePayload(BlockPos tilePos, int minCx, int minCz, int zoom, boolean includeMarkers, boolean update) implements CustomPacketPayload {

    public static final Type<DrawTilePayload> TYPE = new Type<>(Constants.id("chart_draw_tile"));
    public static final StreamCodec<ByteBuf, DrawTilePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DrawTilePayload::tilePos,
            ByteBufCodecs.VAR_INT, DrawTilePayload::minCx,
            ByteBufCodecs.VAR_INT, DrawTilePayload::minCz,
            ByteBufCodecs.VAR_INT, DrawTilePayload::zoom,
            ByteBufCodecs.BOOL, DrawTilePayload::includeMarkers,
            ByteBufCodecs.BOOL, DrawTilePayload::update,
            DrawTilePayload::new);

    /** A first draw (or redraw) at zoom 1 (what MAP2 sent). */
    public DrawTilePayload(BlockPos tilePos, int minCx, int minCz, boolean includeMarkers) {
        this(tilePos, minCx, minCz, 1, includeMarkers, false);
    }

    /** An update of the board's current drawing. */
    public static DrawTilePayload update(BlockPos tilePos, boolean includeMarkers) {
        return new DrawTilePayload(tilePos, 0, 0, 1, includeMarkers, true);
    }

    @Override
    public Type<DrawTilePayload> type() {
        return TYPE;
    }
}

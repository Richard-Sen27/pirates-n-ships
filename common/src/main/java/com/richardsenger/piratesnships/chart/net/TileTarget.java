package com.richardsenger.piratesnships.chart.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The map tile a chart screen draws onto (work package MAP2), sent with {@link ChartOpenPayload}: where it is, the
 * selection's size in cells ({@code chart.tiles.tile_cells}), whether it already holds a drawing (then the draw button
 * asks "Redraw?") and where the selection starts (the old drawing's area, or centred on the player).
 */
public record TileTarget(BlockPos pos, int tileCells, boolean drawn, int minCx, int minCz) {

    public static final StreamCodec<ByteBuf, TileTarget> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TileTarget::pos,
            ByteBufCodecs.VAR_INT, TileTarget::tileCells,
            ByteBufCodecs.BOOL, TileTarget::drawn,
            ByteBufCodecs.VAR_INT, TileTarget::minCx,
            ByteBufCodecs.VAR_INT, TileTarget::minCz,
            TileTarget::new);
}

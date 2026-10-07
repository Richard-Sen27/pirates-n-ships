package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.chart.tile.BoardRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The map board a chart screen draws onto (work packages MAP2, MAP3), sent with {@link ChartOpenPayload}: the used
 * tile, the pixels per tile and side ({@code tileCells}), what the board shows now ({@code state}: blank, an intact
 * drawing that can be updated, or leftovers that a draw replaces), where the frame starts (an intact board's area, or
 * centred on the player), the board's size in tiles, its zoom and the highest zoom allowed, whether a redraw or a clear
 * is allowed, and the ink: ink per tile ({@code 0} = free: the cost is off or the player is in creative mode) and what
 * one kraken ink is worth.
 */
public record TileTarget(BlockPos pos, int tileCells, BoardRules.State state, int minCx, int minCz, int columns, int rows,
                         int zoom, int maxZoom, boolean redrawAllowed, int inkPerTile, int krakenWorth) {

    /** Whether the board shows anything (a draw replaces it: the screen asks first). */
    public boolean drawn() {
        return state != BoardRules.State.BLANK;
    }

    /** Whether the board holds one intact drawing that can be updated. */
    public boolean updatable() {
        return state == BoardRules.State.INTACT;
    }

    public int tiles() {
        return columns * rows;
    }

    private static final BoardRules.State[] STATES = BoardRules.State.values();

    public static final StreamCodec<ByteBuf, TileTarget> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TileTarget decode(ByteBuf buf) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            int tileCells = ByteBufCodecs.VAR_INT.decode(buf);
            int state = ByteBufCodecs.VAR_INT.decode(buf);
            int minCx = ByteBufCodecs.VAR_INT.decode(buf);
            int minCz = ByteBufCodecs.VAR_INT.decode(buf);
            int columns = ByteBufCodecs.VAR_INT.decode(buf);
            int rows = ByteBufCodecs.VAR_INT.decode(buf);
            int zoom = ByteBufCodecs.VAR_INT.decode(buf);
            int maxZoom = ByteBufCodecs.VAR_INT.decode(buf);
            boolean redraw = ByteBufCodecs.BOOL.decode(buf);
            int inkPerTile = ByteBufCodecs.VAR_INT.decode(buf);
            int krakenWorth = ByteBufCodecs.VAR_INT.decode(buf);
            return new TileTarget(pos, tileCells, STATES[Math.floorMod(state, STATES.length)], minCx, minCz, columns, rows, zoom, maxZoom,
                    redraw, inkPerTile, krakenWorth);
        }

        @Override
        public void encode(ByteBuf buf, TileTarget t) {
            BlockPos.STREAM_CODEC.encode(buf, t.pos());
            ByteBufCodecs.VAR_INT.encode(buf, t.tileCells());
            ByteBufCodecs.VAR_INT.encode(buf, t.state().ordinal());
            ByteBufCodecs.VAR_INT.encode(buf, t.minCx());
            ByteBufCodecs.VAR_INT.encode(buf, t.minCz());
            ByteBufCodecs.VAR_INT.encode(buf, t.columns());
            ByteBufCodecs.VAR_INT.encode(buf, t.rows());
            ByteBufCodecs.VAR_INT.encode(buf, t.zoom());
            ByteBufCodecs.VAR_INT.encode(buf, t.maxZoom());
            ByteBufCodecs.BOOL.encode(buf, t.redrawAllowed());
            ByteBufCodecs.VAR_INT.encode(buf, t.inkPerTile());
            ByteBufCodecs.VAR_INT.encode(buf, t.krakenWorth());
        }
    };
}

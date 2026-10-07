package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Which slice of a map board a tile's drawing is (work package MAP3: tiles placed side by side form one board showing
 * one chart region): the board's id (new on every first draw or redraw, kept by updates), this tile's column (0 = the
 * drawing's west edge) and row (0 = its north edge), and the board's size in tiles.
 */
public record BoardSlice(UUID board, int column, int row, int columns, int rows) {

    public static final int MAX_TILES = 64;

    public BoardSlice {
        if (columns < 1 || rows < 1 || columns > MAX_TILES || rows > MAX_TILES) throw new IllegalArgumentException("board of " + columns + "x" + rows);
        if (column < 0 || row < 0 || column >= columns || row >= rows) throw new IllegalArgumentException("slice " + column + "," + row);
    }

    public static final Codec<BoardSlice> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("board").forGetter(BoardSlice::board),
            Codec.INT.fieldOf("column").forGetter(BoardSlice::column),
            Codec.INT.fieldOf("row").forGetter(BoardSlice::row),
            Codec.INT.fieldOf("columns").forGetter(BoardSlice::columns),
            Codec.INT.fieldOf("rows").forGetter(BoardSlice::rows)
    ).apply(i, BoardSlice::new));

    public static final StreamCodec<ByteBuf, BoardSlice> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BoardSlice::board,
            ByteBufCodecs.VAR_INT, BoardSlice::column,
            ByteBufCodecs.VAR_INT, BoardSlice::row,
            ByteBufCodecs.VAR_INT, BoardSlice::columns,
            ByteBufCodecs.VAR_INT, BoardSlice::rows,
            BoardSlice::new);
}

package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a map tile shows (work package MAP2): a square raster of {@code size x size} palette bytes (one pixel per chart
 * cell, row-major, index {@code y * size + x}, north up; the palette is
 * {@link com.richardsenger.piratesnships.chart.render.MapTileRaster}'s, 0 = blank parchment), the drawn area (cell
 * {@code (minCx, minCz)} is the top-left pixel, cells of {@code cellBlocks} blocks, {@code zoom} cells per pixel side),
 * who drew it on which game day, the stamped markers, and (reserved for MAP3 boards) which {@link BoardSlice} of a
 * board it is. Immutable: the constructor copies the pixel array and {@link #pixels()} hands out a copy.
 *
 * <p>Pixel index 0 is always "nothing charted here" ({@link #known}): a later update can re-raster the same area
 * ({@link #minCx}, {@link #minCz}, {@link #size}, {@link #zoom}, {@link #cellBlocks}) and keep old pixels where the new
 * chart knows nothing. MAP2 itself always draws at zoom 1 and never sets a board.
 *
 * <p>Stored (block entity NBT, the item's {@code pirates_n_ships:map_tile_drawing} component) and synced with the
 * pixels packed by {@link PixelPack} (deflate; the chart's hatching and dots defeat a run-length encoding), so a
 * typical tile is a kilobyte or two instead of {@code size^2} bytes.
 */
public final class MapTileDrawing {

    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 256;
    public static final int MAX_MARKERS = 512;
    public static final int MAX_NAME = 64;
    public static final int MAX_ZOOM = 16;

    private final int size;
    private final byte[] pixels;
    private final int minCx;
    private final int minCz;
    private final int cellBlocks;
    private final String drawer;
    private final long day;
    private final List<TileMarker> markers;
    private final int zoom;
    private final Optional<BoardSlice> board;
    private final int hash;

    /** A single tile at zoom 1 (what MAP2 draws). */
    public MapTileDrawing(int size, byte[] pixels, int minCx, int minCz, int cellBlocks, String drawer, long day, List<TileMarker> markers) {
        this(size, pixels, minCx, minCz, cellBlocks, drawer, day, markers, 1, Optional.empty());
    }

    public MapTileDrawing(int size, byte[] pixels, int minCx, int minCz, int cellBlocks, String drawer, long day, List<TileMarker> markers,
                          int zoom, Optional<BoardSlice> board) {
        if (size < MIN_SIZE || size > MAX_SIZE) throw new IllegalArgumentException("tile size " + size);
        if (zoom < 1 || zoom > MAX_ZOOM) throw new IllegalArgumentException("zoom " + zoom);
        if (pixels.length != size * size) throw new IllegalArgumentException(pixels.length + " pixels for a tile of " + size);
        if (markers.size() > MAX_MARKERS) throw new IllegalArgumentException(markers.size() + " markers");
        this.size = size;
        this.pixels = pixels.clone();
        this.minCx = minCx;
        this.minCz = minCz;
        this.cellBlocks = Math.max(1, cellBlocks);
        this.drawer = drawer == null ? "" : drawer;
        this.day = day;
        this.markers = List.copyOf(markers);
        this.zoom = zoom;
        this.board = Objects.requireNonNull(board);
        this.hash = Objects.hash(size, Arrays.hashCode(this.pixels), minCx, minCz, this.cellBlocks, this.drawer, day, this.markers, zoom, board);
    }

    public int size() {
        return size;
    }

    /** The palette index of pixel {@code (x, y)}. */
    public int pixel(int x, int y) {
        return pixels[y * size + x] & 0xFF;
    }

    /** Whether pixel {@code (x, y)} shows charted ground or sea (false: blank parchment, nothing was known there). */
    public boolean known(int x, int y) {
        return pixels[y * size + x] != 0;
    }

    /** A copy of the raster. */
    public byte[] pixels() {
        return pixels.clone();
    }

    public int minCx() {
        return minCx;
    }

    public int minCz() {
        return minCz;
    }

    public int cellBlocks() {
        return cellBlocks;
    }

    public String drawer() {
        return drawer;
    }

    public long day() {
        return day;
    }

    public List<TileMarker> markers() {
        return markers;
    }

    /** Chart cells per pixel side (1 in MAP2). */
    public int zoom() {
        return zoom;
    }

    /** The board this tile is a slice of (MAP3); empty for a single tile. */
    public Optional<BoardSlice> board() {
        return board;
    }

    /** Chart cells along each side of the drawn area. */
    public int cells() {
        return size * zoom;
    }

    /** West edge in blocks (inclusive). */
    public long minX() {
        return (long) minCx * cellBlocks;
    }

    /** North edge in blocks (inclusive). */
    public long minZ() {
        return (long) minCz * cellBlocks;
    }

    /** East edge in blocks (exclusive). */
    public long maxX() {
        return ((long) minCx + cells()) * cellBlocks;
    }

    /** South edge in blocks (exclusive). */
    public long maxZ() {
        return ((long) minCz + cells()) * cellBlocks;
    }

    /** The pixels packed for storage and the network ({@link PixelPack}). */
    public byte[] packed() {
        return PixelPack.pack(pixels);
    }

    // --- codecs --------------------------------------------------------------------------------------------------

    private record Stored(int size, byte[] packed, int minCx, int minCz, int cellBlocks, String drawer, long day, List<TileMarker> markers,
                          int zoom, Optional<BoardSlice> board) {
    }

    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.duplicate().get(a);
        return a;
    }, ByteBuffer::wrap);

    private static final Codec<Stored> STORED = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("size").forGetter(Stored::size),
            BYTES.fieldOf("pixels").forGetter(Stored::packed),
            Codec.INT.fieldOf("min_cx").forGetter(Stored::minCx),
            Codec.INT.fieldOf("min_cz").forGetter(Stored::minCz),
            Codec.INT.optionalFieldOf("cell_blocks", 4).forGetter(Stored::cellBlocks),
            Codec.STRING.optionalFieldOf("drawer", "").forGetter(Stored::drawer),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(Stored::day),
            TileMarker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(Stored::markers),
            Codec.INT.optionalFieldOf("zoom", 1).forGetter(Stored::zoom),
            BoardSlice.CODEC.optionalFieldOf("board").forGetter(Stored::board)
    ).apply(i, Stored::new));

    /** NBT / JSON form: pixels packed in a byte array. Bad data is a codec error, not an exception. */
    public static final Codec<MapTileDrawing> CODEC = STORED.comapFlatMap(MapTileDrawing::fromStored, MapTileDrawing::toStored);

    private static DataResult<MapTileDrawing> fromStored(Stored s) {
        try {
            return DataResult.success(fromPacked(s.size(), s.packed(), s.minCx(), s.minCz(), s.cellBlocks(), s.drawer(), s.day(), s.markers(),
                    s.zoom(), s.board()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "bad map tile drawing: " + e.getMessage());
        }
    }

    private Stored toStored() {
        return new Stored(size, packed(), minCx, minCz, cellBlocks, drawer, day, markers, zoom, board);
    }

    /** Builds a drawing from packed pixels; throws {@link IllegalArgumentException} on bad data. */
    public static MapTileDrawing fromPacked(int size, byte[] packed, int minCx, int minCz, int cellBlocks, String drawer, long day, List<TileMarker> markers) {
        return fromPacked(size, packed, minCx, minCz, cellBlocks, drawer, day, markers, 1, Optional.empty());
    }

    public static MapTileDrawing fromPacked(int size, byte[] packed, int minCx, int minCz, int cellBlocks, String drawer, long day, List<TileMarker> markers,
                                            int zoom, Optional<BoardSlice> board) {
        if (size < MIN_SIZE || size > MAX_SIZE) throw new IllegalArgumentException("tile size " + size);
        return new MapTileDrawing(size, PixelPack.unpack(packed, size * size), minCx, minCz, cellBlocks, drawer, day, markers, zoom, board);
    }

    private static final StreamCodec<ByteBuf, byte[]> PACKED_BYTES = ByteBufCodecs.byteArray(MAX_SIZE * MAX_SIZE * 2);
    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(MAX_NAME * 4);
    private static final StreamCodec<ByteBuf, Optional<BoardSlice>> BOARD = ByteBufCodecs.optional(BoardSlice.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, List<TileMarker>> MARKER_LIST = TileMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS));

    /** Network form (the item component); the block entity syncs through its update tag with {@link #CODEC}. */
    public static final StreamCodec<ByteBuf, MapTileDrawing> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapTileDrawing decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            byte[] packed = PACKED_BYTES.decode(buf);
            int minCx = ByteBufCodecs.VAR_INT.decode(buf);
            int minCz = ByteBufCodecs.VAR_INT.decode(buf);
            int cellBlocks = ByteBufCodecs.VAR_INT.decode(buf);
            String drawer = NAME.decode(buf);
            long day = ByteBufCodecs.VAR_LONG.decode(buf);
            List<TileMarker> markers = MARKER_LIST.decode(buf);
            int zoom = ByteBufCodecs.VAR_INT.decode(buf);
            Optional<BoardSlice> board;
            try {
                board = BOARD.decode(buf);
                return fromPacked(size, packed, minCx, minCz, cellBlocks, drawer, day, markers, zoom, board);
            } catch (IllegalArgumentException e) {
                throw new DecoderException("bad map tile drawing: " + e.getMessage(), e);
            }
        }

        @Override
        public void encode(ByteBuf buf, MapTileDrawing d) {
            ByteBufCodecs.VAR_INT.encode(buf, d.size);
            PACKED_BYTES.encode(buf, d.packed());
            ByteBufCodecs.VAR_INT.encode(buf, d.minCx);
            ByteBufCodecs.VAR_INT.encode(buf, d.minCz);
            ByteBufCodecs.VAR_INT.encode(buf, d.cellBlocks);
            NAME.encode(buf, d.drawer);
            ByteBufCodecs.VAR_LONG.encode(buf, d.day);
            MARKER_LIST.encode(buf, d.markers);
            ByteBufCodecs.VAR_INT.encode(buf, d.zoom);
            BOARD.encode(buf, d.board);
        }
    };

    // --- value semantics (item components compare drawings) -----------------------------------------------------

    @Override
    public boolean equals(Object o) {
        return o instanceof MapTileDrawing d && d.hash == hash && d.size == size && d.minCx == minCx && d.minCz == minCz
                && d.cellBlocks == cellBlocks && d.day == day && d.zoom == zoom && d.drawer.equals(drawer) && d.markers.equals(markers)
                && d.board.equals(board)
                && Arrays.equals(d.pixels, pixels);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "MapTileDrawing[" + size + "px at cell " + minCx + "," + minCz + " x" + cellBlocks + ", by " + drawer + " day " + day
                + ", " + markers.size() + " markers, zoom " + zoom + board.map(b -> ", board slice " + b.column() + "," + b.row()).orElse("") + "]";
    }
}

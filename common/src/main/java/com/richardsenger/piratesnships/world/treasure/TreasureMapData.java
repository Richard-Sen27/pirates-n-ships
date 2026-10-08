package com.richardsenger.piratesnships.world.treasure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.chart.data.PixelPack;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code pirates_n_ships:treasure_map} item component (TM1): the port and treasure site a map is bound to, whether
 * the treasure has been found, and the picture of the surroundings. A blank map has no component.
 *
 * <p>The picture is stored as chart cell bytes ({@code chart.data.ChartCells}: class and coast flag), {@link #SIZE}
 * by {@link #SIZE} cells of {@code cellBlocks} blocks starting at cell {@code (minCx, minCz)}, row-major, and the
 * client draws it with the chart's pirate style. That is 4096 bytes, deflated to a few hundred for storage and the
 * network ({@link PixelPack}); a pre-drawn ARGB picture would be 16 times larger before packing and could not follow
 * changes of the chart's style. Unknown cells (0) were in unloaded chunks when the map was bound.
 */
public final class TreasureMapData {

    /** Cells along each side of the picture. */
    public static final int SIZE = 64;
    public static final int CELLS = SIZE * SIZE;

    private final ResourceLocation port;
    private final BlockPos site;
    private final boolean found;
    private final int cellBlocks;
    private final int minCx;
    private final int minCz;
    private final byte[] cells;
    private final int hash;
    /** The deflated cells, made on the first save or send (immutable data, so a race only packs twice). */
    private volatile byte[] packed;

    public TreasureMapData(ResourceLocation port, BlockPos site, boolean found, int cellBlocks, int minCx, int minCz, byte[] cells) {
        if (cells.length != CELLS) throw new IllegalArgumentException("treasure map picture has " + cells.length + " cells, not " + CELLS);
        if (cellBlocks < 1) throw new IllegalArgumentException("cell size " + cellBlocks);
        this.port = port;
        this.site = site.immutable();
        this.found = found;
        this.cellBlocks = cellBlocks;
        this.minCx = minCx;
        this.minCz = minCz;
        this.cells = cells.clone();
        this.hash = Objects.hash(port, this.site, found, cellBlocks, minCx, minCz, Arrays.hashCode(this.cells));
    }

    /** The first cell of a picture centred on block {@code b} (the site's cell lands at index {@code SIZE / 2}). */
    public static int originCell(int b, int cellBlocks) {
        return Math.floorDiv(b, cellBlocks) - SIZE / 2;
    }

    public ResourceLocation port() {
        return port;
    }

    public BlockPos site() {
        return site;
    }

    public boolean found() {
        return found;
    }

    public int cellBlocks() {
        return cellBlocks;
    }

    public int minCx() {
        return minCx;
    }

    public int minCz() {
        return minCz;
    }

    /** A copy of the cell bytes. */
    public byte[] cells() {
        return cells.clone();
    }

    /** The cell byte at world cell {@code (cx, cz)}; 0 (unknown) outside the picture. */
    public int cell(int cx, int cz) {
        int x = cx - minCx;
        int z = cz - minCz;
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) return 0;
        return cells[z * SIZE + x];
    }

    public int knownCells() {
        int n = 0;
        for (byte b : cells) if ((b & 0x07) != 0) n++;
        return n;
    }

    public TreasureMapData withFound(boolean value) {
        return value == found ? this : new TreasureMapData(port, site, value, cellBlocks, minCx, minCz, cells);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TreasureMapData d)) return false;
        return d.hash == hash && d.found == found && d.cellBlocks == cellBlocks && d.minCx == minCx && d.minCz == minCz
                && d.port.equals(port) && d.site.equals(site) && Arrays.equals(d.cells, cells);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "TreasureMapData[" + port + " " + site.toShortString() + (found ? " found" : "") + ", " + knownCells() + " known cells]";
    }

    // --- Codecs -------------------------------------------------------------------------------------------------

    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.duplicate().get(a);
        return a;
    }, ByteBuffer::wrap);

    private record Stored(ResourceLocation port, BlockPos site, boolean found, int cellBlocks, int minCx, int minCz, byte[] packed) {
    }

    private static final Codec<Stored> STORED = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("port").forGetter(Stored::port),
            BlockPos.CODEC.fieldOf("site").forGetter(Stored::site),
            Codec.BOOL.optionalFieldOf("found", false).forGetter(Stored::found),
            Codec.INT.optionalFieldOf("cell_blocks", 4).forGetter(Stored::cellBlocks),
            Codec.INT.fieldOf("min_cx").forGetter(Stored::minCx),
            Codec.INT.fieldOf("min_cz").forGetter(Stored::minCz),
            BYTES.fieldOf("cells").forGetter(Stored::packed)
    ).apply(i, Stored::new));

    /** Saved form (NBT, JSON): the cells deflated. Bad data is a codec error, not an exception. */
    public static final Codec<TreasureMapData> CODEC = STORED.comapFlatMap(TreasureMapData::fromStored, TreasureMapData::toStored);

    private static DataResult<TreasureMapData> fromStored(Stored s) {
        try {
            return DataResult.success(new TreasureMapData(s.port(), s.site(), s.found(), s.cellBlocks(), s.minCx(), s.minCz(),
                    PixelPack.unpack(s.packed(), CELLS)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "bad treasure map: " + e.getMessage());
        }
    }

    private Stored toStored() {
        return new Stored(port, site, found, cellBlocks, minCx, minCz, packed());
    }

    private byte[] packed() {
        byte[] p = packed;
        if (p == null) packed = p = PixelPack.pack(cells);
        return p;
    }

    /** Deflate never makes 4096 bytes much larger; anything above this is a hostile packet. */
    private static final int MAX_PACKED = CELLS + 1024;
    private static final StreamCodec<ByteBuf, byte[]> PACKED = ByteBufCodecs.byteArray(MAX_PACKED);

    /** Network form (the synced component). */
    public static final StreamCodec<ByteBuf, TreasureMapData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TreasureMapData decode(ByteBuf buf) {
            ResourceLocation port = ResourceLocation.STREAM_CODEC.decode(buf);
            BlockPos site = BlockPos.STREAM_CODEC.decode(buf);
            boolean found = ByteBufCodecs.BOOL.decode(buf);
            int cellBlocks = ByteBufCodecs.VAR_INT.decode(buf);
            int minCx = ByteBufCodecs.VAR_INT.decode(buf);
            int minCz = ByteBufCodecs.VAR_INT.decode(buf);
            byte[] cells = PixelPack.unpack(PACKED.decode(buf), CELLS);
            return new TreasureMapData(port, site, found, Math.max(1, cellBlocks), minCx, minCz, cells);
        }

        @Override
        public void encode(ByteBuf buf, TreasureMapData d) {
            ResourceLocation.STREAM_CODEC.encode(buf, d.port);
            BlockPos.STREAM_CODEC.encode(buf, d.site);
            ByteBufCodecs.BOOL.encode(buf, d.found);
            ByteBufCodecs.VAR_INT.encode(buf, d.cellBlocks);
            ByteBufCodecs.VAR_INT.encode(buf, d.minCx);
            ByteBufCodecs.VAR_INT.encode(buf, d.minCz);
            PACKED.encode(buf, d.packed());
        }
    };
}

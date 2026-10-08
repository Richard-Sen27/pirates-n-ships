package com.richardsenger.piratesnships.world.treasure;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.SpecialOffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The treasure map component's codecs, the picture's coast flags and the fence's special offer (TM1). */
class TreasureMapDataTest {

    private static final ResourceLocation PORT = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "pirate_island_100_-40");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** An island in the middle of the picture: land ring 10..20, shallows around, deep sea beyond, unknown top rows. */
    private static byte[] classes() {
        int n = TreasureMapData.SIZE;
        byte[] c = new byte[n * n];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int d = Math.max(Math.abs(x - 32), Math.abs(z - 32));
                CellClass cls = z < 4 ? CellClass.UNKNOWN : d <= 6 ? CellClass.LAND : d == 7 ? CellClass.BEACH
                        : d <= 10 ? CellClass.SHALLOW_WATER : CellClass.DEEP_WATER;
                c[z * n + x] = (byte) cls.ordinal();
            }
        }
        return c;
    }

    private static TreasureMapData sample() {
        BlockPos site = new BlockPos(130, 61, -38);
        return new TreasureMapData(PORT, site, false, 4, TreasureMapData.originCell(site.getX(), 4),
                TreasureMapData.originCell(site.getZ(), 4), TreasureRaster.withCoast(classes()));
    }

    @Test
    void savedFormRoundTripsWithThePicture() {
        TreasureMapData d = sample();
        Tag nbt = TreasureMapData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        TreasureMapData back = TreasureMapData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(d, back);
        assertEquals(d.hashCode(), back.hashCode());
        JsonElement json = TreasureMapData.CODEC.encodeStart(JsonOps.INSTANCE, d.withFound(true)).getOrThrow();
        assertEquals(d.withFound(true), TreasureMapData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void networkFormRoundTripsAndStaysSmall() {
        TreasureMapData d = sample();
        ByteBuf buf = Unpooled.buffer();
        TreasureMapData.STREAM_CODEC.encode(buf, d);
        int size = buf.readableBytes();
        assertTrue(size < 1024, "a deflated picture is small: " + size + " bytes");
        assertEquals(d, TreasureMapData.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void badPicturesAreCodecErrors() {
        TreasureMapData d = sample();
        Tag nbt = TreasureMapData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        ((net.minecraft.nbt.CompoundTag) nbt).putByteArray("cells", new byte[]{1, 2, 3});
        assertTrue(TreasureMapData.CODEC.parse(NbtOps.INSTANCE, nbt).isError());
        assertThrows(IllegalArgumentException.class, () -> new TreasureMapData(PORT, BlockPos.ZERO, false, 4, 0, 0, new byte[10]));
    }

    @Test
    void equalityCoversTheFoundFlagAndThePicture() {
        TreasureMapData d = sample();
        assertEquals(d, sample());
        assertNotEquals(d, d.withFound(true));
        assertSame(d, d.withFound(false));
        byte[] other = d.cells();
        other[0] = 1;
        assertNotEquals(d, new TreasureMapData(d.port(), d.site(), false, 4, d.minCx(), d.minCz(), other));
        assertEquals(TreasureMapData.SIZE / 2, Math.floorDiv(d.site().getX(), 4) - d.minCx(), "the site's cell is in the middle");
    }

    @Test
    void coastFlagsLandNextToWater() {
        byte[] cells = TreasureRaster.withCoast(classes());
        int n = TreasureMapData.SIZE;
        int beach = cells[32 * n + 25]; // d == 7, next to shallows at d == 8
        assertEquals(CellClass.BEACH, ChartCells.cellClass(beach));
        assertTrue(ChartCells.coast(beach));
        int inland = cells[32 * n + 32];
        assertEquals(CellClass.LAND, ChartCells.cellClass(inland));
        assertFalse(ChartCells.coast(inland));
        int sea = cells[32 * n + 23];
        assertFalse(ChartCells.coast(sea), "water is never coast");
        assertFalse(ChartCells.known(cells[0]), "unknown stays unknown");
        TreasureMapData d = sample();
        assertEquals(n * n - 4 * n, d.knownCells());
        assertEquals(0, d.cell(d.minCx() - 1, d.minCz() + 10), "outside the picture is unknown");
    }

    @Test
    void specialOfferTotalsAndKinds() {
        assertEquals(60, SpecialOffer.total(60, 1));
        assertEquals(180, SpecialOffer.total(60, 3));
        assertEquals(0, SpecialOffer.total(60, 0));
        assertEquals(Long.MAX_VALUE, SpecialOffer.total(Long.MAX_VALUE / 2, 3), "saturates");
        AtomicBoolean on = new AtomicBoolean(true);
        SpecialOffer offer = new SpecialOffer(PORT, PORT, PortKind.PIRATE_ISLAND, () -> 60, on::get, () -> ItemStack.EMPTY);
        assertTrue(offer.offeredAt(PortKind.PIRATE_ISLAND));
        assertFalse(offer.offeredAt(PortKind.SEAFARER_VILLAGE));
        assertFalse(offer.offeredAt(PortKind.NAVY_OUTPOST));
        on.set(false);
        assertFalse(offer.offeredAt(PortKind.PIRATE_ISLAND), "off with its config toggle");
    }
}

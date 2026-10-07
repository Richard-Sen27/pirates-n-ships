package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartRegion;

import java.util.Optional;

/**
 * Where the chart draws its sea monsters and small compass roses (work package MAP1), pure and deterministic: about
 * one region in three gets one doodle at a place chosen from the region's coordinates, but only where every cell
 * under it is charted deep water. The footprint is checked at the widest zoom (one GUI pixel per cell, the doodle's
 * full pixel size in cells), so at closer zooms the smaller footprint is open sea too.
 */
public final class ChartDoodles {

    public enum Kind {
        SERPENT(32, 16), WHALE(32, 16), ROSE(16, 16);

        public final int w;
        public final int h;

        Kind(int w, int h) {
            this.w = w;
            this.h = h;
        }
    }

    /** A doodle whose top-left corner is at cell {@code (cx, cz)}. */
    public record Doodle(Kind kind, int cx, int cz) {
    }

    private ChartDoodles() {
    }

    public static Optional<Doodle> place(CellLookup cells, int rx, int rz) {
        int h = hash(rx, rz);
        if (Math.floorMod(h, 3) != 0) return Optional.empty();
        Kind kind = Kind.values()[Math.floorMod(h >> 4, Kind.values().length)];
        int ox = Math.floorMod(h >> 8, ChartRegion.SIZE - kind.w + 1);
        int oz = Math.floorMod(h >> 16, ChartRegion.SIZE - kind.h + 1);
        int cx = (rx << ChartRegion.SHIFT) + ox;
        int cz = (rz << ChartRegion.SHIFT) + oz;
        for (int z = cz; z < cz + kind.h; z++) {
            for (int x = cx; x < cx + kind.w; x++) {
                if (ChartCells.cellClass(cells.cell(x, z)) != CellClass.DEEP_WATER) return Optional.empty();
            }
        }
        return Optional.of(new Doodle(kind, cx, cz));
    }

    static int hash(int x, int z) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h;
    }
}

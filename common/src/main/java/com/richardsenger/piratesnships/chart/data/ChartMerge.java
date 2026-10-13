package com.richardsenger.piratesnships.chart.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges a {@link SampleGrid} into a {@link ChartData} (work package MAP1), pure. Sampled cells take their new class
 * (the world may have changed); unsampled cells keep what is known, so a chart never forgets. The coast flag is then
 * recomputed for every sampled cell and the ring around it: a land cell (beach, land, snow/ice) with water on one of
 * its four sides. Finally the cap: when more than {@code maxCells} cells are stored (whole regions), the least
 * recently touched regions are dropped until it fits; the regions of this sample go last.
 *
 * <p>CHART4: a sampled cell also takes its sampled colour (0 included: what the world shows now); unsampled cells keep
 * theirs. CHART5: the built flag (bit 4 of the cell byte) is taken from the sample like the class. A region gets a colour array on its first sampled cell with a colour; regions from before CHART4 stay
 * without one until then. A region whose colours changed counts as changed (a new version, sent again).
 */
public final class ChartMerge {

    /** The new data, how many cell bytes changed, which regions changed and which were dropped by the cap. */
    public record Result(ChartData data, int changedCells, List<Long> changedRegions, List<Long> droppedRegions) {
    }

    private ChartMerge() {
    }

    /** Most regions {@code maxCells} allows (at least one). */
    public static int maxRegions(long maxCells) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, maxCells / ChartRegion.CELLS));
    }

    public static Result merge(ChartData data, SampleGrid grid, long gameTime, long maxCells) {
        Work work = new Work(data);
        Set<Long> sampled = new HashSet<>();
        for (int z = 0; z < grid.h(); z++) {
            for (int x = 0; x < grid.w(); x++) {
                int cls = grid.classes()[z * grid.w() + x];
                if ((cls & ChartCells.CLASS_MASK) == 0) continue;
                int cx = grid.minCx() + x;
                int cz = grid.minCz() + z;
                sampled.add(ChartRegion.keyOfCell(cx, cz));
                int old = work.read(cx, cz);
                int now = (old & ~(ChartCells.CLASS_MASK | ChartCells.BUILT)) | cls;
                if (now != old) work.write(cx, cz, now);
                work.writeColour(cx, cz, grid.colours()[z * grid.w() + x] & 0xFF);
            }
        }
        if (!sampled.isEmpty()) {
            for (int cz = grid.minCz() - 1; cz <= grid.minCz() + grid.h(); cz++) {
                for (int cx = grid.minCx() - 1; cx <= grid.minCx() + grid.w(); cx++) {
                    int v = work.read(cx, cz);
                    if (!ChartCells.known(v)) continue;
                    boolean coast = ChartCells.cellClass(v).isLand()
                            && (water(work.read(cx + 1, cz)) || water(work.read(cx - 1, cz))
                            || water(work.read(cx, cz + 1)) || water(work.read(cx, cz - 1)));
                    int now = coast ? v | ChartCells.COAST : v & ~ChartCells.COAST;
                    if (now != v) work.write(cx, cz, now);
                }
            }
        }

        Map<Long, ChartRegion> regions = new HashMap<>(data.regions());
        List<Long> changed = new ArrayList<>();
        Set<Long> touchedKeys = new HashSet<>(work.copies.keySet());
        touchedKeys.addAll(work.colourCopies.keySet());
        for (long key : touchedKeys) {
            ChartRegion old = data.regions().get(key);
            if (old == null || !Arrays.equals(old.cellsUnsafe(), work.cells(key)) || !Arrays.equals(old.coloursUnsafe(), work.colours(key))) {
                changed.add(key);
            }
        }
        long version = data.version() + 1;
        for (long key : changed) {
            ChartRegion old = data.regions().get(key);
            long touched = sampled.contains(key) ? gameTime : old == null ? gameTime : old.touched();
            regions.put(key, ChartRegion.adopt(ChartRegion.keyX(key), ChartRegion.keyZ(key), work.cells(key), work.colours(key), version, touched));
        }
        for (long key : sampled) {
            ChartRegion r = regions.get(key);
            if (r != null) regions.put(key, r.withTouched(gameTime));
        }

        List<Long> dropped = new ArrayList<>();
        int max = maxRegions(maxCells);
        if (regions.size() > max) {
            List<ChartRegion> order = new ArrayList<>(regions.values());
            order.sort(Comparator.comparingLong(ChartRegion::touched).thenComparingLong(ChartRegion::key));
            for (int i = 0; regions.size() > max && i < order.size(); i++) {
                long key = order.get(i).key();
                regions.remove(key);
                dropped.add(key);
            }
            changed.removeAll(dropped);
        }
        changed.sort(Long::compare);
        boolean any = !changed.isEmpty() || !dropped.isEmpty();
        ChartData result = new ChartData(data.cellBlocks(), regions, data.markers(), data.nextMarkerId(),
                any ? version : data.version());
        return new Result(result, work.changedCells, changed, dropped);
    }

    private static boolean water(int cell) {
        return ChartCells.cellClass(cell).isWater();
    }

    /** Copy-on-write view: a region's cells (and apart from them its colours) are copied on their first write. */
    private static final class Work {
        final ChartData data;
        final Map<Long, byte[]> copies = new HashMap<>();
        final Map<Long, byte[]> colourCopies = new HashMap<>();
        int changedCells;

        /** The region's cells after the merge: the copy, else the stored array (never changed), else blank. */
        byte[] cells(long key) {
            byte[] copy = copies.get(key);
            if (copy != null) return copy;
            ChartRegion r = data.regions().get(key);
            return r == null ? new byte[ChartRegion.CELLS] : r.cellsUnsafe();
        }

        /** The region's colours after the merge, null when it has none. */
        byte[] colours(long key) {
            byte[] copy = colourCopies.get(key);
            if (copy != null) return copy;
            ChartRegion r = data.regions().get(key);
            return r == null ? null : r.coloursUnsafe();
        }

        void writeColour(int cx, int cz, int colour) {
            long key = ChartRegion.keyOfCell(cx, cz);
            int i = ChartRegion.index(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
            byte[] copy = colourCopies.get(key);
            if (copy == null) {
                ChartRegion r = data.regions().get(key);
                byte[] stored = r == null ? null : r.coloursUnsafe();
                if (stored == null ? colour == 0 : (stored[i] & 0xFF) == colour) return;
                copy = stored == null ? new byte[ChartRegion.CELLS] : stored.clone();
                colourCopies.put(key, copy);
            }
            copy[i] = (byte) colour;
        }

        Work(ChartData data) {
            this.data = data;
        }

        int read(int cx, int cz) {
            long key = ChartRegion.keyOfCell(cx, cz);
            int i = ChartRegion.index(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
            byte[] copy = copies.get(key);
            if (copy != null) return copy[i];
            ChartRegion r = data.regions().get(key);
            return r == null ? 0 : r.cellsUnsafe()[i];
        }

        void write(int cx, int cz, int value) {
            long key = ChartRegion.keyOfCell(cx, cz);
            byte[] copy = copies.get(key);
            if (copy == null) {
                ChartRegion r = data.regions().get(key);
                copy = r == null ? new byte[ChartRegion.CELLS] : r.copyCells();
                copies.put(key, copy);
            }
            copy[ChartRegion.index(cx & ChartRegion.MASK, cz & ChartRegion.MASK)] = (byte) value;
            changedCells++;
        }
    }
}

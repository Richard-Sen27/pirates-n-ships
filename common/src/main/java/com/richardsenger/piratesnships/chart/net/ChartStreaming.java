package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.chart.data.ChartRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which regions a client still needs (work package MAP1), pure: every stored region that overlaps the viewport
 * square and whose version is newer than the one last sent to that client, nearest to the viewport centre first,
 * at most {@code limit}.
 */
public final class ChartStreaming {

    private ChartStreaming() {
    }

    public static List<ChartRegion> pending(Map<Long, ChartRegion> regions, Map<Long, Long> sent, int centerCx, int centerCz, int radiusCells, int limit) {
        int minRx = (centerCx - radiusCells) >> ChartRegion.SHIFT;
        int maxRx = (centerCx + radiusCells) >> ChartRegion.SHIFT;
        int minRz = (centerCz - radiusCells) >> ChartRegion.SHIFT;
        int maxRz = (centerCz + radiusCells) >> ChartRegion.SHIFT;
        List<ChartRegion> out = new ArrayList<>();
        long area = (long) (maxRx - minRx + 1) * (maxRz - minRz + 1);
        if (area <= regions.size()) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                for (int rx = minRx; rx <= maxRx; rx++) {
                    ChartRegion r = regions.get(ChartRegion.key(rx, rz));
                    if (r != null && needs(sent, r)) out.add(r);
                }
            }
        } else {
            for (ChartRegion r : regions.values()) {
                if (r.rx() >= minRx && r.rx() <= maxRx && r.rz() >= minRz && r.rz() <= maxRz && needs(sent, r)) out.add(r);
            }
        }
        // distances between centres, in regions: the region holding the centre cell is always nearest
        double ccx = (centerCx + 0.5) / ChartRegion.SIZE;
        double ccz = (centerCz + 0.5) / ChartRegion.SIZE;
        out.sort(Comparator.<ChartRegion>comparingDouble(r -> sq(r.rx() + 0.5 - ccx) + sq(r.rz() + 0.5 - ccz)).thenComparingLong(ChartRegion::key));
        return out.size() > limit ? List.copyOf(out.subList(0, Math.max(0, limit))) : out;
    }

    private static boolean needs(Map<Long, Long> sent, ChartRegion r) {
        Long v = sent.get(r.key());
        return v == null || v < r.version();
    }

    private static double sq(double d) {
        return d * d;
    }
}

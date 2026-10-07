package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.data.BoardMarker;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How an update merges into a map board (work package MAP3), pure.
 *
 * <p><b>Pixels.</b> The updater's raster of the same area and zoom wins where it is {@link MapTileRaster#known}
 * (the updater knows at least one cell of the pixel's block); elsewhere the old pixel stays. So knowledge only
 * accumulates: nothing drawn before turns back into parchment.
 *
 * <p><b>Markers.</b> Markers live on the board in board pixels ({@link BoardMarker}). Each slice stamps the markers
 * that fall on it plus those within a small margin beyond its edges ({@link MapTileRaster#markerMargin}), so an icon
 * at a seam is drawn on both tiles. A slice <i>owns</i> only the markers whose pixel lies on it; collecting the owned
 * markers of every slice gives back the board's markers exactly once. An update keeps the old markers and adds the
 * updater's where no marker sits at that pixel yet (union by position, the old one wins).
 */
public final class BoardMerge {

    private BoardMerge() {
    }

    /** {@code fresh} where it is known, {@code old} elsewhere (same length). */
    public static byte[] mergePixels(byte[] old, byte[] fresh) {
        if (old.length != fresh.length) throw new IllegalArgumentException(old.length + " vs " + fresh.length + " pixels");
        byte[] out = new byte[old.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = MapTileRaster.known(fresh[i] & 0xFF) ? fresh[i] : old[i];
        }
        return out;
    }

    /** The markers slice {@code (column, row)} of {@code size} pixels owns (on its own pixels), in board pixels. */
    public static List<BoardMarker> owned(List<TileMarker> markers, int column, int row, int size) {
        List<BoardMarker> out = new ArrayList<>();
        for (TileMarker m : markers) {
            if (m.px() < 0 || m.py() < 0 || m.px() >= size || m.py() >= size) continue;
            out.add(new BoardMarker(m.icon(), column * size + m.px(), row * size + m.py(), m.name()));
        }
        return out;
    }

    /** {@code kept}, then each of {@code added} whose pixel holds no marker yet; at most {@code max}. */
    public static List<BoardMarker> union(List<BoardMarker> kept, List<BoardMarker> added, int max) {
        List<BoardMarker> out = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (BoardMarker m : kept) {
            if (out.size() >= max) return out;
            if (taken.add(m.position())) out.add(m);
        }
        for (BoardMarker m : added) {
            if (out.size() >= max) return out;
            if (taken.add(m.position())) out.add(m);
        }
        return out;
    }

    /**
     * The markers slice {@code (column, row)} of {@code size} pixels stamps: every board marker on it or within
     * {@code margin} pixels beyond its edges, in slice pixels (so those beyond the edge have pixels outside
     * {@code 0..size-1}), at most {@code max}.
     */
    public static List<TileMarker> slice(List<BoardMarker> board, int column, int row, int size, int margin, int max) {
        List<TileMarker> out = new ArrayList<>();
        int x0 = column * size;
        int y0 = row * size;
        for (BoardMarker m : board) {
            if (out.size() >= max) break;
            int px = m.bx() - x0;
            int py = m.by() - y0;
            if (px < -margin || py < -margin || px >= size + margin || py >= size + margin) continue;
            out.add(new TileMarker(m.icon(), px, py, m.name()));
        }
        return out;
    }

    /** Whether two marker lists hold the same markers by position, icon and name (order ignored). */
    public static boolean sameMarkers(List<BoardMarker> a, List<BoardMarker> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }
}

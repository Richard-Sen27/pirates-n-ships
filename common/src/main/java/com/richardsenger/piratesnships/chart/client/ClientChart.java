package com.richardsenger.piratesnships.chart.client;

import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.chart.render.ChartProjection;
import com.richardsenger.piratesnships.chart.render.CellLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * The client's copy of its own chart (work package MAP1): the server's settings, the regions received so far (kept
 * until the client leaves the server, like the server's record of what it sent), the markers, the other players and
 * the last refusal. Uses no client-only classes, so the payload handlers may reference it on both sides;
 * {@link ChartClient} installs the screen opener and the region listener (texture invalidation). The viewport is
 * remembered per player for the whole game session.
 */
public final class ClientChart {

    private static volatile ChartSettings settings = ChartSettings.DEFAULT;
    private static final Map<Long, ChartRegion> REGIONS = new ConcurrentHashMap<>();
    private static volatile List<ChartMarker> markers = List.of();
    private static volatile List<ChartStatePayload.OtherPlayer> others = List.of();
    private static volatile Optional<String> refusal = Optional.empty();
    private static volatile long version;
    private static volatile Runnable opener = () -> { };
    private static volatile LongConsumer regionListener = key -> { };
    private static final Map<UUID, ChartProjection> VIEWS = new HashMap<>();

    private ClientChart() {
    }

    /** Client init only: what happens when the server opens the chart (opens the screen). */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    /** Client init only: called with a region's key whenever it arrives. */
    public static void setRegionListener(LongConsumer l) {
        regionListener = l;
    }

    public static void acceptSettings(ChartSettings s) {
        settings = s;
        version++;
    }

    public static void open(ChartOpenPayload p) {
        settings = p.settings();
        markers = p.markers();
        refusal = Optional.empty();
        version++;
        opener.run();
    }

    public static void acceptRegion(ChartRegionPayload p) {
        ChartRegion r = p.region();
        REGIONS.put(r.key(), r);
        version++;
        regionListener.accept(r.key());
    }

    public static void acceptState(ChartStatePayload p) {
        markers = p.markers();
        others = p.others();
        if (p.refusal().isPresent()) refusal = p.refusal();
        version++;
    }

    public static ChartSettings settings() {
        return settings;
    }

    public static List<ChartMarker> markers() {
        return markers;
    }

    public static List<ChartStatePayload.OtherPlayer> others() {
        return others;
    }

    /** The last refusal not yet shown, cleared by reading it. */
    public static Optional<String> takeRefusal() {
        Optional<String> r = refusal;
        refusal = Optional.empty();
        return r;
    }

    public static long version() {
        return version;
    }

    public static Optional<ChartRegion> region(long key) {
        return Optional.ofNullable(REGIONS.get(key));
    }

    public static Map<Long, ChartRegion> regions() {
        return REGIONS;
    }

    public static int cell(int cx, int cz) {
        ChartRegion r = REGIONS.get(ChartRegion.keyOfCell(cx, cz));
        return r == null ? 0 : r.get(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
    }

    public static CellLookup lookup() {
        return ClientChart::cell;
    }

    /** The charted area in blocks (whole regions), or {@code null} when nothing is charted. */
    public static ChartProjection.Bounds bounds() {
        ChartProjection.Bounds b = null;
        int cb = Math.max(1, settings.cellBlocks());
        for (ChartRegion r : REGIONS.values()) {
            double x0 = (double) (r.rx() << ChartRegion.SHIFT) * cb;
            double z0 = (double) (r.rz() << ChartRegion.SHIFT) * cb;
            double x1 = x0 + ChartRegion.SIZE * cb;
            double z1 = z0 + ChartRegion.SIZE * cb;
            b = b == null ? new ChartProjection.Bounds(x0, z0, x1, z1) : b.include(x0, z0).include(x1, z1);
        }
        return b;
    }

    public static synchronized Optional<ChartProjection> view(UUID player) {
        return Optional.ofNullable(VIEWS.get(player));
    }

    public static synchronized void rememberView(UUID player, ChartProjection p) {
        VIEWS.put(player, p);
    }

    /** The client left the server: forget everything the server sent (not the remembered viewports). */
    public static void reset() {
        settings = ChartSettings.DEFAULT;
        REGIONS.clear();
        markers = List.of();
        others = List.of();
        refusal = Optional.empty();
        version++;
        regionListener.accept(Long.MIN_VALUE);
    }
}

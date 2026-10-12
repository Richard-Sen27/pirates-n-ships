package com.richardsenger.piratesnships.chart.client;

import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.ChartShip;
import com.richardsenger.piratesnships.chart.net.ChartStructure;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
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
 * until the client leaves the server, like the server's record of what it sent), the markers, the ships and structures
 * on the chart (CHART2) and the last refusal. Uses no client-only classes, so the payload handlers may reference it on both sides;
 * {@link ChartClient} installs the screen opener and the region listener (texture invalidation). The viewport is
 * remembered per player for the whole game session.
 */
public final class ClientChart {

    private static volatile ChartSettings settings = ChartSettings.DEFAULT;
    private static final Map<Long, ChartRegion> REGIONS = new ConcurrentHashMap<>();
    private static volatile List<ChartMarker> markers = List.of();
    private static volatile List<ChartShip> ships = List.of();
    private static volatile List<ChartStructure> structures = List.of();
    private static volatile Optional<String> refusal = Optional.empty();
    private static volatile long version;
    private static volatile Optional<TileTarget> drawTarget = Optional.empty();
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
        setSettings(s);
        version++;
    }

    /** CHART4: a new style redraws every texture (the listener's "drop all"). */
    private static void setSettings(ChartSettings s) {
        boolean restyled = settings.style() != s.style();
        settings = s;
        if (restyled) regionListener.accept(Long.MIN_VALUE);
    }

    public static void open(ChartOpenPayload p) {
        setSettings(p.settings());
        markers = p.markers();
        drawTarget = p.tile();
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
        p.markers().ifPresent(m -> markers = m);
        ships = p.ships();
        p.structures().ifPresent(s -> structures = s);
        if (p.refusal().isPresent()) refusal = p.refusal();
        version++;
    }

    /** The map tile the chart was last opened to draw on (work package MAP2), empty for a plain chart. */
    public static Optional<TileTarget> drawTarget() {
        return drawTarget;
    }

    public static ChartSettings settings() {
        return settings;
    }

    public static List<ChartMarker> markers() {
        return markers;
    }

    /** CHART2: the ship the player stands on and their own loaded ships. */
    public static List<ChartShip> ships() {
        return ships;
    }

    /** CHART2: the ports and structures the chart shows. */
    public static List<ChartStructure> structures() {
        return structures;
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

    /** The wide cell (CHART4: the cell byte and its map colour, {@code ChartCells.wide}) at {@code (cx, cz)}, 0 when unknown. */
    public static int cell(int cx, int cz) {
        ChartRegion r = REGIONS.get(ChartRegion.keyOfCell(cx, cz));
        return r == null ? 0 : r.wide(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
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
        ships = List.of();
        structures = List.of();
        refusal = Optional.empty();
        drawTarget = Optional.empty();
        version++;
        regionListener.accept(Long.MIN_VALUE);
    }
}

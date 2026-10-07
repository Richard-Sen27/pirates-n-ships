package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One player's chart (work package MAP1), stored as the persistent player attachment {@code pirates_n_ships:chart}:
 * the explored cells in {@link ChartRegion}s keyed by {@link ChartRegion#key}, the player's markers, the next marker
 * id and a version that grows with every change of a cell (regions remember the version of their last change, so
 * the server can send only what a client has not seen). {@code cellBlocks} is the cell size the cells were sampled
 * with (0 = nothing sampled yet); when the server's {@code chart.cell_blocks} changes, the old cells no longer fit
 * and are cleared on the next sample (markers stay).
 *
 * <p>Immutable: every change returns a new value (only changed regions are new objects). Only the overworld is
 * charted.
 */
public record ChartData(int cellBlocks, Map<Long, ChartRegion> regions, List<ChartMarker> markers, int nextMarkerId, long version) {

    public static final ChartData EMPTY = new ChartData(0, Map.of(), List.of(), 1, 0);

    public static final Codec<ChartData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("cell_blocks", 0).forGetter(ChartData::cellBlocks),
            ChartRegion.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(d -> List.copyOf(d.regions().values())),
            ChartMarker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(ChartData::markers),
            Codec.INT.optionalFieldOf("next_marker", 1).forGetter(ChartData::nextMarkerId),
            Codec.LONG.optionalFieldOf("version", 0L).forGetter(ChartData::version)
    ).apply(i, ChartData::fromList));

    public ChartData {
        regions = Collections.unmodifiableMap(new HashMap<>(regions));
        markers = List.copyOf(markers);
    }

    private static ChartData fromList(int cellBlocks, List<ChartRegion> list, List<ChartMarker> markers, int next, long version) {
        Map<Long, ChartRegion> map = new HashMap<>();
        for (ChartRegion r : list) map.put(r.key(), r);
        return new ChartData(cellBlocks, map, markers, next, version);
    }

    /** The cell byte at cell coordinates {@code (cx, cz)}, 0 when unknown. */
    public byte cell(int cx, int cz) {
        ChartRegion r = regions.get(ChartRegion.keyOfCell(cx, cz));
        return r == null ? 0 : r.get(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
    }

    public Optional<ChartRegion> region(int rx, int rz) {
        return Optional.ofNullable(regions.get(ChartRegion.key(rx, rz)));
    }

    /** Cells held in memory: every region counts all its {@link ChartRegion#CELLS} cells (what {@code max_cells} caps). */
    public long storedCells() {
        return (long) regions.size() * ChartRegion.CELLS;
    }

    public Optional<ChartMarker> marker(int id) {
        return markers.stream().filter(m -> m.id() == id).findFirst();
    }

    public ChartData withMarkers(List<ChartMarker> newMarkers, int newNextId) {
        return new ChartData(cellBlocks, regions, newMarkers, newNextId, version);
    }

    /** The cells cleared (a new cell size), markers kept. */
    public ChartData resetCells(int newCellBlocks) {
        return new ChartData(newCellBlocks, Map.of(), markers, nextMarkerId, version + 1);
    }
}

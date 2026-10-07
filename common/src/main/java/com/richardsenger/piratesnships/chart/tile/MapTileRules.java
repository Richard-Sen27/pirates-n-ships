package com.richardsenger.piratesnships.chart.tile;

import java.util.Locale;

/**
 * Who may draw on a map tile (work package MAP2), pure: the first rule a request breaks, in a fixed order, or
 * {@link Refusal#NONE}. Opening the draw mode checks the same rules except the area (not chosen yet).
 */
public final class MapTileRules {

    /** Why a draw is refused. {@link #key()} names the message {@code message.pirates_n_ships.chart.tile.<key>}. */
    public enum Refusal {
        NONE, CHARTS_DISABLED, TILES_DISABLED, NO_TILE, TOO_FAR, NEEDS_CHART, PERMANENT, OUT_OF_WORLD;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the server knows about a request. {@code areaInWorld} is true when no area is asked yet. */
    public record Request(boolean chartsEnabled, boolean tilesEnabled, boolean isTile, boolean inReach, boolean requireChart,
                          boolean holdsChart, boolean drawn, boolean redrawAllowed, boolean areaInWorld) {
    }

    private MapTileRules() {
    }

    public static Refusal check(Request r) {
        if (!r.chartsEnabled()) return Refusal.CHARTS_DISABLED;
        if (!r.tilesEnabled()) return Refusal.TILES_DISABLED;
        if (!r.isTile()) return Refusal.NO_TILE;
        if (!r.inReach()) return Refusal.TOO_FAR;
        if (r.requireChart() && !r.holdsChart()) return Refusal.NEEDS_CHART;
        if (r.drawn() && !r.redrawAllowed()) return Refusal.PERMANENT;
        if (!r.areaInWorld()) return Refusal.OUT_OF_WORLD;
        return Refusal.NONE;
    }
}

package com.richardsenger.piratesnships.chart.tile;

import java.util.Locale;

/**
 * Who may draw on a map tile (work package MAP2), pure: the first rule a request breaks, in a fixed order, or
 * {@link Refusal#NONE}. Opening the draw mode checks the same rules except the area (not chosen yet).
 */
public final class MapTileRules {

    /** Why a draw is refused. {@link #key()} names the message {@code message.pirates_n_ships.chart.tile.<key>}. */
    public enum Refusal {
        NONE, CHARTS_DISABLED, TILES_DISABLED, NO_TILE, TOO_FAR, NEEDS_CHART, PERMANENT, OUT_OF_WORLD, UNCHARTED,
        /** MAP3: the tiles around the used one do not fill a rectangle. */
        NOT_RECTANGLE,
        /** MAP3: the board is longer than {@code chart.tiles.max_board_side} on a side. */
        TOO_BIG,
        /** MAP3: the player cannot pay the ink. */
        NO_INK,
        /** MAP3: an update would change nothing. */
        NOTHING_NEW,
        /** MAP3: there is nothing to clear. */
        BLANK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What the server knows about a request. {@code areaInWorld} and {@code areaCharted} (the area holds at least one
     * cell of the drawer's own chart) are true when no area is asked yet.
     */
    public record Request(boolean chartsEnabled, boolean tilesEnabled, boolean isTile, boolean inReach, boolean requireChart,
                          boolean holdsChart, boolean drawn, boolean redrawAllowed, boolean areaInWorld, boolean areaCharted) {

        public Request withArea(boolean inWorld, boolean charted) {
            return new Request(chartsEnabled, tilesEnabled, isTile, inReach, requireChart, holdsChart, drawn, redrawAllowed, inWorld, charted);
        }
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
        if (!r.areaCharted()) return Refusal.UNCHARTED;
        return Refusal.NONE;
    }
}

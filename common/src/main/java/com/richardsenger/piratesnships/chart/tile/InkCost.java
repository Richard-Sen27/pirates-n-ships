package com.richardsenger.piratesnships.chart.tile;

import org.jetbrains.annotations.Nullable;

/**
 * What drawing on a map board costs (work package MAP3), pure. Ink is counted in "ink": one item of
 * {@code #pirates_n_ships:chart_ink} (an ink sac, a glow ink sac) is one ink; a kraken ink is worth
 * {@code kraken_ink_tile_value} tiles, that is {@code kraken_ink_tile_value * ink_per_tile} ink.
 *
 * <p>A first draw (or a redraw) pays {@code ink_per_tile} for every tile of the board; an update pays for every tile
 * whose pixels changed, and at least one tile when anything changed (also only the markers). Creative players and
 * servers with {@code ink_cost_enabled} off pay nothing. Payment uses as few kraken inks as possible (each is worth
 * many sacs) and then only the sacs still needed.
 */
public final class InkCost {

    /** Which items pay: plain ink items and kraken inks. */
    public record Payment(int ink, int kraken) {
        public static final Payment NONE = new Payment(0, 0);
    }

    private InkCost() {
    }

    /** Tiles a first draw or a redraw of a board of {@code tiles} tiles pays for. */
    public static int drawTiles(int tiles) {
        return Math.max(0, tiles);
    }

    /** Tiles an update pays for: those whose pixels changed, at least one when anything changed. */
    public static int updateTiles(int changedTiles, boolean anythingChanged) {
        if (!anythingChanged && changedTiles <= 0) return 0;
        return Math.max(1, changedTiles);
    }

    /** The ink due for {@code tiles} tiles (0 when the cost is off or the player is in creative mode). */
    public static int due(boolean enabled, boolean creative, int tiles, int inkPerTile) {
        if (!enabled || creative || tiles <= 0 || inkPerTile <= 0) return 0;
        return tiles * inkPerTile;
    }

    /** What one kraken ink is worth in ink. */
    public static int krakenWorth(int krakenTileValue, int inkPerTile) {
        return Math.max(0, krakenTileValue) * Math.max(0, inkPerTile);
    }

    /** All the ink {@code ink} plain items and {@code kraken} kraken inks are worth. */
    public static long held(int ink, int kraken, int krakenWorth) {
        return (long) Math.max(0, ink) + (long) Math.max(0, kraken) * Math.max(0, krakenWorth);
    }

    /**
     * How to pay {@code due} ink from {@code ink} plain items and {@code kraken} kraken inks worth {@code krakenWorth}
     * each, or {@code null} when it is not enough. The fewest kraken inks that, with all plain items, cover the cost;
     * then only the plain items still needed.
     */
    public static @Nullable Payment pay(int due, int ink, int kraken, int krakenWorth) {
        if (due <= 0) return Payment.NONE;
        if (held(ink, kraken, krakenWorth) < due) return null;
        int krakenUsed = 0;
        if (ink < due) {
            int rest = due - Math.max(0, ink);
            krakenUsed = (rest + krakenWorth - 1) / krakenWorth;
        }
        int inkUsed = Math.max(0, due - krakenUsed * krakenWorth);
        return new Payment(inkUsed, krakenUsed);
    }
}

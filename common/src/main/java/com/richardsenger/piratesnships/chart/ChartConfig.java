package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Config of the {@code chart} module (work package MAP1, docs/design.md §17): the server section {@code chart}
 * (gameplay) and the client section {@code chart_visuals}.
 */
public final class ChartConfig {

    private static final ConfigSection S = ModConfigs.server("chart", "The pirate chart: every player's own map of the coasts they have seen");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Players chart the world around them and can open their chart (false: nothing is sampled and the chart does not open)");
    public static final ConfigValue<Integer> CELL_BLOCKS = S.intRange("cell_blocks", 4, 1, 16,
            "Size of one chart cell in blocks (changing it clears every chart's explored cells on the next sample; markers stay)");
    public static final ConfigValue<Integer> SAMPLE_RADIUS = S.intRange("sample_radius", 96, 16, 256,
            "Radius in blocks around a player that is charted (only loaded chunks)");
    public static final ConfigValue<Integer> SAMPLE_INTERVAL_TICKS = S.intRange("sample_interval_ticks", 40, 5, 1200,
            "Ticks between two samples of a player's surroundings");
    public static final ConfigValue<Integer> SHALLOW_DEPTH = S.intRange("shallow_depth", 4, 1, 32,
            "Water up to this many blocks deep is drawn as shallows");
    public static final ConfigValue<Integer> MAX_CELLS = S.intRange("max_cells", 1_048_576, 65_536, 16_777_216,
            "Most chart cells kept per player, counted in whole regions of 64x64 cells (one byte each); beyond it the least recently visited regions are forgotten");
    public static final ConfigValue<Boolean> OPEN_WITHOUT_ITEM = S.bool("open_without_item", false,
            "The chart key (default M) opens the chart without a chart in hand");
    public static final ConfigValue<Boolean> SHOW_OTHER_PLAYERS = S.bool("show_other_players", false,
            "Other players within view distance appear on the chart");
    public static final ConfigValue<Integer> MAX_MARKERS = S.intRange("max_markers", 64, 0, 512,
            "Most markers a player can put on their chart");

    private static final ConfigSection TILES = S.section("tiles", "Map tiles (work package MAP2): a part of a player's chart drawn onto a block for everyone to see");

    public static final ConfigValue<Boolean> TILES_ENABLED = TILES.bool("enabled", true,
            "Players can draw their chart onto map tiles (false: drawn tiles keep their drawing, but nothing new is drawn)");
    public static final ConfigValue<Integer> TILE_CELLS = TILES.intRange("tile_cells", 128, 32, 256,
            "Chart cells along each side of a tile drawing (one pixel per cell; 128 cells of 4 blocks = 512 blocks square)");
    public static final ConfigValue<Boolean> REDRAW_ALLOWED = TILES.bool("redraw_allowed", true,
            "A drawn tile can be redrawn (a full redraw, never a partial edit); false: the first drawing is permanent");
    public static final ConfigValue<Boolean> REQUIRE_CHART_ITEM = TILES.bool("require_chart_item", true,
            "A chart must be in hand (either hand) to draw onto a tile");
    public static final ConfigValue<Integer> TILE_REACH = TILES.intRange("reach", 8, 2, 64,
            "Farthest distance in blocks between a player and the tile they draw on");

    private static final ConfigSection C =ModConfigs.client("chart_visuals", "How the pirate chart is drawn");

    public static final ConfigValue<Boolean> DOODLES = C.bool("doodles", true,
            "Sea monsters and compass roses drawn into open water");

    private ChartConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}

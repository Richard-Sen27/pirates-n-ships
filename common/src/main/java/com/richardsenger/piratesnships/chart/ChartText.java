package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.tile.MapTileRules;
import com.richardsenger.piratesnships.chart.tile.MapTileService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translation keys and English texts of the chart (work package MAP1); the lang datagen writes {@link #ENGLISH}. */
public final class ChartText {

    private static final String SCREEN = "screen." + Constants.MOD_ID + ".chart.";

    public static final String TITLE = SCREEN + "title";
    public static final String NOT_CHARTED = SCREEN + "not_charted";
    public static final String LOADING = SCREEN + "empty";
    public static final String CENTRE = SCREEN + "centre";
    public static final String ZOOM_IN = SCREEN + "zoom_in";
    public static final String ZOOM_OUT = SCREEN + "zoom_out";
    public static final String POSITION = SCREEN + "position";
    public static final String SCALE = SCREEN + "scale";
    public static final String HINT = SCREEN + "hint";
    public static final String NEW_MARKER = SCREEN + "new_marker";
    public static final String EDIT_MARKER = SCREEN + "edit_marker";
    public static final String MARKER_NAME = SCREEN + "marker_name";
    public static final String SAVE = SCREEN + "save";
    public static final String DELETE = SCREEN + "delete";
    public static final String CANCEL = SCREEN + "cancel";
    public static final String MARKER_COUNT = SCREEN + "marker_count";

    public static final String KEY_OPEN = "key." + Constants.MOD_ID + ".open_chart";
    public static final String KEY_CATEGORY = "key.categories." + Constants.MOD_ID;
    public static final String KEY_TOOLTIP = "item." + Constants.MOD_ID + ".chart.key_tooltip";

    // map tile (MAP2)
    public static final String DRAW_TITLE = SCREEN + "draw_title";
    public static final String DRAW = SCREEN + "draw";
    public static final String REDRAW = SCREEN + "redraw";
    public static final String REDRAW_CONFIRM = SCREEN + "redraw_confirm";
    public static final String INCLUDE_MARKERS = SCREEN + "include_markers";
    public static final String DRAW_HINT = SCREEN + "draw_hint";
    public static final String DRAW_AREA = SCREEN + "draw_area";
    public static final String TILE_BLANK_TOOLTIP = "item." + Constants.MOD_ID + ".map_tile.blank";
    public static final String TILE_DRAWN_BY = "item." + Constants.MOD_ID + ".map_tile.drawn_by";
    public static final String TILE_AREA = "item." + Constants.MOD_ID + ".map_tile.area";

    /** "Drawn by NAME on day N". */
    public static MutableComponent drawnBy(MapTileDrawing d) {
        return Component.translatable(TILE_DRAWN_BY, d.drawer(), d.day());
    }

    /** "x A to B, z C to D" (blocks). */
    public static MutableComponent tileArea(MapTileDrawing d) {
        return Component.translatable(TILE_AREA, d.minX(), d.maxX() - 1, d.minZ(), d.maxZ() - 1);
    }

    public static String icon(MarkerIcon icon) {
        return SCREEN + "icon." + icon.getSerializedName();
    }

    public static final Map<String, String> ENGLISH = english();

    private ChartText() {
    }

    private static Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(TITLE, "Chart of %s");
        m.put(NOT_CHARTED, "Only the overworld's seas are charted");
        m.put(LOADING, "Nothing charted yet: sail and the coasts will appear");
        m.put(CENTRE, "Centre");
        m.put(ZOOM_IN, "+");
        m.put(ZOOM_OUT, "-");
        m.put(POSITION, "x %s, z %s");
        m.put(SCALE, "%s blocks");
        m.put(HINT, "Drag to pan, wheel to zoom, right-click to place a marker");
        m.put(NEW_MARKER, "New marker");
        m.put(EDIT_MARKER, "Marker");
        m.put(MARKER_NAME, "Name");
        m.put(SAVE, "Save");
        m.put(DELETE, "Delete");
        m.put(CANCEL, "Cancel");
        m.put(MARKER_COUNT, "%s / %s markers");
        m.put(icon(MarkerIcon.X), "X marks the spot");
        m.put(icon(MarkerIcon.ANCHOR), "Anchorage");
        m.put(icon(MarkerIcon.SKULL), "Skull");
        m.put(icon(MarkerIcon.PORT), "Port");
        m.put(icon(MarkerIcon.DANGER), "Danger");
        m.put(KEY_OPEN, "Open Chart");
        m.put(KEY_CATEGORY, "Pirates 'n' Ships");
        m.put(KEY_TOOLTIP, "Press %s to open it without a chart in hand");
        m.put(com.richardsenger.piratesnships.chart.ChartItem.TOOLTIP, "Use to open your own chart of the coasts you have seen");
        m.put(ChartBackend.MSG + "disabled", "Charts are disabled on this server");
        m.put(ChartBackend.MSG + "needs_item", "You need a chart in hand to open it");
        m.put(DRAW_TITLE, "Draw on map tile");
        m.put(DRAW, "Draw");
        m.put(REDRAW, "Redraw");
        m.put(REDRAW_CONFIRM, "Redraw?");
        m.put(INCLUDE_MARKERS, "Markers");
        m.put(DRAW_HINT, "Drag the frame to choose the area, then draw");
        m.put(DRAW_AREA, "x %s, z %s (%s blocks square)");
        m.put(TILE_BLANK_TOOLTIP, "Use with a chart to draw a part of it onto the tile");
        m.put(TILE_DRAWN_BY, "Drawn by %s on day %s");
        m.put(TILE_AREA, "x %s to %s, z %s to %s");
        m.put(MapTileService.DRAWN, "You draw your chart onto the tile");
        for (MapTileRules.Refusal r : MapTileRules.Refusal.values()) {
            if (r == MapTileRules.Refusal.NONE) continue;
            m.put(MapTileService.MSG + r.key(), switch (r) {
                case CHARTS_DISABLED -> "Charts are disabled on this server";
                case TILES_DISABLED -> "Drawing on map tiles is disabled on this server";
                case NO_TILE -> "That map tile is gone";
                case TOO_FAR -> "You are too far from the map tile";
                case NEEDS_CHART -> "You need a chart in hand to draw on the tile";
                case PERMANENT -> "This tile's drawing is permanent";
                case OUT_OF_WORLD -> "That area is beyond the edge of the world";
                case NONE -> "";
            });
        }
        for (MarkerRules.Refusal r : MarkerRules.Refusal.values()) {
            m.put(ChartBackend.MSG + "refused." + r.key(), switch (r) {
                case TOO_MANY -> "Your chart holds no more markers";
                case NAME_TOO_LONG -> "That name is too long";
                case BAD_NAME -> "That name cannot be written on a chart";
                case OUT_OF_WORLD -> "That place is beyond the edge of the world";
                case UNKNOWN_MARKER -> "That marker is no longer on your chart";
                case DISABLED -> "Charts are disabled on this server";
            });
        }
        return m;
    }
}

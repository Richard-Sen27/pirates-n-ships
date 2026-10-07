package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import net.minecraft.resources.ResourceLocation;

/**
 * The chart's sprite sheet {@code textures/gui/chart/sheet.png} (work package MAP1, drawn by
 * {@code tools/gen_gui_textures.py}; the layout here mirrors the script's comment). A plain texture, not in the GUI
 * atlas: drawn with {@code GuiGraphics.blit(TEXTURE, x, y, u, v, w, h, WIDTH, HEIGHT)}. No client classes, so tests
 * can load it.
 */
public final class ChartSheet {

    public static final ResourceLocation TEXTURE = Constants.id("textures/gui/chart/sheet.png");
    public static final int WIDTH = 128;
    public static final int HEIGHT = 64;

    /** One part of the sheet. */
    public record Part(int u, int v, int w, int h) {
    }

    public static final Part COMPASS = new Part(0, 0, 32, 32);
    public static final Part SERPENT = new Part(32, 0, 32, 16);
    public static final Part WHALE = new Part(32, 16, 32, 16);
    public static final Part SMALL_ROSE = new Part(0, 32, 16, 16);
    public static final Part RING = new Part(64, 10, 11, 11);
    public static final Part OWN_SHIP = new Part(76, 10, 9, 9);
    public static final Part OTHER_SHIP = new Part(86, 10, 9, 9);
    public static final int ICON = 9;

    private ChartSheet() {
    }

    public static Part marker(MarkerIcon icon) {
        return new Part(64 + 10 * icon.ordinal(), 0, ICON, ICON);
    }

    public static Part doodle(ChartDoodles.Kind kind) {
        return switch (kind) {
            case SERPENT -> SERPENT;
            case WHALE -> WHALE;
            case ROSE -> SMALL_ROSE;
        };
    }
}

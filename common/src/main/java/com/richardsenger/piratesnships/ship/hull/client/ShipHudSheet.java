package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

/**
 * UV layout of {@code textures/gui/ship_hud.png}, written by {@code tools/gen_ship_hud_texture.py} (HUD1). A plain
 * texture outside the GUI sprite atlas, drawn with {@code GuiGraphics.blit(TEXTURE, x, y, u, v, w, h, WIDTH, HEIGHT)}.
 * No client classes, so JUnit checks the parts against the PNG. Never move a part without changing the script.
 */
public final class ShipHudSheet {

    public static final ResourceLocation TEXTURE = Constants.id("textures/gui/ship_hud.png");
    public static final int WIDTH = 64;
    public static final int HEIGHT = 64;

    public record Part(int u, int v, int w, int h) {
    }

    /** The compass rose, north up, with N/E/S/W letters. */
    public static final Part ROSE = new Part(0, 0, 40, 40);
    /** The heading needle: a ship seen from above, bow up; its centre is the rotation point. */
    public static final Part NEEDLE = new Part(40, 0, 9, 19);
    /** The pointed bow end left of the hull strip. */
    public static final Part BOW = new Part(50, 0, 6, 14);
    /** The pump glyph drawn on a cell while it is pumped. */
    public static final Part PUMP = new Part(40, 20, 7, 7);
    /** The red breach tick drawn on a cell with an open breach. */
    public static final Part BREACH = new Part(48, 20, 3, 6);

    private ShipHudSheet() {
    }
}

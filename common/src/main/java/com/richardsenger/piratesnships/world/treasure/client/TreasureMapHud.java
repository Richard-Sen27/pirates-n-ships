package com.richardsenger.piratesnships.world.treasure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.world.treasure.TreasureBearing;
import com.richardsenger.piratesnships.world.treasure.TreasureMapContent;
import com.richardsenger.piratesnships.world.treasure.TreasureMapData;
import com.richardsenger.piratesnships.world.treasure.TreasureMapText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The treasure map read while held (client only, TM1): on the right edge of the screen, the map's picture on
 * parchment in the chart's pirate style with a red X at the site and a dot where the reader stands (when inside the
 * picture), and below it the bearing and distance from the reader ("NW, 340 blocks"). A found treasure greys the
 * picture, drops the X and says "The treasure has been found". A bound map in the main hand wins over one in the off
 * hand; blank maps show nothing.
 */
public final class TreasureMapHud {

    /** GUI pixels of the picture: 1.5 per cell. */
    static final int MAP = TreasureMapData.SIZE * 3 / 2;
    static final int PAD = 5;
    static final int TEXT_H = 12;
    private static final int RED = 0xFFB01810;
    private static final int GREY_WASH = 0x90A09888;

    private TreasureMapHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null) return;
        TreasureMapData data = held(player.getMainHandItem());
        if (data == null) data = held(player.getOffhandItem());
        if (data == null) return;

        Component text = data.found() ? Component.translatable(TreasureMapText.FOUND)
                : TreasureMapText.bearing(TreasureBearing.of(player.getX(), player.getZ(), data.site().getX() + 0.5, data.site().getZ() + 0.5));
        int w = Math.max(MAP + 2 * PAD, mc.font.width(text) + 8);
        int h = MAP + 2 * PAD + TEXT_H;
        int x0 = g.guiWidth() - w - 6;
        int y0 = Math.max(4, g.guiHeight() / 2 - h / 2 - 16);
        GuiKit.parchment(g, x0, y0, w, h);
        int mx = x0 + (w - MAP) / 2;
        int my = y0 + PAD;

        RenderSystem.enableBlend();
        g.blit(TreasureMapTextures.texture(data), mx, my, MAP, MAP, 0, 0, TreasureMapTextures.SIZE, TreasureMapTextures.SIZE,
                TreasureMapTextures.SIZE, TreasureMapTextures.SIZE);
        RenderSystem.disableBlend();
        // a thin ink border
        g.fill(mx - 1, my - 1, mx + MAP + 1, my, GuiKit.INK);
        g.fill(mx - 1, my + MAP, mx + MAP + 1, my + MAP + 1, GuiKit.INK);
        g.fill(mx - 1, my, mx, my + MAP, GuiKit.INK);
        g.fill(mx + MAP, my, mx + MAP + 1, my + MAP, GuiKit.INK);

        if (data.found()) {
            g.fill(mx, my, mx + MAP, my + MAP, GREY_WASH);
        } else {
            cross(g, mx + toGui(data.site().getX() + 0.5, data.minCx(), data.cellBlocks()),
                    my + toGui(data.site().getZ() + 0.5, data.minCz(), data.cellBlocks()));
        }
        int px = toGui(player.getX(), data.minCx(), data.cellBlocks());
        int pz = toGui(player.getZ(), data.minCz(), data.cellBlocks());
        if (px >= 1 && pz >= 1 && px < MAP - 1 && pz < MAP - 1) {
            g.fill(mx + px - 1, my + pz - 1, mx + px + 2, my + pz + 2, GuiKit.INK);
            g.fill(mx + px, my + pz, mx + px + 1, my + pz + 1, GuiKit.INK_NAVY);
        }
        int color = data.found() ? GuiKit.INK_DIM : GuiKit.INK;
        g.drawString(mc.font, text, x0 + (w - mc.font.width(text)) / 2, my + MAP + 3, color, false);
    }

    private static TreasureMapData held(ItemStack stack) {
        return stack.is(TreasureMapContent.TREASURE_MAP.get()) ? stack.get(TreasureMapContent.TREASURE_MAP_DATA.get()) : null;
    }

    /** GUI offset in the picture of block coordinate {@code b}. */
    static int toGui(double b, int minCell, int cellBlocks) {
        return (int) Math.floor((b / cellBlocks - minCell) * MAP / TreasureMapData.SIZE);
    }

    /** A red X, seven pixels across, two pixels thick. */
    private static void cross(GuiGraphics g, int cx, int cy) {
        for (int i = -3; i <= 3; i++) {
            g.fill(cx + i, cy + i, cx + i + 2, cy + i + 1, RED);
            g.fill(cx + i, cy - i, cx + i + 2, cy - i + 1, RED);
        }
    }
}

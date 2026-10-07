package com.richardsenger.piratesnships.chart.client;

import com.richardsenger.piratesnships.chart.ChartText;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.tile.MapTileBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A HUD layer (client only, work package MAP2): while the crosshair rests on a drawn map tile, two lines under it
 * name who drew it on which day and the area it shows, like a sign's text you read by looking at it.
 */
public final class MapTileHud {

    private static final int COLOR = 0xFFEEE0BC;
    private static final int DIM = 0xFFB8A27C;

    private MapTileHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.options.hideGui) return;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof MapTileBlockEntity tile)) return;
        MapTileDrawing d = tile.drawing();
        if (d == null) return;
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 10;
        Component by = ChartText.drawnBy(d);
        Component area = ChartText.tileArea(d);
        g.drawString(mc.font, by, x - mc.font.width(by) / 2, y, COLOR, true);
        g.drawString(mc.font, area, x - mc.font.width(area) / 2, y + 10, DIM, true);
    }
}

package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.ChartText;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The map tile as an item (work package MAP2). Blank tiles stack; a drawn tile carries its drawing in the
 * {@code pirates_n_ships:map_tile_drawing} component (so only identical drawings would stack, and there is only ever
 * one of each) and names who drew it, when and which area.
 */
public class MapTileItem extends BlockItem {

    public MapTileItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        MapTileDrawing d = stack.get(ChartContent.MAP_TILE_DRAWING.get());
        if (d == null) {
            lines.add(Component.translatable(ChartText.TILE_BLANK_TOOLTIP).withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(ChartText.drawnBy(d).withStyle(ChatFormatting.GRAY));
        lines.add(ChartText.tileArea(d).withStyle(ChatFormatting.DARK_GRAY));
    }
}

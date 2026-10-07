package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.tile.MapTileService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The chart (work package MAP1): right-click opens the holder's own chart. The chart itself is player data
 * ({@link ChartAttachments#CHART}), not item data, so any chart item opens the same map of the player who uses it.
 */
public class ChartItem extends Item {

    public static final String TOOLTIP = "item.pirates_n_ships.chart.tooltip";

    public ChartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            ChartBackend.open(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Used on a block: on a map tile (work package MAP2) the chart opens in "draw on tile" mode. Reached when the
     * player sneaks (vanilla skips the block's own use then); without sneaking the tile's block handles it.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.getBlockState(context.getClickedPos()).is(ChartContent.MAP_TILE.get())) return super.useOn(context);
        if (context.getPlayer() instanceof ServerPlayer sp) MapTileService.openDrawMode(sp, context.getClickedPos());
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TOOLTIP).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * The boarding plank item (BRD1). Used on a gunwale block of a ship lying alongside another, it lays a plank run
 * through {@link BoardingPlanks#lay}: on the clicked side face it runs straight out at that block's height, on the top
 * face one block up and out in the player's facing. A {@link BlockItem} only so the block, its loot and pick-block
 * know their item; it never places a single block itself.
 */
public class BoardingPlankItem extends BlockItem {

    public static final String KEY_NO_DECK = "message." + Constants.MOD_ID + ".boarding_plank.no_deck";
    public static final String KEY_BLOCKED = "message." + Constants.MOD_ID + ".boarding_plank.blocked";
    public static final String KEY_NOT_ON_SHIP = "message." + Constants.MOD_ID + ".boarding_plank.not_on_ship";
    public static final String KEY_DISABLED = "message." + Constants.MOD_ID + ".boarding_plank.disabled";
    public static final String KEY_TOOLTIP = "item." + Constants.MOD_ID + ".boarding_plank.tooltip";

    public BoardingPlankItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        BoardingPlanks.Laid laid = BoardingPlanks.lay(level, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getHorizontalDirection());
        if (laid.outcome() != BoardingPlanks.Outcome.OK) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(messageKey(laid.outcome())), true);
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.gameEvent(player, GameEvent.BLOCK_PLACE, laid.base());
        return InteractionResult.CONSUME;
    }

    /** The action bar message for a refused plank. */
    public static String messageKey(BoardingPlanks.Outcome outcome) {
        return switch (outcome) {
            case DISABLED -> KEY_DISABLED;
            case NOT_ON_SHIP -> KEY_NOT_ON_SHIP;
            case BLOCKED -> KEY_BLOCKED;
            default -> KEY_NO_DECK;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY_TOOLTIP).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

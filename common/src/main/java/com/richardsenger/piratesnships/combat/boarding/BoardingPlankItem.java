package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.text.Num;
import net.minecraft.ChatFormatting;
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
 * The boarding plank item (BRD1, BRD2, BRD4). Used on the edge of a deck, a rail, a pier or the shore, it lays a gangway
 * through {@link BoardingPlanks#lay}: on the clicked side face it runs straight out at that block's height, on the top
 * face one block up and out in the player's facing; it lands on a ship or on the world as one straight board (BRD4).
 * A refusal says why on the action bar. A {@link BlockItem} only so the block, its loot and pick-block know their item;
 * it never places a single block itself.
 */
public class BoardingPlankItem extends BlockItem {

    public static final String KEY_NO_START = "message." + Constants.MOD_ID + ".boarding_plank.no_start";
    public static final String KEY_BLOCKED = "message." + Constants.MOD_ID + ".boarding_plank.blocked";
    public static final String KEY_TOO_FAR = "message." + Constants.MOD_ID + ".boarding_plank.too_far";
    public static final String KEY_TOO_STEEP = "message." + Constants.MOD_ID + ".boarding_plank.too_steep";
    public static final String KEY_NOTHING_TO_LAND = "message." + Constants.MOD_ID + ".boarding_plank.nothing_to_land";
    public static final String KEY_DISABLED = "message." + Constants.MOD_ID + ".boarding_plank.disabled";
    public static final String KEY_TOOLTIP = "item." + Constants.MOD_ID + ".boarding_plank.tooltip";
    public static final String KEY_TOOLTIP_PICK_UP = "item." + Constants.MOD_ID + ".boarding_plank.tooltip_pick_up";

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
                player.displayClientMessage(message(laid), true);
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.gameEvent(player, GameEvent.BLOCK_PLACE, laid.base());
        if (laid.ship() != null && laid.farShip() != null && !laid.ship().equals(laid.farShip())) {
            com.richardsenger.piratesnships.core.advancement.ModAdvancements.shipBoarded(player, "plank"); // ADV1: a plank from one ship to another
        }
        return InteractionResult.CONSUME;
    }

    /** The action bar message key for a refused plank. */
    public static String messageKey(BoardingPlanks.Outcome outcome) {
        return switch (outcome) {
            case DISABLED -> KEY_DISABLED;
            case NO_START -> KEY_NO_START;
            case BLOCKED -> KEY_BLOCKED;
            case TOO_FAR -> KEY_TOO_FAR;
            case TOO_STEEP -> KEY_TOO_STEEP;
            default -> KEY_NOTHING_TO_LAND;
        };
    }

    /** The action bar message for a refused plank, with its number (cell in the way, reach, steepest pitch). */
    public static Component message(BoardingPlanks.Laid laid) {
        String key = messageKey(laid.outcome());
        return switch (laid.outcome()) {
            case BLOCKED -> Component.translatable(key, laid.blockedAt() + 1);
            case TOO_FAR -> Component.translatable(key, BoardingConfig.MAX_LENGTH.get());
            case TOO_STEEP -> Component.translatable(key, Num.trim(BoardingConfig.MAX_PITCH.get()));
            default -> Component.translatable(key);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY_TOOLTIP).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY_TOOLTIP_PICK_UP).withStyle(ChatFormatting.GRAY));
    }
}

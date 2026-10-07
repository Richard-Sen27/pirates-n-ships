package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Shipwright's Toolkit (RS2): hammer, saw, nails and leather in one roll. Sneak-use on a block of a ship marks that
 * piece as the keeper; use on a block of another piece of the same ship rejoins that piece to the marked one
 * ({@link ShipRejoin}). Never automatic.
 */
public class ShipwrightToolkitItem extends Item {

    public static final String KEY_TOOLTIP = "item." + Constants.MOD_ID + ".shipwright_toolkit.tooltip";
    public static final String KEY_MARKED = "item." + Constants.MOD_ID + ".shipwright_toolkit.marked";
    public static final String KEY_MARKED_UNNAMED = "item." + Constants.MOD_ID + ".shipwright_toolkit.marked_unnamed";

    public ShipwrightToolkitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        ShipRejoin.Result r = player.isSecondaryUseActive()
                ? ShipRejoin.mark(serverLevel, player, stack, context.getClickedPos())
                : ShipRejoin.use(serverLevel, player, context.getHand(), context.getClickedPos());
        player.displayClientMessage(r.message(), true);
        return r.outcome() == ShipRejoin.Outcome.NOT_A_SHIP ? InteractionResult.PASS : InteractionResult.CONSUME;
    }

    /** Keeps the stack's durability at {@code assembly.rejoin.toolkit_durability} (a config value, read after registration). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            syncDurability(stack);
        }
    }

    /** Sets the stack's maximum damage to the config value, keeping the damage below it. */
    public static void syncDurability(ItemStack stack) {
        int max = AssemblyConfig.TOOLKIT_DURABILITY.get();
        Integer current = stack.get(DataComponents.MAX_DAMAGE);
        if (current == null || current != max) {
            stack.set(DataComponents.MAX_DAMAGE, max);
            if (stack.getDamageValue() >= max) {
                stack.setDamageValue(max - 1);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RejoinMark mark = stack.get(AssemblyContent.REJOIN_MARK.get());
        if (mark != null) {
            tooltip.add((mark.label().isEmpty() ? Component.translatable(KEY_MARKED_UNNAMED)
                    : Component.translatable(KEY_MARKED, mark.label())).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable(KEY_TOOLTIP).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(AssemblyContent.REJOIN_MARK.get()) || super.isFoil(stack);
    }
}

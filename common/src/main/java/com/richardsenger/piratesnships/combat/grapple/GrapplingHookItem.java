package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.firearms.FirearmItem;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The grappling hook (docs/design.md §8.3, G11, GR3, GR4).
 * <ul>
 *   <li><b>Thrown by hand</b> (G11): use without a musket in the other hand throws it at {@code throw_velocity}
 *       ({@link GrappleService#throwHook}). The item leaves the hand (not in creative) and comes back when the hook
 *       is released or reeled in.</li>
 *   <li><b>Launched</b> (GR3): with the hook in the <i>off hand</i> and our musket in the main hand (with
 *       {@code grapple.launch.musket_enabled}; with {@code offhand_required} off also the swapped hands), holding use
 *       runs the musket's own loading session and moves the hook into it ({@link MusketHookLoad}); the loaded musket
 *       then fires it like a shot. The musket is the only launcher (GR4): next to any other item the hook in the off
 *       hand is thrown when the main hand's own use passes. The hook item itself never starts a use session.</li>
 * </ul>
 * Sneak + use with an empty hand releases a hook that is out. With {@code grapple.enabled} off the item does nothing.
 */
public class GrapplingHookItem extends Item {

    public static final String TOOLTIP_KEY = "item." + Constants.MOD_ID + ".grappling_hook.tooltip";
    public static final String LAUNCH_TOOLTIP_KEY = "item." + Constants.MOD_ID + ".grappling_hook.tooltip.launch";

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    /** What {@code stack} is for launching. */
    public static GrappleLaunch.Held heldOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return GrappleLaunch.Held.EMPTY;
        }
        if (stack.getItem() instanceof GrapplingHookItem) {
            return GrappleLaunch.Held.HOOK;
        }
        if (stack.getItem() instanceof FirearmItem gun && gun.kind() == FirearmKind.MUSKET) {
            return GrappleLaunch.Held.MUSKET;
        }
        return GrappleLaunch.Held.OTHER;
    }

    /** The hand of {@code entity} holding the musket next to a hook in a valid arrangement (by config), or {@code null}. */
    public static @Nullable InteractionHand launcherHand(LivingEntity entity) {
        if (!GrappleConfig.ENABLED.get()) {
            return null;
        }
        return GrappleLaunch.launcherHand(heldOf(entity.getMainHandItem()), heldOf(entity.getOffhandItem()),
                GrappleConfig.OFFHAND_REQUIRED.get(), musketEnabled());
    }

    static boolean musketEnabled() {
        return GrappleConfig.MUSKET_ENABLED.get() && FirearmsConfig.ENABLED.get();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GrappleConfig.ENABLED.get()) {
            return InteractionResultHolder.pass(stack);
        }
        GrappleLaunch.HookUse use = GrappleLaunch.hookUse(hand, heldOf(player.getMainHandItem()), heldOf(player.getOffhandItem()),
                GrappleConfig.OFFHAND_REQUIRED.get(), musketEnabled());
        if (use == GrappleLaunch.HookUse.LAUNCHER) {
            return useLauncher(level, player, GrappleLaunch.other(hand), stack);
        }
        if (level instanceof ServerLevel server) {
            boolean consume = !player.getAbilities().instabuild;
            GrappleService.throwHook(server, player, stack.copyWithCount(1), consume);
            if (consume) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * The hook was offered the use while the musket in {@code launcherHand} is in a valid arrangement: a musket in the
     * off hand gets the use passed on (its own loading or aiming session). A musket in the main hand already had its
     * turn (vanilla offers the main hand first), so the hook does nothing then.
     */
    private static InteractionResultHolder<ItemStack> useLauncher(Level level, Player player, InteractionHand launcherHand, ItemStack hook) {
        ItemStack launcher = player.getItemInHand(launcherHand);
        if (launcherHand == InteractionHand.OFF_HAND) {
            InteractionResultHolder<ItemStack> r = launcher.use(level, player, launcherHand);
            return new InteractionResultHolder<>(r.getResult(), hook);
        }
        return InteractionResultHolder.pass(hook);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(LAUNCH_TOOLTIP_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }
}

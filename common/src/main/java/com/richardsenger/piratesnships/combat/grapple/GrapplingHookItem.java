package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmItem;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmService;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The grappling hook (docs/design.md §8.3, G11, GR1). How use launches it depends on the other hand
 * ({@link GrappleLaunch#mode}):
 * <ul>
 *   <li><b>Nothing</b> (or a switched-off weapon): use throws it at {@code throw_velocity}
 *       ({@link GrappleService#throwHook}).</li>
 *   <li><b>A crossbow</b> ({@code grapple.launch.crossbow_enabled}): use draws like a crossbow charge; letting go after
 *       {@code crossbow_draw_ticks} shoots the hook at {@code crossbow_speed} times the throw with a longer rope, earlier
 *       cancels. The crossbow takes one point of wear; its own charge is never touched (no vanilla crossbow
 *       internals: the hook is launched by our code).</li>
 *   <li><b>A musket</b> ({@code musket_enabled}): use fires the hook at once at {@code musket_speed} times the throw with
 *       the longest rope, burning one gunpowder (no lead shot) with the musket's shot sound, smoke, recoil, cooldown and
 *       rain misfire ({@link FirearmService#fireBlank}); a misfire keeps hook and powder. A musket loaded with a ball
 *       refuses ("unload the musket first"); the loaded state is neither read for the shot nor changed.</li>
 * </ul>
 * The item leaves the hand with the hook (not in creative) and comes back when the hook is released or reeled in. A
 * second launch releases the first hook. Sneak + use with an empty hand releases a hook that is out. With
 * {@code grapple.enabled} off the item does nothing.
 *
 * <p>The weapon must be in the <i>other</i> hand: vanilla offers the use to the main hand first, so a crossbow with
 * arrows or a musket with ammunition in the main hand does its own thing. Hook in the main hand, weapon in the off
 * hand always works.
 */
public class GrapplingHookItem extends Item {

    public static final String TOOLTIP_KEY = "item." + Constants.MOD_ID + ".grappling_hook.tooltip";
    public static final String LAUNCH_TOOLTIP_KEY = "item." + Constants.MOD_ID + ".grappling_hook.tooltip.launch";
    public static final String MUSKET_LOADED_KEY = "message." + Constants.MOD_ID + ".grapple.musket_loaded";
    public static final String NO_POWDER_KEY = "message." + Constants.MOD_ID + ".grapple.no_powder";

    /** Use duration of a crossbow draw session (the draw ends on release, like a bow). */
    public static final int DRAW_SESSION_TICKS = 72000;

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    /** What {@code stack} (held in the other hand) is for launching. */
    public static GrappleLaunch.Weapon weaponOf(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) {
            return GrappleLaunch.Weapon.CROSSBOW;
        }
        if (stack.getItem() instanceof FirearmItem gun && gun.kind() == FirearmKind.MUSKET) {
            return GrappleLaunch.Weapon.MUSKET;
        }
        return GrappleLaunch.Weapon.NONE;
    }

    /** The launch mode for a hook used from {@code hand} (by config and the other hand's item). */
    public static GrappleLaunch.Mode modeFor(LivingEntity entity, InteractionHand hand) {
        return GrappleLaunch.mode(weaponOf(entity.getItemInHand(other(hand))), GrappleConfig.CROSSBOW_ENABLED.get(),
                GrappleConfig.MUSKET_ENABLED.get() && FirearmsConfig.ENABLED.get());
    }

    static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GrappleConfig.ENABLED.get()) {
            return InteractionResultHolder.pass(stack);
        }
        return switch (modeFor(player, hand)) {
            case CROSSBOW -> {
                player.startUsingItem(hand); // the draw; the hook leaves on release
                if (!level.isClientSide) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(),
                            SoundSource.PLAYERS, 0.8f, 1.0f);
                }
                yield InteractionResultHolder.consume(stack);
            }
            case MUSKET -> fireFromMusket(level, player, hand, stack);
            case THROW -> {
                if (level instanceof ServerLevel server) {
                    boolean consume = !player.getAbilities().instabuild;
                    GrappleService.throwHook(server, player, stack.copyWithCount(1), consume);
                    if (consume) {
                        stack.shrink(1);
                    }
                }
                yield InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        };
    }

    private static InteractionResultHolder<ItemStack> fireFromMusket(Level level, Player player, InteractionHand hand, ItemStack stack) {
        ItemStack musket = player.getItemInHand(other(hand));
        GrappleLaunch.Powder powder = GrappleLaunch.powder(player.hasInfiniteMaterials(),
                player.getInventory().countItem(Items.GUNPOWDER), FirearmsConfig.CONSUME_GUNPOWDER.get());
        GrappleLaunch.MusketRefusal refusal = GrappleLaunch.musketRefusal(FirearmContent.isLoaded(musket),
                player.getCooldowns().isOnCooldown(musket.getItem()), powder);
        if (refusal != GrappleLaunch.MusketRefusal.NONE) {
            if (level instanceof ServerLevel) {
                switch (refusal) {
                    case LOADED -> player.displayClientMessage(Component.translatable(MUSKET_LOADED_KEY), true);
                    case NO_POWDER -> {
                        player.displayClientMessage(Component.translatable(NO_POWDER_KEY), true);
                        FirearmService.playEmpty(level, player, FirearmsConfig.type(FirearmKind.MUSKET).soundPitch());
                    }
                    default -> { }
                }
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server
                && FirearmService.fireBlank(server, player, musket, FirearmKind.MUSKET) == FirearmService.Shot.FIRED) {
            if (powder == GrappleLaunch.Powder.CONSUME) {
                consumeOne(player.getInventory(), Items.GUNPOWDER);
            }
            boolean consume = !player.getAbilities().instabuild;
            GrappleService.launchHook(server, player, stack.copyWithCount(1), consume, GrappleLaunch.Mode.MUSKET);
            if (consume) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void consumeOne(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                inventory.setChanged();
                return;
            }
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) return;
        int held = getUseDuration(stack, entity) - remainingUseDuration;
        if (held == GrappleConfig.CROSSBOW_DRAW_TICKS.get()) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    /** End of a crossbow draw: shoots the hook if it was drawn long enough and the crossbow is still in the other hand. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || !GrappleConfig.ENABLED.get()) return;
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (modeFor(player, hand) != GrappleLaunch.Mode.CROSSBOW) return;
        int held = getUseDuration(stack, entity) - timeLeft;
        if (!GrappleLaunch.drawn(held, GrappleConfig.CROSSBOW_DRAW_TICKS.get())) return;
        boolean consume = !player.getAbilities().instabuild;
        GrappleService.launchHook(server, player, stack.copyWithCount(1), consume, GrappleLaunch.Mode.CROSSBOW);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        ItemStack crossbow = player.getItemInHand(other(hand));
        crossbow.hurtAndBreak(1, player, LivingEntity.getSlotForHand(other(hand)));
        if (consume) {
            stack.shrink(1);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRAW_SESSION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(LAUNCH_TOOLTIP_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }
}

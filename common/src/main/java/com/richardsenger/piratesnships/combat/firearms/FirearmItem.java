package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Pistol and musket (docs/design.md §8.1). Use on an unloaded gun starts loading if the player has one lead shot and
 * one gunpowder (none in creative): the gun is held like a drawn bow for the reload time, and loads when it is
 * reached ({@link #finishUsingItem}); letting go early ({@link #releaseUsing}) cancels and takes nothing. Use on a
 * loaded gun fires at once ({@link FirearmService#fire}). Use on an unloaded gun without ammunition clicks. With
 * {@code firearms.enabled} off the gun does nothing.
 */
public class FirearmItem extends Item {

    public static final String LOADED_KEY = "item." + Constants.MOD_ID + ".firearm.loaded";
    public static final String UNLOADED_KEY = "item." + Constants.MOD_ID + ".firearm.unloaded";

    private final FirearmKind kind;

    public FirearmItem(Properties properties, FirearmKind kind) {
        super(properties);
        this.kind = kind;
    }

    public FirearmKind kind() {
        return kind;
    }

    /** The current numbers of this gun (from config). */
    public FirearmType type() {
        return FirearmsConfig.type(kind);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!FirearmsConfig.ENABLED.get()) return InteractionResultHolder.pass(stack);
        if (FirearmContent.isLoaded(stack)) {
            if (level instanceof ServerLevel server) {
                FirearmService.fire(server, player, stack, kind);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (FirearmService.canLoad(player)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            FirearmService.playEmpty(level, player, type().soundPitch());
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) return;
        int duration = getUseDuration(stack, entity);
        int used = duration - remainingUseDuration;
        if (used == duration / 2) {
            // the ramrod, half way through loading
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(),
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && FirearmsConfig.ENABLED.get() && !FirearmContent.isLoaded(stack)) {
            FirearmService.completeLoading(level, entity, stack);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // let go before the reload time: loading is cancelled, nothing is used up
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return type().reloadTicks();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        boolean loaded = FirearmContent.isLoaded(stack);
        lines.add(Component.translatable(loaded ? LOADED_KEY : UNLOADED_KEY)
                .withStyle(loaded ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
}

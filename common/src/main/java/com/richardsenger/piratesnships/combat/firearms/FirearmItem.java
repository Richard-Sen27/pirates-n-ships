package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.firearms.client.FirearmClientState;
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
 * Pistol and musket (docs/design.md §8.1). Holding use is a <b>session</b> ({@link FirearmRules#isAimSession}):
 * <ul>
 *   <li><b>Loading</b> (the gun was unloaded at the press and the player has what the load takes: by default one
 *       lead shot and one gunpowder, none in creative; another module can offer another load through
 *       {@link FirearmLoads}, e.g. the grappling hook held in the other hand): after the reload time the gun loads
 *       ({@link #onUseTick}); the player keeps holding without effect,
 *       so letting go never fires a gun that was just loaded. Letting go before the reload time cancels and takes
 *       nothing.</li>
 *   <li><b>Aiming</b> (the gun was loaded at the press): the shot (whatever the gun holds,
 *       {@link FirearmLoads#loadedIn}) leaves when the player lets go
 *       ({@link #releaseUsing}), if held at least {@code firearms.aim.aim_min_ticks} (default 0: a click fires at once).
 *       After {@code aim_steady_ticks} of aiming the spread is multiplied by {@code aimed_spread_factor}; a loaded
 *       musket also zooms in on the client ({@code firearm_view.musket_zoom}).</li>
 * </ul>
 * <b>Input (FA1):</b> with {@code firearms.fire_on_attack} on (the default) letting go of an aim never fires: the
 * attack key does, aimed or from the hip ({@link FirearmTrigger}); letting go only lowers the gun. Off restores the
 * release-to-fire scheme described above.
 * <b>Lowering (P5):</b> sneaking while aiming lowers the gun without firing ({@code firearms.aim.lower_on_sneak}):
 * the client lets go as soon as sneak is pressed ({@code client.FirearmLowering}), a release while sneaking never
 * fires ({@link FirearmRules#lowers}), and a loaded gun is not raised while sneaking ({@link FirearmRules#aimsOnUse}).
 * <b>Item bar (P5):</b> white and filling while the local player loads the gun (read through
 * {@link FirearmClientState}), full and gold on a loaded gun, none on an empty one ({@link FirearmBar}).
 * Use on an unloaded gun without ammunition clicks. With {@code firearms.enabled} off the gun does nothing.
 *
 * <p>Pose: {@link UseAnim#NONE}, the gun stays in the normal held position in both views. The crossbow poses can't be
 * used: vanilla draws {@code CROSSBOW_HOLD} and the crossbow's first-person transform only for {@code CrossbowItem}
 * instances, and {@link UseAnim#CROSSBOW} on another item gets no first-person arm transform at all (the gun would be
 * drawn at the camera). Aim and reload poses come with the player animations (P3).
 */
public class FirearmItem extends Item {

    public static final String LOADED_KEY = "item." + Constants.MOD_ID + ".firearm.loaded";
    public static final String UNLOADED_KEY = "item." + Constants.MOD_ID + ".firearm.unloaded";
    public static final String AIM_HINT_KEY = "item." + Constants.MOD_ID + ".firearm.hint.aim";
    public static final String AIM_ATTACK_HINT_KEY = "item." + Constants.MOD_ID + ".firearm.hint.aim_attack";
    public static final String LOWER_HINT_KEY = "item." + Constants.MOD_ID + ".firearm.hint.lower";
    public static final String LOAD_HINT_KEY = "item." + Constants.MOD_ID + ".firearm.hint.load";

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
            // another load is offered (e.g. a hook in the other hand) but the gun holds something else: say so, then aim
            FirearmLoad offered = FirearmLoads.forLoading(player, hand, stack, kind);
            if (!level.isClientSide && offered != FirearmLoads.LEAD_BALL && !offered.isIn(stack) && offered.occupiedMessage() != null) {
                player.displayClientMessage(offered.occupiedMessage(), true);
            }
            // sneaking keeps a loaded gun lowered (so holding use after lowering doesn't raise it again)
            if (!FirearmRules.aimsOnUse(player.isShiftKeyDown(), FirearmsConfig.LOWER_ON_SNEAK.get())) {
                return InteractionResultHolder.pass(stack);
            }
            player.startUsingItem(hand); // aim; the shot leaves on release
            return InteractionResultHolder.consume(stack);
        }
        FirearmLoad load = FirearmLoads.forLoading(player, hand, stack, kind);
        if (load.canLoad(player, hand, stack)) {
            player.startUsingItem(hand);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(),
                        SoundSource.PLAYERS, 0.6f, 0.8f);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            FirearmService.playEmpty(level, player, type().soundPitch());
            if (load.missingMessage() != null) player.displayClientMessage(load.missingMessage(), true);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || FirearmRules.isAimSession(remainingUseDuration) || FirearmContent.isLoaded(stack)) return;
        if (!FirearmsConfig.ENABLED.get()) return;
        int reload = type().reloadTicks();
        int held = FirearmRules.heldTicks(remainingUseDuration);
        if (held == reload / 2) {
            // the ramrod, half way through loading
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(),
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        if (FirearmRules.reloadComplete(held, reload)) {
            FirearmService.completeLoading(level, entity, stack);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // only after a whole session (an hour or half an hour of holding): nothing to do
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // a loading session: let go before the reload time cancels and takes nothing, after it the gun stays loaded
        if (!FirearmRules.isAimSession(timeLeft) || !FirearmsConfig.ENABLED.get() || !FirearmContent.isLoaded(stack)) return;
        // sneaking lowers the gun: no shot, it stays loaded (the client releases the use when sneak is pressed)
        if (FirearmRules.lowers(entity.isShiftKeyDown(), true, FirearmsConfig.LOWER_ON_SNEAK.get())) return;
        int held = FirearmRules.heldTicks(timeLeft);
        // FA1: with fire_on_attack on, letting go only lowers the gun; the attack key fires (FirearmTrigger)
        if (level instanceof ServerLevel server
                && FirearmTriggerRules.releaseFires(FirearmsConfig.FIRE_ON_ATTACK.get(), held, FirearmsConfig.AIM_MIN_TICKS.get())) {
            FirearmService.fire(server, entity, stack, kind, held);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return FirearmRules.sessionTicks(FirearmContent.isLoaded(stack));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // ---- item bar: loading progress (white) and the loaded state (full, gold) ----

    private FirearmBar.State barState(ItemStack stack, int loadingHeld) {
        return FirearmBar.state(FirearmContent.isLoaded(stack), loadingHeld >= 0);
    }

    /** Ticks the local player has been loading this stack, -1 if not (always -1 without a client). */
    private static int loadingHeld(ItemStack stack) {
        return FirearmContent.isLoaded(stack) ? -1 : FirearmClientState.get().loadingHeldTicks(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return FirearmBar.visible(barState(stack, loadingHeld(stack)));
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int held = loadingHeld(stack);
        return FirearmBar.width(barState(stack, held), held, type().reloadTicks());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return FirearmBar.color(barState(stack, loadingHeld(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (FirearmContent.isLoaded(stack)) {
            lines.add(Component.translatable(LOADED_KEY).withStyle(ChatFormatting.GOLD));
            Component what = FirearmLoads.loadedIn(stack).describe(stack);
            if (what != null) lines.add(what.copy().withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(FirearmsConfig.FIRE_ON_ATTACK.get() ? AIM_ATTACK_HINT_KEY : AIM_HINT_KEY)
                    .withStyle(ChatFormatting.GRAY));
            if (FirearmsConfig.LOWER_ON_SNEAK.get()) {
                lines.add(Component.translatable(LOWER_HINT_KEY).withStyle(ChatFormatting.GRAY));
            }
        } else {
            lines.add(Component.translatable(UNLOADED_KEY).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(LOAD_HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

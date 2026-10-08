package com.richardsenger.piratesnships.combat.firearms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the attack-key trigger (FA1, docs/design.md §8 "Input"). The client sends a
 * {@link FirearmFirePayload} when the attack key is pressed with a gun to fire; {@link #pull} validates it against the
 * server's own view of the player (the gun in hand, firearms and {@code firearms.fire_on_attack} on, no loading
 * session, no cooldown, loaded) and fires through {@link FirearmService}: aimed when the player is in an aim session
 * with that gun (the steadied spread after {@code aim_steady_ticks}), from the hip otherwise (the gun's full spread).
 * An empty gun clicks. Rules in {@link FirearmTriggerRules}.
 */
public final class FirearmTrigger {

    /**
     * What a pull did.
     *
     * @param outcome the decision
     * @param hand    the hand of the gun, {@code null} without one
     * @param aimed   the shot was aimed (an aim session with that gun), not from the hip
     * @param spread  the spread the shot left with, in degrees (0 when it did not fire)
     * @param shot    fired or misfired, {@code null} when the trigger was not pulled
     */
    public record Result(FirearmTriggerRules.Outcome outcome, @Nullable InteractionHand hand, boolean aimed, double spread,
                         @Nullable FirearmService.Shot shot) {
        static Result refused(FirearmTriggerRules.Outcome outcome, @Nullable InteractionHand hand, boolean aimed) {
            return new Result(outcome, hand, aimed, 0.0, null);
        }
    }

    private FirearmTrigger() {
    }

    /**
     * The hand of the gun the attack key fires: the gun being used (aimed or loaded) in either hand; without a use,
     * a gun in the main hand. {@code null} while using anything else (vanilla ignores the attack key then too) or
     * without a gun in the main hand. Used by the client to decide whether to intercept, and by the server.
     */
    public static @Nullable InteractionHand gunHand(Player player) {
        if (player.isUsingItem()) {
            return player.getUseItem().getItem() instanceof FirearmItem ? player.getUsedItemHand() : null;
        }
        return player.getMainHandItem().getItem() instanceof FirearmItem ? InteractionHand.MAIN_HAND : null;
    }

    /** {@link FirearmFirePayload} handler (server main thread). */
    public static void onFirePayload(FirearmFirePayload payload, Player player) {
        if (player.level() instanceof ServerLevel level && player.isAlive() && !player.isSpectator()) {
            pull(level, player);
        }
    }

    /** Pulls the trigger of the player's gun with the aim state the server knows (the player's use session). */
    public static Result pull(ServerLevel level, Player player) {
        return pull(level, player, -1);
    }

    /**
     * Like {@link #pull(ServerLevel, Player)}, but an aim (if the player is in an aim session with the gun) counts as
     * held {@code aimHeldTicks}. For GameTests: a mock player is not ticked, so its use time never counts down.
     */
    public static Result pullAimedFor(ServerLevel level, Player player, int aimHeldTicks) {
        return pull(level, player, Math.max(0, aimHeldTicks));
    }

    private static Result pull(ServerLevel level, Player player, int aimHeldOverride) {
        InteractionHand hand = gunHand(player);
        if (hand == null) return Result.refused(FirearmTriggerRules.Outcome.NO_GUN, null, false);
        ItemStack gun = player.getItemInHand(hand);
        if (!(gun.getItem() instanceof FirearmItem item)) return Result.refused(FirearmTriggerRules.Outcome.NO_GUN, null, false);
        // gunHand returned the used hand while using, so a running use is a session with this gun
        boolean using = player.isUsingItem();
        int remaining = player.getUseItemRemainingTicks();
        boolean aimSession = using && FirearmRules.isAimSession(remaining);
        boolean loadingSession = using && !aimSession;
        FirearmTriggerRules.Outcome outcome = FirearmTriggerRules.onAttack(FirearmsConfig.ENABLED.get(),
                FirearmsConfig.FIRE_ON_ATTACK.get(), loadingSession, player.getCooldowns().isOnCooldown(item),
                FirearmContent.isLoaded(gun));
        switch (outcome) {
            case EMPTY -> {
                FirearmService.playEmpty(level, player, item.type().soundPitch());
                return Result.refused(outcome, hand, aimSession);
            }
            case FIRE -> {
                int held = aimHeldOverride >= 0 ? aimHeldOverride : FirearmRules.heldTicks(remaining);
                double spread = FirearmTriggerRules.shotSpread(item.type().spreadDegrees(), aimSession, held,
                        FirearmsConfig.AIM_STEADY_TICKS.get(), FirearmsConfig.AIMED_SPREAD_FACTOR.get());
                FirearmService.Shot shot = FirearmService.fireWithSpread(level, player, gun, item.kind(), spread);
                return new Result(outcome, hand, aimSession, spread, shot);
            }
            default -> {
                return Result.refused(outcome, hand, aimSession);
            }
        }
    }
}

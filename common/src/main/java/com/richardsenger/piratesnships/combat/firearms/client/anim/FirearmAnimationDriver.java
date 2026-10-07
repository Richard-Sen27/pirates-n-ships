package com.richardsenger.piratesnships.combat.firearms.client.anim;

import com.richardsenger.piratesnships.combat.firearms.FirearmItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Decides every client tick ({@code ClientEvents.CLIENT_TICK_END}) what each player in the level shows with a gun and
 * hands it to {@link FirearmAnimations}. No payload: the server syncs every player's use state to all clients
 * (vanilla {@code LivingEntity}: the living-entity flags carry "using" and the hand; on the client
 * {@code onSyncedDataUpdated} takes the use item from the synced hand item and sets the remaining use time from its
 * {@code getUseDuration}, which {@code FirearmItem} derives from the synced loaded state, and every tick counts it
 * down), so remote players' sessions are read the same way as the local player's.
 */
public final class FirearmAnimationDriver {

    private FirearmAnimationDriver() {
    }

    /** {@code CLIENT_TICK_END} listener. */
    public static void onClientTickEnd(Minecraft mc) {
        if (mc.level == null) return;
        FirearmAnimations animations = FirearmAnimations.get();
        for (AbstractClientPlayer player : mc.level.players()) {
            update(animations, player);
        }
    }

    private static void update(FirearmAnimations animations, Player player) {
        ItemStack stack = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (!(stack.getItem() instanceof FirearmItem gun)) {
            animations.update(player, null, false, 0);
            return;
        }
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        animations.update(player, FirearmAnimationMapping.forUse(gun.kind(), player.getUseItemRemainingTicks()),
                arm == HumanoidArm.LEFT, gun.type().reloadTicks());
    }
}

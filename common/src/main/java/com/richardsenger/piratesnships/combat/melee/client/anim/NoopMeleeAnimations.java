package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * No combat animations, only vanilla's hand swing when the local player attacks (the melee input cancels vanilla's
 * own swing). The local player's swing is sent to the server, which shows it to everyone else. The default until
 * milestone 8, and later the fallback when the animation library is missing or animations are turned off.
 */
public final class NoopMeleeAnimations implements MeleeAnimations {

    @Override
    public void predict(LocalPlayer player, MeleeAction action) {
        if (action == MeleeAction.SLASH || action == MeleeAction.THRUST) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void play(LivingEntity entity, Phase phase, @Nullable AttackKind attack, boolean riposte, int elapsedTicks, int durationTicks) {
    }

    @Override
    public void stop(LivingEntity entity) {
    }
}

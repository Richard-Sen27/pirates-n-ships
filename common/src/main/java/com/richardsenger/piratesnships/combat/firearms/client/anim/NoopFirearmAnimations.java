package com.richardsenger.piratesnships.combat.firearms.client.anim;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** No firearm animations: the gun stays in vanilla's held pose ({@code UseAnim.NONE}). Used without PAL. */
public final class NoopFirearmAnimations implements FirearmAnimations {

    @Override
    public void update(Player player, FirearmAnimationMapping.@Nullable Pose pose, boolean leftArm, int reloadTicks) {
    }
}

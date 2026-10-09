package com.richardsenger.piratesnships.fabric.mixin;

import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@code CommonEvents.PLAYER_TICK_END} at the end of {@code Player#tick}, on both logical sides, where NeoForge
 * fires {@code PlayerTickEvent.Post}.
 *
 * <p>Why a mixin: Fabric API has no player tick event (its tick events are per server, level and client only), and a
 * loop over the players at the end of the server tick would run at another time (players tick while their connection
 * is handled) and miss the client side.
 */
@Mixin(Player.class)
public abstract class MixinPlayer {

    @Inject(method = "tick", at = @At("TAIL"))
    private void pirates_n_ships$afterTick(CallbackInfo ci) {
        CommonEvents.PLAYER_TICK_END.invoker().onTick((Player) (Object) this);
    }
}

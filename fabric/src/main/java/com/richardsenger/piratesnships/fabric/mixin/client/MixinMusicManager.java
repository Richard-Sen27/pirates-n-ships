package com.richardsenger.piratesnships.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fires {@code ClientEvents.SELECT_MUSIC} where NeoForge's {@code MusicManager#tick} fires {@code SelectMusicEvent}:
 * around the situational music vanilla picks every client tick (FAB2, the sea music AU1).
 *
 * <p>Why a mixin: Fabric API has no music selection event. The first non-null listener result replaces vanilla's
 * choice, as the NeoForge forwarder does. The target is checked headlessly by {@code ClientMixinTargetsTest}.
 */
@Mixin(MusicManager.class)
public abstract class MixinMusicManager {

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/sounds/Music;"))
    private Music pirates_n_ships$selectMusic(Music vanillaChoice) {
        Music music = ClientEvents.SELECT_MUSIC.invoker().select(vanillaChoice);
        return music != null ? music : vanillaChoice;
    }
}

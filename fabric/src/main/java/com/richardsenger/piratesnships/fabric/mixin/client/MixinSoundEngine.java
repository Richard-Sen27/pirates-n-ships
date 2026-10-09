package com.richardsenger.piratesnships.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Fires {@code ClientEvents.SOUND_STREAM_STARTED} for a streamed sound on its channel, on the sound thread, after
 * vanilla set the channel's pitch and volume (FAB2, the sea music volume AU1).
 *
 * <p>Why a mixin: Fabric API has no sound-source event. NeoForge fires {@code PlayStreamingSourceEvent} inside the
 * lambda that attaches the stream and starts the channel; that lambda has no stable name to target. {@code play} hands
 * the channel exactly one setup task (pitch, volume, attenuation, position: the method's only
 * {@code ChannelHandle#execute} call); for a streamed sound the event runs right after that task, in the same sound
 * thread job. The difference: the stream is attached and started a moment later (as soon as it is opened), which
 * does not matter for a listener that sets the channel's volume. The target is checked headlessly by
 * {@code ClientMixinTargetsTest}.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void pirates_n_ships$streamStarted(ChannelAccess.ChannelHandle handle, Consumer<Channel> setup,
                                               Operation<Void> original, @Local(argsOnly = true) SoundInstance sound) {
        if (!sound.getSound().shouldStream()) {
            original.call(handle, setup);
            return;
        }
        original.call(handle, setup.andThen(channel -> ClientEvents.SOUND_STREAM_STARTED.invoker().onStarted(sound, channel)));
    }
}

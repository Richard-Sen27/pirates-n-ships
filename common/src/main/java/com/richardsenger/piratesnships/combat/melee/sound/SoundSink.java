package com.richardsenger.piratesnships.combat.melee.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/** Where {@link MeleeSoundPlayer} sends a sword sound. The default plays it in the world for every nearby player. */
@FunctionalInterface
public interface SoundSink {

    /** Plays the vanilla way: {@code level.playSound(null, …)}, so every nearby client hears it, the emitter included. */
    SoundSink WORLD = (emitter, cue, event, source, x, y, z, volume, pitch) ->
            emitter.level().playSound(null, x, y, z, event, source, volume, pitch);

    /**
     * @param emitter the entity the sound plays at
     * @param cue     the cue it comes from
     */
    void play(LivingEntity emitter, MeleeSoundRules.Cue cue, SoundEvent event, SoundSource source, double x, double y, double z,
              float volume, float pitch);
}

package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Melee animations, client only: the only thing the rest of the mod sees of the animation library
 * (docs/animation-libraries.md, "Code layout"). {@link NoopMeleeAnimations} is the default; at client setup
 * {@link MeleeAnimationsSetup} installs {@link PalMeleeAnimations} when the Player Animation Library is loaded.
 *
 * <p>Phase changes map to animations like this ({@code play} arguments):
 * <ul>
 *   <li>slash / thrust wind-up, active, recovery: {@code WINDUP / ACTIVE / RECOVERY} with {@code attack}</li>
 *   <li>riposte: any attack phase with {@code riposte = true}</li>
 *   <li>guard down: {@code GUARDING}; guard up: {@link #stop} (the server's {@code IDLE} arrives as {@code stop})</li>
 *   <li>parry: {@code PARRYING}; stagger: {@code STAGGERED}</li>
 * </ul>
 * Every {@code play} carries the time already spent in the phase and its length (0 = open-ended), so an animation
 * can start part-way and be stretched to the server's phase timings.
 */
public interface MeleeAnimations {

    /**
     * Local prediction: the local player's input was just sent to the server. Start the matching animation now
     * (wind-up for attacks, guard, parry); the server's answer arrives as {@link #play} only if it disagrees.
     */
    void predict(LocalPlayer player, MeleeAction action);

    /**
     * Server-confirmed phase of any living entity: remote players and NPCs on every change, the local player when
     * the server disagrees with the prediction (or after a phase the client can't predict, e.g. a stagger).
     */
    void play(LivingEntity entity, Phase phase, @Nullable AttackKind attack, boolean riposte, int elapsedTicks, int durationTicks);

    /** Back to no combat animation (idle, entity left the fight). */
    void stop(LivingEntity entity);

    /** The installed implementation (never {@code null}). */
    static MeleeAnimations get() {
        return Holder.current;
    }

    /** Installs the implementation (client setup). */
    static void install(MeleeAnimations animations) {
        Holder.current = animations;
    }

    /** Mutable slot behind {@link #get} (interfaces can't hold mutable fields). */
    final class Holder {
        private static volatile MeleeAnimations current = new NoopMeleeAnimations();

        private Holder() {
        }
    }
}

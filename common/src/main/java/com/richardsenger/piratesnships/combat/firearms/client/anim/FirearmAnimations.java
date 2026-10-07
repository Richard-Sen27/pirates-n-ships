package com.richardsenger.piratesnships.combat.firearms.client.anim;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Firearm animations of players, client only: the only thing the rest of the mod sees of the animation library.
 * {@link NoopFirearmAnimations} is the default (guns stay in vanilla's held pose); at client setup
 * {@link FirearmAnimationsSetup} installs {@link PalFirearmAnimations} when the Player Animation Library is loaded.
 *
 * <p>{@link FirearmAnimationDriver} calls {@link #update} every client tick for every player in the level, with the
 * pose {@link FirearmAnimationMapping#forUse} derives from the player's use state ({@code null} = not using a gun).
 */
public interface FirearmAnimations {

    /**
     * The current pose of {@code player}: keeps it playing, starts it when it changed or a new session began, stops
     * the layer on {@code null}. {@code leftArm}: the gun is in the player's left hand (the animation is mirrored).
     * {@code reloadTicks}: the gun's reload time, the length a reload animation is stretched to.
     */
    void update(Player player, FirearmAnimationMapping.@Nullable Pose pose, boolean leftArm, int reloadTicks);

    /** The installed implementation (never {@code null}). */
    static FirearmAnimations get() {
        return Holder.current;
    }

    /** Installs the implementation (client setup). */
    static void install(FirearmAnimations animations) {
        Holder.current = animations;
    }

    /** Mutable slot behind {@link #get} (interfaces can't hold mutable fields). */
    final class Holder {
        private static volatile FirearmAnimations current = new NoopFirearmAnimations();

        private Holder() {
        }
    }
}

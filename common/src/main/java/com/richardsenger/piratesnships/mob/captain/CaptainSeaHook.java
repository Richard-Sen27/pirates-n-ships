package com.richardsenger.piratesnships.mob.captain;

/**
 * What the captain's voyages (BOS2, {@code worldsim.captain}) need to hear from a {@link PirateCaptain} entity, without
 * the mob depending on the world simulation. The default does nothing (a captain who never puts to sea).
 */
public interface CaptainSeaHook {

    CaptainSeaHook NONE = new CaptainSeaHook() {
    };

    /**
     * Whether a captain discarded alive is only leaving the world for his voyage (his ship became a record again, or
     * his voyage ended with his ship): then he is stowed ({@link #stow}) and not lost.
     */
    default boolean stows(PirateCaptain captain) {
        return false;
    }

    /** Keeps the captain's state (health, equipment) for his next appearance; called before he is removed. */
    default void stow(PirateCaptain captain) {
    }

    /**
     * Every {@link PirateCaptain#SEA_CHECK_INTERVAL} ticks for every loaded captain: a copy in the wrong place goes
     * ({@link PirateCaptain#vanish}), a captain taken prisoner leaves his voyage.
     */
    default void check(PirateCaptain captain) {
    }
}

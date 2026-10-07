package com.richardsenger.piratesnships.combat.grapple;

/**
 * Pure rules for launching the grappling hook (GR1, docs/design.md §8.3): thrown by hand, shot from a crossbow held in
 * the other hand after a draw, or fired from an empty musket held in the other hand with one gunpowder. No world
 * access; tested in JUnit.
 */
public final class GrappleLaunch {

    /** Inaccuracy (vanilla {@code shoot} units) of a thrown hook, as before GR1. */
    public static final float THROW_INACCURACY = 1.0f;
    /** A crossbow and a musket rest the hook on a stock: steadier than a throw. */
    public static final float WEAPON_INACCURACY = 0.5f;

    private GrappleLaunch() {
    }

    /** What the player holds in the other hand. */
    public enum Weapon { NONE, CROSSBOW, MUSKET }

    /** How the hook leaves. */
    public enum Mode { THROW, CROSSBOW, MUSKET }

    /** The launch mode for the weapon in the other hand; a switched-off mode falls back to a throw. */
    public static Mode mode(Weapon otherHand, boolean crossbowEnabled, boolean musketEnabled) {
        return switch (otherHand) {
            case CROSSBOW -> crossbowEnabled ? Mode.CROSSBOW : Mode.THROW;
            case MUSKET -> musketEnabled ? Mode.MUSKET : Mode.THROW;
            case NONE -> Mode.THROW;
        };
    }

    /**
     * Start speed [blocks per tick]: {@code throwVelocity}, times {@code crossbowFactor} or {@code musketFactor} for a
     * weapon launch.
     */
    public static double speed(Mode mode, double throwVelocity, double crossbowFactor, double musketFactor) {
        return switch (mode) {
            case THROW -> throwVelocity;
            case CROSSBOW -> throwVelocity * crossbowFactor;
            case MUSKET -> throwVelocity * musketFactor;
        };
    }

    /**
     * Rope length [blocks] for a launch: the thrown rope is {@code throwRope} ({@code max_rope_length}); a weapon gets
     * its own length, but never less than the thrown one. Every launch speed carries the hook farther than the rope
     * (a throw would fly about 53 blocks on flat ground, a crossbow shot 112, a musket shot 205), so the rope length is
     * the effective range: without longer ropes all three would stop at the same distance.
     */
    public static double ropeLength(Mode mode, double throwRope, double crossbowRope, double musketRope) {
        return switch (mode) {
            case THROW -> throwRope;
            case CROSSBOW -> Math.max(throwRope, crossbowRope);
            case MUSKET -> Math.max(throwRope, musketRope);
        };
    }

    /** Inaccuracy of the launch (vanilla {@code shoot} units). */
    public static float inaccuracy(Mode mode) {
        return mode == Mode.THROW ? THROW_INACCURACY : WEAPON_INACCURACY;
    }

    /** The crossbow is drawn once the use key has been held {@code drawTicks}; letting go earlier cancels. */
    public static boolean drawn(int heldTicks, int drawTicks) {
        return heldTicks >= Math.max(0, drawTicks);
    }

    /** What the musket's powder charge needs. */
    public enum Powder {
        /** One gunpowder is taken when the shot leaves. */
        CONSUME,
        /** Creative mode, or {@code firearms.consume_gunpowder} off: nothing is taken. */
        FREE,
        /** No gunpowder: the musket clicks and the hook stays in hand. */
        MISSING
    }

    /** The powder rule of a musket launch (a lead shot is never needed, the hook is the shot). */
    public static Powder powder(boolean infiniteMaterials, int gunpowder, boolean consumeGunpowder) {
        if (infiniteMaterials || !consumeGunpowder) {
            return Powder.FREE;
        }
        return gunpowder > 0 ? Powder.CONSUME : Powder.MISSING;
    }

    /** Why a musket launch does not happen, or {@link #NONE}. Checked in this order. */
    public enum MusketRefusal {
        NONE,
        /** The musket holds a ball: firing the hook would waste or mix the load ("unload the musket first"). */
        LOADED,
        /** The musket's cooldown after a shot is running. */
        COOLDOWN,
        /** No gunpowder. */
        NO_POWDER
    }

    public static MusketRefusal musketRefusal(boolean musketLoaded, boolean onCooldown, Powder powder) {
        if (musketLoaded) {
            return MusketRefusal.LOADED;
        }
        if (onCooldown) {
            return MusketRefusal.COOLDOWN;
        }
        return powder == Powder.MISSING ? MusketRefusal.NO_POWDER : MusketRefusal.NONE;
    }
}

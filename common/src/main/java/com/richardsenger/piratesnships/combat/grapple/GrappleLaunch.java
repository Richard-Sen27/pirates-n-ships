package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for launching the grappling hook (GR3, GR4, docs/design.md §8.3 "Launching"). The hook is held in the off
 * hand and the musket, the only launcher (GR4 removed the crossbow), in the main hand ({@code
 * grapple.launch.offhand_required} off also takes the swapped hands). Holding use runs the musket's own loading
 * session, which moves the hook into it; the hook-loaded musket fires it like a shot. A hook without a musket in the
 * other hand is thrown by hand (G11). No world access; tested in JUnit.
 */
public final class GrappleLaunch {

    /** Inaccuracy (vanilla {@code shoot} units) of a thrown hook. */
    public static final float THROW_INACCURACY = 1.0f;
    /** A musket rests the hook on a stock: steadier than a throw. */
    public static final float WEAPON_INACCURACY = 0.5f;

    private GrappleLaunch() {
    }

    /** What a hand holds, as far as launching (and grabbing a rope, GR4) is concerned. */
    public enum Held { EMPTY, HOOK, MUSKET, OTHER }

    /** How the hook leaves. */
    public enum Mode { THROW, MUSKET }

    /** The launch mode of a launcher held as {@code launcher} (anything else throws). */
    public static Mode mode(Held launcher) {
        return launcher == Held.MUSKET ? Mode.MUSKET : Mode.THROW;
    }

    /**
     * The hand that holds the musket in a valid arrangement, or {@code null}: the hook in the off hand and the musket
     * (with {@code musketEnabled}) in the main hand, or (only with {@code offhandRequired} off) the swapped hands.
     */
    public static @Nullable InteractionHand launcherHand(Held main, Held off, boolean offhandRequired, boolean musketEnabled) {
        if (!musketEnabled) {
            return null;
        }
        if (off == Held.HOOK && main == Held.MUSKET) {
            return InteractionHand.MAIN_HAND;
        }
        if (!offhandRequired && main == Held.HOOK && off == Held.MUSKET) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** The other hand. */
    public static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** What using the hook item itself does. */
    public enum HookUse {
        /** No musket arrangement: the hook is thrown by hand (G11). */
        THROW,
        /** A musket in the other hand in a valid arrangement: its own session (load or fire) runs instead. */
        LAUNCHER
    }

    /** What using the hook from {@code hookHand} does with {@code main} and {@code off} held. */
    public static HookUse hookUse(InteractionHand hookHand, Held main, Held off, boolean offhandRequired, boolean musketEnabled) {
        InteractionHand launcher = launcherHand(main, off, offhandRequired, musketEnabled);
        return launcher != null && launcher == other(hookHand) ? HookUse.LAUNCHER : HookUse.THROW;
    }

    // ------------------------------------------------------------------ musket

    /** What the musket's powder charge needs. */
    public enum Powder {
        /** One gunpowder is taken when the load completes. */
        CONSUME,
        /** Creative mode, or {@code firearms.consume_gunpowder} off: nothing is taken. */
        FREE,
        /** No gunpowder: the musket clicks and the hook stays in hand. */
        MISSING
    }

    /** The powder rule of loading the hook into a musket (a lead shot is never needed, the hook is the shot). */
    public static Powder powder(boolean infiniteMaterials, int gunpowder, boolean consumeGunpowder) {
        if (infiniteMaterials || !consumeGunpowder) {
            return Powder.FREE;
        }
        return gunpowder > 0 ? Powder.CONSUME : Powder.MISSING;
    }

    /** Why the hook cannot be loaded into the musket, or {@link #NONE}. Checked in this order. */
    public enum MusketRefusal {
        NONE,
        /** The musket already holds a lead ball ("the musket is loaded with shot"): it aims with the ball instead. */
        LOADED_WITH_SHOT,
        /** The musket already holds a hook: it aims with it. */
        LOADED_WITH_HOOK,
        /** No gunpowder. */
        NO_POWDER
    }

    public static MusketRefusal musketRefusal(boolean loaded, boolean hookLoaded, Powder powder) {
        if (loaded) {
            return hookLoaded ? MusketRefusal.LOADED_WITH_HOOK : MusketRefusal.LOADED_WITH_SHOT;
        }
        return powder == Powder.MISSING ? MusketRefusal.NO_POWDER : MusketRefusal.NONE;
    }

    /** After a pull of the trigger: a misfire keeps the hook in the weapon, a shot sends it out. */
    public static boolean hookLeaves(boolean misfire) {
        return !misfire;
    }

    // ------------------------------------------------------------------ flight

    /** Start speed [blocks per tick]: {@code throwVelocity}, times {@code musketFactor} for a musket shot. */
    public static double speed(Mode mode, double throwVelocity, double musketFactor) {
        return mode == Mode.MUSKET ? throwVelocity * musketFactor : throwVelocity;
    }

    /**
     * Rope length [blocks] for a launch: the thrown rope is {@code throwRope} ({@code max_rope_length}); the musket
     * gets its own length, but never less than the thrown one. The default speeds carry a level launch farther than
     * its rope (measured in {@code GrappleLaunchGameTests}, GR4), so the rope length is the effective range.
     */
    public static double ropeLength(Mode mode, double throwRope, double musketRope) {
        return mode == Mode.MUSKET ? Math.max(throwRope, musketRope) : throwRope;
    }

    /**
     * Gravity of the flying hook as a multiple of {@code grapple.gravity}: {@code musketFactor} for a musket shot (a
     * fast, flat shot, GR4), 1 for a throw. Never negative.
     */
    public static double gravityFactor(Mode mode, double musketFactor) {
        return mode == Mode.MUSKET ? Math.max(0.0, musketFactor) : 1.0;
    }

    /** Inaccuracy of the launch (vanilla {@code shoot} units). */
    public static float inaccuracy(Mode mode) {
        return mode == Mode.THROW ? THROW_INACCURACY : WEAPON_INACCURACY;
    }
}

package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for launching the grappling hook (GR3, docs/design.md §8.3 "Launching"). The hook is held in the off hand
 * and the launcher (a crossbow or a musket) in the main hand ({@code grapple.launch.offhand_required} off also takes
 * the swapped hands). Holding use runs the launcher's own loading session, which moves the hook into the weapon;
 * a hook-loaded launcher fires it like a shot. A hook without a launcher in the other hand is thrown (G11). No world
 * access; tested in JUnit.
 */
public final class GrappleLaunch {

    /** Inaccuracy (vanilla {@code shoot} units) of a thrown hook. */
    public static final float THROW_INACCURACY = 1.0f;
    /** A crossbow and a musket rest the hook on a stock: steadier than a throw. */
    public static final float WEAPON_INACCURACY = 0.5f;

    private GrappleLaunch() {
    }

    /** What a hand holds, as far as launching is concerned. */
    public enum Held { EMPTY, HOOK, CROSSBOW, MUSKET, OTHER }

    /** How the hook leaves. */
    public enum Mode { THROW, CROSSBOW, MUSKET }

    /** The launch mode of a launcher held as {@code launcher} (anything else throws). */
    public static Mode mode(Held launcher) {
        return switch (launcher) {
            case CROSSBOW -> Mode.CROSSBOW;
            case MUSKET -> Mode.MUSKET;
            default -> Mode.THROW;
        };
    }

    static boolean isLauncher(Held held, boolean crossbowEnabled, boolean musketEnabled) {
        return held == Held.CROSSBOW && crossbowEnabled || held == Held.MUSKET && musketEnabled;
    }

    /**
     * The hand that holds the launcher in a valid arrangement, or {@code null}: the hook in the off hand and an enabled
     * launcher in the main hand, or (only with {@code offhandRequired} off) the swapped hands.
     */
    public static @Nullable InteractionHand launcherHand(Held main, Held off, boolean offhandRequired,
                                                        boolean crossbowEnabled, boolean musketEnabled) {
        if (off == Held.HOOK && isLauncher(main, crossbowEnabled, musketEnabled)) {
            return InteractionHand.MAIN_HAND;
        }
        if (!offhandRequired && main == Held.HOOK && isLauncher(off, crossbowEnabled, musketEnabled)) {
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
        /** No launcher arrangement: the hook is thrown by hand (G11). */
        THROW,
        /** A launcher in the other hand in a valid arrangement: its own session (load or fire) runs instead. */
        LAUNCHER
    }

    /** What using the hook from {@code hookHand} does with {@code main} and {@code off} held. */
    public static HookUse hookUse(InteractionHand hookHand, Held main, Held off, boolean offhandRequired,
                                  boolean crossbowEnabled, boolean musketEnabled) {
        InteractionHand launcher = launcherHand(main, off, offhandRequired, crossbowEnabled, musketEnabled);
        return launcher != null && launcher == other(hookHand) ? HookUse.LAUNCHER : HookUse.THROW;
    }

    // ------------------------------------------------------------------ crossbow

    /** What pressing use on a crossbow does in a hook arrangement. */
    public enum CrossbowUse {
        /** Empty: the vanilla draw starts and loads the hook when it completes. */
        DRAW,
        /** Holds a hook: it is shot at once (vanilla's charged crossbow fires on the press). */
        FIRE,
        /** Charged with arrows or fireworks: vanilla's own shot, the hook stays where it is. */
        VANILLA
    }

    public static CrossbowUse crossbowUse(boolean hookLoaded, boolean chargedWithOther) {
        if (hookLoaded) return CrossbowUse.FIRE;
        return chargedWithOther ? CrossbowUse.VANILLA : CrossbowUse.DRAW;
    }

    /** The draw is complete once use has been held for the crossbow's charge time (vanilla's, with Quick Charge). */
    public static boolean drawn(int heldTicks, int chargeTicks) {
        return heldTicks >= Math.max(0, chargeTicks);
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

    // ------------------------------------------------------------------ release

    /** What letting go of the use key does. */
    public enum Release {
        /** Nothing loaded yet and the load is not complete: cancelled, nothing taken. */
        CANCEL,
        /** The load completed during this hold: the hook stays loaded, nothing fires. */
        KEEP_LOADED,
        /** An aim with a loaded launcher: the hook leaves. */
        FIRE,
        /** An aim lowered by sneaking: nothing fires, the hook stays loaded. */
        LOWER
    }

    /**
     * The release of the launcher's session. {@code aimSession}: the launcher was hook-loaded when use was pressed
     * (the musket; the crossbow fires on the press and never aims); {@code loadedNow}: it holds the hook now;
     * {@code lowered}: the player sneaks with {@code firearms.aim.lower_on_sneak}.
     */
    public static Release release(Mode launcher, boolean aimSession, boolean loadedNow, boolean lowered,
                                  int heldTicks, int aimMinTicks) {
        if (aimSession && launcher == Mode.MUSKET) {
            if (lowered) return Release.LOWER;
            return heldTicks >= aimMinTicks ? Release.FIRE : Release.LOWER;
        }
        return loadedNow ? Release.KEEP_LOADED : Release.CANCEL;
    }

    /** After a pull of the trigger: a misfire keeps the hook in the weapon, a shot sends it out. */
    public static boolean hookLeaves(boolean misfire) {
        return !misfire;
    }

    // ------------------------------------------------------------------ flight

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
     * the effective range.
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
}

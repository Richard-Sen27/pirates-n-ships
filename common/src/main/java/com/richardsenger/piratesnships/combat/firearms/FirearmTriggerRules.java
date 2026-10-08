package com.richardsenger.piratesnships.combat.firearms;

/**
 * Pure rules of the firearm input (FA1, docs/design.md §8 "Input"): with {@code firearms.fire_on_attack} on, holding
 * use aims, the attack key pulls the trigger (aimed or from the hip) and letting go of use only lowers the gun. With
 * it off, letting go of an aim fires as before (P1) and the attack key is left to vanilla. No world access.
 */
public final class FirearmTriggerRules {

    private FirearmTriggerRules() {
    }

    /** What a pull of the trigger (the attack key) does. */
    public enum Outcome {
        /** The shot leaves (or misfires in the rain, decided later by {@link FirearmRules#misfires}). */
        FIRE,
        /** Firearms are off, or {@code fire_on_attack} is off: the attack key is not the trigger. */
        OFF,
        /** The gun is being loaded (a loading session runs): no shot. */
        RELOADING,
        /** The gun is still cooling down after the last shot or misfire. */
        COOLDOWN,
        /** The gun is not loaded: a dry click. */
        EMPTY,
        /** The player holds no gun to fire (not returned by {@link #onAttack}; the server's own answer). */
        NO_GUN
    }

    /**
     * The outcome of the attack key on a gun. Checked in this order: the toggles, a running loading session, the
     * cooldown, the loaded state.
     */
    public static Outcome onAttack(boolean enabled, boolean fireOnAttack, boolean loadingSession, boolean onCooldown,
                                   boolean loaded) {
        if (!enabled || !fireOnAttack) return Outcome.OFF;
        if (loadingSession) return Outcome.RELOADING;
        if (onCooldown) return Outcome.COOLDOWN;
        if (!loaded) return Outcome.EMPTY;
        return Outcome.FIRE;
    }

    /**
     * Whether the client takes the attack key away from vanilla (no swing, no melee hit, no block breaking): firearms
     * and {@code fire_on_attack} are on and the player has a gun to fire ({@code hasGun}).
     */
    public static boolean interceptsAttack(boolean enabled, boolean fireOnAttack, boolean hasGun) {
        return enabled && fireOnAttack && hasGun;
    }

    /**
     * The spread of a shot: from the hip ({@code aiming} false) the gun's full spread; aimed, the spread of
     * {@link FirearmRules#aimedSpread} for an aim held {@code heldTicks} (narrowed once it is steady).
     */
    public static double shotSpread(double spreadDegrees, boolean aiming, int heldTicks, int steadyTicks, double aimedFactor) {
        if (!aiming) return spreadDegrees;
        return FirearmRules.aimedSpread(spreadDegrees, heldTicks, steadyTicks, aimedFactor);
    }

    /**
     * Whether letting go of an aim held {@code heldTicks} fires: never with {@code fire_on_attack} on (letting go only
     * lowers the gun), otherwise after the minimum hold ({@link FirearmRules#firesOnRelease}).
     */
    public static boolean releaseFires(boolean fireOnAttack, int heldTicks, int minTicks) {
        return !fireOnAttack && FirearmRules.firesOnRelease(heldTicks, minTicks);
    }
}

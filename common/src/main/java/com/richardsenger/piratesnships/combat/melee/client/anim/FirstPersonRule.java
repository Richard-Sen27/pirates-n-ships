package com.richardsenger.piratesnships.combat.melee.client.anim;

import java.util.List;
import java.util.function.Predicate;

/**
 * Whether melee animations replace the vanilla first-person hand (PAL's {@code THIRD_PERSON_MODEL} mode: the animated
 * arm and sword of the third-person model, drawn in first person). Client config {@code melee_animations.first_person}.
 * Pure: the loaded-mod check is passed in.
 */
public final class FirstPersonRule {

    /** Camera mods that already draw the player's body in first person; {@link Mode#AUTO} stays out of their way. */
    public static final List<String> BODY_CAMERA_MODS = List.of("firstperson", "realcamera");

    /** Config values of {@code first_person}. */
    public enum Mode {
        /** On, unless a camera mod that draws the body is loaded ({@link #BODY_CAMERA_MODS}). */
        AUTO,
        ON,
        /** Vanilla first-person hand (with vanilla's swing), animations in third person only. */
        OFF
    }

    private FirstPersonRule() {
    }

    public static boolean animateFirstPerson(Mode mode, Predicate<String> isModLoaded) {
        return switch (mode) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> BODY_CAMERA_MODS.stream().noneMatch(isModLoaded);
        };
    }
}

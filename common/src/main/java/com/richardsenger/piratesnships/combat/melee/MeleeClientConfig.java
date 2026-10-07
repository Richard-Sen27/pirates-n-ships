package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.client.MeleeInputClassifier;
import com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config sections {@code melee_input} (how mouse presses become actions), {@code melee_hud} (the stamina
 * bar) and {@code melee_animations} (sword animations through the Player Animation Library). Declared on both sides from {@code MeleeModule.registerConfig()} so datagen and the config screen see them;
 * only the client reads them.
 */
public final class MeleeClientConfig {

    private static final MeleeInputClassifier.Thresholds D = MeleeInputClassifier.Thresholds.DEFAULTS;

    private static final ConfigSection INPUT = ModConfigs.client("melee_input", "Sword input timing (skill-based combat)");

    public static final ConfigValue<Integer> HOLD_TO_THRUST = INPUT.intRange("hold_to_thrust_ticks", D.holdToThrustTicks(), 1, 40,
            "Attack held at least this many ticks (20 per second), then released = thrust; released sooner = slash");
    public static final ConfigValue<Integer> PARRY_TAP = INPUT.intRange("parry_tap_ticks", D.parryTapTicks(), 1, 20,
            "Use key released before it was held this many ticks = parry (after a short guard); held longer = guard only");

    private static final ConfigSection HUD = ModConfigs.client("melee_hud", "Stamina bar shown while holding a skill-based sword");

    public static final ConfigValue<Boolean> HUD_ENABLED = HUD.bool("enabled", true,
            "Show the stamina bar above the hotbar while holding a skill-based sword");
    public static final ConfigValue<Double> HUD_SCALE = HUD.doubleRange("scale", 1.0, 0.25, 4.0,
            "Size of the stamina bar");
    public static final ConfigValue<Integer> HUD_X_OFFSET = HUD.intRange("x_offset", 0, -2000, 2000,
            "Horizontal shift of the stamina bar from the screen center, in GUI pixels (positive = right)");
    public static final ConfigValue<Integer> HUD_Y_OFFSET = HUD.intRange("y_offset", 0, -2000, 2000,
            "Vertical shift of the stamina bar from its place above the health and food rows, in GUI pixels (positive = down)");

    private static final ConfigSection ANIM = ModConfigs.client("melee_animations", "Sword animations of players (Player Animation Library)");

    public static final ConfigValue<Boolean> ANIMATIONS_ENABLED = ANIM.bool("enabled", true,
            "Animate players' sword attacks, guard, parry and stagger. Off = only vanilla's hand swing");
    public static final ConfigValue<FirstPersonRule.Mode> ANIMATIONS_FIRST_PERSON = ANIM.enumValue("first_person", FirstPersonRule.Mode.AUTO,
            "Show the sword animations in first person (animated arm and sword instead of the vanilla hand). "
                    + "AUTO = on, unless a camera mod that draws the body is installed (First-person Model, Real Camera)");
    public static final ConfigValue<Integer> ANIMATIONS_LAYER_PRIORITY = ANIM.intRange("layer_priority", 1500, 0, 100000,
            "Priority of the sword animation layer among other mods' player animations (higher = drawn over them; "
                    + "emotes typically use 1000). Applied at game start");

    private MeleeClientConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static MeleeInputClassifier.Thresholds thresholds() {
        return new MeleeInputClassifier.Thresholds(HOLD_TO_THRUST.get(), PARRY_TAP.get());
    }
}

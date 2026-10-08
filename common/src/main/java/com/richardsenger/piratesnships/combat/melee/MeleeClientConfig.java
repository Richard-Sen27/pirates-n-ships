package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.client.MeleeInputClassifier;
import com.richardsenger.piratesnships.combat.melee.client.StaminaHudLayout;
import com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config sections {@code melee_input} (how mouse presses become actions), {@code melee_hud} (the stamina
 * bar), {@code melee_animations} (sword animations through the Player Animation Library) and
 * {@code firearm_animations} (pistol and musket aim and reload animations, same library). Declared on both sides from {@code MeleeModule.registerConfig()} so datagen and the config screen see them;
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
            "Show the stamina bar near the hotbar while holding a skill-based sword");
    public static final ConfigValue<StaminaHudLayout.Position> HUD_POSITION = HUD.enumValue("position", StaminaHudLayout.Position.ABOVE_HOTBAR_TIGHT,
            "Where the stamina bar sits. ABOVE_HOTBAR_TIGHT = 2 px above the hotbar in creative, over the right half just above "
                    + "the food row in survival (the experience bar takes the space above the hotbar); LEFT_OF_HOTBAR = an upright bar "
                    + "left of the hotbar and offhand slot, aligned to the hotbar's bottom");
    public static final ConfigValue<Double> HUD_SCALE = HUD.doubleRange("scale", 1.0, 0.25, 4.0,
            "Size of the stamina bar");
    public static final ConfigValue<Integer> HUD_X_OFFSET = HUD.intRange("x_offset", 0, -2000, 2000,
            "Horizontal shift of the stamina bar from its position, in GUI pixels (positive = right)");
    public static final ConfigValue<Integer> HUD_Y_OFFSET = HUD.intRange("y_offset", 0, -2000, 2000,
            "Vertical shift of the stamina bar from its position, in GUI pixels (positive = down)");
    public static final ConfigValue<Double> HUD_OPACITY = HUD.doubleRange("opacity", 1.0, 0.1, 1.0,
            "Opacity of the visible stamina bar (1 = solid)");
    public static final ConfigValue<Boolean> HUD_FADE = HUD.bool("fade_when_full", true,
            "Fade the stamina bar out while stamina is full and you are not fighting; it comes back at once when stamina "
                    + "drops, you guard, attack, parry, are staggered or get hit");
    public static final ConfigValue<Integer> HUD_FADE_DELAY = HUD.intRange("fade_delay_ticks", 20, 0, 1200,
            "Ticks (20 per second) the stamina bar stays after stamina is full before it starts to fade");
    public static final ConfigValue<Integer> HUD_FADE_TICKS = HUD.intRange("fade_ticks", 10, 1, 200,
            "Ticks the stamina bar takes to fade from visible to transparent");

    private static final ConfigSection ANIM = ModConfigs.client("melee_animations", "Sword animations of players (Player Animation Library)");

    public static final ConfigValue<Boolean> ANIMATIONS_ENABLED = ANIM.bool("enabled", true,
            "Animate players' sword attacks, guard, parry and stagger. Off = only vanilla's hand swing");
    public static final ConfigValue<FirstPersonRule.Mode> ANIMATIONS_FIRST_PERSON = ANIM.enumValue("first_person", FirstPersonRule.Mode.AUTO,
            "Show the sword animations in first person (animated arm and sword instead of the vanilla hand). "
                    + "AUTO = on, unless a camera mod that draws the body is installed (First-person Model, Real Camera)");
    public static final ConfigValue<Integer> ANIMATIONS_LAYER_PRIORITY = ANIM.intRange("layer_priority", 1500, 0, 100000,
            "Priority of the sword animation layer among other mods' player animations (higher = drawn over them; "
                    + "emotes typically use 1000). Applied at game start");
    public static final ConfigValue<Integer> ANIMATIONS_FADE_TICKS = ANIM.intRange("fade_ticks", 4, 0, 20,
            "Ticks (20 per second) a sword animation takes to blend in when it does not start from the pose on screen "
                    + "(an attack cut short by a parry or a hit, a riposte, a parry from rest, going back to idle mid-swing). "
                    + "Cosmetic only: hit timing is unchanged. 0 = snap");
    public static final ConfigValue<Boolean> ANIMATIONS_KEEP_CROUCH = ANIM.bool("keep_crouch", true,
            "Keep the crouch while a sneaking player's sword animation plays (lowered, leaning upper body). "
                    + "Off = the sword animation shows a standing upper body on crouched legs");

    // Declared here (not in FirearmsConfig) because this class is already loaded on both sides by MeleeModule; the
    // firearm layer shares first_person and sits 100 below layer_priority (FirearmAnimationsSetup).
    private static final ConfigSection FIREARM_ANIM = ModConfigs.client("firearm_animations", "Pistol and musket animations of players (Player Animation Library)");

    public static final ConfigValue<Boolean> FIREARM_ANIMATIONS_ENABLED = FIREARM_ANIM.bool("enabled", true,
            "Animate players aiming and reloading pistols and muskets (first person follows melee_animations.first_person). "
                    + "Off = guns stay in the normal held pose");

    private MeleeClientConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static MeleeInputClassifier.Thresholds thresholds() {
        return new MeleeInputClassifier.Thresholds(HOLD_TO_THRUST.get(), PARRY_TAP.get());
    }
}

package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code grapple} (docs/design.md §8.3, §8.4, §17 "Combat"). Forces are absolute, in
 * kpg·m/s² (Sable's mass unit is the kpg, one block is one metre): the same pull moves a light sloop faster than a
 * heavy brigantine.
 */
public final class GrappleConfig {

    private static final ConfigSection S = ModConfigs.server("grapple", "Grappling hook: throwing, latching onto ships, hauling them together");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Grappling hooks can be thrown. Off = the item does nothing and hooks already out are released");
    public static final ConfigValue<Double> THROW_VELOCITY = S.doubleRange("throw_velocity", 1.5, 0.1, 5.0,
            "Start speed of a thrown hook in blocks per tick (a snowball: 1.5)");
    public static final ConfigValue<Double> GRAVITY = S.doubleRange("gravity", 0.03, 0.0, 1.0,
            "Downward acceleration of a flying hook in blocks per tick squared");
    public static final ConfigValue<Double> MAX_ROPE_LENGTH = S.doubleRange("max_rope_length", 24.0, 2.0, 128.0,
            "Rope length in blocks: a flying hook stops there, and a thrower farther than this from a latched hook snaps the rope");
    public static final ConfigValue<Double> HAUL_FORCE = S.doubleRange("haul_force", 120.0, 0.0, 100000.0,
            "Pull of a taut rope between two ships in kpg*m/s^2, on each ship in opposite directions "
                    + "(about 2.4 m/s^2 on a small 50 kpg hull; 0 = ships are not hauled)");
    public static final ConfigValue<Double> ROPE_DAMPING = S.doubleRange("rope_damping", 40.0, 0.0, 100000.0,
            "Damping of the rope in kpg/s: less pull the faster the ends approach each other, so hauled ships "
                    + "close at about haul_force / rope_damping m/s instead of crashing");
    public static final ConfigValue<Double> HOLD_DISTANCE = S.doubleRange("hold_distance", 1.5, 0.0, 32.0,
            "Horizontal distance in blocks between the rope's two ends (the thrower ship's nearest block and the hook) "
                    + "at which hauling stops: the hulls are side by side");
    public static final ConfigValue<Double> HOLD_SLACK = S.doubleRange("hold_slack", 0.5, 0.0, 8.0,
            "Dead band in blocks beyond hold_distance in which the rope holds without pulling (hulls that touch or "
                    + "collide before the rope's ends reach hold_distance still count as alongside)");
    public static final ConfigValue<Double> SHORE_HAUL_FORCE = S.doubleRange("shore_haul_force", 30.0, 0.0, 100000.0,
            "Pull on a hooked ship toward a thrower who is not on a ship, in kpg*m/s^2 (0 = no pulling from land)");
    public static final ConfigValue<Double> ENTITY_DAMAGE = S.doubleRange("entity_damage", 2.0, 0.0, 100.0,
            "Damage of a hook hitting an entity (it does not latch onto entities)");
    public static final ConfigValue<Boolean> ROPE_BREAKS_LOSE_HOOK = S.bool("rope_breaks_lose_hook", false,
            "A snapped rope loses the hook (dropped where it hung). Off = the hook returns to the thrower");
    public static final ConfigValue<Integer> RETRACT_TICKS = S.intRange("retract_ticks", 40, 0, 1200,
            "Ticks a hook that missed (land, water, an entity, the thrower's own ship) lies there before the rope pulls it back");

    private GrappleConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Distance up to which the rope holds without pulling: {@code hold_distance + hold_slack}. */
    public static double holdLength() {
        return GrappleRules.holdLength(HOLD_DISTANCE.get(), HOLD_SLACK.get());
    }
}

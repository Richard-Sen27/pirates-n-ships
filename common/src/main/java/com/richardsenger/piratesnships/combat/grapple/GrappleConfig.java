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

    public static final ConfigValue<Boolean> RINGS_ENABLED = S.bool("rings_enabled", true,
            "Mooring rings catch hooks passing close by, hold them harder, and take the rope's near end when tied off. "
                    + "Off = a ring is a plain ship block");
    public static final ConfigValue<Double> RING_CATCH_RADIUS = S.doubleRange("ring_catch_radius", 1.0, 0.0, 4.0,
            "A flying hook passing this close [blocks] to a mooring ring on another ship latches onto the ring (0 = only a direct hit)");
    public static final ConfigValue<Double> RING_HOLD_MULTIPLIER = S.doubleRange("ring_hold_multiplier", 2.0, 1.0, 8.0,
            "A hook latched on a mooring ring snaps only when the rope's ends are this many times its length apart");

    private static final ConfigSection LAUNCH = S.section("launch",
            "Launching the hook with a crossbow or a musket held in the other hand instead of throwing it");
    public static final ConfigValue<Boolean> CROSSBOW_ENABLED = LAUNCH.bool("crossbow_enabled", true,
            "A crossbow in the other hand draws and shoots the hook. Off = the hook is thrown");
    public static final ConfigValue<Double> CROSSBOW_SPEED = LAUNCH.doubleRange("crossbow_speed", 1.6, 0.1, 10.0,
            "Start speed of a hook shot from a crossbow, as a multiple of throw_velocity");
    public static final ConfigValue<Integer> CROSSBOW_DRAW_TICKS = LAUNCH.intRange("crossbow_draw_ticks", 25, 0, 200,
            "Ticks the use key must be held to draw the crossbow; letting go after that shoots, before it cancels (a crossbow charge: 25)");
    public static final ConfigValue<Double> CROSSBOW_ROPE_LENGTH = LAUNCH.doubleRange("crossbow_rope_length", 36.0, 2.0, 128.0,
            "Rope length in blocks of a hook shot from a crossbow (never shorter than max_rope_length)");
    public static final ConfigValue<Boolean> MUSKET_ENABLED = LAUNCH.bool("musket_enabled", true,
            "An empty musket in the other hand fires the hook with one gunpowder. Off = the hook is thrown");
    public static final ConfigValue<Double> MUSKET_SPEED = LAUNCH.doubleRange("musket_speed", 2.4, 0.1, 10.0,
            "Start speed of a hook fired from a musket, as a multiple of throw_velocity");
    public static final ConfigValue<Double> MUSKET_ROPE_LENGTH = LAUNCH.doubleRange("musket_rope_length", 48.0, 2.0, 128.0,
            "Rope length in blocks of a hook fired from a musket (never shorter than max_rope_length)");

    private static final ConfigSection SLIDE = S.section("slide",
            "Sliding along a latched rope (e.g. from the crow's nest down to the other ship): use the rope while looking at it");
    public static final ConfigValue<Boolean> SLIDE_ENABLED = SLIDE.bool("enabled", true,
            "Players can hang on a latched grappling rope and slide down it. Off = using the rope does nothing and riders drop off");
    public static final ConfigValue<Double> BOARD_REACH = SLIDE.doubleRange("board_reach", 2.5, 0.5, 8.0,
            "How far [blocks] from the eyes a player can grab the rope");
    public static final ConfigValue<Double> BOARD_PICK_RADIUS = SLIDE.doubleRange("board_pick_radius", 0.6, 0.1, 2.0,
            "How close [blocks] the look ray must pass the rope to grab it");
    public static final ConfigValue<Double> SLIDE_SPEED = SLIDE.doubleRange("slide_speed", 0.35, 0.01, 2.0,
            "Start speed of the slide in blocks per tick, toward the rope's lower end");
    public static final ConfigValue<Double> SLIDE_GRAVITY = SLIDE.doubleRange("slide_gravity", 0.02, 0.0, 0.5,
            "Speed gained per tick, in blocks per tick, times the rope's slope (height difference per block of rope): steeper is faster");
    public static final ConfigValue<Double> SLIDE_MIN_SPEED = SLIDE.doubleRange("slide_min_speed", 0.1, 0.01, 2.0,
            "Crawling speed in blocks per tick along a level rope, toward the hook");
    public static final ConfigValue<Double> SLIDE_MAX_SPEED = SLIDE.doubleRange("slide_max_speed", 0.8, 0.01, 4.0,
            "Top speed of the slide in blocks per tick");
    public static final ConfigValue<Double> HANG_OFFSET = SLIDE.doubleRange("hang_offset", 2.0, 0.0, 4.0,
            "How far [blocks] the hanging player's feet are below the rope (2: both hands raised to the rope)");
    public static final ConfigValue<Double> DISMOUNT_DISTANCE = SLIDE.doubleRange("dismount_distance", 1.0, 0.1, 8.0,
            "Distance [blocks] from the end of the rope at which the rider lets go and lands on that spot");

    private GrappleConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Start speed [blocks per tick] of a hook launched in {@code mode}. */
    public static double speed(GrappleLaunch.Mode mode) {
        return GrappleLaunch.speed(mode, THROW_VELOCITY.get(), CROSSBOW_SPEED.get(), MUSKET_SPEED.get());
    }

    /** Rope length [blocks] of a hook launched in {@code mode}. */
    public static double ropeLength(GrappleLaunch.Mode mode) {
        return GrappleLaunch.ropeLength(mode, MAX_ROPE_LENGTH.get(), CROSSBOW_ROPE_LENGTH.get(), MUSKET_ROPE_LENGTH.get());
    }

    /** The slide's speeds and distances ({@link RopeSlide}). */
    public static RopeSlide.Params slideParams() {
        return new RopeSlide.Params(SLIDE_SPEED.get(), SLIDE_GRAVITY.get(), SLIDE_MIN_SPEED.get(), SLIDE_MAX_SPEED.get(),
                DISMOUNT_DISTANCE.get());
    }

    /** Distance up to which the rope holds without pulling: {@code hold_distance + hold_slack}. */
    public static double holdLength() {
        return GrappleRules.holdLength(HOLD_DISTANCE.get(), HOLD_SLACK.get());
    }
}

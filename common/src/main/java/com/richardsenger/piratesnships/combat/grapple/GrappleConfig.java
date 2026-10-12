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

    private static final ConfigSection S = ModConfigs.server("grapple", "Grappling hook: throwing or firing it, latching onto ships and blocks, hauling ships");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Grappling hooks can be thrown. Off = the item does nothing and hooks already out are released");
    public static final ConfigValue<Double> THROW_VELOCITY = S.doubleRange("throw_velocity", 3.0, 0.1, 5.0,
            "Base speed of a launched hook in blocks per tick: the musket fires it at launch.musket_speed times this. "
                    + "A hook thrown by hand flies at hand_throw_velocity instead (GR7)");
    public static final ConfigValue<Double> HAND_THROW_VELOCITY = S.doubleRange("hand_throw_velocity", 0.7, 0.1, 5.0,
            "Start speed of a hook thrown by hand in blocks per tick (GR7). With the default gravity a level throw from "
                    + "standing height lands about 9.2 blocks away and the farthest lob (40 degrees up) about 20 blocks");
    public static final ConfigValue<Double> GRAVITY = S.doubleRange("gravity", 0.02, 0.0, 1.0,
            "Downward acceleration of a flying hook in blocks per tick squared (a musket shot uses launch.musket_gravity_factor times this)");
    public static final ConfigValue<Double> MAX_ROPE_LENGTH = S.doubleRange("max_rope_length", 32.0, 2.0, 128.0,
            "Rope length in blocks of a thrown hook: a flying hook stops there, and a thrower farther than this from a "
                    + "latched hook snaps the rope");
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
            "Ticks a hook that missed (water, an entity, a block it slips off, or a surface its latch toggle forbids) lies there "
                    + "before the rope pulls it back");
    public static final ConfigValue<Boolean> LATCH_WORLD_BLOCKS = S.bool("latch_world_blocks", true,
            "A hook latches on solid world blocks (land, cliffs, a quay). Thrown from a ship, the rope hauls that ship "
                    + "toward the block (a kedge line, haul_force); thrown from land it is a fixed line to slide along. "
                    + "Off = a hook on a world block drops and is pulled back");
    public static final ConfigValue<Boolean> LATCH_OWN_SHIP = S.bool("latch_own_ship", true,
            "A hook latches on the thrower's own ship without hauling it (a line between two points of the ship to slide "
                    + "along, e.g. from the mast top to the deck). Off = it drops and is pulled back");

    public static final ConfigValue<Boolean> HAULING = S.bool("hauling", true,
            "Hauling by hand (GR5): holding sneak with the rope's near end in hand and the hook in a ship freezes the rope's "
                    + "length, and walking away pulls the hooked ship toward the player. A hand-held rope from land no longer "
                    + "drags the hooked ship by itself (shore_haul_force then applies only to ropes tied off on land). "
                    + "Off = sneaking does nothing and shore_haul_force pulls as before");
    public static final ConfigValue<Double> HAUL_STIFFNESS = S.doubleRange("haul_stiffness", 60.0, 0.0, 100000.0,
            "Spring of a frozen rope in kpg/s^2: the pull on the hooked ship per block the rope's ends are beyond the frozen "
                    + "length (60: two blocks pull with 120 kpg*m/s^2, about 2.4 m/s^2 on a small 50 kpg hull)");
    public static final ConfigValue<Double> HAUL_DAMPING = S.doubleRange("haul_damping", 30.0, 0.0, 100000.0,
            "Damping of a frozen rope in kpg/s: less pull the faster the hooked ship closes in, more while the player walks away");
    public static final ConfigValue<Double> HAUL_MAX_FORCE = S.doubleRange("haul_max_force", 200.0, 0.0, 100000.0,
            "Strongest pull of a frozen rope in kpg*m/s^2. Beyond it the rope slips through the hand (its frozen length "
                    + "grows) instead of snapping or stopping the player");
    public static final ConfigValue<Double> HAUL_PLAYER_PULL = S.doubleRange("haul_player_pull", 0.08, 0.0, 1.0,
            "Velocity in blocks per tick added every tick to an airborne or swimming player on a frozen rope at full tension, "
                    + "toward the hook (players standing on the ground or a deck feel nothing)");
    public static final ConfigValue<Double> CLIMB_MIN_RISE = S.doubleRange("climb_min_rise", 2.0, 0.0, 16.0,
            "Using a rope still in your hand pulls you hand over hand to the hook only as a climb (GR6): when the hook is at "
                    + "least this many blocks above your feet (up a mast, a cliff or a hull side from the water). With the "
                    + "hook lower (level, below, across a gap) the use does nothing but a hint to tie the rope off on a cleat. "
                    + "0 = every use pulls you to the hook (GR5)");

    public static final ConfigValue<Integer> PULL_COOLDOWN_TICKS = S.intRange("pull_cooldown_ticks", 10, 0, 200,
            "Ticks after a pull on your own rope (a right click with the rope in hand, GR7) before the next pull takes: "
                    + "clicks in between are ignored and the hook item shows the cooldown in the hotbar. Holding the button "
                    + "pulls once per cooldown");
    public static final ConfigValue<Double> ROPE_CLIMB_STEP = S.doubleRange("rope_climb_step", 1.5, 0.1, 8.0,
            "How much one pull shortens your own hand-held rope [blocks] (GR7): the rope draws you this far toward the hook");
    public static final ConfigValue<Double> ROPE_CLIMB_SPEED = S.doubleRange("rope_climb_speed", 0.4, 0.05, 2.0,
            "Fastest speed in blocks per tick at which a shortened rope draws you up to its new length (GR7)");
    public static final ConfigValue<Boolean> SWING = S.bool("swing", true,
            "A hand-held rope holds you like a liana (GR7): off the ground with the latched hook above your hand you get no "
                    + "farther from the hook than the rope's length, gravity swings you below it and walking pushes the "
                    + "swing; each use pulls you up a step (rope_climb_step). Holding another item (or sneaking) lets go. Off = "
                    + "the rope never holds you, and using your own rope does not climb it");
    public static final ConfigValue<Double> SWING_PUSH = S.doubleRange("swing_push", 0.015, 0.0, 0.2,
            "Speed in blocks per tick that walking input adds every tick along the swing while you hang on the rope (GR7)");
    public static final ConfigValue<Double> SWING_DAMPING = S.doubleRange("swing_damping", 0.03, 0.0, 1.0,
            "Share of the swing's speed lost every tick while you hang on the rope (GR7): 0.03 lets a swing die out in "
                    + "about 5 seconds, 0 swings on for a long time");

    public static final ConfigValue<Boolean> RINGS_ENABLED = S.bool("rings_enabled", true,
            "Mooring rings catch hooks passing close by, hold them harder, and take the rope's near end when tied off. "
                    + "Off = a ring is a plain ship block");
    public static final ConfigValue<Boolean> CLEATS_ENABLED = S.bool("cleats_enabled", true,
            "Cleats act like mooring rings (with the ring values below): they catch hooks passing close by, hold them harder, "
                    + "and take the rope's near end when tied off. Needs rings_enabled. Off = only rings do");
    public static final ConfigValue<Double> RING_CATCH_RADIUS = S.doubleRange("ring_catch_radius", 1.0, 0.0, 4.0,
            "A flying hook passing this close [blocks] to a mooring ring (or cleat) on another ship latches onto it (0 = only a direct hit)");
    public static final ConfigValue<Double> RING_HOLD_MULTIPLIER = S.doubleRange("ring_hold_multiplier", 2.0, 1.0, 8.0,
            "A hook latched on a mooring ring (or cleat) snaps only when the rope's ends are this many times its length apart");
    public static final ConfigValue<Boolean> LATCHES_HOLD = S.bool("latches_hold", true,
            "A hook latched on a railing or railing gate (the block tag pirates_n_ships:grapple_latches) holds like one on a "
                    + "mooring ring: it snaps only at ring_hold_multiplier times the rope's length. Off = a railing holds like any block");
    public static final ConfigValue<Double> RAILING_BREAK_FORCE = S.doubleRange("railing_break_force", 180.0, 0.0, 100000.0,
            "Rope pull in kpg*m/s^2 above which a hauled hook tears out the railing (or gate) it holds and lets go: a ship-to-ship "
                    + "haul pulls at most haul_force (120), a hand haul up to haul_max_force (200), so by default only a hard haul "
                    + "by hand breaks a railing. 0 = railings never break");

    private static final ConfigSection LAUNCH = S.section("launch",
            "Loading the hook into a musket (hook in the off hand, musket in the main hand) and firing it");
    public static final ConfigValue<Boolean> OFFHAND_REQUIRED = LAUNCH.bool("offhand_required", true,
            "The hook must be in the off hand and the musket in the main hand. Off = the swapped hands work too (hook in "
                    + "the main hand, musket in the off hand, e.g. for left-handed players)");
    public static final ConfigValue<Boolean> MUSKET_ENABLED = LAUNCH.bool("musket_enabled", true,
            "A musket next to the hook loads it (the musket's reload time, one gunpowder) and fires it like a shot "
                    + "(cooldown, recoil, rain misfire). Off = the hook is thrown");
    public static final ConfigValue<Double> MUSKET_SPEED = LAUNCH.doubleRange("musket_speed", 1.6, 0.1, 10.0,
            "Start speed of a hook fired from a musket, as a multiple of throw_velocity. With the default gravity factor "
                    + "a level shot from standing height would land about 83.4 blocks away (measured), past the 64-block rope");
    public static final ConfigValue<Double> MUSKET_GRAVITY_FACTOR = LAUNCH.doubleRange("musket_gravity_factor", 0.5, 0.0, 4.0,
            "Gravity of a hook fired from a musket as a multiple of grapple.gravity: a flatter shot than a throw");
    public static final ConfigValue<Double> MUSKET_ROPE_LENGTH = LAUNCH.doubleRange("musket_rope_length", 64.0, 2.0, 128.0,
            "Rope length in blocks of a hook fired from a musket (never shorter than max_rope_length)");

    private static final ConfigSection SLIDE = S.section("slide",
            "Sliding along a latched rope tied off on a cleat or mooring ring (e.g. from the crow's nest down to the other ship): "
                    + "use the rope while looking at it, with an empty main hand or a grappling hook in it. Using a rope still in "
                    + "your hand pulls you along it to the hook instead, when the hook is high enough above you to climb (climb_min_rise)");
    public static final ConfigValue<Boolean> SLIDE_ENABLED = SLIDE.bool("enabled", true,
            "Players can hang on a latched grappling rope and slide down it. Off = using the rope does nothing and riders drop off");
    public static final ConfigValue<Double> BOARD_REACH = SLIDE.doubleRange("board_reach", 2.5, 0.5, 8.0,
            "How far [blocks] from the eyes a player can grab the rope");
    public static final ConfigValue<Integer> GRAB_COOLDOWN_TICKS = SLIDE.intRange("grab_cooldown_ticks", 20, 0, 200,
            "Ticks after a hook is thrown or fired during which its rope cannot be grabbed (a use right after the shot "
                    + "never hangs the player on their own rope)");
    public static final ConfigValue<Double> BOARD_PICK_RADIUS = SLIDE.doubleRange("board_pick_radius", 0.6, 0.1, 2.0,
            "How close [blocks] the look ray must pass the rope to grab it");
    public static final ConfigValue<Double> SLIDE_SPEED = SLIDE.doubleRange("slide_speed", 0.35, 0.01, 2.0,
            "Start speed of the slide in blocks per tick, toward the rope's lower end");
    public static final ConfigValue<Double> SLIDE_GRAVITY = SLIDE.doubleRange("slide_gravity", 0.02, 0.0, 0.5,
            "Speed gained per tick, in blocks per tick, times the rope's slope (height difference per block of rope): steeper is faster");
    public static final ConfigValue<Double> SLIDE_MIN_SPEED = SLIDE.doubleRange("slide_min_speed", 0.1, 0.01, 2.0,
            "Crawling speed in blocks per tick along a level rope, toward the hook; also the speed at which a player pulls "
                    + "themselves along their own hand-held rope to the hook");
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
        return GrappleLaunch.speed(mode, HAND_THROW_VELOCITY.get(), THROW_VELOCITY.get(), MUSKET_SPEED.get());
    }

    /** Rope length [blocks] of a hook launched in {@code mode}. */
    public static double ropeLength(GrappleLaunch.Mode mode) {
        return GrappleLaunch.ropeLength(mode, MAX_ROPE_LENGTH.get(), MUSKET_ROPE_LENGTH.get());
    }

    /** Gravity factor of a hook launched in {@code mode} ({@link GrappleLaunch#gravityFactor}). */
    public static double gravityFactor(GrappleLaunch.Mode mode) {
        return GrappleLaunch.gravityFactor(mode, MUSKET_GRAVITY_FACTOR.get());
    }

    /** The slide's speeds and distances ({@link RopeSlide}). */
    public static RopeSlide.Params slideParams() {
        return new RopeSlide.Params(SLIDE_SPEED.get(), SLIDE_GRAVITY.get(), SLIDE_MIN_SPEED.get(), SLIDE_MAX_SPEED.get(),
                DISMOUNT_DISTANCE.get());
    }

    /** The swing's push, damping and climbing speed ({@link RopeSwing}, GR7). */
    public static RopeSwing.Params swingParams() {
        return new RopeSwing.Params(SWING_PUSH.get(), SWING_DAMPING.get(), ROPE_CLIMB_SPEED.get());
    }

    /** Distance up to which the rope holds without pulling: {@code hold_distance + hold_slack}. */
    public static double holdLength() {
        return GrappleRules.holdLength(HOLD_DISTANCE.get(), HOLD_SLACK.get());
    }
}

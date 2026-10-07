package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Config of wheel steering at the helm (docs/design.md §5.3, §17, HELM1): server section {@code helm.wheel} (gameplay,
 * synced to clients, so the client's input and view lock follow it) and client section {@code helm_view}.
 */
public final class HelmConfig {

    private static final ConfigSection HELM = ModConfigs.server("helm", "The helm: steering by turning the wheel");
    private static final ConfigSection WHEEL = HELM.section("wheel",
            "Hold use on the helm of an assembled ship and turn the wheel with the mouse or the strafe keys");

    public static final ConfigValue<Boolean> DRAG_STEERING = WHEEL.bool("drag_steering", true,
            "Steer by holding use on the helm and turning the wheel with the mouse or A/D. Off: the old click steps (right third of the wheel = one step to starboard, left third = port, middle = midships)");
    public static final ConfigValue<Double> MOUSE_DEGREES_PER_UNIT = WHEEL.doubleRange("mouse_degrees_per_unit", 1.0, 0.0, 20.0,
            "Wheel degrees per degree the mouse would have turned the view (the game's mouse sensitivity applies)");
    public static final ConfigValue<Double> KEY_DEGREES_PER_TICK = WHEEL.doubleRange("key_degrees_per_tick", 6.0, 0.0, 90.0,
            "Wheel degrees per tick while the strafe left or right key is held");
    public static final ConfigValue<Boolean> LOCK_VIEW = WHEEL.bool("lock_view", true,
            "While steering, horizontal mouse movement turns only the wheel, not the view. Off: it turns both");
    public static final ConfigValue<Double> TURNS_LOCK_TO_LOCK = WHEEL.doubleRange("turns_lock_to_lock", 1.5, 0.25, 10.0,
            "Full turns of the wheel from hard to port to hard to starboard; half of it lies on each side of midships");
    public static final ConfigValue<Double> MAX_DEGREES_PER_TICK = WHEEL.doubleRange("max_degrees_per_tick", 30.0, 1.0, 360.0,
            "Largest change of the wheel per tick the server accepts from one helmsman");
    public static final ConfigValue<Double> SESSION_REACH = WHEEL.doubleRange("session_reach", 2.0, 0.5, 16.0,
            "The helmsman lets go of the wheel when farther than this many blocks from the helm block");

    private static final ConfigSection VIEW = ModConfigs.client("helm_view", "What the helmsman sees while steering");

    public static final ConfigValue<Boolean> SHOW_RUDDER_ANGLE = VIEW.bool("show_rudder_angle", true,
            "Show the rudder angle and side above the hotbar while holding the wheel");

    private HelmConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The wheel angle at either lock, from {@link #TURNS_LOCK_TO_LOCK}. */
    public static double lockAngle() {
        return WheelMath.lockAngle(TURNS_LOCK_TO_LOCK.get());
    }
}

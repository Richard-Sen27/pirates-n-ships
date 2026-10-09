package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code boarding} (docs/design.md §8.3, §8.4, §17 "Combat"). Its {@code plank} sub-section holds
 * the boarding plank, the gangway (BRD1, BRD2): a run of plank blocks laid from a ship, a pier or the shore onto a ship,
 * a pier or the shore, climbing or falling on ramps. The client's placement preview is in {@link BoardingClientConfig}.
 */
public final class BoardingConfig {

    private static final ConfigSection S = ModConfigs.server("boarding", "Boarding another ship");
    private static final ConfigSection PLANK = S.section("plank",
            "Boarding plank (gangway): a walkway laid from a ship, pier or shore onto a ship, pier or shore");

    public static final ConfigValue<Boolean> ENABLED = PLANK.bool("enabled", true,
            "Boarding planks can be laid. Off = the item refuses; planks already laid still break when the ships part");
    public static final ConfigValue<Integer> MAX_LENGTH = PLANK.intRange("max_length", 6, 1, PlankRun.MAX_SEGMENTS,
            "Longest plank run in blocks, from the cell beside the clicked block to the cell over what it lands on");
    public static final ConfigValue<Integer> MAX_RISE = PLANK.intRange("max_rise", 2, 0, PlankRun.MAX_RISE,
            "Blocks a plank may climb or fall over its length, on ramp cells. 0 = level planks only");
    public static final ConfigValue<Integer> RISE_RUN = PLANK.intRange("rise_run", 2, 1, 2,
            "Plank cells per block of climb: 2 = a 1-in-2 ramp, 1 = a 45 degree ramp");
    public static final ConfigValue<Boolean> STEEP_FALLBACK = PLANK.bool("steep_fallback", true,
            "When a 1-in-2 ramp does not fit (a short gap), a 45 degree ramp may be laid instead");
    public static final ConfigValue<Boolean> PICK_UP = PLANK.bool("pick_up", true,
            "Sneak-use a laid plank with an empty hand to take the whole run up again (one plank back)");
    public static final ConfigValue<Double> BREAK_DISTANCE = PLANK.doubleRange("break_distance", 1.5, 0.1, 8.0,
            "A plank breaks (dropping one plank) when its far end has moved this many blocks sideways from the spot it "
                    + "was laid onto, for example when the hulls part or a ship drifts off the pier");
    public static final ConfigValue<Double> BREAK_RISE = PLANK.doubleRange("break_rise", 2.5, 0.1, 8.0,
            "A plank breaks when its far end has moved this many blocks up or down from the spot it was laid onto; "
                    + "larger than break_distance so a moored ship's heave in a storm does not snap a gangway");
    public static final ConfigValue<Integer> CHECK_INTERVAL_TICKS = PLANK.intRange("check_interval_ticks", 10, 1, 200,
            "Ticks between two checks of a plank's ends");

    private BoardingConfig() {
    }

    public static void init() {
    }
}

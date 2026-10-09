package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code boarding} (docs/design.md §8.3, §8.4, §17 "Combat"). Its {@code plank} sub-section holds
 * the boarding plank, the gangway (BRD1, BRD2, BRD4): a run of plank blocks laid from a ship, a pier or the shore onto
 * a ship, a pier or the shore, one straight board climbing or falling up to {@link #MAX_PITCH}. The client's placement
 * preview is in {@link BoardingClientConfig}.
 *
 * <p>BRD3: the "Board!" crew order ({@code combat.boarding.order}) has its keys in the section itself: the toggle
 * {@link #BOARD_ORDER}, the widest gap crossed without a plank {@link #BOARD_GAP}, whether gunners and the helmsman stay
 * {@link #BOARDERS_KEEP_STATIONS}, the hold before a cleared deck counts as taken {@link #CAPTURE_SECONDS}, and the NPC
 * captains' boarding {@link #NPC_BOARDING} after {@link #NPC_BOARD_DELAY}.
 */
public final class BoardingConfig {

    private static final ConfigSection S = ModConfigs.server("boarding", "Boarding another ship");
    private static final ConfigSection PLANK = S.section("plank",
            "Boarding plank (gangway): a walkway laid from a ship, pier or shore onto a ship, pier or shore");

    public static final ConfigValue<Boolean> ENABLED = PLANK.bool("enabled", true,
            "Boarding planks can be laid. Off = the item refuses; planks already laid still break when the ships part");
    public static final ConfigValue<Integer> MAX_LENGTH = PLANK.intRange("max_length", 6, 1, PlankRun.MAX_LENGTH,
            "Longest plank run in blocks, from the block beside the clicked block to the far end");
    /**
     * BRD4: the steepest straight board. Replaces BRD2's {@code max_rise}, {@code rise_run} and {@code steep_fallback}
     * (a run is no longer built from ramp pieces); those keys are no longer read, and the loader drops them from an
     * existing config file.
     */
    public static final ConfigValue<Double> MAX_PITCH = PLANK.doubleRange("max_pitch", 45.0, 0.0, PlankRun.MAX_PITCH,
            "Steepest a plank may climb or fall, in degrees: it is one straight board from its start to where it lands. "
                    + "0 = level planks only; 45 = a block of rise per block of run");
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

    // ------------------------------------------------------------------ the boarding order (BRD3)

    public static final ConfigValue<Boolean> BOARD_ORDER = S.bool("board_order", true,
            "The whistle's \"Board!\" order: the crew cross a plank or a narrow gap onto the other ship and fight its crew. "
                    + "Off = the order is refused and boarders already across come back");
    public static final ConfigValue<Integer> BOARD_GAP = S.intRange("board_gap", 2, 0, 4,
            "Widest gap in blocks the crew jump without a plank (0 = only touching hulls); the decks may differ by one block");
    public static final ConfigValue<Boolean> BOARDERS_KEEP_STATIONS = S.bool("boarders_keep_stations", true,
            "Gunners and the helmsman stay at their stations when the crew board; off = every hand goes");
    public static final ConfigValue<Integer> CAPTURE_SECONDS = S.intRange("capture_seconds", 10, 1, 600,
            "Seconds the boarders must hold the other deck with no enemy left before it counts as taken");
    public static final ConfigValue<Boolean> NPC_BOARDING = S.bool("npc_boarding", true,
            "Pirate and navy ships lying alongside a ship they fight send their fighters across");
    public static final ConfigValue<Integer> NPC_BOARD_DELAY = S.intRange("npc_board_delay", 8, 0, 600,
            "Seconds an NPC ship lies alongside its enemy before its captain gives \"Board!\"");
    public static final ConfigValue<Double> NPC_RECALL_OUTNUMBERED = S.doubleRange("npc_recall_outnumbered", 2.0, 1.0, 10.0,
            "An NPC captain calls his boarders back when the defenders outnumber them this many times (and boards only "
                    + "while they do not)");
    public static final ConfigValue<Double> NPC_RECALL_FLOODED = S.doubleRange("npc_recall_flooded", 0.3, 0.05, 1.0,
            "An NPC captain calls his boarders back when this fraction of his own hull is flooded");
    public static final ConfigValue<Boolean> TAKE_ARMS = S.bool("take_arms", true,
            "Boarders with an empty hand take a sword (cutlass, saber, rapier) from a chest or barrel of their ship");
    public static final ConfigValue<Double> UNARMED_DAMAGE = S.doubleRange("unarmed_damage", 2.0, 0.0, 20.0,
            "Damage of a crew member's punch when it boards without a sword");
    public static final ConfigValue<DuelistSkill> CREW_SKILL = S.enumValue("crew_skill", DuelistSkill.SAILOR,
            "Sword skill of the own crew when it fights with a sword (parry chance, reaction time); scaled by "
                    + "melee.npc_skill_multiplier");
    public static final ConfigValue<Integer> CROSS_TIMEOUT_TICKS = S.intRange("cross_timeout_ticks", 200, 20, 2400,
            "Ticks a boarder may take to reach the crossing and get across before it is set down on the other side");
    public static final ConfigValue<Integer> ORDER_CHECK_TICKS = S.intRange("order_check_ticks", 10, 1, 200,
            "Ticks between two looks of the boarders for an enemy, a way back and the deck's state");

    private BoardingConfig() {
    }

    public static void init() {
    }
}

package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.law.bounty.BountyRules;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.FalseColorsDetection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Server config of the law module (docs/design.md §17, groups "Law" and "Flags &amp; brig"). The pure rules never read
 * these handles: {@link #crimeRules()}, {@link #bountyRules()} and {@link #detectionParams()} copy them into plain
 * parameter records.
 */
public final class LawConfig {

    private static final ConfigSection LAW = ModConfigs.server("law", "Criminal score, fines and bounties");

    public static final ConfigValue<Boolean> CRIMINAL_SCORE_ENABLED = LAW.bool("criminal_score_enabled", true,
            "Track criminal scores. Off = no crime is recorded, nobody is wanted and the navy places no bounties");
    public static final ConfigValue<Double> DECAY_PER_DAY = LAW.doubleRange("decay_per_day", 10.0, 0.0, 100000.0,
            "Criminal score points lost per in-game day (24000 ticks)");
    public static final ConfigValue<Integer> DECAY_DELAY_SECONDS = LAW.intRange("decay_delay_seconds", 300, 0, 864000,
            "Seconds after the last crime before the score starts to decay");
    public static final ConfigValue<Integer> MAX_SCORE = LAW.intRange("max_score", 1000, 1, 1000000,
            "Highest possible criminal score");
    public static final ConfigValue<Integer> SUSPECT_THRESHOLD = LAW.intRange("suspect_threshold", 10, 0, 1000000,
            "Score from which an entity counts as a suspect");
    public static final ConfigValue<Integer> BOUNTY_THRESHOLD = LAW.intRange("bounty_threshold", 50, 1, 1000000,
            "Score from which an entity is wanted and the navy places a bounty");
    public static final ConfigValue<Integer> NOTORIOUS_THRESHOLD = LAW.intRange("notorious_threshold", 200, 1, 1000000,
            "Score from which an entity is notorious");
    public static final ConfigValue<Integer> FINE_COST_PER_POINT = LAW.intRange("fine_cost_per_point", 3, 1, 10000,
            "Doubloons per criminal score point when paying a fine");
    public static final ConfigValue<Boolean> FINES_WHEN_NOTORIOUS = LAW.bool("fines_when_notorious", false,
            "Notorious criminals may still pay fines");
    public static final ConfigValue<Double> NAVY_BOUNTY_PER_POINT = LAW.doubleRange("navy_bounty_per_point", 2.0, 0.0, 10000.0,
            "Navy bounty doubloons per criminal score point");
    public static final ConfigValue<Double> NAVY_BOUNTY_WITHDRAW_RATIO = LAW.doubleRange("navy_bounty_withdraw_ratio", 0.5, 0.0, 1.0,
            "The navy withdraws its bounty once the score decays below bounty_threshold times this");
    public static final ConfigValue<Boolean> PLAYER_BOUNTIES = LAW.bool("player_bounties", true,
            "Players may place bounties on other players and NPCs");
    public static final ConfigValue<Integer> PLAYER_BOUNTY_MINIMUM = LAW.intRange("player_bounty_minimum", 10, 1, 1000000,
            "Smallest bounty a player can place, in doubloons");
    public static final ConfigValue<Integer> PLAYER_BOUNTY_DURATION_DAYS = LAW.intRange("player_bounty_duration_days", 0, 0, 100000,
            "In-game days a player bounty stays on the board (0 = until claimed)");
    public static final ConfigValue<Double> ALIVE_FACTOR = LAW.doubleRange("alive_factor", 1.5, 1.0, 100.0,
            "Bounty payout multiplier for delivering the target alive instead of a proof item");
    public static final ConfigValue<Double> SCORE_AFTER_CLAIM_FACTOR = LAW.doubleRange("score_after_claim_factor", 0.0, 0.0, 1.0,
            "The target's criminal score is multiplied by this when a bounty on them is claimed (0 = clean slate)");

    private static final ConfigSection SEVERITY = LAW.section("severity", "Criminal score points per crime");
    private static final ConfigSection COOLDOWN = LAW.section("repeat_cooldown_seconds",
            "The same crime against the same victim within this many seconds is not counted again");
    private static final ConfigSection TURN_IN = LAW.section("pirate_turn_in", "Navy reward in doubloons for delivering a captured pirate, by rank");

    public static final Map<CrimeType, ConfigValue<Integer>> SEVERITIES;
    public static final Map<CrimeType, ConfigValue<Integer>> COOLDOWNS;
    public static final Map<PirateTier, ConfigValue<Integer>> TURN_IN_REWARDS;

    static {
        Map<CrimeType, ConfigValue<Integer>> sev = new EnumMap<>(CrimeType.class);
        Map<CrimeType, ConfigValue<Integer>> cd = new EnumMap<>(CrimeType.class);
        for (CrimeType t : CrimeType.values()) {
            sev.put(t, SEVERITY.intRange(t.id(), t.defaultSeverity(), 0, 100000, "Points for: " + t.id().replace('_', ' ')));
            cd.put(t, COOLDOWN.intRange(t.id(), t.defaultCooldownSeconds(), 0, 864000, "Repeat window for: " + t.id().replace('_', ' ')));
        }
        Map<PirateTier, ConfigValue<Integer>> rewards = new EnumMap<>(PirateTier.class);
        for (PirateTier t : PirateTier.values()) {
            rewards.put(t, TURN_IN.intRange(t.id(), t.defaultReward(), 0, 1000000, "Reward for a captured pirate " + t.id()));
        }
        SEVERITIES = Collections.unmodifiableMap(sev);
        COOLDOWNS = Collections.unmodifiableMap(cd);
        TURN_IN_REWARDS = Collections.unmodifiableMap(rewards);
    }

    private static final ConfigSection FLAGS = ModConfigs.server("flags_brig", "Flags, false colors and prisoners");

    public static final ConfigValue<Double> FALSE_FLAG_DETECTION_STRENGTH = FLAGS.doubleRange("false_flag_detection_strength", 1.0, 0.0, 100.0,
            "Multiplier for how quickly observers see through false colors (0 = never)");
    public static final ConfigValue<Double> DETECTION_BASE_RATE = FLAGS.doubleRange("detection_base_rate", 0.05, 0.0, 100.0,
            "Detections per second at close range against a captain with a clean record");
    public static final ConfigValue<Double> DETECTION_CLOSE_RANGE = FLAGS.doubleRange("detection_close_range", 16.0, 0.0, 1024.0,
            "Blocks within which an observer sees a flag perfectly");
    public static final ConfigValue<Double> DETECTION_MAX_RANGE = FLAGS.doubleRange("detection_max_range", 96.0, 0.0, 4096.0,
            "Blocks beyond which false colors can't be detected (without a crow's nest)");
    public static final ConfigValue<Double> CROWS_NEST_RANGE_FACTOR = FLAGS.doubleRange("crows_nest_range_factor", 1.5, 1.0, 10.0,
            "Detection range multiplier for an observer with a manned crow's nest");
    public static final ConfigValue<Double> CROWS_NEST_RATE_FACTOR = FLAGS.doubleRange("crows_nest_rate_factor", 1.5, 1.0, 10.0,
            "Detection rate multiplier for an observer with a manned crow's nest");
    public static final ConfigValue<Double> DETECTION_SCORE_SCALE = FLAGS.doubleRange("detection_score_scale", 100.0, 1.0, 1000000.0,
            "Criminal score that doubles the detection rate");
    public static final ConfigValue<Integer> NAVY_FLAG_MIN_STANDING = FLAGS.intRange("navy_flag_min_standing", 100, -1000000, 1000000,
            "Navy reputation needed to fly the navy flag legitimately");
    public static final ConfigValue<Boolean> NPC_SURRENDER = FLAGS.bool("npc_surrender", true,
            "Merchant NPC ships may surrender to a ship flying the Jolly Roger");
    public static final ConfigValue<Boolean> PLAYER_CAPTURE = FLAGS.bool("player_capture", true,
            "Players with a bounty can be captured in shackles (PvP)");
    public static final ConfigValue<Boolean> PRISONER_ESCAPES = FLAGS.bool("prisoner_escapes", true,
            "Prisoners may try to escape from the brig");

    private LawConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The criminal score rules from the current config. */
    public static CrimeRules crimeRules() {
        Map<CrimeType, Integer> sev = new EnumMap<>(CrimeType.class);
        Map<CrimeType, Long> cd = new EnumMap<>(CrimeType.class);
        for (CrimeType t : CrimeType.values()) {
            sev.put(t, SEVERITIES.get(t).get());
            cd.put(t, COOLDOWNS.get(t).get() * 20L);
        }
        // Keep the thresholds ordered even if the config file isn't
        double wanted = BOUNTY_THRESHOLD.get();
        double suspect = Math.min(SUSPECT_THRESHOLD.get(), wanted);
        double notorious = Math.max(NOTORIOUS_THRESHOLD.get(), wanted);
        return new CrimeRules(CRIMINAL_SCORE_ENABLED.get(), sev, cd, DECAY_PER_DAY.get(), DECAY_DELAY_SECONDS.get() * 20L,
                MAX_SCORE.get(), suspect, wanted, notorious, FINE_COST_PER_POINT.get(), FINES_WHEN_NOTORIOUS.get());
    }

    /** The bounty rules from the current config. */
    public static BountyRules bountyRules() {
        Map<PirateTier, Integer> rewards = new EnumMap<>(PirateTier.class);
        for (PirateTier t : PirateTier.values()) rewards.put(t, TURN_IN_REWARDS.get(t).get());
        return new BountyRules(CRIMINAL_SCORE_ENABLED.get(), BOUNTY_THRESHOLD.get(), NAVY_BOUNTY_WITHDRAW_RATIO.get(),
                NAVY_BOUNTY_PER_POINT.get(), PLAYER_BOUNTIES.get(), PLAYER_BOUNTY_MINIMUM.get(),
                PLAYER_BOUNTY_DURATION_DAYS.get() * CrimeRules.TICKS_PER_DAY, ALIVE_FACTOR.get(),
                SCORE_AFTER_CLAIM_FACTOR.get(), rewards);
    }

    /** The false-colors detection parameters from the current config. */
    public static FalseColorsDetection.Params detectionParams() {
        return new FalseColorsDetection.Params(FALSE_FLAG_DETECTION_STRENGTH.get(), DETECTION_BASE_RATE.get(),
                DETECTION_CLOSE_RANGE.get(), DETECTION_MAX_RANGE.get(), CROWS_NEST_RANGE_FACTOR.get(),
                CROWS_NEST_RATE_FACTOR.get(), DETECTION_SCORE_SCALE.get());
    }
}

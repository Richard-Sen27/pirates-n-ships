package com.richardsenger.piratesnships.crew.provisions;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code provisions} (design.md §17). The rules never read these handles directly: call
 * {@link #settings()} once per update and pass the {@link ProvisionSettings} on. Defaults equal
 * {@link ProvisionSettings#DEFAULTS}.
 */
public final class ProvisionsConfig {

    private static final ProvisionSettings D = ProvisionSettings.DEFAULTS;
    private static final ConfigSection S = ModConfigs.server("provisions", "Crew food, fresh water, rum, scurvy and spoilage");

    public static final ConfigValue<Boolean> CONSUMPTION_ENABLED = S.bool("consumption_enabled", D.consumptionEnabled(),
            "The crew consumes provisions. Off: pantries never shrink and there are no hunger, thirst, rum or scurvy effects");
    public static final ConfigValue<Double> CONSUMPTION_RATE = S.doubleRange("consumption_rate", D.consumptionRate(), 0.0, 10.0,
            "Multiplier on all provision consumption");
    public static final ConfigValue<Double> FOOD_PER_CREW_PER_DAY = S.doubleRange("food_per_crew_per_day", D.foodPerCrewPerDay(), 0.0, 40.0,
            "Nutrition (food hunger points) one crew member eats per in-game day");
    public static final ConfigValue<Double> WATER_PER_CREW_PER_DAY = S.doubleRange("water_per_crew_per_day", D.waterPerCrewPerDay(), 0.0, 20.0,
            "Water rations one crew member drinks per in-game day (a water bottle is one ration)");
    public static final ConfigValue<Double> RUM_RATION_PER_CREW_PER_DAY = S.doubleRange("rum_ration_per_crew_per_day", D.rumRationPerCrewPerDay(), 0.0, 10.0,
            "Rum items in one crew member's standard daily rum ration");
    public static final ConfigValue<Double> PRISONER_SHARE = S.doubleRange("prisoner_share", D.prisonerShare(), 0.0, 1.0,
            "Share of a crew member's food and water that a prisoner consumes");

    public static final ConfigValue<Double> HUNGER_MORALE_PER_DAY = S.doubleRange("hunger_morale_per_day", D.hungerMoralePerDay(), 0.0, 100.0,
            "Morale lost per in-game day without food");
    public static final ConfigValue<Double> THIRST_MORALE_PER_DAY = S.doubleRange("thirst_morale_per_day", D.thirstMoralePerDay(), 0.0, 100.0,
            "Morale lost per in-game day without water");
    public static final ConfigValue<Double> RUM_MORALE_PER_DAY = S.doubleRange("rum_morale_per_day", D.rumMoralePerDay(), 0.0, 100.0,
            "Morale gained per in-game day while the rum ration is issued");
    public static final ConfigValue<Double> SCURVY_MORALE_PER_DAY = S.doubleRange("scurvy_morale_per_day", D.scurvyMoralePerDay(), 0.0, 100.0,
            "Morale lost per in-game day with scurvy");
    public static final ConfigValue<Double> HUNGRY_WORK_SPEED = S.doubleRange("hungry_work_speed", D.hungryWorkSpeed(), 0.0, 1.0,
            "Work speed multiplier of a hungry crew");
    public static final ConfigValue<Double> THIRSTY_WORK_SPEED = S.doubleRange("thirsty_work_speed", D.thirstyWorkSpeed(), 0.0, 1.0,
            "Work speed multiplier of a thirsty crew");
    public static final ConfigValue<Double> DRUNK_WORK_SPEED = S.doubleRange("drunk_work_speed", D.drunkWorkSpeed(), 0.0, 1.0,
            "Work speed multiplier while the crew is drunk from more than the rum ration");
    public static final ConfigValue<Integer> DRUNK_DURATION_TICKS = S.intRange("drunk_duration_ticks", (int) D.drunkDurationTicks(), 0, 240000,
            "How long the drunk penalty lasts after the last over-ration, in ticks");
    public static final ConfigValue<Double> HUNGER_DESERTION_DAYS = S.doubleRange("hunger_desertion_days", D.hungerDesertionDays(), 0.0, 100.0,
            "In-game days without food until crew start to desert (or mutiny, if enabled)");
    public static final ConfigValue<Double> THIRST_DESERTION_DAYS = S.doubleRange("thirst_desertion_days", D.thirstDesertionDays(), 0.0, 100.0,
            "In-game days without water until crew start to desert (or mutiny, if enabled)");

    public static final ConfigValue<Boolean> SCURVY_ENABLED = S.bool("scurvy_enabled", D.scurvyEnabled(),
            "Crew get scurvy (weakness, slower healing) after a long time without citrus or fresh food");
    public static final ConfigValue<Double> SCURVY_ONSET_DAYS = S.doubleRange("scurvy_onset_days", D.scurvyOnsetDays(), 0.0, 1000.0,
            "In-game days without anti-scurvy food until scurvy sets in");
    public static final ConfigValue<Boolean> FRESH_FOOD_PREVENTS_SCURVY = S.bool("fresh_food_prevents_scurvy", D.freshFoodPreventsScurvy(),
            "Any fresh (not preserved) food prevents scurvy, not only citrus");
    public static final ConfigValue<Boolean> SCURVY_AFFECTS_PLAYERS = S.bool("scurvy_affects_players", D.scurvyAffectsPlayers(),
            "Players on a ship whose crew has scurvy get scurvy too");

    public static final ConfigValue<Boolean> SPOILAGE_ENABLED = S.bool("spoilage_enabled", D.spoilageEnabled(),
            "Fresh food in the pantry spoils after its shelf life. Preserved food never spoils");
    public static final ConfigValue<Double> FRESH_SHELF_LIFE_DAYS = S.doubleRange("fresh_shelf_life_days", D.freshShelfLifeDays(), 0.1, 1000.0,
            "In-game days fresh food keeps in the pantry");

    public static final ConfigValue<Double> FOOD_WEIGHT_PER_UNIT = S.doubleRange("food_weight_per_unit", D.foodWeightPerUnit(), 0.0, 100.0,
            "Cargo weight of one food item");
    public static final ConfigValue<Double> WATER_WEIGHT_PER_RATION = S.doubleRange("water_weight_per_ration", D.waterWeightPerRation(), 0.0, 100.0,
            "Cargo weight of one water ration");
    public static final ConfigValue<Double> RUM_WEIGHT_PER_UNIT = S.doubleRange("rum_weight_per_unit", D.rumWeightPerUnit(), 0.0, 100.0,
            "Cargo weight of one rum item");
    public static final ConfigValue<Integer> WATER_BUCKET_RATIONS = S.intRange("water_bucket_rations", D.waterBucketRations(), 1, 1000,
            "Water rations in a water bucket");
    public static final ConfigValue<Integer> WATER_BARREL_RATIONS = S.intRange("water_barrel_rations", D.waterBarrelRations(), 1, 1000,
            "Water rations in a water barrel");

    private ProvisionsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The current config as a plain settings value for {@link ProvisionRules} and {@link ProvisionClassifier}. */
    public static ProvisionSettings settings() {
        return new ProvisionSettings(
                CONSUMPTION_ENABLED.get(), CONSUMPTION_RATE.get(),
                FOOD_PER_CREW_PER_DAY.get(), WATER_PER_CREW_PER_DAY.get(), RUM_RATION_PER_CREW_PER_DAY.get(), PRISONER_SHARE.get(),
                HUNGER_MORALE_PER_DAY.get(), THIRST_MORALE_PER_DAY.get(), RUM_MORALE_PER_DAY.get(), SCURVY_MORALE_PER_DAY.get(),
                HUNGRY_WORK_SPEED.get(), THIRSTY_WORK_SPEED.get(), DRUNK_WORK_SPEED.get(), DRUNK_DURATION_TICKS.get(),
                HUNGER_DESERTION_DAYS.get(), THIRST_DESERTION_DAYS.get(),
                SCURVY_ENABLED.get(), SCURVY_ONSET_DAYS.get(), FRESH_FOOD_PREVENTS_SCURVY.get(), SCURVY_AFFECTS_PLAYERS.get(),
                SPOILAGE_ENABLED.get(), FRESH_SHELF_LIFE_DAYS.get(),
                FOOD_WEIGHT_PER_UNIT.get(), WATER_WEIGHT_PER_RATION.get(), RUM_WEIGHT_PER_UNIT.get(),
                WATER_BUCKET_RATIONS.get(), WATER_BARREL_RATIONS.get());
    }
}

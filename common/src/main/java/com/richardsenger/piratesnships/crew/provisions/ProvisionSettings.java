package com.richardsenger.piratesnships.crew.provisions;

/**
 * All numbers the provision rules use, as a plain value. {@link ProvisionsConfig#settings()} fills it from the
 * server config; tests build it with {@link #builder()}. {@link #DEFAULTS} equals the config defaults.
 *
 * <p>Units: "per day" means per 24000 ticks. Morale is on the crew system's 0..100 scale. Work speed values are
 * multipliers (1 = normal).
 *
 * @param consumptionEnabled      master toggle: off = stores never shrink and no penalties appear
 * @param consumptionRate         multiplier on all consumption
 * @param foodPerCrewPerDay       nutrition one crew member eats per day (6 = a bit more than one bread)
 * @param waterPerCrewPerDay      water rations one crew member drinks per day
 * @param rumRationPerCrewPerDay  rum rations in one standard daily rum ration per crew member
 * @param prisonerShare           a prisoner eats and drinks this fraction of a crew member (no rum)
 * @param hungerMoralePerDay      morale lost per day of hunger
 * @param thirstMoralePerDay      morale lost per day of thirst (bites harder than hunger)
 * @param rumMoralePerDay         morale gained per day with the rum ration issued
 * @param scurvyMoralePerDay      morale lost per day with scurvy
 * @param hungryWorkSpeed         work speed multiplier while hungry
 * @param thirstyWorkSpeed        work speed multiplier while thirsty
 * @param drunkWorkSpeed          work speed multiplier while drunk (after more than the ration)
 * @param drunkDurationTicks      how long the drunk penalty lasts after the last over-ration
 * @param hungerDesertionDays     continuous hunger until the desertion/mutiny risk is critical
 * @param thirstDesertionDays     continuous thirst until the desertion/mutiny risk is critical
 * @param scurvyEnabled           scurvy toggle
 * @param scurvyOnsetDays         days without anti-scurvy food until scurvy sets in
 * @param freshFoodPreventsScurvy whether any fresh (non-preserved) food counts as anti-scurvy, not only citrus
 * @param scurvyAffectsPlayers    whether the integration should also apply scurvy to players on board
 * @param spoilageEnabled         spoilage toggle
 * @param freshShelfLifeDays      shelf life of fresh food in the pantry
 * @param foodWeightPerUnit       cargo weight of one food item
 * @param waterWeightPerRation    cargo weight of one water ration
 * @param rumWeightPerUnit        cargo weight of one rum item
 * @param waterBucketRations      water rations in a water bucket
 * @param waterBarrelRations      water rations in a water barrel (items tagged {@code provisions/water_barrel})
 */
public record ProvisionSettings(
        boolean consumptionEnabled, double consumptionRate,
        double foodPerCrewPerDay, double waterPerCrewPerDay, double rumRationPerCrewPerDay, double prisonerShare,
        double hungerMoralePerDay, double thirstMoralePerDay, double rumMoralePerDay, double scurvyMoralePerDay,
        double hungryWorkSpeed, double thirstyWorkSpeed, double drunkWorkSpeed, long drunkDurationTicks,
        double hungerDesertionDays, double thirstDesertionDays,
        boolean scurvyEnabled, double scurvyOnsetDays, boolean freshFoodPreventsScurvy, boolean scurvyAffectsPlayers,
        boolean spoilageEnabled, double freshShelfLifeDays,
        double foodWeightPerUnit, double waterWeightPerRation, double rumWeightPerUnit,
        int waterBucketRations, int waterBarrelRations) {

    /** One in-game day. */
    public static final long TICKS_PER_DAY = 24000L;

    public static final ProvisionSettings DEFAULTS = new ProvisionSettings(
            true, 1.0,
            6.0, 1.0, 0.25, 0.5,
            10.0, 25.0, 4.0, 5.0,
            0.75, 0.5, 0.6, 6000L,
            3.0, 1.0,
            true, 8.0, true, false,
            true, 5.0,
            0.25, 1.0, 0.5,
            3, 16);

    public long freshShelfLifeTicks() {
        return Math.max(1L, Math.round(freshShelfLifeDays * TICKS_PER_DAY));
    }

    public double scurvyOnsetTicks() {
        return scurvyOnsetDays * TICKS_PER_DAY;
    }

    public static Builder builder() {
        return new Builder(DEFAULTS);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Fluent copy-and-change builder, mostly for tests. */
    public static final class Builder {
        private boolean consumptionEnabled, scurvyEnabled, freshFoodPreventsScurvy, scurvyAffectsPlayers, spoilageEnabled;
        private double consumptionRate, foodPerCrewPerDay, waterPerCrewPerDay, rumRationPerCrewPerDay, prisonerShare;
        private double hungerMoralePerDay, thirstMoralePerDay, rumMoralePerDay, scurvyMoralePerDay;
        private double hungryWorkSpeed, thirstyWorkSpeed, drunkWorkSpeed, hungerDesertionDays, thirstDesertionDays;
        private double scurvyOnsetDays, freshShelfLifeDays, foodWeightPerUnit, waterWeightPerRation, rumWeightPerUnit;
        private long drunkDurationTicks;
        private int waterBucketRations, waterBarrelRations;

        private Builder(ProvisionSettings s) {
            consumptionEnabled = s.consumptionEnabled;
            consumptionRate = s.consumptionRate;
            foodPerCrewPerDay = s.foodPerCrewPerDay;
            waterPerCrewPerDay = s.waterPerCrewPerDay;
            rumRationPerCrewPerDay = s.rumRationPerCrewPerDay;
            prisonerShare = s.prisonerShare;
            hungerMoralePerDay = s.hungerMoralePerDay;
            thirstMoralePerDay = s.thirstMoralePerDay;
            rumMoralePerDay = s.rumMoralePerDay;
            scurvyMoralePerDay = s.scurvyMoralePerDay;
            hungryWorkSpeed = s.hungryWorkSpeed;
            thirstyWorkSpeed = s.thirstyWorkSpeed;
            drunkWorkSpeed = s.drunkWorkSpeed;
            drunkDurationTicks = s.drunkDurationTicks;
            hungerDesertionDays = s.hungerDesertionDays;
            thirstDesertionDays = s.thirstDesertionDays;
            scurvyEnabled = s.scurvyEnabled;
            scurvyOnsetDays = s.scurvyOnsetDays;
            freshFoodPreventsScurvy = s.freshFoodPreventsScurvy;
            scurvyAffectsPlayers = s.scurvyAffectsPlayers;
            spoilageEnabled = s.spoilageEnabled;
            freshShelfLifeDays = s.freshShelfLifeDays;
            foodWeightPerUnit = s.foodWeightPerUnit;
            waterWeightPerRation = s.waterWeightPerRation;
            rumWeightPerUnit = s.rumWeightPerUnit;
            waterBucketRations = s.waterBucketRations;
            waterBarrelRations = s.waterBarrelRations;
        }

        public Builder consumptionEnabled(boolean v) { consumptionEnabled = v; return this; }
        public Builder consumptionRate(double v) { consumptionRate = v; return this; }
        public Builder foodPerCrewPerDay(double v) { foodPerCrewPerDay = v; return this; }
        public Builder waterPerCrewPerDay(double v) { waterPerCrewPerDay = v; return this; }
        public Builder rumRationPerCrewPerDay(double v) { rumRationPerCrewPerDay = v; return this; }
        public Builder prisonerShare(double v) { prisonerShare = v; return this; }
        public Builder hungerMoralePerDay(double v) { hungerMoralePerDay = v; return this; }
        public Builder thirstMoralePerDay(double v) { thirstMoralePerDay = v; return this; }
        public Builder rumMoralePerDay(double v) { rumMoralePerDay = v; return this; }
        public Builder scurvyMoralePerDay(double v) { scurvyMoralePerDay = v; return this; }
        public Builder hungryWorkSpeed(double v) { hungryWorkSpeed = v; return this; }
        public Builder thirstyWorkSpeed(double v) { thirstyWorkSpeed = v; return this; }
        public Builder drunkWorkSpeed(double v) { drunkWorkSpeed = v; return this; }
        public Builder drunkDurationTicks(long v) { drunkDurationTicks = v; return this; }
        public Builder hungerDesertionDays(double v) { hungerDesertionDays = v; return this; }
        public Builder thirstDesertionDays(double v) { thirstDesertionDays = v; return this; }
        public Builder scurvyEnabled(boolean v) { scurvyEnabled = v; return this; }
        public Builder scurvyOnsetDays(double v) { scurvyOnsetDays = v; return this; }
        public Builder freshFoodPreventsScurvy(boolean v) { freshFoodPreventsScurvy = v; return this; }
        public Builder scurvyAffectsPlayers(boolean v) { scurvyAffectsPlayers = v; return this; }
        public Builder spoilageEnabled(boolean v) { spoilageEnabled = v; return this; }
        public Builder freshShelfLifeDays(double v) { freshShelfLifeDays = v; return this; }
        public Builder foodWeightPerUnit(double v) { foodWeightPerUnit = v; return this; }
        public Builder waterWeightPerRation(double v) { waterWeightPerRation = v; return this; }
        public Builder rumWeightPerUnit(double v) { rumWeightPerUnit = v; return this; }
        public Builder waterBucketRations(int v) { waterBucketRations = v; return this; }
        public Builder waterBarrelRations(int v) { waterBarrelRations = v; return this; }

        public ProvisionSettings build() {
            return new ProvisionSettings(consumptionEnabled, consumptionRate,
                    foodPerCrewPerDay, waterPerCrewPerDay, rumRationPerCrewPerDay, prisonerShare,
                    hungerMoralePerDay, thirstMoralePerDay, rumMoralePerDay, scurvyMoralePerDay,
                    hungryWorkSpeed, thirstyWorkSpeed, drunkWorkSpeed, drunkDurationTicks,
                    hungerDesertionDays, thirstDesertionDays,
                    scurvyEnabled, scurvyOnsetDays, freshFoodPreventsScurvy, scurvyAffectsPlayers,
                    spoilageEnabled, freshShelfLifeDays,
                    foodWeightPerUnit, waterWeightPerRation, rumWeightPerUnit,
                    waterBucketRations, waterBarrelRations);
        }
    }
}

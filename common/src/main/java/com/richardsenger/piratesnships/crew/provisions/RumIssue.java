package com.richardsenger.piratesnships.crew.provisions;

/**
 * How much rum the crew drinks in a day (FIX1, design.md §7.4: "Rum: a morale booster. Too much reduces the crew's work
 * speed for a while"). Pure. The standard ration is 1; a crew that finds rum for at least {@code bingeDays} days of
 * that ration in the pantries drinks {@code bingeRations} rations instead, which keeps it drunk
 * ({@link ProvisionSettings#drunkWorkSpeed()} for {@link ProvisionSettings#drunkDurationTicks()} after the last
 * over-ration, {@link ProvisionRules}). Stocking less rum keeps the crew sober.
 */
public final class RumIssue {

    private RumIssue() {
    }

    /**
     * The rum rations per crew member for the coming day ({@link CrewHeadcount#rumRations()}).
     *
     * @param rumValue     rum aboard, in rum units (the pantries' rum value plus the crew's rum credit)
     * @param crew         crew members aboard
     * @param s            the provision settings (ration size, consumption rate)
     * @param bingeDays    days of standard rations from which the crew drinks too much ({@code <= 0}: never)
     * @param bingeRations the rations it then drinks (at least 1)
     */
    public static double rations(double rumValue, int crew, ProvisionSettings s, double bingeDays, double bingeRations) {
        if (bingeDays <= 0 || crew <= 0) return 1.0;
        double perDay = crew * s.rumRationPerCrewPerDay() * s.consumptionRate();
        if (perDay <= 0) return 1.0;
        return rumValue / perDay >= bingeDays ? Math.max(1.0, bingeRations) : 1.0;
    }
}

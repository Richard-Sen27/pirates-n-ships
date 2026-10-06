package com.richardsenger.piratesnships.crew.provisions;

/**
 * Who eats from the pantry during a period, and how much rum the captain issues.
 *
 * @param crew       crew members aboard
 * @param prisoners  prisoners in the brig (design.md §13.3); they take {@link ProvisionSettings#prisonerShare()}
 *                   of a crew member's food and water and no rum
 * @param rumRations rum issued, in standard rations per crew member per day: 0 = none, 1 = the ration, above 1 =
 *                   "too much" (morale stays up but work speed drops for a while)
 */
public record CrewHeadcount(int crew, int prisoners, double rumRations) {

    public static final CrewHeadcount NOBODY = new CrewHeadcount(0, 0, 0);

    public CrewHeadcount {
        if (crew < 0 || prisoners < 0 || rumRations < 0) {
            throw new IllegalArgumentException("Negative headcount or rum rations");
        }
    }

    /** A crew with the standard rum ration and no prisoners. */
    public static CrewHeadcount crew(int crew) {
        return new CrewHeadcount(crew, 0, 1.0);
    }

    public CrewHeadcount withPrisoners(int n) {
        return new CrewHeadcount(crew, n, rumRations);
    }

    public CrewHeadcount withRum(double rations) {
        return new CrewHeadcount(crew, prisoners, rations);
    }
}

package com.richardsenger.piratesnships.crew.upkeep;

/**
 * The {@code crew.wages}, {@code crew.desertion} and {@code crew.mutiny} config values (CR2, docs/design.md §7.3) as one
 * plain value for the pure rules ({@link WageRules}, {@link UpkeepDay}). The world layer reads it once per day tick
 * ({@link Upkeep#settings()}); tests build it directly.
 *
 * @param wagesEnabled     crew are paid (off: nobody pays, nobody loses or gains morale for it)
 * @param wagePerDay       doubloons per crew member and day
 * @param unpaidPerDay     morale an unpaid member loses
 * @param paidPerDay       morale a paid member gains
 * @param desertionEnabled low morale leads to desertion
 * @param desertBelow      morale below which a dawn counts toward desertion
 * @param desertDays       consecutive low dawns until a member deserts
 * @param mutinyEnabled    a low average leads to mutiny
 * @param mutinyBelow      average morale below which a dawn counts toward mutiny
 * @param mutinyDays       consecutive low dawns until the crew mutinies
 */
public record UpkeepSettings(boolean wagesEnabled, int wagePerDay, int unpaidPerDay, int paidPerDay,
                             boolean desertionEnabled, int desertBelow, int desertDays,
                             boolean mutinyEnabled, int mutinyBelow, int mutinyDays) {

    /** Equal to the config defaults. */
    public static final UpkeepSettings DEFAULTS = new UpkeepSettings(true, 2, 8, 1, true, 20, 2, false, 15, 3);

    public UpkeepSettings withWages(boolean on) {
        return new UpkeepSettings(on, wagePerDay, unpaidPerDay, paidPerDay, desertionEnabled, desertBelow, desertDays, mutinyEnabled, mutinyBelow, mutinyDays);
    }

    public UpkeepSettings withDesertion(boolean on) {
        return new UpkeepSettings(wagesEnabled, wagePerDay, unpaidPerDay, paidPerDay, on, desertBelow, desertDays, mutinyEnabled, mutinyBelow, mutinyDays);
    }

    public UpkeepSettings withMutiny(boolean on) {
        return new UpkeepSettings(wagesEnabled, wagePerDay, unpaidPerDay, paidPerDay, desertionEnabled, desertBelow, desertDays, on, mutinyBelow, mutinyDays);
    }
}

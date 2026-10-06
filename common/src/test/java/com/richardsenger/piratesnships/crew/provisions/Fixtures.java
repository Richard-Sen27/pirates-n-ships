package com.richardsenger.piratesnships.crew.provisions;

/** Shared provision types and helpers for the provisions unit tests. */
final class Fixtures {

    static final long DAY = ProvisionSettings.TICKS_PER_DAY;
    static final ProvisionSettings S = ProvisionSettings.DEFAULTS;

    static final ProvisionType KELP = ProvisionType.food("kelp", 1, true, false, 0.25, 0);
    static final ProvisionType BREAD = ProvisionType.food("bread", 5, true, false, 0.25, 0);
    static final ProvisionType BEEF = ProvisionType.food("beef", 8, false, false, 0.25, 5 * DAY);
    static final ProvisionType APPLE = ProvisionType.food("apple", 4, false, true, 0.25, 5 * DAY);
    static final ProvisionType LEMON = ProvisionType.food("lemon", 2, true, true, 0.25, 0);
    static final ProvisionType WATER = ProvisionType.water("water", 1, 1.0);
    static final ProvisionType RUM = ProvisionType.rum("rum", 1, 0.5);

    private Fixtures() {
    }

    static ProvisionStore store(Object... typeAndUnits) {
        ProvisionStore s = ProvisionStore.EMPTY;
        for (int i = 0; i < typeAndUnits.length; i += 2) {
            s = s.add((ProvisionType) typeAndUnits[i], (Integer) typeAndUnits[i + 1]);
        }
        return s;
    }

    static ProvisionUpdate run(ProvisionStore store, CrewHeadcount crew, ProvisionSettings s, long ticks) {
        return ProvisionRules.advance(store, ProvisioningState.INITIAL, crew, s, ticks);
    }

    static CrewHeadcount crew(int n) {
        return CrewHeadcount.crew(n).withRum(0);
    }
}

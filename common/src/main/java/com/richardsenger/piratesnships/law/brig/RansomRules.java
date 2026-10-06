package com.richardsenger.piratesnships.law.brig;

import java.util.Locale;

/** Ransom amounts at a faction's port (design.md §13.3). Pure; amounts are plain doubloon numbers. */
public final class RansomRules {

    /** What the prisoner is worth to its faction. Navy officers and merchants are the spec's ransom cases. */
    public enum Kind {
        COMMON, MERCHANT, NAVY_OFFICER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Params(int common, int merchant, int navyOfficer, double captainMultiplier) {
    }

    private RansomRules() {
    }

    /** Base by kind, times the captain multiplier for captured captains ("worth extra"), rounded. */
    public static int ransom(Kind kind, boolean captain, Params p) {
        int base = switch (kind) {
            case COMMON -> p.common();
            case MERCHANT -> p.merchant();
            case NAVY_OFFICER -> p.navyOfficer();
        };
        double value = captain ? base * p.captainMultiplier() : base;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }
}

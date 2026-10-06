package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.core.datagen.LangBuilder;

/** Translation keys of the cargo containers and their English text (datagen). */
public final class CargoText {

    public static final String EMPTY = "pirates_n_ships.cargo.empty";
    public static final String HOLDS = "pirates_n_ships.cargo.holds";
    public static final String HOLDS_PLUNDERED = "pirates_n_ships.cargo.holds_plundered";
    public static final String TOOLTIP = "pirates_n_ships.cargo.tooltip";
    public static final String PLUNDERED_TOOLTIP = "pirates_n_ships.plundered.tooltip";

    private CargoText() {
    }

    public static void lang(LangBuilder lang) {
        lang.add(EMPTY, "%s: empty")
                .add(HOLDS, "%s: %s / %s %s")
                .add(HOLDS_PLUNDERED, "%s: %s / %s %s (plundered)")
                .add(TOOLTIP, "%s × %s")
                .add(PLUNDERED_TOOLTIP, "Plundered")
                .add(BulkStore.Refusal.EMPTY_STACK.translationKey(), "Nothing to put in")
                .add(BulkStore.Refusal.NOT_STACKABLE.translationKey(), "%s doesn't go into bulk cargo (%s)")
                .add(BulkStore.Refusal.NOT_ACCEPTED.translationKey(), "%s is no trade good (%s)")
                .add(BulkStore.Refusal.OTHER_KIND.translationKey(), "Holds one kind only, refused %s (%s)")
                .add(BulkStore.Refusal.PLUNDER_MIX.translationKey(), "Plundered and clean goods don't mix, refused %s (%s)")
                .add(BulkStore.Refusal.FULL.translationKey(), "Full, refused %s (%s)");
    }
}

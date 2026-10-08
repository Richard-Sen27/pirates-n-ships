package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;

/**
 * Pure answer to "may the gun crews of a ship of {@code self} fire at that ship at will?" (WS4a, docs/design.md §4.7,
 * §8.2, §10.4). A ship that has struck its colours ({@link ShipStance#SURRENDERED}, or a struck reading) is never a
 * target. Otherwise:
 * <ul>
 *   <li><b>Navy</b>: a ship under the Jolly Roger, a ship whose cover the navy has blown ({@link ShipStance#UNMASKED}),
 *       or a ship whose owner is wanted or has a bounty, whatever it flies.</li>
 *   <li><b>Pirates</b>: a ship under the navy flag or a merchant ship (merchant flag, or no flag: §4.7 counts an
 *       unflagged ship as a merchant). Banner flags (neutral) and fellow Jolly Rogers are left alone.</li>
 *   <li><b>Merchants</b>: only a ship under the Jolly Roger.</li>
 * </ul>
 * The firing ship's own faction follows from its flag ({@link #factionOf}).
 */
public final class ShipHostility {

    private ShipHostility() {
    }

    /**
     * @param self              the firing ship's faction
     * @param target            what the target ship shows
     * @param targetOwnerWanted the target's owner has a bounty or is wanted at the navy's hostility threshold
     * @param targetStance      the target's stance ({@code ShipStance.of(shown, struck, coverBlown)})
     */
    public static boolean hostile(Faction self, FlagReading target, boolean targetOwnerWanted, ShipStance targetStance) {
        if (targetStance == ShipStance.SURRENDERED || target.isStruck()) return false;
        FlagKind shown = target.shown();
        return switch (self) {
            case NAVY -> shown == FlagKind.JOLLY_ROGER || targetStance == ShipStance.JOLLY_ROGER
                    || targetStance == ShipStance.UNMASKED || targetOwnerWanted;
            case PIRATES -> shown == FlagKind.NAVY || shown == FlagKind.MERCHANT || shown == FlagKind.NONE;
            case MERCHANTS -> shown == FlagKind.JOLLY_ROGER;
        };
    }

    /**
     * The faction a ship fights for, from the flag it shows: the navy flag makes it navy, the Jolly Roger a pirate,
     * anything else (merchant, banner, none, struck) a merchant.
     */
    public static Faction factionOf(FlagReading own) {
        return switch (own.shown()) {
            case NAVY -> Faction.NAVY;
            case JOLLY_ROGER -> Faction.PIRATES;
            case NONE, MERCHANT, CUSTOM -> Faction.MERCHANTS;
        };
    }
}

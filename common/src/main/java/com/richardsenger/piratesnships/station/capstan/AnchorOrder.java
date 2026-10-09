package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The orders of the capstan station (CRW3, docs/design.md §6, §7.5): let the anchor go, or heave it in. A
 * {@link CrewOrder}: the whistle's and the helm screen's one anchor toggle (WHS1, {@link #toggle}) and
 * {@code /pirates crew order drop_anchor} / {@code raise_anchor} give it, and the job board posts it for an unmanned
 * capstan.
 */
public enum AnchorOrder implements CrewOrder {
    DROP_ANCHOR,
    RAISE_ANCHOR;

    /**
     * The direction of the anchor toggle (WHS1) for the anchor in {@code phase}: let it go while it is stowed or coming
     * up (or the phase is unknown), weigh it while it runs out or holds; the same as a player's use of the capstan
     * ({@code ShipControls#useCapstan}). Pure.
     */
    public static AnchorOrder toggle(AnchorState.@Nullable Phase phase) {
        return phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.HOLDING ? RAISE_ANCHOR : DROP_ANCHOR;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "let go the anchor", "weigh the anchor". */
    @Override
    public String nameKey() {
        return "anchor_order." + Constants.MOD_ID + "." + id();
    }


    /** Drop: "The anchor is out already, captain". (A raise with the anchor stowed is unable, not nothing to do.) */
    @Override
    public String nothingToDoKey() {
        return "message." + Constants.MOD_ID + ".crew.anchor_nothing." + id();
    }

    /** Drop: no ground within the chain, or the anchor is switched off; raise: the anchor is stowed already. */
    @Override
    public String unableKey() {
        return "message." + Constants.MOD_ID + ".crew.anchor_unable." + id();
    }
}

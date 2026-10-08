package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.station.order.WhistleOrder;

/** English text of the capstan station (CRW3), for the station module's datagen. */
public final class CapstanLang {

    private CapstanLang() {
    }

    public static void lang(LangBuilder lang) {
        lang.add(AnchorOrder.DROP_ANCHOR.nameKey(), "let go the anchor")
                .add(AnchorOrder.RAISE_ANCHOR.nameKey(), "weigh anchor")
                .add(AnchorOrder.DROP_ANCHOR.ackKey(), "Aye, letting go the anchor!")
                .add(AnchorOrder.RAISE_ANCHOR.ackKey(), "Aye, heave away!")
                .add(AnchorOrder.DROP_ANCHOR.nothingToDoKey(), "The anchor is out already, captain")
                .add(AnchorOrder.RAISE_ANCHOR.nothingToDoKey(), "The anchor is stowed, captain")
                .add(AnchorOrder.DROP_ANCHOR.unableKey(), "No ground for the anchor within the chain's reach, captain!")
                .add(AnchorOrder.RAISE_ANCHOR.unableKey(), "The anchor is stowed already, captain")
                .add(WhistleOrder.DROP_ANCHOR.nameKey(), "Drop anchor")
                .add(WhistleOrder.DROP_ANCHOR.descriptionKey(), "Crew at the capstan let the anchor go")
                .add(WhistleOrder.RAISE_ANCHOR.nameKey(), "Weigh anchor")
                .add(WhistleOrder.RAISE_ANCHOR.descriptionKey(), "Crew at the capstan heave the anchor in until it is stowed")
                .add(ShipControls.KEY_ALREADY_OUT, "The anchor is out already")
                .add(ShipControls.KEY_STOWED, "The anchor is stowed");
    }
}

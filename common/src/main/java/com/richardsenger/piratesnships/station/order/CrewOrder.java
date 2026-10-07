package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An order the captain gives a crew member at a station (docs/design.md §6, §7.2), by whistle or by
 * {@code /pirates crew order}. Which station carries it out follows from its type: a station kind takes the orders of
 * its {@code StationKind#orderType()} ({@link SailOrder} → sail winch, {@link PumpOrder} → bilge pump). A ship-wide
 * order (whistle, command without a crew argument) reaches only the crew at stations that take it; the others ignore
 * it. An order addressed to one crew member at another kind of station is refused
 * ({@code Stations.OrderResult#WRONG_STATION}). Pure data: ids and translation keys.
 */
public interface CrewOrder {

    /** Command and lang id ("hoist", "pump"), unique over {@link #all()}. */
    String id();

    /** Translation key of the order as a phrase ("hoist the sails", "pump the bilge"). */
    String nameKey();

    /** Translation key of the crew's acknowledgement ("Aye, hoisting the sails!"). */
    String ackKey();

    /** Translation key of the crew's answer when there is nothing to do; gets the order name as argument. */
    String nothingToDoKey();

    /** Translation key of the crew's answer when its station cannot carry the order out here (no sails, pump off). */
    String unableKey();

    /** Every crew order, sail orders first. */
    static List<CrewOrder> all() {
        List<CrewOrder> out = new ArrayList<>(List.of(SailOrder.values()));
        out.addAll(List.of(PumpOrder.values()));
        return Collections.unmodifiableList(out);
    }

    /** All ids, for command suggestions. */
    static List<String> ids() {
        return all().stream().map(CrewOrder::id).toList();
    }

    /** The order with this {@link #id()}, empty for an unknown id. */
    static Optional<CrewOrder> byId(String id) {
        for (CrewOrder o : all()) {
            if (o.id().equals(id)) return Optional.of(o);
        }
        return Optional.empty();
    }
}

package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.sailing.oars.OarsSync;
import com.richardsenger.piratesnships.sailing.oars.RowOrder;
import com.richardsenger.piratesnships.station.capstan.AnchorOrder;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The orders of the captain's whistle radial menu (docs/design.md §7.2), in wheel order: entry 0 sits at the top, the
 * others follow clockwise. The wheel lays out whatever this list holds, so a later station order (crow's nest,
 * anchor, repair, boarding) is one more constant here with its {@link CrewOrder}; {@link WhistleOrders} gives it
 * to the crew at the stations that take it. An entry without a crew order is a whistle action of its own
 * ({@link #RELEASE}). Pure data: the icon is an item id that the client resolves.
 */
public enum WhistleOrder {
    HOIST(SailOrder.HOIST, Constants.id("yard")),
    REEF(SailOrder.REEF, ResourceLocation.withDefaultNamespace("white_wool")),
    FURL(SailOrder.FURL, ResourceLocation.withDefaultNamespace("lead")),
    PUMP(PumpOrder.PUMP, Constants.id("bilge_pump")),
    FIRE(CannonOrder.FIRE, Constants.id("cannon")),
    LOAD(CannonOrder.LOAD, ResourceLocation.withDefaultNamespace("gunpowder")),
    /** WS4a: the gun crews aim and fire at hostile ships by themselves until released. */
    FIRE_AT_WILL(CannonOrder.FIRE_AT_WILL, ResourceLocation.withDefaultNamespace("fire_charge")),
    /** CRW3: the crew at the capstans let the anchor go. */
    DROP_ANCHOR(AnchorOrder.DROP_ANCHOR, Constants.id("capstan")),
    /** CRW3: the crew at the capstans heave the anchor in. */
    RAISE_ANCHOR(AnchorOrder.RAISE_ANCHOR, ResourceLocation.withDefaultNamespace("chain")),
    /**
     * OAR1: a toggle. "Row" while the ship does not row: the free crew take oars at benches and the rail; "Ship oars"
     * while it rows ({@code sailing.oars.Rowing#toggle}). The label follows the ship's state ({@link #shownNameKey}).
     */
    ROW(RowOrder.ROW, Constants.id("oar")),
    RELEASE(null, Constants.id("sail_winch"));

    private static final List<WhistleOrder> ENTRIES = List.of(values());

    private final @Nullable CrewOrder order;
    private final ResourceLocation icon;

    WhistleOrder(@Nullable CrewOrder order, ResourceLocation icon) {
        this.order = order;
        this.icon = icon;
    }

    /** The menu entries in wheel order. */
    public static List<WhistleOrder> entries() {
        return ENTRIES;
    }

    /** The order with this {@link #id()}, empty for an unknown id (a stale or forged payload). */
    public static Optional<WhistleOrder> byId(String id) {
        for (WhistleOrder o : ENTRIES) {
            if (o.id().equals(id)) return Optional.of(o);
        }
        return Optional.empty();
    }

    /** Network and lang id ("hoist"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The crew order this entry gives, or null for a whistle action that is no station order ({@link #RELEASE}). */
    public @Nullable CrewOrder order() {
        return order;
    }

    /** The sail order this entry issues, or null for an order that is not a sail order. */
    public @Nullable SailOrder sail() {
        return order instanceof SailOrder s ? s : null;
    }

    /** Item shown in the entry's sector. */
    public ResourceLocation icon() {
        return icon;
    }

    /** Translation key of the short menu name ("Hoist sails"). */
    public String nameKey() {
        return "whistle_order." + Constants.MOD_ID + "." + id();
    }

    /** Translation key of the one-line description under the wheel. */
    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    /** Whether this entry is a toggle whose label follows a state of the ship (OAR1: {@link #ROW}). */
    public boolean isToggle() {
        return this == ROW;
    }

    /** The name key of the toggle's other direction ("Ship oars"); {@link #nameKey()} for an entry that is no toggle. */
    public String toggledNameKey() {
        return isToggle() ? nameKey() + ".toggled" : nameKey();
    }

    /** The description key of the toggle's other direction. */
    public String toggledDescriptionKey() {
        return isToggle() ? toggledNameKey() + ".desc" : descriptionKey();
    }

    /** Client: the name key to show for the ship the player stands on ("Ship oars" while it rows). */
    public String shownNameKey() {
        return toggledOnClient() ? toggledNameKey() : nameKey();
    }

    /** Client: the description key to show for the ship the player stands on. */
    public String shownDescriptionKey() {
        return toggledOnClient() ? toggledDescriptionKey() : descriptionKey();
    }

    private boolean toggledOnClient() {
        return this == ROW && OarsSync.clientRowing();
    }
}

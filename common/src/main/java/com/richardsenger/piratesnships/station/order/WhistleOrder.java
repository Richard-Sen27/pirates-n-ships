package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.order.ClientBoarding;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.sailing.oars.OarsSync;
import com.richardsenger.piratesnships.sailing.oars.RowOrder;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
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
 * ({@link #RELEASE}), except {@link #ANCHOR}, whose crew order follows the anchor's state (WHS1, {@link #order(AnchorState.Phase)}).
 * {@link #BOARD} (BRD3) is a toggle too: "Board!" while no boarding party of the ship is out, "Back aboard" while one
 * is ({@link #nameKey(AnchorState.Phase, boolean)}); it is no station order, the server sends the crew across or back.
 * Pure data: the icon is an item id that the client resolves.
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
    /**
     * WHS1: one toggle for the anchor (CRW3's "Drop anchor" and "Weigh anchor" before): the crew at the capstans let it go
     * or heave it in, whichever the anchor's state at that moment needs ({@link AnchorOrder#toggle}); the server resolves
     * the direction, the client only names it.
     */
    ANCHOR(null, Constants.id("capstan")),
    /**
     * OAR1: a toggle too. "Row" while the ship does not row: the free crew take oars at benches and the rail; "Ship oars"
     * while it rows ({@code sailing.oars.Rowing#toggle}). The label follows the ship's rowing state ({@link #shownNameKey}).
     */
    ROW(RowOrder.ROW, Constants.id("oar")),
    /**
     * BRD3: "Board!" sends the crew (gunners and helmsman stay) across a gangway or a narrow gap onto the ship alongside
     * to fight; while they are out the same entry reads "Back aboard" and calls them home
     * ({@code combat.boarding.order.BoardingParties}). The server decides by the party's state, the client only names it.
     */
    BOARD(null, Constants.id("cutlass")),
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

    /**
     * The fixed crew order this entry gives, or null for a whistle action that is no station order ({@link #RELEASE})
     * and for {@link #ANCHOR}, whose order depends on the anchor ({@link #order(AnchorState.Phase)}).
     */
    public @Nullable CrewOrder order() {
        return order;
    }

    /**
     * The crew order this entry gives on a ship whose anchor is in {@code anchor} (null: unknown, or no anchor runtime):
     * {@link #ANCHOR} resolves to {@link AnchorOrder#toggle}, every other entry to {@link #order()}. Pure.
     */
    public @Nullable CrewOrder order(AnchorState.@Nullable Phase anchor) {
        return this == ANCHOR ? AnchorOrder.toggle(anchor) : order;
    }

    /** Whether the order (and its label) follows the anchor's state (WHS1). */
    public boolean followsAnchor() {
        return this == ANCHOR;
    }

    /** Whether the order (and its label) follows the ship's boarding party: out or not (BRD3). */
    public boolean followsBoarding() {
        return this == BOARD;
    }

    /** Every crew order this entry can give: its fixed one, both anchor directions for {@link #ANCHOR}, none for release. */
    public List<CrewOrder> orders() {
        if (this == ANCHOR) return List.of(AnchorOrder.DROP_ANCHOR, AnchorOrder.RAISE_ANCHOR);
        return order == null ? List.of() : List.of(order);
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

    /**
     * The label of this entry on a ship whose anchor is in {@code anchor} (WHS1): {@link #ANCHOR} reads "Drop anchor"
     * while the anchor is stowed or coming up (or unknown), "Raise anchor" while it runs out or holds; every other
     * entry its {@link #nameKey()}. Pure.
     */
    public String nameKey(AnchorState.@Nullable Phase anchor) {
        return this == ANCHOR ? anchorKey(AnchorOrder.toggle(anchor)) : nameKey();
    }

    /** {@link #descriptionKey()} following the anchor like {@link #nameKey(AnchorState.Phase)}. */
    public String descriptionKey(AnchorState.@Nullable Phase anchor) {
        return this == ANCHOR ? anchorKey(AnchorOrder.toggle(anchor)) + ".desc" : descriptionKey();
    }

    /**
     * The label of this entry on a ship whose anchor is in {@code anchor} and whose boarding party is out or not
     * ({@code boardingOut}, BRD3): {@link #BOARD} reads "Board!" or "Back aboard"; the anchor follows
     * {@link #nameKey(AnchorState.Phase)}. Pure.
     */
    public String nameKey(AnchorState.@Nullable Phase anchor, boolean boardingOut) {
        return this == BOARD && boardingOut ? toggledNameKey() : nameKey(anchor);
    }

    /** {@link #descriptionKey()} following the anchor and the boarding party like {@link #nameKey(AnchorState.Phase, boolean)}. */
    public String descriptionKey(AnchorState.@Nullable Phase anchor, boolean boardingOut) {
        return this == BOARD && boardingOut ? toggledDescriptionKey() : descriptionKey(anchor);
    }

    /** Label key of the boarding toggle while a party is out ("Back aboard", BRD3). */
    public static final String BACK_ABOARD_KEY = "whistle_order." + Constants.MOD_ID + ".back_aboard";

    /** Label key of the anchor toggle in direction {@code direction} ("Drop anchor", "Raise anchor"). */
    public static String anchorKey(AnchorOrder direction) {
        return "whistle_order." + Constants.MOD_ID + "." + direction.id();
    }

    /**
     * Whether this entry is a toggle whose label follows the ship's state: the oars (OAR1: {@link #ROW}, rowing or not)
     * and boarding (BRD3: {@link #BOARD}, a party out or not).
     */
    public boolean isToggle() {
        return this == ROW || this == BOARD;
    }

    /**
     * The name key of the toggle's other direction ("Ship oars", "Back aboard"); {@link #nameKey()} for an entry that is
     * no toggle.
     */
    public String toggledNameKey() {
        if (this == BOARD) return BACK_ABOARD_KEY;
        return isToggle() ? nameKey() + ".toggled" : nameKey();
    }

    /** The description key of the toggle's other direction. */
    public String toggledDescriptionKey() {
        return isToggle() ? toggledNameKey() + ".desc" : descriptionKey();
    }

    /**
     * Client: the label to show on the ship the player stands on, whose anchor is in {@code anchor}: "Ship oars" while it
     * rows (OAR1, {@link OarsSync#clientRowing}), "Back aboard" while the player's boarding party is out (BRD3,
     * {@link ClientBoarding#outHere}), else {@link #nameKey(AnchorState.Phase)}.
     */
    public String shownNameKey(AnchorState.@Nullable Phase anchor) {
        return toggledOnClient() ? toggledNameKey() : nameKey(anchor);
    }

    /** Client: the description to show, like {@link #shownNameKey}. */
    public String shownDescriptionKey(AnchorState.@Nullable Phase anchor) {
        return toggledOnClient() ? toggledDescriptionKey() : descriptionKey(anchor);
    }

    private boolean toggledOnClient() {
        return this == ROW && OarsSync.clientRowing() || this == BOARD && ClientBoarding.outHere();
    }
}

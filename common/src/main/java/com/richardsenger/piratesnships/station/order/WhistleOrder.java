package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The orders of the captain's whistle radial menu (docs/design.md §7.2), in wheel order: entry 0 sits at the top, the
 * others follow clockwise. The wheel lays out whatever this list holds, so a later station order (cannons, crow's
 * nest, anchor, repair, boarding) is one more constant here with its {@link CrewOrder}; {@link WhistleOrders} gives it
 * to the crew at the stations that take it. An entry without a crew order is a whistle action of its own
 * ({@link #RELEASE}). Pure data: the icon is an item id that the client resolves.
 */
public enum WhistleOrder {
    HOIST(SailOrder.HOIST, Constants.id("yard")),
    REEF(SailOrder.REEF, ResourceLocation.withDefaultNamespace("white_wool")),
    FURL(SailOrder.FURL, ResourceLocation.withDefaultNamespace("lead")),
    PUMP(PumpOrder.PUMP, Constants.id("bilge_pump")),
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
}

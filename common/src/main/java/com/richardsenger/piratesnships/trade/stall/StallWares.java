package com.richardsenger.piratesnships.trade.stall;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What each village stall sells and buys (LIFE1, docs/design.md §10.3), pure. A ware that is a trade good
 * ({@link TradeGoods}: fish, rum, cloth) goes through the port's market when the port trades it (its live price, stock
 * and the reputation swing, {@code trade.net.MarketBackend}); every other ware, and a good the port does not trade, has
 * a fixed price ({@link StallPricing}) from the ware's base price (a good's own base price) times
 * {@code mobs.villagers.vendor_price_scale}, swung by the villagers' reputation like the market.
 *
 * <table>
 *   <caption>The stalls' wares (doubloons per unit before the scale)</caption>
 *   <tr><th>stall</th><th>wares</th></tr>
 *   <tr><td>fish market</td><td>fish (trade good, cod), salted fish 3</td></tr>
 *   <tr><td>provisions</td><td>salt pork 4, hardtack 3, rum (trade good)</td></tr>
 *   <tr><td>chandlery</td><td>rope 3, cloth (trade good), nails 2</td></tr>
 *   <tr><td>shipwright</td><td>hull patch 12, shipwright's toolkit 40 (sold only)</td></tr>
 * </table>
 */
public final class StallWares {

    /**
     * One line of a stall: the item sold (also the line's id on the market screen, unless it is a trade good, whose
     * line carries the good's id), the trade good it is, if any, its base price per unit, and whether the vendor buys it
     * back.
     */
    public record Ware(ResourceLocation item, Optional<ResourceLocation> good, int basePrice, boolean buysBack) {
        /** The id of its market line: the good's id for a trade good, else the item's. */
        public ResourceLocation lineId() {
            return good.orElse(item);
        }
    }

    private static final Map<StallKind, List<Ware>> WARES = new EnumMap<>(StallKind.class);

    static {
        WARES.put(StallKind.FISH, List.of(
                good(TradeGoods.FISH, "minecraft:cod", 2),
                ware("salted_fish", 3, true)));
        WARES.put(StallKind.PROVISIONS, List.of(
                ware("salt_pork", 4, true),
                ware("hardtack", 3, true),
                good(TradeGoods.RUM, Constants.id("rum").toString(), 4)));
        WARES.put(StallKind.CHANDLERY, List.of(
                ware("rope", 3, true),
                good(TradeGoods.CLOTH, Constants.id("cloth").toString(), 6),
                ware("nails", 2, true)));
        WARES.put(StallKind.SHIPWRIGHT, List.of(
                ware("hull_patch", 12, true),
                ware("shipwright_toolkit", 40, false)));
    }

    private StallWares() {
    }

    private static Ware ware(String item, int price, boolean buysBack) {
        return new Ware(Constants.id(item), Optional.empty(), price, buysBack);
    }

    private static Ware good(ResourceLocation good, String item, int price) {
        return new Ware(ResourceLocation.parse(item), Optional.of(good), price, true);
    }

    /** The wares of {@code stall}, in the order the screen lists them. */
    public static List<Ware> of(StallKind stall) {
        return WARES.get(stall);
    }

    /** The ware of {@code stall} whose market line is {@code lineId}. */
    public static Optional<Ware> byLine(StallKind stall, ResourceLocation lineId) {
        return of(stall).stream().filter(w -> w.lineId().equals(lineId)).findFirst();
    }
}

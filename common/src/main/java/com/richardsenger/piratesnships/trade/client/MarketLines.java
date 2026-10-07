package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.net.MarketView;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Pure helpers of the market screen (no client classes, tested with JUnit): which goods a port lists and in which
 * order, quantity parsing and clamping, what the player can afford and how prices read.
 */
public final class MarketLines {

    /** Quick quantity buttons of the screen. */
    public static final int[] QUICK_QUANTITIES = {1, 8, 16, 64};

    private MarketLines() {
    }

    /**
     * The goods a port lists: every good it trades (not {@link GoodRole#NOT_TRADED}), produced goods first (cheap
     * here), then neutral, then demanded (sell here), each group by good id.
     */
    public static List<MarketView.GoodLine> order(List<MarketView.GoodLine> lines) {
        List<MarketView.GoodLine> out = new ArrayList<>();
        for (MarketView.GoodLine l : lines) if (l.role().traded()) out.add(l);
        out.sort(Comparator.comparingInt((MarketView.GoodLine l) -> rank(l.role())).thenComparing(l -> l.good().toString()));
        return out;
    }

    private static int rank(GoodRole role) {
        return switch (role) {
            case PRODUCES -> 0;
            case NEUTRAL -> 1;
            case DEMANDS -> 2;
            case NOT_TRADED -> 3;
        };
    }

    /** {@code quantity} clamped to {@code 1..max} ({@code max} below 1 counts as 1). */
    public static int clampQuantity(int quantity, int max) {
        return Math.max(1, Math.min(quantity, Math.max(1, max)));
    }

    /** The quantity typed into the field, clamped to {@code 1..max}; empty for blank or non-numeric text. */
    public static OptionalInt parseQuantity(String text, int max) {
        if (text == null) return OptionalInt.empty();
        String t = text.trim();
        if (t.isEmpty() || !t.chars().allMatch(Character::isDigit)) return OptionalInt.empty();
        // Long digit strings would overflow: anything that long is above max anyway
        if (t.length() > 9) return OptionalInt.of(clampQuantity(Integer.MAX_VALUE, max));
        return OptionalInt.of(clampQuantity(Integer.parseInt(t), max));
    }

    /**
     * How many units {@code coins} buy at the average unit price of a quote ({@code total} for {@code quantity}
     * units). Prices rise while buying, so this is an upper estimate for amounts above {@code quantity}.
     */
    public static long affordable(long coins, long total, int quantity) {
        if (quantity <= 0 || coins <= 0) return 0;
        if (total <= 0) return Long.MAX_VALUE;
        return (long) Math.floor((double) coins * quantity / total);
    }

    /** Whether the buy quote can go through with {@code coins}. */
    public static boolean canBuy(MarketView.Price buy, long coins) {
        return buy.outcome() == Market.Outcome.OK && buy.total() <= coins;
    }

    /** Whether the sell quote can go through when the player carries {@code carried} matching units. */
    public static boolean canSell(MarketView.Price sell, int carried, int quantity) {
        return sell.outcome() == Market.Outcome.OK && carried >= quantity;
    }

    /** Doubloons with thousands separators: {@code 12345} reads {@code "12,345"}. */
    public static String formatCoins(long coins) {
        String digits = Long.toString(Math.abs(coins));
        StringBuilder sb = new StringBuilder();
        int lead = digits.length() % 3;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) sb.append(',');
            sb.append(digits.charAt(i));
        }
        return coins < 0 ? "-" + sb : sb.toString();
    }

    /** A quote as the screen shows it: the total, {@code "max N"} over a stock limit, {@code "-"} if not traded. */
    public static String price(MarketView.Price p) {
        return switch (p.outcome()) {
            case OK -> formatCoins(p.total());
            case LIMIT -> "max " + formatCoins(p.available());
            case NOT_TRADED, INVALID_QUANTITY -> "-";
        };
    }

    /** A readable port name from its id: {@code pirates_n_ships:debug/port_royal} reads {@code "Port Royal"}. */
    public static String portName(ResourceLocation port) {
        String path = port.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String word : last.split("[_\\-]+")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    /** Index of the first visible row for a scroll offset, clamped so the last page stays full. */
    public static int clampScroll(int scroll, int rows, int visible) {
        return Math.max(0, Math.min(scroll, rows - Math.max(1, visible)));
    }
}

package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.market.Climate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lang keys and English text of the recipe-viewer compat (JEI1, docs/compat.md). Plain strings, no JEI classes: the
 * datagen of {@link CompatModule} and the JEI plugin ({@code compat.jei}) both read them.
 */
public final class CompatText {

    static final String KEY = Constants.MOD_ID + ".jei.";

    public static final String CATEGORY_MARKET = KEY + "category.market";
    public static final String CATEGORY_PROVISIONS = KEY + "category.provisions";

    public static final String INFO_SOURCE = KEY + "info.source";

    public static final String MARKET_BUY = KEY + "market.buy";
    public static final String MARKET_SELL = KEY + "market.sell";
    public static final String MARKET_PRODUCED = KEY + "market.produced";
    public static final String MARKET_DEMANDED = KEY + "market.demanded";
    public static final String MARKET_PRODUCED_IN = KEY + "market.produced_in";
    public static final String MARKET_PRODUCED_NOWHERE = KEY + "market.produced_nowhere";
    public static final String MARKET_WEIGHT = KEY + "market.weight";

    public static final String PROVISION_FOOD = KEY + "provisions.food";
    public static final String PROVISION_WATER = KEY + "provisions.water";
    public static final String PROVISION_RUM = KEY + "provisions.rum";
    public static final String PROVISION_PRESERVED = KEY + "provisions.preserved";
    public static final String PROVISION_SPOILS = KEY + "provisions.spoils";
    public static final String PROVISION_ANTI_SCURVY = KEY + "provisions.anti_scurvy";
    public static final String PROVISION_WEIGHT = KEY + "provisions.weight";

    private CompatText() {
    }

    /** {@code pirates_n_ships.jei.climate.<name>}. */
    public static String climate(Climate climate) {
        return KEY + "climate." + climate.name().toLowerCase(Locale.ROOT);
    }

    /** Every key with its English text, for datagen. */
    public static Map<String, String> lang() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(CATEGORY_MARKET, "Harbor Market");
        m.put(CATEGORY_PROVISIONS, "Ship's Provisions");
        m.put(INFO_SOURCE, "From the Captain's Almanac, chapter \"%s\".");
        m.put(MARKET_BUY, "Buy: %s doubloons");
        m.put(MARKET_SELL, "Sell: %s doubloons");
        m.put(MARKET_PRODUCED, "Where it is made: buy for %s");
        m.put(MARKET_DEMANDED, "Where it is wanted: sell for %s");
        m.put(MARKET_PRODUCED_IN, "Made in %s ports");
        m.put(MARKET_PRODUCED_NOWHERE, "Made only by some kinds of port");
        m.put(MARKET_WEIGHT, "Cargo weight: %s per item");
        m.put(PROVISION_FOOD, "Food: %s nutrition per item");
        m.put(PROVISION_WATER, "Water: %s rations per item");
        m.put(PROVISION_RUM, "Rum: %s rations per item");
        m.put(PROVISION_PRESERVED, "Never spoils");
        m.put(PROVISION_SPOILS, "Spoils after %s days in the pantry");
        m.put(PROVISION_ANTI_SCURVY, "Keeps scurvy away");
        m.put(PROVISION_WEIGHT, "Cargo weight: %s per item");
        m.put(climate(Climate.TROPICAL), "tropical");
        m.put(climate(Climate.TEMPERATE), "temperate");
        m.put(climate(Climate.ARID), "arid");
        m.put(climate(Climate.COLD), "cold");
        return m;
    }
}

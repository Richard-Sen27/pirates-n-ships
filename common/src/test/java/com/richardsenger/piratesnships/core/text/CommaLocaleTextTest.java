package com.richardsenger.piratesnships.core.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudConfig;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudText;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetItem;
import com.richardsenger.piratesnships.station.lookout.LookoutLang;
import com.richardsenger.piratesnships.station.lookout.SightingCue;
import com.richardsenger.piratesnships.station.lookout.SightingPayload;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.net.MarketView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FIX2: on a machine whose locale writes a decimal comma (German), the mod's texts still write a dot, because they are
 * English and the lang file says "6.3". Run from {@code common/} under {@link Locale#GERMANY}.
 */
class CommaLocaleTextTest {

    private static final Path LANG = Path.of("src/generated/resources/assets/pirates_n_ships/lang/en_us.json");

    private Locale saved;

    @BeforeEach
    void german() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(saved);
    }

    /** {@code c} with its arguments put into the English template, as the client's language does with {@code %s}. */
    private static String english(Component c) throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        TranslatableContents t = (TranslatableContents) c.getContents();
        String text = lang.get(t.getKey()).getAsString();
        for (Object arg : t.getArgs()) {
            text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return text;
    }

    @Test
    void theLocaleIsReallyComma() {
        assertEquals("6,3", String.format("%.1f", 6.3), "the test does not run under a comma locale");
    }

    @Test
    void helperWritesADot() {
        assertEquals("6.3", Num.one(6.3));
        assertEquals("0.25", Num.two(0.25));
        assertEquals("12", Num.whole(12.4));
        assertEquals("8", Num.trim(8.0));
        assertEquals("7.5", Num.trim(7.5));
        assertEquals("1.5 2.0", Num.format("%.1f %.1f", 1.5, 2.0));
    }

    @Test
    void ratlineNetRefusalsWriteADot() throws IOException {
        assertEquals("Too long: 6.3 of 7.5 blocks", english(RatlineNetItem.tooLong(6.3, 7.5)));
        assertEquals("Too long: 9.0 of 8 blocks", english(RatlineNetItem.tooLong(9.0, 8.0)));
        assertEquals("Too flat: 12°, at least 17.5°", english(RatlineNetItem.tooFlat(12.4, 17.5)));
    }

    @Test
    void tradePricesUseTheEnglishSeparator() {
        assertEquals("12,345", MarketLines.formatCoins(12345));
        assertEquals("max 1,000", MarketLines.price(new MarketView.Price(0, 1000, Market.Outcome.LIMIT)));
        assertEquals("2,500", MarketLines.price(new MarketView.Price(2500, 64, Market.Outcome.OK)));
    }

    @Test
    void lookoutDistanceHasNoGrouping() {
        assertEquals("Land ahead, 1020 blocks",
                SightingCue.text(SightingPayload.What.LAND, "", 3, 1020, k -> LookoutLang.ENGLISH.getOrDefault(k, k)));
    }

    @Test
    void shipStatusSpeedWritesADot() throws IOException {
        assertEquals("6.3", ShipHudText.speedNumber(ShipHudConfig.SpeedUnit.BLOCKS, 6.3));
        assertEquals("6.3 b/s", english(ShipHudText.speed(ShipHudConfig.SpeedUnit.BLOCKS, 6.3)));
        assertEquals("12.2 kn", english(ShipHudText.speed(ShipHudConfig.SpeedUnit.KNOTS, 12.2 * ShipHudText.KNOT)));
    }
}

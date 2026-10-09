package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout;

/**
 * Client config section {@code career_hud} (HON1, HUD5, docs/design.md §4.6 and §15): the small rank box. Declared on
 * both sides from {@code CareerModule.registerConfig()} so datagen and the config screen see it (no client classes
 * here); only the client reads it. HUD5 replaced {@code enabled}, {@code x} and {@code y} with {@code mode},
 * {@code show_seconds} and {@code corner}; the box keeps {@code ship_hud.margin} from the screen edges.
 */
public final class RankHudConfig {

    private static final ConfigSection S = ModConfigs.client("career_hud", "The rank box: your career title, reputation and letter of marque");

    public static final ConfigValue<RankHudVisibility.Mode> MODE = S.enumValue("mode", RankHudVisibility.Mode.ON_CHANGE,
            "When the rank box shows: OFF never, ON_CHANGE for a few seconds on joining, after a promotion or other "
                    + "change of rank and after closing the career screen, ALWAYS all the time");
    public static final ConfigValue<Integer> SHOW_SECONDS = S.intRange("show_seconds",
            RankHudVisibility.DEFAULT_SHOW_SECONDS, RankHudVisibility.MIN_SHOW_SECONDS, RankHudVisibility.MAX_SHOW_SECONDS,
            "Seconds the rank box stays on screen in mode ON_CHANGE, fading in and out included");
    public static final ConfigValue<ShipHudLayout.Corner> CORNER = S.enumValue("corner", ShipHudLayout.Corner.TOP_LEFT,
            "Screen corner of the rank box: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. It keeps clear of the "
                    + "hotbar, the stamina bar, the effect icons and the ship HUD panels in the same corner");

    private RankHudConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}

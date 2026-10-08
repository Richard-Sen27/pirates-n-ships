package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.reputation.Faction;

import java.util.Locale;
import java.util.Optional;

/**
 * Deeds that shift a player's reputation (docs/design.md §15, REP1). Each deed has a default delta per faction; the
 * live deltas are the config values {@code reputation.deeds.<deed>.<faction>} ({@link DeedTable}). Careers and the
 * faction state consume the same deeds later through {@link Deeds#listen}.
 */
public enum Deed {
    //                   navy pirates villagers
    ATTACK_NAVY(-3, 1, 0),
    KILL_NAVY(-15, 5, 0),
    ATTACK_PIRATE(1, -3, 0),
    KILL_PIRATE(5, -15, 2),
    ATTACK_VILLAGER(-2, 0, -5),
    KILL_VILLAGER(-10, 0, -25),
    /** A cannonball hit on a ship under a merchant or neutral flag (the law's {@code attack_neutral_ship}). */
    ATTACK_MERCHANT_SHIP(-3, 2, -3),
    /** Capturing a merchant ship's cargo (the law's {@code piracy}). */
    PLUNDER_MERCHANT(-8, 8, -5),
    /** Selling plunder to a pirate fence. */
    FENCE_PLUNDER(0, 3, 0),
    /** A trade at a seafarer village's market, counted at most {@code reputation.village_trade_daily_cap} times a day. */
    TRADE_VILLAGE(0, 0, 1),
    TURN_IN_PIRATE(6, -5, 0),
    PAY_FINE(3, 0, 0),
    /** Caught by the navy under false colours. */
    FLY_FALSE_COLOURS(-10, 0, 0);

    private final int navy;
    private final int pirates;
    private final int villagers;

    Deed(int navy, int pirates, int villagers) {
        this.navy = navy;
        this.pirates = pirates;
        this.villagers = villagers;
    }

    /** Config key and command argument: {@code kill_navy}, {@code fly_false_colours}, ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".reputation.deed." + id();
    }

    /** The default delta for {@code faction}. */
    public int defaultDelta(Faction faction) {
        return switch (faction) {
            case NAVY -> navy;
            case PIRATES -> pirates;
            case VILLAGERS -> villagers;
        };
    }

    public static Optional<Deed> byId(String id) {
        for (Deed d : values()) if (d.id().equals(id)) return Optional.of(d);
        return Optional.empty();
    }
}

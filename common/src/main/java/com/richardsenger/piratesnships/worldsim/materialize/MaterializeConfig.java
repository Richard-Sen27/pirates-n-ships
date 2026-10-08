package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Server config {@code world_simulation.materialize} (WS3b, design.md §10.4, §17). The distance itself is
 * {@code world_simulation.materialize_radius} ({@link WorldSimConfig#MATERIALIZE_RADIUS}), clamped at runtime to what
 * the server keeps loaded around a player ({@link #radius}).
 */
public final class MaterializeConfig {

    private static final ConfigSection S = WorldSimConfig.sub("materialize",
            "NPC voyages near a player become real ships with crew, fighters, flag and cargo");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Turn NPC voyages near players into real ships. Off = voyages stay abstract records (ships already at sea stay)");
    public static final ConfigValue<Integer> MAX_MATERIALIZED = S.intRange("max_materialized", 4, 0, 64,
            "Most NPC voyages that are real ships at the same time in the whole world");
    public static final ConfigValue<Integer> LINGER_TICKS = S.intRange("linger_ticks", 200, 20, 72_000,
            "Ticks an NPC ship stays real after the last player left its surroundings (materialize_radius + linger_margin)");
    public static final ConfigValue<Integer> LINGER_MARGIN = S.intRange("linger_margin", 64, 0, 1024,
            "Blocks beyond materialize_radius within which a player still keeps an NPC ship real");
    public static final ConfigValue<Integer> TICKET_TICKS = S.intRange("ticket_ticks", 600, 20, 72_000,
            "Ticks the chunks at a voyage's spawn point are kept loaded while it waits to appear");
    public static final ConfigValue<Integer> RETRY_TICKS = S.intRange("retry_ticks", 600, 20, 72_000,
            "Ticks before a voyage that could not appear (land, no water, obstructed) or got stuck is tried again");
    public static final ConfigValue<Double> SUNK_FLOOD_FRACTION = S.doubleRange("sunk_flood_fraction", 0.8, 0.05, 1.0,
            "Part of the hull's inside volume under water at which an NPC ship counts as sunk");
    public static final ConfigValue<Integer> SUBMERGED_TICKS = S.intRange("submerged_ticks", 100, 1, 72_000,
            "Ticks the whole ship must stay under the sea surface to count as sunk");
    public static final ConfigValue<Integer> CAPTURE_HOLD_TICKS = S.intRange("capture_hold_ticks", 100, 1, 72_000,
            "Ticks a player must stay aboard an NPC ship whose fighters are all dead to capture it");
    public static final ConfigValue<Integer> SHOOTER_MEMORY_TICKS = S.intRange("shooter_memory_ticks", 1200, 1, 72_000,
            "Ticks a player's cannon hit still counts as the cause when the ship sinks");
    public static final ConfigValue<Integer> CREW_PER_SHIP = S.intRange("crew_per_ship", 3, 0, 16,
            "Deckhands on an NPC ship besides its helmsman (they work the sails through the job board)");
    public static final ConfigValue<Integer> FIGHTERS_MERCHANT = S.intRange("fighters_merchant", 2, 0, 16,
            "Armed sailors guarding a merchant ship");
    public static final ConfigValue<Integer> FIGHTERS_NAVY = S.intRange("fighters_navy", 4, 0, 16,
            "Navy soldiers on a navy ship (the first is an officer)");
    public static final ConfigValue<Integer> FIGHTERS_PIRATE = S.intRange("fighters_pirate", 4, 0, 16,
            "Pirates on a pirate ship");
    public static final ConfigValue<Boolean> CARGO_IS_PLUNDER = S.bool("cargo_is_plunder", true,
            "Goods aboard an NPC ship carry the plunder mark, so whatever a player takes from it sells as plunder");

    private MaterializeConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
    }

    /** Whether new ships appear (both toggles). */
    public static boolean active() {
        return WorldSimConfig.ENABLED.get() && ENABLED.get();
    }

    /**
     * {@code materialize_radius}, at most {@code (view distance − 1) × 16} so that a ship never appears in chunks the
     * player does not keep loaded (at least 32).
     */
    public static int radius(MinecraftServer server) {
        return clampRadius(WorldSimConfig.MATERIALIZE_RADIUS.get(), server.getPlayerList().getViewDistance());
    }

    /** Pure part of {@link #radius}. */
    public static int clampRadius(int configured, int viewDistanceChunks) {
        int loaded = Math.max(32, (viewDistanceChunks - 1) * 16);
        return Math.min(configured, loaded);
    }

    /** Fighters aboard a fresh ship of {@code faction}. */
    public static int fighters(Faction faction) {
        return switch (faction) {
            case MERCHANTS -> FIGHTERS_MERCHANT.get();
            case NAVY -> FIGHTERS_NAVY.get();
            case PIRATES -> FIGHTERS_PIRATE.get();
        };
    }

    public static EndingRules.Params endingParams() {
        return new EndingRules.Params(SUNK_FLOOD_FRACTION.get(), SUBMERGED_TICKS.get(), CAPTURE_HOLD_TICKS.get(),
                SHOOTER_MEMORY_TICKS.get());
    }
}

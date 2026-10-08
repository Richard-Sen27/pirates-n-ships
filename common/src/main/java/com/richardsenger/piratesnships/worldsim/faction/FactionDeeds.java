package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * The adapter from REP1's deeds to the faction state (design.md §10.4, §15; WS1b): every recorded deed with a faction
 * side is reported once through {@link Factions#reportDeed} (scaled by {@code deed_scale}). Per the rule in
 * {@link FactionEvent}, a player's act reaches the factions only here; NPC outcomes are world events.
 * <p>
 * Toggles: {@link Deeds#record} tells no listener while {@code reputation.enabled} is off, and
 * {@link Factions#reportDeed} changes nothing while {@code world_simulation.factions.enabled} (or
 * {@code world_simulation.enabled}) is off; both are checked here as well.
 */
public final class FactionDeeds {

    private static boolean registered;

    private FactionDeeds() {
    }

    /** Listens to the deeds. Called once from {@link FactionModule}; a second call does nothing. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Deeds.listen(FactionDeeds::onDeed);
    }

    static void onDeed(ServerPlayer player, Deed deed, DeedContext context, Map<Faction, Integer> applied) {
        if (!Reputation.enabled() || !FactionConfig.active()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        // The navy score before this deed: kill_pirate itself raises it, which must not make the player navy-aligned
        int navyBefore = Reputation.get(player, Faction.NAVY) - applied.getOrDefault(Faction.NAVY, 0);
        eventFor(deed, navyBefore).ifPresent(event -> Factions.reportDeed(server, event));
    }

    /**
     * The faction event of {@code deed} done by a player whose navy reputation (before the deed) is
     * {@code navyReputation}, or empty if the deed has no faction side. A pirate killed by a player counts as
     * {@link FactionEvent#PIRATE_KILLED_BY_NAVY} only while the player is navy-aligned (navy reputation &ge; 0).
     */
    public static Optional<FactionEvent> eventFor(Deed deed, int navyReputation) {
        return Optional.ofNullable(switch (deed) {
            case PLUNDER_MERCHANT -> FactionEvent.MERCHANT_PLUNDERED;
            case KILL_NAVY -> FactionEvent.NAVY_KILLED_BY_PIRATE;
            case ATTACK_NAVY -> FactionEvent.NAVY_ATTACKED;
            case KILL_PIRATE -> navyReputation >= 0 ? FactionEvent.PIRATE_KILLED_BY_NAVY : null;
            case ATTACK_VILLAGER, ATTACK_MERCHANT_SHIP -> FactionEvent.MERCHANT_ATTACKED;
            case TURN_IN_PIRATE -> FactionEvent.PIRATE_TURNED_IN;
            case FENCE_PLUNDER -> FactionEvent.PLUNDER_FENCED;
            case TRADE_VILLAGE -> FactionEvent.PORT_TRADE;
            case ATTACK_PIRATE, KILL_VILLAGER, PAY_FINE, FLY_FALSE_COLOURS -> null;
        });
    }
}

package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The deeds API (docs/design.md §15, REP1): the one way gameplay shifts reputation. {@link #record} applies the
 * configured deltas ({@code reputation.deeds.<deed>.<faction>}) through {@link Reputation#adjust} and tells the
 * {@link #listen listeners}. A no-op while {@code reputation.enabled} is off.
 *
 * <pre>{@code
 * Deeds.record(player, Deed.KILL_PIRATE, DeedContext.victim(pirate));
 * Deeds.listen((player, deed, context, applied) -> careers.onDeed(player, deed));   // at mod construction
 * }</pre>
 *
 * Where deeds come from: combat ({@link CombatDeeds}), the law's crime hooks, fines and pirate turn-ins
 * ({@link LawDeeds}), and the markets ({@code rpg.market.MarketReputation}).
 */
public final class Deeds {

    private static final List<DeedListener> LISTENERS = new CopyOnWriteArrayList<>();

    private Deeds() {
    }

    /** Adds a listener for every recorded deed. Register once, at mod construction. */
    public static void listen(DeedListener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Records {@code deed} for {@code player}: every non-zero configured delta is added to the player's score with that
     * faction. Returns the deltas applied (empty while reputation is disabled; then no listener is told either).
     */
    public static Map<Faction, Integer> record(ServerPlayer player, Deed deed, DeedContext context) {
        if (!Reputation.enabled()) return Map.of();
        Map<Faction, Integer> row = ReputationConfig.deedTable().row(deed);
        String reason = deed.id();
        row.forEach((faction, delta) -> Reputation.adjust(player, faction, delta, reason));
        for (DeedListener l : LISTENERS) {
            try {
                l.onDeed(player, deed, context, row);
            } catch (RuntimeException e) {
                Constants.LOG.error("Deed listener failed for {} of {}", deed.id(), player.getName().getString(), e);
            }
        }
        return row;
    }
}

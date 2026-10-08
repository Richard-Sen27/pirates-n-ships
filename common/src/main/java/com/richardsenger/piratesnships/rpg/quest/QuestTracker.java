package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.law.world.CombatCrimeDetector;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.treasure.TreasureBinding;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where quest progress comes from (docs/design.md §15, QST1):
 * <ul>
 *   <li>{@link #onDeed} ({@code Deeds.listen}): {@code kill_pirate}, {@code kill_navy}, {@code turn_in_pirate} for the
 *       hunts and turn-ins;</li>
 *   <li>{@link #onDeath} ({@code LIVING_DEATH}): a kill by a player (the shooter of a projectile, a pet's owner, as the
 *       law counts it) for the monster quests;</li>
 *   <li>{@link #onServerTick}: every {@code quests.poll_ticks}, each online player's deliveries (the contract's state),
 *       treasure hunts ({@link TreasureBinding#found}) and deadlines.</li>
 * </ul>
 */
public final class QuestTracker {

    private QuestTracker() {
    }

    public static void onDeed(ServerPlayer player, Deed deed, DeedContext context, Map<Faction, Integer> applied) {
        Quests.apply(player, new QuestEvent.DeedDone(deed));
    }

    /** Never cancels the death. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide()) return false;
        if (CombatCrimeDetector.offender(source) instanceof ServerPlayer player && player != victim) {
            Quests.apply(player, new QuestEvent.Killed(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())));
        }
        return false;
    }

    public static void onServerTick(MinecraftServer server) {
        if (!Quests.enabled() || server.getTickCount() % QuestConfig.POLL_TICKS.get() != 0) return;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) poll(p);
    }

    /** Checks the player's deliveries, treasure hunts and deadlines now; returns the quests that changed. */
    public static List<Quest> poll(ServerPlayer player) {
        return pollAt(player, Quests.day(player.server));
    }

    /** {@link #poll} as if it were day {@code day} (deadlines; GameTests). */
    public static List<Quest> pollAt(ServerPlayer player, long day) {
        QuestLog log = Quests.log(player);
        if (log.active().isEmpty()) return List.of();
        MinecraftServer server = player.server;
        List<Quest> changed = new ArrayList<>();
        for (Quest q : log.active()) {
            if (q.target() instanceof QuestTarget.Cargo c && c.contract().isPresent()) {
                Optional<DeliveryContract> contract = TradeService.contract(server, c.contract().get());
                changed.addAll(Quests.apply(player, new QuestEvent.ContractChanged(c.contract().get(),
                        contract.map(DeliveryContract::state).orElse(null))));
            } else if (q.target() instanceof QuestTarget.Treasure t
                    && TreasureBinding.found(PortRegistry.get(server).index().byId(t.port()), t.site())) {
                changed.addAll(Quests.apply(player, new QuestEvent.TreasureLooted(t.port(), t.site())));
            }
        }
        changed.addAll(Quests.apply(player, new QuestEvent.Day(day)));
        return changed;
    }
}

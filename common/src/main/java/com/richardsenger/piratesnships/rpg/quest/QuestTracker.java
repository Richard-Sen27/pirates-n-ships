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
import net.minecraft.network.chat.Component;
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
 *       treasure hunts ({@link TreasureBinding#found}), captain hunts (his {@code CaptainRegistry} entry) and
 *       deadlines.</li>
 * </ul>
 * A captain hunt (QST1b) completes on {@link QuestEvent.VictimDown}: his death with the player as offender
 * ({@link #onDeath}), or the {@code kill_pirate} / {@code turn_in_pirate} deed whose context names him. Both happen in
 * the tick of the kill or the hand-over, before the poll; the poll then fails the hunt of anyone else whose captain is
 * no longer an island's living captain ({@link QuestEvent.VictimLost}). A successor has another id and never counts.
 */
public final class QuestTracker {

    private QuestTracker() {
    }

    public static void onDeed(ServerPlayer player, Deed deed, DeedContext context, Map<Faction, Integer> applied) {
        Quests.apply(player, new QuestEvent.DeedDone(deed));
        if ((deed == Deed.KILL_PIRATE || deed == Deed.TURN_IN_PIRATE) && context.victim().isPresent()) {
            Quests.apply(player, new QuestEvent.VictimDown(context.victim().get()));
        }
    }

    /** Never cancels the death. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide()) return false;
        if (CombatCrimeDetector.offender(source) instanceof ServerPlayer player && player != victim) {
            Quests.apply(player, new QuestEvent.Killed(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())));
            Quests.apply(player, new QuestEvent.VictimDown(victim.getUUID()));
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
            } else if (q.type() == QuestType.HUNT_CAPTAIN && q.target() instanceof QuestTarget.Victim v && !Quests.captainAlive(server, v.id())) {
                List<Quest> lost = Quests.apply(player, new QuestEvent.VictimLost(v.id()));
                if (!lost.isEmpty()) player.displayClientMessage(Component.translatable(QuestText.TARGET_LOST, v.name()), false);
                changed.addAll(lost);
            }
        }
        changed.addAll(Quests.apply(player, new QuestEvent.Day(day)));
        return changed;
    }
}

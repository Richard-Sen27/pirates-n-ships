package com.richardsenger.piratesnships.rpg;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.rpg.deeds.CombatDeeds;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.LawDeeds;
import com.richardsenger.piratesnships.rpg.reputation.ClientReputation;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.ReputationAttachments;
import com.richardsenger.piratesnships.rpg.reputation.ReputationCommands;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.rpg.reputation.ReputationSync;
import com.richardsenger.piratesnships.rpg.reputation.ReputationSyncPayload;

import java.util.List;

/**
 * The {@code rpg} module (docs/design.md §15): reputation with the navy, the pirates and the villagers
 * ({@code rpg.reputation}), the deeds that shift it ({@code rpg.deeds}) and its effect on markets
 * ({@code rpg.market}). Hostility, the false-flag rule and port fees read {@code Reputation} directly.
 */
public final class RpgModule implements ModModule {

    @Override
    public String id() {
        return "rpg";
    }

    @Override
    public void registerConfig() {
        ReputationConfig.init();
    }

    @Override
    public void registerContent() {
        ReputationAttachments.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(ReputationSyncPayload.TYPE, ReputationSyncPayload.CODEC, (p, player) -> ClientReputation.accept(p));
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(ReputationSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(ReputationSync::sendNow);
        CommonEvents.PLAYER_LOGOUT.register(ReputationSync::onLogout);
        CommonEvents.SERVER_STOPPED.register(server -> {
            ReputationSync.onServerStopped(server);
            CombatDeeds.clear();
            LawDeeds.clear();
        });
        CommonEvents.LIVING_INCOMING_DAMAGE.register(CombatDeeds::onIncomingDamage);
        CommonEvents.LIVING_DEATH.register(CombatDeeds::onDeath);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> ReputationCommands.register(dispatcher));
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.rpg.client.RpgClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(ReputationCommands::lang);
        data.lang(lang -> {
            for (Faction f : Faction.values()) {
                lang.add(f.nameKey(), switch (f) {
                    case NAVY -> "navy";
                    case PIRATES -> "pirates";
                    case VILLAGERS -> "villagers";
                });
            }
            for (Deed d : Deed.values()) {
                lang.add(d.nameKey(), switch (d) {
                    case ATTACK_NAVY -> "Attacking the navy";
                    case KILL_NAVY -> "Killing a navy sailor";
                    case ATTACK_PIRATE -> "Attacking a pirate";
                    case KILL_PIRATE -> "Killing a pirate";
                    case ATTACK_VILLAGER -> "Attacking a villager";
                    case KILL_VILLAGER -> "Killing a villager";
                    case ATTACK_MERCHANT_SHIP -> "Firing on a merchant ship";
                    case PLUNDER_MERCHANT -> "Plundering a merchant";
                    case FENCE_PLUNDER -> "Fencing plunder";
                    case TRADE_VILLAGE -> "Trading at a village";
                    case TURN_IN_PIRATE -> "Turning in a pirate";
                    case PAY_FINE -> "Paying a fine";
                    case FLY_FALSE_COLOURS -> "Caught under false colours";
                    case COMPLETE_NAVY_QUEST -> "Completing a navy quest";
                    case COMPLETE_PIRATE_QUEST -> "Completing a pirate quest";
                    case COMPLETE_VILLAGE_QUEST -> "Completing a village quest";
                });
            }
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ReputationGameTests.class);
    }
}

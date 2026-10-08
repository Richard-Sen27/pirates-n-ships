package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pays a completed quest (docs/design.md §15, QST1): its doubloons ({@link QuestRules#coinsOnCompletion}; a delivery's
 * contract already paid on delivery) and its reward deed ({@code complete_navy_quest}, {@code complete_pirate_quest} or
 * {@code complete_village_quest} by the giver's port kind, with the port and the reward as context, so careers count
 * quests per faction from the deeds), then tells the player.
 */
public final class QuestRewards {

    private QuestRewards() {
    }

    /** Pays {@code quest} to {@code player}; returns the doubloons given. */
    public static long grant(ServerPlayer player, Quest quest) {
        long coins = QuestRules.coinsOnCompletion(quest);
        if (coins > 0) {
            Wallet.give(player, coins);
            player.containerMenu.broadcastChanges();
        }
        Deeds.record(player, quest.rewardDeed(), DeedContext.port(quest.port(), quest.rewardCoins()));
        Component title = QuestText.title(quest, false);
        player.displayClientMessage(coins > 0 ? Component.translatable(QuestText.COMPLETED_PAID, title, coins)
                : Component.translatable(QuestText.COMPLETED, title), false);
        return coins;
    }
}

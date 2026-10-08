package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.trade.net.MarketBackend;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Server side of the Quests tab (QST1). {@code MarketBackend.openDesk} sends {@link #view} with the market;
 * {@code MarketBackend} routes {@link QuestPayloads.QuestAction} to {@link #handle}, which refuses without a valid desk
 * session for the port ({@link MarketBackend#canUse}: reach and binding) and answers with the tab's new content and the
 * result.
 */
public final class QuestBackend {

    private QuestBackend() {
    }

    /** The Quests tab of {@code port} for {@code player}; empty while quests are off (no tab). */
    public static Optional<QuestPayloads.QuestsView> view(ServerPlayer player, ResourceLocation port) {
        if (!Quests.enabled()) return Optional.empty();
        QuestParams params = QuestConfig.params();
        return Optional.of(new QuestPayloads.QuestsView(port, params.maxActive(), params.deadlineDays(),
                Quests.offers(player.server, port), Quests.log(player).active()));
    }

    /** Runs a tab action; returns the payload to send back. */
    public static QuestPayloads.QuestsPayload handle(ServerPlayer player, QuestPayloads.QuestAction p) {
        if (!MarketBackend.canUse(player, p.port())) {
            return new QuestPayloads.QuestsPayload(Optional.empty(),
                    Optional.of(QuestPayloads.QuestResult.of(Quests.Result.fail(Quests.NO_SESSION))));
        }
        Quests.Result r = p.accept() ? Quests.accept(player, p.port(), p.quest()) : Quests.abandon(player, p.quest());
        return new QuestPayloads.QuestsPayload(view(player, p.port()), Optional.of(QuestPayloads.QuestResult.of(r)));
    }
}

package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.rpg.career.ShipPrizes;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;

import java.util.List;

/**
 * The quests module {@code rpg.quest} (docs/design.md §15, QST1, QST1b): offers per port, the player's quest log, progress
 * from deeds, kills and a poll, rewards, {@code /pirates quest}. The Quests tab's payloads are registered with the
 * market's ({@code trade.net.MarketBackend}), whose desk sessions they use.
 */
public final class QuestModule implements ModModule {

    @Override
    public String id() {
        return "rpg.quest";
    }

    @Override
    public void registerConfig() {
        QuestConfig.init();
    }

    @Override
    public void registerContent() {
        QuestAttachments.init();
    }

    @Override
    public void registerEvents() {
        Deeds.listen(QuestTracker::onDeed);
        CommonEvents.LIVING_DEATH.register(QuestTracker::onDeath);
        CommonEvents.SERVER_TICK_END.register(QuestTracker::onServerTick);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> QuestCommands.register(dispatcher));
        // QST2: quests at sea and the letter of marque's prize money for pirate ships
        CommonEvents.SERVER_STARTED.register(SeaQuests::onServerStarted);
        CommonEvents.SERVER_STOPPED.register(server -> SeaQuests.onServerStopped());
        VoyageEndings.onEnding(SeaQuests::onEnding);
        Voyages.onEnd(SeaQuests::onVoyageEnd);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(QuestText::lang);
        data.lang(ShipPrizes::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(QuestGameTests.class, CaptainHuntGameTests.class, QuestSeaGameTests.class, QuestChartingGameTests.class);
    }
}

package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/** The {@code law} module: criminal score, bounties and flag rules (docs/design.md §13, §4.7). */
public final class LawModule implements ModModule {

    @Override
    public String id() {
        return "law";
    }

    @Override
    public void registerConfig() {
        LawConfig.init();
    }

    @Override
    public void registerContent() {
        LawAttachments.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(LawService::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(LawService::onLogin);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> LawCommands.register(dispatcher));
    }

    @Override
    public void gatherData(DataContributions data) {
        String k = LawCommands.KEY;
        data.lang(lang -> {
            lang.add(LawCommands.wantedKey(WantedLevel.CLEAN), "Clean")
                    .add(LawCommands.wantedKey(WantedLevel.SUSPECT), "Suspect")
                    .add(LawCommands.wantedKey(WantedLevel.WANTED), "Wanted")
                    .add(LawCommands.wantedKey(WantedLevel.NOTORIOUS), "Notorious")
                    .add(k + "not_living", "The target must be a living entity")
                    .add(k + "unknown_crime", "Unknown crime")
                    .add(k + "score.get", "%s has a criminal score of %s (%s, %s crimes on record)")
                    .add(k + "score.set", "Set the criminal score of %s to %s")
                    .add(k + "crime", "%s committed %s: %s, +%s points (score now %s)")
                    .add(k + "fine", "%s paid %s doubloons, %s points removed (%s, score now %s)")
                    .add(k + "bounty.list.empty", "There are no bounties")
                    .add(k + "bounty.list.header", "%s bounty targets:")
                    .add(k + "bounty.list.entry", "%s: %s doubloons (%s bounties)")
                    .add(k + "bounty.list.entry_navy", "%s: %s doubloons (%s bounties, wanted by the navy)")
                    .add(k + "bounty.place.success", "Placed a bounty of %s doubloons on %s (total now %s)")
                    .add(k + "bounty.place.failed", "Could not place the bounty: %s")
                    .add(k + "bounty.claim.success", "Claimed the bounty on %s: %s doubloons from %s bounties")
                    .add(k + "bounty.claim.failed", "Could not claim a bounty: %s")
                    .add(k + "bounty.clear", "Removed all bounties on %s");
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LawGameTests.class);
    }
}

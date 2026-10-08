package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;

import java.util.List;

/**
 * The {@code rpg.career} module (docs/design.md §15, CAR1): the navy rank ladder, pirate infamy and the letter of
 * marque. Fed by the reputation module's deeds ({@link CareerDeeds}); the career screen opens at navy officers
 * ({@link CareerInteractions}); {@code /pirates career}.
 */
public final class CareerModule implements ModModule {

    @Override
    public String id() {
        return "rpg.career";
    }

    @Override
    public void registerConfig() {
        CareerConfig.init();
    }

    @Override
    public void registerContent() {
        CareerAttachments.init();
    }

    @Override
    public void registerPayloads() {
        CareerBackend.registerPayloads();
    }

    @Override
    public void registerEvents() {
        Deeds.listen(CareerDeeds::onDeed);
        CommonEvents.ENTITY_INTERACT.register(CareerInteractions::onEntityInteract);
        CommonEvents.PLAYER_LOGIN.register(player -> {
            Careers.promoteIfEligible(player);
            CareerSync.sendNow(player);
        });
        CommonEvents.PLAYER_LOGOUT.register(player -> {
            CareerSync.onLogout(player);
            CareerBackend.onLogout(player);
        });
        CommonEvents.SERVER_STOPPED.register(server -> {
            CareerSync.clear();
            CareerBackend.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> CareerCommands.register(dispatcher));
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.rpg.career.client.CareerClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(CareerText::lang);
        data.lang(CareerRewards::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CareerGameTests.class, CareerRewardsGameTests.class);
    }
}

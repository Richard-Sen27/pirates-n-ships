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
        com.richardsenger.piratesnships.rpg.career.client.RankHudConfig.init(); // HON1
    }

    @Override
    public void registerContent() {
        CareerAttachments.init();
        ShipGrantContent.init(); // SHP1: the ship commission
    }

    @Override
    public void registerPayloads() {
        CareerBackend.registerPayloads();
    }

    @Override
    public void registerEvents() {
        Deeds.listen(CareerDeeds::onDeed);
        CommonEvents.ENTITY_INTERACT.register(CareerInteractions::onEntityInteract);
        CommonEvents.ENTITY_INTERACT.register(ShipGrants::onEntityInteract); // SHP1: a commission handed to an officer
        CommonEvents.PLAYER_LOGIN.register(player -> {
            Careers.promoteIfEligible(player);
            CareerSync.sendNow(player);
            CareerTeams.refresh(player); // HON1: joins or leaves the title team (prefix toggles)
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
        data.lang(CareerTitles::lang);
        data.lang(com.richardsenger.piratesnships.rpg.career.client.RankHudLayout::lang);
        data.lang(ShipGrants::lang);
        // SHP1: the commission looks like the shipwright's receipt until it has its own Blockbench model
        data.models(models -> models.models().accept(
                net.minecraft.data.models.model.ModelLocationUtils.getModelLocation(ShipGrantContent.SHIP_COMMISSION.get()), () -> {
                    com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                    json.addProperty("parent", com.richardsenger.piratesnships.Constants.MOD_ID + ":item/ship_receipt");
                    return json;
                }));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CareerGameTests.class, CareerRewardsGameTests.class, HonorGameTests.class, ShipGrantGameTests.class);
    }
}

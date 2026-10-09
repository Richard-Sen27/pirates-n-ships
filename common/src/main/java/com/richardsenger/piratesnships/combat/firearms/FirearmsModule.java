package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;

import java.util.List;

/**
 * The {@code combat.firearms} module: pistol and musket that load and fire (docs/design.md §8.1). The gun items are
 * registered by {@code combat.content} as {@link FirearmItem}s; this module adds the loaded state, the lead ball, the
 * recoil payload, config and datagen.
 */
public final class FirearmsModule implements ModModule {

    @Override
    public String id() {
        return "combat.firearms";
    }

    @Override
    public void registerConfig() {
        FirearmsConfig.init();
    }

    @Override
    public void registerContent() {
        FirearmContent.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(RecoilPayload.TYPE, RecoilPayload.CODEC, RecoilPayload::apply);
        Services.NETWORK.registerToServer(FirearmFirePayload.TYPE, FirearmFirePayload.CODEC, FirearmTrigger::onFirePayload);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.firearms.client.FirearmsClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(FirearmContent.LEAD_BALL.get().getDescriptionId(), "Lead Ball")
                .add(FirearmItem.LOADED_KEY, "Loaded")
                .add(FirearmItem.UNLOADED_KEY, "Not loaded")
                .add(FirearmItem.AIM_HINT_KEY, "Hold to aim, release to fire")
                .add(FirearmItem.AIM_ATTACK_HINT_KEY, "Hold use to aim, attack to fire")
                .add(FirearmItem.LOWER_HINT_KEY, "Sneak to lower without firing")
                .add(FirearmItem.LOAD_HINT_KEY, "Hold with lead shot and gunpowder to load"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(FirearmGameTests.class, FirearmTriggerGameTests.class);
    }
}

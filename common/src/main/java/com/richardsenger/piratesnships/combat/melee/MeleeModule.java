package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.net.MeleeActions;
import com.richardsenger.piratesnships.combat.melee.net.MeleeNet;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * The {@code combat.melee} module: skill-based swordplay rules, weapon definitions, the server service, the action
 * and state payloads, and on the client the sword input and the stamina HUD (§8.5).
 */
public final class MeleeModule implements ModModule {

    @Override
    public String id() {
        return "combat.melee";
    }

    @Override
    public void registerConfig() {
        MeleeConfig.init();
        MeleeClientConfig.init();
    }

    @Override
    public void registerContent() {
        MeleeWeapons.init();
        MeleeAttachments.init();
    }

    @Override
    public void registerPayloads() {
        MeleeNet.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(MeleeService::onServerTick);
        CommonEvents.SERVER_STOPPED.register(MeleeService::onServerStopped);
        CommonEvents.LIVING_INCOMING_DAMAGE.register(MeleeService::onIncomingDamage);
        CommonEvents.PLAYER_TICK_END.register(com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundPlayer::onPlayerTick);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.melee.client.MeleeClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.definitions(MeleeWeapons.WEAPONS, DefaultWeapons.all());
        data.lang(lang -> lang
                .add(MeleeActions.refusalMessageKey(Refusal.NO_STAMINA), "Too exhausted")
                .add(MeleeActions.refusalMessageKey(Refusal.LOCKED_OUT), "Can't parry again so soon")
                .add(MeleeActions.refusalMessageKey(Refusal.STAGGERED), "Staggered"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(MeleeGameTests.class, MeleeNetGameTests.class,
                com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundGameTests.class);
    }
}

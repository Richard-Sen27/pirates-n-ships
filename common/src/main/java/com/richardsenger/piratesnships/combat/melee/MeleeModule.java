package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/** The {@code combat.melee} module: skill-based swordplay rules, weapon definitions and the server service (§8.5). */
public final class MeleeModule implements ModModule {

    @Override
    public String id() {
        return "combat.melee";
    }

    @Override
    public void registerConfig() {
        MeleeConfig.init();
    }

    @Override
    public void registerContent() {
        MeleeWeapons.init();
        MeleeAttachments.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(MeleeService::onServerTick);
        CommonEvents.SERVER_STOPPED.register(MeleeService::onServerStopped);
        CommonEvents.LIVING_INCOMING_DAMAGE.register(MeleeService::onIncomingDamage);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.definitions(MeleeWeapons.WEAPONS, DefaultWeapons.all());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(MeleeGameTests.class);
    }
}

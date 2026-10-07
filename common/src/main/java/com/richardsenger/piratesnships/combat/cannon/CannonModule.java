package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;

import java.util.List;

/**
 * The {@code combat.cannon} module (G9, docs/design.md §8.2, §4.6): the cannon block (also a crew station), loaded with
 * gunpowder and a cannonball, aimed in elevation steps and fired; the cannonball, which holes ship hulls (a breach
 * below the waterline floods through the hull runtime), hurts entities and splashes into water. Config section
 * {@code cannons}; the block damage toggle and damage multipliers are in {@code combat}.
 */
public final class CannonModule implements ModModule {

    @Override
    public String id() {
        return "combat.cannon";
    }

    @Override
    public void registerConfig() {
        CannonConfig.init();
    }

    @Override
    public void registerContent() {
        CannonContent.init();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.cannon.client.CannonClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        CannonData.gather(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CannonGameTests.class, CannonOrderGameTests.class);
    }
}

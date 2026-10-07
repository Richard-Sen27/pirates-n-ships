package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;

import java.util.List;

/**
 * The {@code combat.cannon} module (G9, P2, docs/design.md §8.2, §4.6): the two-block cannon (also a crew station), loaded with
 * gunpowder and a cannonball, aimed in elevation steps and fired; the cannonball, which holes ship hulls (a breach
 * below the waterline floods through the hull runtime), hurts entities and splashes into water; and the swivel gun, a
 * small gun on a railing that follows the aiming player's view and fires on release (also a crew station). Config
 * section {@code cannons} (with {@code cannons.swivel}); the block damage toggle and damage multipliers are in
 * {@code combat}.
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
    public void registerPayloads() {
        Services.NETWORK.registerToServer(SwivelReleasePayload.TYPE, SwivelReleasePayload.CODEC, SwivelReleasePayload::handle);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.cannon.client.CannonClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        CannonData.gather(data);
        SwivelData.gather(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CannonGameTests.class, CannonOrderGameTests.class, SwivelGunGameTests.class);
    }
}

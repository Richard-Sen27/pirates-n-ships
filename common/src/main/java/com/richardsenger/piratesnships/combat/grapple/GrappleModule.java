package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;

import java.util.List;

/**
 * The {@code combat.grapple} module (G11, docs/design.md §8.3 version 1, §8.4): the grappling hook is thrown, latches
 * onto another ship's hull at a plot position and hauls the two ships together with a rope force on both bodies until
 * they lie side by side. Config section {@code grapple}. The item is {@code combat.content.CombatContent#GRAPPLING_HOOK}.
 */
public final class GrappleModule implements ModModule {

    @Override
    public String id() {
        return "combat.grapple";
    }

    @Override
    public void registerConfig() {
        GrappleConfig.init();
    }

    @Override
    public void registerContent() {
        GrappleContent.init();
        ShipForces.registerGrapple();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToServer(ReleaseHookPayload.TYPE, ReleaseHookPayload.CODEC, ReleaseHookPayload::handle);
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(GrappleService::onServerTick);
        CommonEvents.PLAYER_LOGOUT.register(GrappleService::onLogout);
        CommonEvents.SERVER_STOPPED.register(server -> GrappleService.onServerStopped());
        SableShips.onPhysicsTick(GrappleService::onPhysicsTick);
        com.richardsenger.piratesnships.ship.assembly.ShipSplits.onSplit(GrappleService::onShipSplit); // RS1
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.grapple.client.GrappleClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(GrappleContent.HOOK.get().getDescriptionId(), "Grappling Hook")
                .add(GrapplingHookItem.TOOLTIP_KEY, "Throw at another ship to haul it alongside. Sneak + use with an empty hand to let go")
                .add(ShipForces.GRAPPLE_KEY, "Grappling Rope"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(GrappleGameTests.class);
    }
}

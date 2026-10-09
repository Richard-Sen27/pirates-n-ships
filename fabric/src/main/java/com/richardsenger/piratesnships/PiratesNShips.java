package com.richardsenger.piratesnships;

import com.richardsenger.piratesnships.platform.FabricAttachmentHelper;
import com.richardsenger.piratesnships.platform.FabricCapabilityHelper;
import com.richardsenger.piratesnships.platform.FabricEventForwarder;
import com.richardsenger.piratesnships.platform.FabricGameTests;
import com.richardsenger.piratesnships.platform.FabricNetworkHelper;
import com.richardsenger.piratesnships.platform.FabricRegistryHelper;
import com.richardsenger.piratesnships.platform.Services;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point (FAB1: both sides' common init, the server-side services, event forwarding and GameTests;
 * docs/fabric.md). Feature modules never add code here; everything is generic over {@code ModModules}.
 *
 * <p>Fabric registers content immediately (the vanilla registries are open during mod initialization), so registry
 * entries, payloads, attachments and configs are in place once {@link PiratesNShipsCommon#init()} returns; what needs
 * every entry to exist (entity attributes, spawn placements, transfer API storages) is applied afterwards.
 *
 * <p>The client side (FAB2) is {@link PiratesNShipsClient}, the {@code client} entry point, which runs after this one.
 */
public class PiratesNShips implements ModInitializer {

    @Override
    public void onInitialize() {
        PiratesNShipsCommon.init();

        ((FabricRegistryHelper) Services.REGISTRY).finish();
        ((FabricCapabilityHelper) Services.CAPABILITIES).finish();
        ((FabricNetworkHelper) Services.NETWORK).attach();
        ((FabricAttachmentHelper) Services.ATTACHMENTS).attach();
        FabricEventForwarder.attach();
        FabricGameTests.register();
    }
}

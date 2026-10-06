package com.richardsenger.piratesnships;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.ModModules;
import com.richardsenger.piratesnships.core.datagen.ModDataGenerator;
import com.richardsenger.piratesnships.platform.NeoForgeAttachmentHelper;
import com.richardsenger.piratesnships.platform.NeoForgeClientSetup;
import com.richardsenger.piratesnships.platform.NeoForgeConfigHelper;
import com.richardsenger.piratesnships.platform.NeoForgeEventForwarder;
import com.richardsenger.piratesnships.platform.NeoForgeNetworkHelper;
import com.richardsenger.piratesnships.platform.NeoForgeRegistryHelper;
import com.richardsenger.piratesnships.platform.Services;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * NeoForge entry point. Runs the common initializer, then wires the collected registrations into NeoForge.
 * Feature modules never add code here; everything is generic over {@link ModModules}.
 */
@Mod(Constants.MOD_ID)
public final class PiratesNShips {

    public PiratesNShips(IEventBus modBus, ModContainer container, Dist dist) {
        PiratesNShipsCommon.init();

        ((NeoForgeRegistryHelper) Services.REGISTRY).attach(modBus);
        ((NeoForgeAttachmentHelper) Services.ATTACHMENTS).attach(modBus);
        ((NeoForgeNetworkHelper) Services.NETWORK).attach(modBus);
        ((NeoForgeConfigHelper) Services.CONFIG).attach(container);
        NeoForgeEventForwarder.attach(NeoForge.EVENT_BUS);

        modBus.addListener(RegisterGameTestsEvent.class, event -> {
            for (ModModule m : ModModules.ALL) m.gameTestClasses().forEach(event::register);
        });
        modBus.addListener(GatherDataEvent.class, event -> ModDataGenerator.gather(
                (client, provider) -> event.getGenerator().addProvider(client ? event.includeClient() : event.includeServer(), provider),
                event.getGenerator().getPackOutput(), event.getLookupProvider()));

        if (dist.isClient()) {
            PiratesNShipsCommon.initClient();
            NeoForgeClientSetup.attach(modBus, container);
        }
    }
}

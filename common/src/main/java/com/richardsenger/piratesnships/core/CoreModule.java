package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.client.CoreClient;
import com.richardsenger.piratesnships.core.data.DefinitionLoading;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** The {@code core} module: shared infrastructure and the creative tab. A template for feature modules. */
public final class CoreModule implements ModModule {

    @Override
    public String id() {
        return "core";
    }

    @Override
    public void registerConfig() {
        CoreConfig.init();
    }

    @Override
    public void registerContent() {
        CoreContent.init();
        CoreDefinitions.init();
    }

    @Override
    public void registerPayloads() {
        DefinitionLoading.registerPayloads();
    }

    @Override
    public void registerEvents() {
        DefinitionLoading.registerEvents();
        ConfigOverrides.registerEvents();
        CommonEvents.SERVER_STARTED.register(server -> {
            if (CoreConfig.DEBUG.get()) {
                Constants.LOG.info("[debug] {} active on server", Constants.MOD_NAME);
            }
        });
    }

    @Override
    public void initClient() {
        CoreClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add("itemGroup." + Constants.MOD_ID, Constants.MOD_NAME));
        // A direct vanilla block, a required reference to a vanilla tag, then an optional one to another mod's tag
        data.blockTags(tags -> tags.tag(CoreTags.TEST_GROUND).add(Blocks.CLAY)
                .addTag(BlockTags.DIRT).addOptionalTag(CoreTags.C_SANDS));
        data.definition(CoreDefinitions.TEST_MARKER, CoreDefinitions.EXAMPLE_ID, CoreDefinitions.EXAMPLE);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CoreGameTests.class);
    }
}

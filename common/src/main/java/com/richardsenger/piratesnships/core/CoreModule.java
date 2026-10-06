package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.client.CoreClient;
import com.richardsenger.piratesnships.core.data.DefinitionLoading;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;

import java.util.List;

/** The {@code core} module: shared infrastructure plus the dev/test block. A template for feature modules. */
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
                .add("itemGroup." + Constants.MOD_ID, Constants.MOD_NAME)
                .block(CoreContent.TEST_BLOCK, "Test Block"));
        data.models(m -> m.blocks().createTrivialCube(CoreContent.TEST_BLOCK.get()));
        data.blockLoot(loot -> loot.dropSelf(CoreContent.TEST_BLOCK.get()));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(CoreContent.TEST_BLOCK.get()));
        // Required reference to a vanilla tag, then an optional one to another mod's tag, chained on the appender
        data.blockTags(tags -> tags.tag(CoreTags.TEST_GROUND).add(CoreContent.TEST_BLOCK.get())
                .addTag(BlockTags.DIRT).addOptionalTag(CoreTags.C_SANDS));
        data.definition(CoreDefinitions.TEST_MARKER, CoreDefinitions.EXAMPLE_ID, CoreDefinitions.EXAMPLE);
        // Raw JSON example: Sable block physics (refs/sable/wiki/Block Physics Properties.md). Values = Sable defaults.
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", CoreContent.TEST_BLOCK.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 1.0);
            JsonObject json = new JsonObject();
            json.addProperty("selector", CoreContent.TEST_BLOCK.id().toString());
            json.add("properties", properties);
            return json;
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CoreGameTests.class);
    }
}

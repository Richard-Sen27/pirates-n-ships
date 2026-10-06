package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
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
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_STARTED.register(server -> {
            if (CoreConfig.DEBUG.get()) {
                Constants.LOG.info("[debug] {} active on server", Constants.MOD_NAME);
            }
        });
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add("itemGroup." + Constants.MOD_ID, Constants.MOD_NAME)
                .block(CoreContent.TEST_BLOCK, "Test Block"));
        data.models(m -> m.blocks().createTrivialCube(CoreContent.TEST_BLOCK.get()));
        data.blockLoot(loot -> loot.dropSelf(CoreContent.TEST_BLOCK.get()));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(CoreContent.TEST_BLOCK.get()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CoreGameTests.class);
    }
}

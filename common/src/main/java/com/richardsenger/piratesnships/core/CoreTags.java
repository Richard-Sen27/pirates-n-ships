package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags of the {@code core} module. Fixtures for the tag datagen (see {@code CoreModule.gatherData}). */
public final class CoreTags {

    /**
     * Dev/test tag: vanilla clay, a required reference to vanilla's {@code #minecraft:dirt} and an optional
     * reference to {@code #c:sands}. Checked by {@code CoreGameTests.generatedTagResolvesVanillaReference}.
     */
    public static final TagKey<Block> TEST_GROUND = TagKey.create(Registries.BLOCK, Constants.id("test_ground"));

    /** The convention tag referenced optionally by {@link #TEST_GROUND}. */
    public static final ResourceLocation C_SANDS = ResourceLocation.fromNamespaceAndPath("c", "sands");

    private CoreTags() {
    }
}

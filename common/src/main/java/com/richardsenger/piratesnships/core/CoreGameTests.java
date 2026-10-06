package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;

/** Foundation GameTests: registration from common works in a real server. */
public final class CoreGameTests {

    private CoreGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CoreGameTests.class);
    }

    @ModGameTest
    public static void testBlockCanBePlaced(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, CoreContent.TEST_BLOCK.get());
        helper.assertBlockPresent(CoreContent.TEST_BLOCK.get(), pos);
        helper.succeed();
    }

    @ModGameTest
    public static void configDefaultsAreReadable(GameTestHelper helper) {
        helper.assertFalse(CoreConfig.DEBUG.get(), "core.debug should default to false");
        helper.succeed();
    }

    @ModGameTest
    public static void generatedDefinitionIsLoaded(GameTestHelper helper) {
        var defs = CoreDefinitions.TEST_MARKER.server();
        helper.assertTrue(defs.contains(CoreDefinitions.EXAMPLE_ID), "test_marker " + CoreDefinitions.EXAMPLE_ID + " not loaded, have " + defs.ids());
        helper.assertValueEqual(defs.require(CoreDefinitions.EXAMPLE_ID), CoreDefinitions.EXAMPLE, "test_marker example");
        helper.assertTrue(CoreDefinitions.TEST_MARKER.of(helper.getLevel()) == defs, "server level must resolve to the server store");
        helper.succeed();
    }

    /** Own batch: changes config (see {@link ConfigOverrides}). */
    @ModGameTest(batch = CONFIG_DEBUG_BATCH)
    public static void configOverrideIsAppliedAndRestored(GameTestHelper helper) {
        boolean before = CoreConfig.DEBUG.get();
        ConfigOverrides.Handle<Boolean> early = ConfigOverrides.apply(CoreConfig.DEBUG, !before);
        helper.assertValueEqual(CoreConfig.DEBUG.get(), !before, "core.debug after apply");
        early.restore();
        helper.assertValueEqual(CoreConfig.DEBUG.get(), before, "core.debug after restore");

        ConfigOverrides.during(helper, CoreConfig.DEBUG, true);
        helper.assertTrue(CoreConfig.DEBUG.get(), "core.debug should be overridden for this test");
        helper.succeed();
    }

    /** The generated tag's required reference to {@code #minecraft:dirt} resolves at load time. */
    @ModGameTest
    public static void generatedTagResolvesVanillaReference(GameTestHelper helper) {
        helper.assertTrue(CoreContent.TEST_BLOCK.get().defaultBlockState().is(CoreTags.TEST_GROUND), "test block in " + CoreTags.TEST_GROUND);
        helper.assertTrue(Blocks.DIRT.defaultBlockState().is(CoreTags.TEST_GROUND), "dirt (via #minecraft:dirt) in " + CoreTags.TEST_GROUND);
        helper.assertTrue(Blocks.PODZOL.defaultBlockState().is(CoreTags.TEST_GROUND), "podzol (via #minecraft:dirt) in " + CoreTags.TEST_GROUND);
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(CoreTags.TEST_GROUND), "stone must not be in " + CoreTags.TEST_GROUND);
        helper.succeed();
    }

    static final String CONFIG_DEBUG_BATCH = "pirates_n_ships_config_core_debug";
}

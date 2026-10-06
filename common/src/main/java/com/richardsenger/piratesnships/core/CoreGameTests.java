package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

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
}

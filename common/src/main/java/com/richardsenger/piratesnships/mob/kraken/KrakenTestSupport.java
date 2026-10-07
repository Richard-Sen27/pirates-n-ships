package com.richardsenger.piratesnships.mob.kraken;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * GameTest support for the kraken tests: a kraken and survival mock players that are removed when the test ends (the
 * framework never clears entities that left the test area, and a kraken left behind would attack a later test's ships).
 */
final class KrakenTestSupport {

    private static volatile Field testInfoField;

    private KrakenTestSupport() {
    }

    /** A kraken with its feet at the test-relative position, discarded (with its parts) when the test ends. */
    static Kraken kraken(GameTestHelper helper, Vec3 relative) {
        Kraken k = KrakenSpawner.spawn(helper.getLevel(), helper.absoluteVec(relative), MobSpawnType.COMMAND);
        if (k == null) throw new AssertionError("could not spawn the kraken");
        onEnd(helper, k::discard);
        return k;
    }

    /** A survival mock player in the level at the test-relative position, discarded when the test ends. */
    static Player playerInLevel(GameTestHelper helper, Vec3 relative) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        onEnd(helper, player::discard);
        return player;
    }

    /** Runs {@code action} when the test passes, fails or is rerun. */
    static void onEnd(GameTestHelper helper, Runnable action) {
        testInfo(helper).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { action.run(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { action.run(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { action.run(); }
        });
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1 (same accessor as {@code ConfigOverrides}). */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        testInfoField = f = candidate;
                        break;
                    }
                }
            }
            if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

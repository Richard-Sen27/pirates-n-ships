package com.richardsenger.piratesnships.hazards;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * GameTest support for the hazard tests: hazards and mock players that are removed when the test ends (the framework
 * only clears the test area when it is reused, and a hazard left behind would keep pushing a later test's entities),
 * and a thunderstorm that lasts for one synchronous block of test code.
 */
final class HazardTestSupport {

    private static volatile Field testInfoField;

    private HazardTestSupport() {
    }

    /** A configured hazard with its base at the test-relative position, stationary, discarded when the test ends. */
    static HazardEntity hazard(GameTestHelper helper, HazardKind kind, Vec3 relative) {
        ServerLevel level = helper.getLevel();
        HazardEntity e = HazardSpawner.spawn(level, kind, helper.absoluteVec(relative), level.getRandom());
        if (e == null) {
            throw new AssertionError("could not spawn the " + kind.id());
        }
        if (e instanceof WhirlpoolEntity pool) {
            pool.setDriftSpeed(0);
        }
        onEnd(helper, e::discard);
        return e;
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

    /**
     * Runs {@code body} with the level thundering ({@code thunder}) or clear, and restores the weather and the rain and
     * thunder levels before returning. Nothing ticks in between, so no other test ever sees the changed weather.
     */
    static void withWeather(ServerLevel level, boolean thunder, Runnable body) {
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        int clear = data.getClearWeatherTime();
        int rainTime = data.getRainTime();
        int thunderTime = data.getThunderTime();
        boolean raining = data.isRaining();
        boolean thundering = data.isThundering();
        float rainLevel = level.getRainLevel(1.0f);
        float thunderLevel = level.getThunderLevel(1.0f) / Math.max(1e-6f, rainLevel);
        try {
            if (thunder) {
                level.setWeatherParameters(0, 6000, true, true);
                level.setRainLevel(1.0f);
                level.setThunderLevel(1.0f);
            } else {
                level.setWeatherParameters(6000, 0, false, false);
                level.setRainLevel(0.0f);
                level.setThunderLevel(0.0f);
            }
            body.run();
        } finally {
            data.setClearWeatherTime(clear);
            data.setRainTime(rainTime);
            data.setThunderTime(thunderTime);
            data.setRaining(raining);
            data.setThundering(thundering);
            level.setRainLevel(rainLevel);
            level.setThunderLevel(Math.min(1.0f, rainLevel <= 1e-6f ? 0.0f : thunderLevel));
        }
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

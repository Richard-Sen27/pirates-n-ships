package com.richardsenger.piratesnships.mob;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * GameTest support: a survival mock player that is <em>in the level</em>, so mob targeting and the melee sweep (both
 * query the level's entities) can find it. Vanilla's mock player is not added to any level, and the GameTest
 * framework never clears players from a test area, so the player is discarded when the test passes, fails or is
 * rerun. It is a plain {@link Player} (no connection), so nothing is ever sent to it.
 */
public final class MobTestSupport {

    private static volatile Field testInfoField;

    private MobTestSupport() {
    }

    /** A survival mock player at the test-relative position, facing {@code yaw} (0 = +Z, 90 = -X, -90 = +X). */
    public static Player playerInLevel(GameTestHelper helper, Vec3 relative, float yaw) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, yaw, 0f);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        helper.getLevel().addFreshEntity(player);
        testInfo(helper).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
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

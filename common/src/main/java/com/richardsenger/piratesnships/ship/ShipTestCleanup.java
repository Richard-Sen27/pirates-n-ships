package com.richardsenger.piratesnships.ship;

import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;

/**
 * GameTest helper: every ship a test creates is removed when the test ends, on success, failure and timeout, wherever
 * the ship ended up. Sable's own GameTest cleanup only removes sub-levels that intersect the test area when a test
 * succeeds or its space is cleared, so a ship that drifted, sank or fell out of the area, or a test that failed, would
 * otherwise leave a live rigid body behind for later tests.
 *
 * <p>Use {@link #assemble} instead of {@link ShipAssembler#assemble} in tests. Generally useful: should move to
 * {@code core/gametest} next to {@code ConfigOverrides} (and share its {@code GameTestInfo} accessor) once ship code is
 * not the only user.
 */
public final class ShipTestCleanup {

    /** Ships per running test. Weak keys: a finished test's info is dropped by the framework. */
    private static final Map<GameTestInfo, List<UUID>> TRACKED = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Field testInfoField;

    private ShipTestCleanup() {
    }

    /** {@link ShipAssembler#assemble} at a test-relative helm position, tracking the new ship for removal. */
    public static AssemblyResult assemble(GameTestHelper helper, BlockPos relativeHelm) {
        AssemblyResult r = ShipAssembler.assemble(helper.getLevel(), helper.absolutePos(relativeHelm), null);
        if (r.shipId() != null) {
            track(helper, r.shipId());
        }
        return r;
    }

    /** Removes the ship with {@code id} when the test ends (no-op if it is already gone by then). */
    public static void track(GameTestHelper helper, UUID id) {
        GameTestInfo info = testInfo(helper);
        ServerLevel level = helper.getLevel();
        List<UUID> ids = TRACKED.get(info);
        if (ids == null) {
            List<UUID> fresh = Collections.synchronizedList(new ArrayList<>());
            TRACKED.put(info, fresh);
            ids = fresh;
            info.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo testInfo) { }
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { removeAll(level, test); }
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { removeAll(level, test); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                    removeAll(level, oldTest);
                }
            });
        }
        ids.add(id);
    }

    private static void removeAll(ServerLevel level, GameTestInfo info) {
        List<UUID> ids = TRACKED.remove(info);
        if (ids == null) {
            return;
        }
        for (UUID id : List.copyOf(ids)) {
            ShipBody ship = SableShips.byId(level, id);
            if (ship != null) {
                SableShips.remove(ship);
            }
        }
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
            if (f == null) {
                throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            }
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.RudderSteps;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of steering by the wheel (HELM1, docs/design.md §5.3) on the 5×4×5 test hull of
 * {@link SailingGameTestsShips} (bow +Z, helm at the stern facing north). The helmsman is a mock player kept standing
 * just north of the helm (in the plot frame), as a real helmsman would; the client's payloads are replaced by direct
 * calls of the server handlers ({@link HelmService#turn}, {@link HelmService#release}).
 */
public final class HelmSteeringGameTests {

    private static final double LOCK = 270.0; // 1.5 turns lock to lock (default)
    private static final double MAX_PER_TICK = 30.0; // default
    private static final double WIND = 6.0;
    private static final int HEAD_FROM = 40;
    private static final int HEAD_TO = 240;
    /** See SailingGameTestsControls: full rudder turns the ballasted hull 6.8° in 200 ticks at this wind. */
    private static final double MIN_TURN = 4.0;

    private HelmSteeringGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HelmSteeringGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static BlockPos helmOf(ShipBody ship) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(AssemblyContent.HELM.get())) {
                return p;
            }
        }
        throw new AssertionError("no helm on the ship");
    }

    /** Puts {@code player} on the helmsman's spot of the helm at {@code plotHelm}, half a block north of it. */
    private static void stand(Player player, ShipBody ship, BlockPos plotHelm, double north) {
        Vec3 p = ship.toWorld(new Vec3(plotHelm.getX() + 0.5, plotHelm.getY(), plotHelm.getZ() - north));
        player.setPos(p.x, p.y, p.z);
    }

    /** A plain use of the helm by {@code player}, as vanilla does it with an empty hand. */
    private static void use(GameTestHelper h, BlockPos plotHelm, Player player) {
        BlockState s = h.getLevel().getBlockState(plotHelm);
        Vec3 c = Vec3.atCenterOf(plotHelm);
        s.useWithoutItem(h.getLevel(), player, new BlockHitResult(c.add(0, 0, -0.5), Direction.NORTH, plotHelm, false));
    }

    private static float wheel(GameTestHelper h, BlockPos plotHelm) {
        return HelmService.wheel(h.getLevel(), plotHelm);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    // ------------------------------------------------------------------ wheel and rudder

    /**
     * A helmsman takes the wheel and turns it: each delta turns the wheel, at most 30° net per tick however many
     * deltas arrive, up to the lock (270°); the runtime's rudder follows linearly up to the maximum rudder angle, also
     * the other way. Deltas of another player or for another helm are ignored.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void deltasTurnTheWheelAndTheRudderFollows(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false);
        Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED));
        BlockPos helm = helmOf(f.ship());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Player other = h.makeMockPlayer(GameType.SURVIVAL);
        double max = SailingConfig.MAX_RUDDER_ANGLE.get();
        h.onEachTick(() -> stand(player, f.ship(), helm, 0.5));
        h.runAfterDelay(2, () -> {
            stand(player, f.ship(), helm, 0.5);
            use(h, helm, player);
            h.assertTrue(helm.equals(HelmService.session(player)), "use did not take the wheel: " + HelmService.session(player));
            h.assertTrue(HelmService.turn(player, helm, 20.0), "delta refused");
            h.assertTrue(near(wheel(h, helm), 20.0), "wheel after +20: " + wheel(h, helm));
            HelmService.turn(player, helm, 20.0); // same tick: only 10 of the 30° budget left
            h.assertTrue(near(wheel(h, helm), MAX_PER_TICK), "per-tick budget: " + wheel(h, helm));
            h.assertTrue(near(f.runtime().wheelAngle(), MAX_PER_TICK), "runtime wheel: " + f.runtime().wheelAngle());
            h.assertTrue(!HelmService.turn(other, helm, 30.0), "a player without the wheel turned it");
            h.assertTrue(!HelmService.turn(player, helm.above(), 30.0), "a delta for another helm was applied");
        });
        h.runAfterDelay(3, () -> HelmService.turn(player, helm, 1000.0)); // clamped to one tick's budget
        h.runAfterDelay(4, () -> {
            h.assertTrue(near(wheel(h, helm), 2 * MAX_PER_TICK), "huge delta not clamped per tick: " + wheel(h, helm));
            h.assertTrue(near(f.runtime().rudderAngle(), 2 * MAX_PER_TICK / LOCK * max),
                    "rudder not linear in the wheel: " + f.runtime().rudderAngle());
        });
        for (int t = 5; t < 20; t++) {
            h.runAfterDelay(t, () -> HelmService.turn(player, helm, MAX_PER_TICK));
        }
        h.runAfterDelay(21, () -> {
            h.assertTrue(near(wheel(h, helm), LOCK), "wheel past the lock: " + wheel(h, helm));
            h.assertTrue(near(f.runtime().rudderAngle(), max), "rudder at the lock: " + f.runtime().rudderAngle());
        });
        for (int t = 22; t < 50; t++) {
            h.runAfterDelay(t, () -> HelmService.turn(player, helm, -MAX_PER_TICK));
        }
        h.runAfterDelay(51, () -> {
            h.assertTrue(near(wheel(h, helm), -LOCK), "wheel past the port lock: " + wheel(h, helm));
            h.assertTrue(near(f.runtime().rudderAngle(), -max), "rudder at the port lock: " + f.runtime().rudderAngle());
            h.assertTrue(HelmService.release(player, helm), "release");
            h.assertTrue(HelmService.session(player) == null, "session after release");
            h.assertTrue(!HelmService.turn(player, helm, 30.0), "a released wheel turned");
            h.assertTrue(near(wheel(h, helm), -LOCK), "the wheel sprang back on release: " + wheel(h, helm));
            h.succeed();
        });
    }

    /** The wheel angle is saved with the block entity, and a rebuilt runtime (after an unload) takes it up again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void wheelAngleSurvivesSaveAndLoad(GameTestHelper h) {
        // on land: a block entity save/load round trip
        BlockPos land = new BlockPos(2, 2, 2);
        h.setBlock(land, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(land));
        h.assertTrue(be instanceof HelmBlockEntity, "the helm has no block entity: " + be);
        ((HelmBlockEntity) be).setWheel(-123.5);
        CompoundTag tag = be.saveWithFullMetadata(h.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(h.absolutePos(land), be.getBlockState(), tag, h.getLevel().registryAccess());
        h.assertTrue(loaded instanceof HelmBlockEntity l && near(l.wheel(), -123.5), "wheel lost in the round trip: " + tag);

        // on a ship: assembled with a turned wheel, then the runtime is forgotten and rebuilt
        SailingGameTestsShips.basin(h, false);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED);
        if (h.getLevel().getBlockEntity(h.absolutePos(helm)) instanceof HelmBlockEntity hb) {
            hb.setWheel(90.0);
        }
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        h.runAfterDelay(5, () -> {
            BlockPos plot = helmOf(f.ship());
            h.assertTrue(near(wheel(h, plot), 90.0), "the wheel did not move with the ship: " + wheel(h, plot));
            h.assertTrue(near(f.runtime().wheelAngle(), 90.0), "runtime wheel after assembly: " + f.runtime().wheelAngle());
            SailingRuntimes.onShipRemoved(h.getLevel(), f.ship().id(), false); // as after an unload
            ShipBody ship = SableShips.byId(h.getLevel(), f.ship().id());
            SailingRuntime fresh = ship == null ? null : SailingRuntimes.getOrCreate(ship);
            h.assertTrue(fresh != null && fresh != f.runtime(), "no fresh runtime");
            h.assertTrue(near(fresh.wheelAngle(), 90.0), "wheel lost on reload: " + fresh.wheelAngle());
            h.succeed();
        });
    }

    /** A helmsman who walks more than {@code session_reach} (2) blocks from the helm lets go of the wheel. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void sessionEndsWhenTheHelmsmanWalksAway(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false);
        Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED));
        BlockPos helm = helmOf(f.ship());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.runAfterDelay(2, () -> {
            stand(player, f.ship(), helm, 0.5);
            use(h, helm, player);
            h.assertTrue(helm.equals(HelmService.session(player)), "use did not take the wheel");
            stand(player, f.ship(), helm, 1.9); // 1.9 blocks from the helm block: still at the wheel
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(helm.equals(HelmService.session(player)), "the session ended within reach");
            h.assertTrue(HelmService.turn(player, helm, 10.0), "delta within reach refused");
            stand(player, f.ship(), helm, 2.6);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(HelmService.session(player) == null, "the session survived walking away");
            h.assertTrue(!HelmService.turn(player, helm, 10.0), "a delta after walking away was applied");
            h.assertTrue(near(wheel(h, helm), 10.0), "the wheel did not stay where it was: " + wheel(h, helm));
            h.succeed();
        });
    }

    /** With {@code helm.wheel.drag_steering} off, a use steps the rudder as in spike 3 and starts no session. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_helm_wheel")
    public static void dragSteeringOffRestoresTheClickSteps(GameTestHelper h) {
        ConfigOverrides.during(h, HelmConfig.DRAG_STEERING, false);
        SailingGameTestsShips.basin(h, false);
        Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED));
        BlockPos helm = helmOf(f.ship());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.runAfterDelay(2, () -> {
            stand(player, f.ship(), helm, 0.5);
            // helm faces north: the helmsman's right hand is west (-x), the right third of the wheel steers to starboard
            Vec3 c = Vec3.atCenterOf(helm);
            BlockState s = h.getLevel().getBlockState(helm);
            s.useWithoutItem(h.getLevel(), player, new BlockHitResult(c.add(-0.4, 0, -0.5), Direction.NORTH, helm, false));
            int step = RudderSteps.fromProperty(h.getLevel().getBlockState(helm).getValue(HelmBlock.RUDDER));
            h.assertTrue(step == 1 && f.runtime().rudderStep() == 1, "click right did not step the rudder: " + step);
            h.assertTrue(HelmService.session(player) == null, "a click started a wheel session");
            h.assertTrue(!HelmService.turn(player, helm, 30.0), "a wheel delta was applied in click mode");
            // the wheel shows the step: one of three steps = a third of the lock
            h.assertTrue(near(wheel(h, helm), LOCK / 3), "the wheel does not show the step: " + wheel(h, helm));
        });
        h.runAfterDelay(4, () -> {
            double expected = RudderSteps.angle(1, SailingConfig.RUDDER_STEPS.get(), SailingConfig.MAX_RUDDER_ANGLE.get());
            h.assertTrue(near(f.runtime().rudderAngle(), expected), "rudder angle in click mode: " + f.runtime().rudderAngle());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the ship answers

    /**
     * Two ballasted ships under the same full sail in one basin; one helmsman turns his wheel hard to starboard, the
     * other hard to port, within the first ticks, and lets go. Each ship turns its way (the wheel stays turned).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = "pirates_n_ships_helm_wheel_under_sail")
    public static void turnedWheelTurnsTheShipUnderSail(GameTestHelper h) {
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, WIND, h.getLevel().getGameTime() + 700);
        SailingGameTestsShips.basin(h, true);
        int[] x0 = {3, 31};
        double[] turn = {LOCK, -LOCK};
        Fixture[] f = new Fixture[2];
        BlockPos[] helm = new BlockPos[2];
        Player[] p = new Player[2];
        double[][] heading = new double[2][2];
        for (int i = 0; i < 2; i++) {
            BlockPos land = SailingGameTestsShips.squareHull(h, x0[i], 3, SailTrim.FULL);
            SailingGameTestsShips.ballast(h, x0[i], 3);
            f[i] = SailingGameTestsShips.assemble(h, land);
            helm[i] = helmOf(f[i].ship());
            p[i] = h.makeMockPlayer(GameType.SURVIVAL);
        }
        h.onEachTick(() -> {
            long t = h.getTick();
            for (int i = 0; i < 2; i++) {
                if (f[i].ship().isRemoved()) continue;
                if (t <= 14) stand(p[i], f[i].ship(), helm[i], 0.5);
                if (t == 2) use(h, helm[i], p[i]);
                if (t > 2 && t <= 12) HelmService.turn(p[i], helm[i], Math.signum(turn[i]) * MAX_PER_TICK);
                if (t == 13) HelmService.release(p[i], helm[i]);
                if (t == HEAD_FROM) heading[i][1] = f[i].runtime().headingDegrees(f[i].ship());
                if (t == HEAD_TO) heading[i][0] = WindSample.normalizeDegrees(f[i].runtime().headingDegrees(f[i].ship()) - heading[i][1] + 180.0) - 180.0;
            }
        });
        h.runAfterDelay(HEAD_TO + 1, () -> {
            WindOverride.clear(h.getLevel().dimension().location().toString());
            double stb = heading[0][0], port = heading[1][0];
            Constants.LOG.info("[helm test] wheel {}°: heading change {}°, wheel {}°: heading change {}° over {} ticks",
                    wheel(h, helm[0]), String.format(java.util.Locale.ROOT, "%.2f", stb), wheel(h, helm[1]), String.format(java.util.Locale.ROOT, "%.2f", port), HEAD_TO - HEAD_FROM);
            h.assertTrue(near(wheel(h, helm[0]), LOCK) && near(wheel(h, helm[1]), -LOCK),
                    "wheels not at the locks: " + wheel(h, helm[0]) + ", " + wheel(h, helm[1]));
            h.assertTrue(stb > MIN_TURN, "wheel to starboard did not turn the ship to starboard: " + stb + "°");
            h.assertTrue(port < -MIN_TURN, "wheel to port did not turn the ship to port: " + port + "°");
            h.succeed();
        });
    }
}

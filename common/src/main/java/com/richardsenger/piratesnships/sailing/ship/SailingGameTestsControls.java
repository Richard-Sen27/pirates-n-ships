package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Spike 3 part 2 GameTests: helm steering and the capstan's anchor, on the 5×4×5 test hull of
 * {@link SailingGameTestsShips} (bow +Z, helm at the stern facing north, capstan on the deck forward of the mast).
 * Every test with a wind override has its own batch.
 */
public final class SailingGameTestsControls {

    private static final double WIND = 6.0;
    /** Heading window: the ship gets up to speed first. */
    private static final int HEAD_FROM = 40;
    private static final int HEAD_TO = 240;

    private SailingGameTestsControls() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsControls.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Test hull with a capstan; rudder at {@code step} (positive = starboard). Returns the helm (test-relative). */
    private static BlockPos ship(GameTestHelper h, SailTrim trim, int step) {
        return ship(h, trim, step, new BlockPos(19, 9, 6));
    }

    /** As above with the capstan at {@code capstan}: (19, 9, 6) is forward of the mast, (18, 9, 4) beside the helm (stern). */
    private static BlockPos ship(GameTestHelper h, SailTrim trim, int step, BlockPos capstan) {
        return ship(h, 17, trim, step, capstan);
    }

    private static BlockPos ship(GameTestHelper h, int x0, SailTrim trim, int step, BlockPos capstan) {
        BlockPos helm = SailingGameTestsShips.hull(h, x0, 3, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, trim);
        h.setBlock(helm, h.getBlockState(helm).setValue(HelmBlock.RUDDER, RudderSteps.toProperty(step)));
        h.setBlock(capstan, SailingBlocks.CAPSTAN.get());
        return helm;
    }

    private static void wind(GameTestHelper h, double strength) {
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, strength, h.getLevel().getGameTime() + 700);
    }

    private static void clearWind(GameTestHelper h) {
        SailingGameTestsShips.clearWind(h);
    }

    private static BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) {
                return p;
            }
        }
        throw new AssertionError("no " + block + " on the ship");
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** Heading change (degrees, positive = clockwise = to starboard) between HEAD_FROM and HEAD_TO, into a[0]. */
    private static double[] headingChange(GameTestHelper h, Fixture f) {
        double[] a = new double[2];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (f.ship().isRemoved()) return;
            if (t == HEAD_FROM) a[1] = f.runtime().headingDegrees(f.ship());
            if (t == HEAD_TO) a[0] = WindSample.normalizeDegrees(f.runtime().headingDegrees(f.ship()) - a[1] + 180.0) - 180.0;
        });
        return a;
    }

    private static void logTurn(String name, Fixture f, double change) {
        Vector3d v = f.runtime().shipFrameVelocity(f.ship(), new Vector3d());
        Constants.LOG.info("[sailing test] {}: heading change {}° over {} ticks, speed fwd {} m/s, rudder {}°, last forces {}", name,
                String.format("%.2f", change), HEAD_TO - HEAD_FROM, String.format("%.2f", v.z), f.runtime().rudderAngle(),
                f.runtime().lastBreakdown() == null ? "none"
                        : f.runtime().lastBreakdown().contributions().stream().map(c -> c.source() + "=" + c.torque()).toList());
    }

    // ------------------------------------------------------------------ steering under sail

    /*
     * Tolerances: with the defaults (rudder_strength 0.5, max angle 35°) the test hull at about 0.3 m/s turns 6 to 8°
     * in 200 ticks with full rudder (measured), and 0.0 to 2.4° midships (2.4° at x=226k): the midships drift depends on where the
     * random test grid lies, because far from the origin the 32-bit physics moves a slow ship in jerks (see the anchor
     * test). MIN_TURN (3°) is under half of the smallest measured turn net of the midships drift, MAX_DRIFT is above the largest measured drift.
     */
    private static final double MIN_TURN = 3.0;
    private static final double MAX_DRIFT = 3.5;

    /**
     * Three identical ships side by side in one basin and one wind, rudder hard to port, midships and hard to
     * starboard. Each turns its way; the turns are also compared with the midships ship's, which cancels the small
     * heading drift that depends on where the test grid lies.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = "pirates_n_ships_rudder_under_sail")
    public static void rudderTurnsTheShipUnderSail(GameTestHelper h) {
        wind(h, WIND);
        SailingGameTestsShips.basin(h, true);
        int[] steps = {-3, 0, 3};
        int[] x0 = {3, 17, 31};
        Fixture[] f = new Fixture[3];
        double[][] a = new double[3][];
        for (int i = 0; i < 3; i++) {
            f[i] = SailingGameTestsShips.assemble(h, ship(h, x0[i], SailTrim.FULL, steps[i], new BlockPos(x0[i] + 2, 9, 6)));
            a[i] = headingChange(h, f[i]);
        }
        h.runAfterDelay(HEAD_TO + 1, () -> {
            clearWind(h);
            logTurn("rudder port", f[0], a[0][0]);
            logTurn("rudder midships", f[1], a[1][0]);
            logTurn("rudder starboard", f[2], a[2][0]);
            double port = a[0][0], mid = a[1][0], stb = a[2][0];
            h.assertTrue(stb > MIN_TURN && stb - mid > MIN_TURN, "rudder to starboard did not turn to starboard: " + stb + "° (midships " + mid + "°)");
            h.assertTrue(port < -MIN_TURN && port - mid < -MIN_TURN, "rudder to port did not turn to port: " + port + "° (midships " + mid + "°)");
            h.assertTrue(Math.abs(mid) < MAX_DRIFT, "midships ship changed heading: " + mid + "°");
            h.succeed();
        });
    }

    /** No wind, sail furled, full rudder: the ship lies still and does not turn (the rudder needs way). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = "pirates_n_ships_rudder_at_rest")
    public static void rudderAtRestDoesNotTurn(GameTestHelper h) {
        wind(h, 0.0);
        SailingGameTestsShips.basin(h, true);
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FURLED, 3));
        double[] a = headingChange(h, f);
        h.runAfterDelay(HEAD_TO + 1, () -> {
            clearWind(h);
            logTurn("rudder at rest", f, a[0]);
            // MAX_DRIFT (1.5°): the rudder force is proportional to the speed along the bow, and the freshly assembled
            // hull still settles at about 0.06 m/s astern, which turns it about 0.7° in 10 s (measured). Under way at
            // 0.3 m/s the same rudder turns it 6 to 8°.
            h.assertTrue(Math.abs(a[0]) < MAX_DRIFT, "ship at rest turned: " + a[0] + "°");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ helm interaction

    /** Plain use steers (right third = starboard, middle = midships) and keeps the ship; sneak-use with an empty hand disassembles. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300)
    public static void helmUseSteersAndSneakUseDisassembles(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false);
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FURLED, 0));
        BlockPos helm = find(f.ship(), com.richardsenger.piratesnships.ship.assembly.AssemblyContent.HELM.get());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        // helm faces north: the helmsman stands north of it looking south, his right hand is west (-x)
        Vec3 c = Vec3.atCenterOf(helm);
        BlockHitResult right = new BlockHitResult(c.add(-0.4, 0, -0.5), Direction.NORTH, helm, false);
        BlockHitResult middle = new BlockHitResult(c.add(0.0, 0, -0.5), Direction.NORTH, helm, false);
        BlockHitResult left = new BlockHitResult(c.add(0.4, 0, -0.5), Direction.NORTH, helm, false);
        use(h, helm, player, right);
        use(h, helm, player, right);
        h.assertTrue(rudder(h, helm) == 2 && f.runtime().rudderStep() == 2, "two clicks right: rudder " + rudder(h, helm));
        use(h, helm, player, middle);
        h.assertTrue(rudder(h, helm) == 0, "middle click: rudder " + rudder(h, helm));
        for (int i = 0; i < 5; i++) use(h, helm, player, left);
        h.assertTrue(rudder(h, helm) == -3, "five clicks left stop at -3: " + rudder(h, helm));
        h.assertTrue(!f.ship().isRemoved(), "steering disassembled the ship");
        // sneaking with an item in hand steers too, it does not disassemble
        player.setShiftKeyDown(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        use(h, helm, player, middle);
        h.assertTrue(!f.ship().isRemoved() && rudder(h, helm) == 0, "sneak-use with an item must steer, not disassemble");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeedWhen(() -> {
            if (!f.ship().isRemoved()) {
                use(h, helm, player, middle); // refused while the ship still settles (spike 1 rules), then disassembles
            }
            h.assertTrue(f.ship().isRemoved(), "sneak-use with an empty hand did not disassemble yet");
        });
    }

    private static void use(GameTestHelper h, BlockPos plotPos, Player player, BlockHitResult hit) {
        BlockState s = h.getLevel().getBlockState(plotPos);
        s.useWithoutItem(h.getLevel(), player, hit);
    }

    private static int rudder(GameTestHelper h, BlockPos plotPos) {
        BlockState s = h.getLevel().getBlockState(plotPos);
        return s.getBlock() instanceof HelmBlock ? RudderSteps.fromProperty(s.getValue(HelmBlock.RUDDER)) : Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ anchor

    /*
     * Tolerance: at the defaults (slack 2, stiffness 0.5/s²) the sail's pull of well under 1 block/s² per unit mass
     * stretches the rode about one block beyond the slack, so the hawse stays within 4 blocks of the anchor point.
     *
     * Measured 2.08 blocks at x=55k.
     *
     * Release: checked by the forward velocity of the free ship, not by its displacement. Sable's physics is 32-bit and
     * the test grid lies up to 250,000 blocks from the origin, where one f32 step is 1/64 block: at 0.3 m/s a substep
     * moves less than half a step, and the ship reports velocity but does not move (measured at x=-191k: 0.2 blocks in
     * 9 s at 0.33 m/s; at x=55k it sailed 6.3 blocks). A stronger wind (15 blocks/s) pitched the hull over.
     */
    private static final double HOLD_RADIUS = 4.0;

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 640, batch = "pirates_n_ships_anchor_hold")
    public static void anchorHoldsUnderFullSailAndReleases(GameTestHelper h) {
        wind(h, WIND);
        SailingGameTestsShips.basin(h, true);
        // anchored from the stern, so the ship lies downwind of the anchor with the sail drawing. Anchored from the bow,
        // it swings head to wind (measured: then the square sail is aback and the ship can't sail off after raising).
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FULL, 0, new BlockPos(18, 9, 4)));
        double[] heading = new double[2];
        BlockPos capstan = find(f.ship(), SailingBlocks.CAPSTAN.get());
        Component dropped = ShipControls.useCapstan(h.getLevel(), capstan);
        h.assertTrue(key(dropped).equals(ShipControls.KEY_DROPPING), "drop refused: " + dropped.getString());
        double[] maxDist = new double[1];
        Vec3[] released = new Vec3[1];
        boolean[] pulled = new boolean[1];
        double[] free = new double[2];
        h.onEachTick(() -> {
            long t = h.getTick();
            ShipAnchor a = f.runtime().anchor();
            if (f.ship().isRemoved()) return;
            Vec3 hawse = f.ship().toWorld(Vec3.atCenterOf(capstan));
            if (t >= 60 && t < 300 && a != null) {
                maxDist[0] = Math.max(maxDist[0], Math.hypot(hawse.x - a.point().x, hawse.z - a.point().z));
            }
            if (t == 60) heading[0] = f.runtime().headingDegrees(f.ship());
            if (t == 300) {
                heading[1] = f.runtime().headingDegrees(f.ship());
                Component raised = ShipControls.useCapstan(h.getLevel(), capstan);
                h.assertTrue(key(raised).equals(ShipControls.KEY_RAISING), "raise refused: " + raised.getString());
            }
            if (t == 420) released[0] = hawse;
            if (t == 290) pulled[0] = hasAnchorForce(f);
            if (t >= 440 && t < 600) {
                free[0] += f.runtime().shipFrameVelocity(f.ship(), new Vector3d()).z;
                free[1]++;
            }
        });
        h.runAfterDelay(600, () -> {
            clearWind(h);
            Vec3 end = f.ship().toWorld(Vec3.atCenterOf(capstan));
            double sailed = released[0] == null ? 0 : Math.hypot(end.x - released[0].x, end.z - released[0].z);
            double freeFwd = free[1] == 0 ? 0 : free[0] / free[1];
            Constants.LOG.info("[sailing test] anchor at {}: max distance to the anchor point while holding {} blocks, heading {}° -> {}° while held, "
                            + "after raising mean forward {} m/s and {} blocks sailed in 180 ticks", end,
                    String.format("%.2f", maxDist[0]), String.format("%.1f", heading[0]), String.format("%.1f", heading[1]),
                    String.format("%.2f", freeFwd), String.format("%.2f", sailed));
            h.assertTrue(pulled[0], "no anchor force while holding");
            h.assertTrue(maxDist[0] > 0 && maxDist[0] < HOLD_RADIUS, "anchored ship drifted " + maxDist[0] + " blocks");
            h.assertTrue(f.runtime().anchor() == null && !hasAnchorForce(f), "anchor not stowed after raising");
            // 0.15 m/s: the free ship under this sail makes about 0.3 m/s (sail tests: > 0.3 m/s mean)
            h.assertTrue(freeFwd > 0.15, "ship did not sail away after raising the anchor: mean forward " + freeFwd);
            h.succeed();
        });
    }

    private static boolean hasAnchorForce(Fixture f) {
        var b = f.runtime().lastBreakdown();
        return b != null && b.contributions().stream().anyMatch(c -> c.source().equals("anchor") && c.force().length() > 0);
    }

    /** Drop takes anchor_drop_ticks, using the capstan mid-way reverses the ramp, the capstan shows the phase. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300)
    public static void anchorStateMachineTimingAndReversal(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.ANCHOR_DROP_TICKS, 40);
        ConfigOverrides.during(h, SailingConfig.ANCHOR_RAISE_TICKS, 100);
        SailingGameTestsShips.basin(h, true);
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FURLED, 0));
        BlockPos capstan = find(f.ship(), SailingBlocks.CAPSTAN.get());
        long[] start = new long[1];
        long[] t0 = {-1};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t0[0] < 0) {
                t0[0] = t;
                ShipControls.useCapstan(h.getLevel(), capstan);
                start[0] = t;
            }
            long dt = t - start[0];
            ShipAnchor a = f.runtime().anchor();
            if (dt == 30) {
                h.assertTrue(a != null && a.state().phase() == AnchorState.Phase.DROPPING && shown(h, capstan) == CapstanBlock.Phase.DROPPING,
                        "not dropping after 30 ticks: " + a);
            }
            if (dt == 45) {
                h.assertTrue(a != null && a.state().phase() == AnchorState.Phase.HOLDING && shown(h, capstan) == CapstanBlock.Phase.HOLDING,
                        "not holding after 45 ticks: " + a);
                ShipControls.useCapstan(h.getLevel(), capstan); // raise: 100 ticks from full hold
            }
            if (dt == 95) {
                // half raised (hold 0.5): reverse; the drop resumes from 0.5 and holds again after 20 more ticks
                h.assertTrue(a != null && a.state().phase() == AnchorState.Phase.RAISING && Math.abs(a.state().hold() - 0.5) < 0.03,
                        "not half raised after 50 ticks: " + a);
                ShipControls.useCapstan(h.getLevel(), capstan);
            }
            if (dt == 110) {
                h.assertTrue(a != null && a.state().phase() == AnchorState.Phase.DROPPING, "reversal did not drop again: " + a);
            }
            if (dt == 120) {
                h.assertTrue(a != null && a.state().phase() == AnchorState.Phase.HOLDING, "reversed drop not holding after 25 ticks: " + a);
                ShipControls.useCapstan(h.getLevel(), capstan);
            }
            if (dt == 230) {
                h.assertTrue(a == null && shown(h, capstan) == CapstanBlock.Phase.RAISED, "not stowed 110 ticks after raising: " + a);
                h.succeed();
            }
        });
    }

    private static CapstanBlock.Phase shown(GameTestHelper h, BlockPos plotPos) {
        BlockState s = h.getLevel().getBlockState(plotPos);
        return s.getBlock() instanceof CapstanBlock ? s.getValue(CapstanBlock.ANCHOR) : null;
    }

    /** A chain shorter than the depth: the drop is refused with a message and no anchor is out. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void capstanWithoutGroundInReachDoesNotHold(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.ANCHOR_CHAIN_LENGTH, 3);
        SailingGameTestsShips.basin(h, true);
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FURLED, 0));
        BlockPos capstan = find(f.ship(), SailingBlocks.CAPSTAN.get());
        h.runAfterDelay(2, () -> {
            Component c = ShipControls.useCapstan(h.getLevel(), capstan);
            h.assertTrue(key(c).equals(ShipControls.KEY_NO_GROUND), "expected 'no ground', got " + c.getString());
            h.assertTrue(f.runtime().anchor() == null && shown(h, capstan) == CapstanBlock.Phase.RAISED, "anchor out without ground");
            h.succeed();
        });
    }

    /** Rudder (helm block state) and anchor (ship user data) come back when the runtime is rebuilt from storage. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void rudderAndAnchorSurviveReload(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Fixture f = SailingGameTestsShips.assemble(h, ship(h, SailTrim.FURLED, -2));
        BlockPos capstan = find(f.ship(), SailingBlocks.CAPSTAN.get());
        h.runAfterDelay(2, () -> {
            ShipControls.useCapstan(h.getLevel(), capstan);
        });
        h.runAfterDelay(20, () -> {
            ShipAnchor before = f.runtime().anchor();
            SailingRuntimes.onShipRemoved(h.getLevel(), f.ship().id(), false); // forget the runtime, as after an unload
            ShipBody ship = SableShips.byId(h.getLevel(), f.ship().id());
            SailingRuntime fresh = ship == null ? null : SailingRuntimes.getOrCreate(ship);
            h.assertTrue(fresh != null && fresh != f.runtime(), "no fresh runtime");
            h.assertTrue(fresh.rudderStep() == -2, "rudder lost: " + fresh.rudderStep());
            h.assertTrue(before != null && before.equals(fresh.anchor()), "anchor lost: " + before + " vs " + fresh.anchor());
            h.succeed();
        });
    }
}

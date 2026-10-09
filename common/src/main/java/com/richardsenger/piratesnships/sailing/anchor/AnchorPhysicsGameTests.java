package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * AN2a GameTests (docs/design.md §5.2): the anchor's fall and chain on a sloop-sized hull. The hull is 9 wide (the
 * starter sloop's beam) and 17 long (the sloop is 29; a longer hull would not stop inside the largest test template), 4 high, with a
 * cobblestone keel row, its helm at the stern (bow +Z, starboard −X) and a capstan forward on the starboard side, so
 * the hawse hangs off the starboard bow. It floats in a 48 × 48 basin with the seabed 6 blocks down (stone at y=1, water
 * y=2..7). Every test overrides config or the wind and has its own batch.
 *
 * <p>"Way on" is given by adding a velocity of {@link #SPEED} along the bow ({@code ShipBody#addVelocity}) and dropping
 * the anchor two ticks later; the speed at the drop is the reference.
 */
public final class AnchorPhysicsGameTests {

    /** Hull corner: x 19..27, z 4..20. */
    private static final int X0 = 19, Z0 = 4;
    /** Beam of the hull, odd so the helm and the keel row sit on the centre line. */
    private static final int BEAM = 9;
    /** Ticks the assembled hull settles before it is pushed. */
    private static final int SETTLE = 40;
    /** Speed given to the hull along its bow [blocks/s]. */
    private static final double SPEED = 4.5;
    /**
     * The sails still drawing: a steady push along +Z (world, the wind from astern) per ton [blocks/s²]. Sable's water
     * drag takes about 1.6 per second of a hull's speed (measured: 3.84 → 0.40 blocks/s in 1.4 s, exponential), so a
     * hull without drive stops within 2 s on its own, before the anchor reaches a seabed 6 blocks down; a ship with way
     * on at anchoring is a ship under sail. This drive alone holds the hull at about 5 blocks/s.
     */
    private static final double DRIVE = 8.0;
    /** Holding power below {@link #DRIVE}: the sails drag the anchor. */
    private static final double WEAK = 3.0;

    private AnchorPhysicsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AnchorPhysicsGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** 48×48 stone basin, water y=2..7 (the seabed's top at y=2, 6 blocks below the surface). */
    private static void basin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    /** The sloop-sized hull; with {@code rig} a 5-wide square sail amidships at {@code trim}. Returns the helm. */
    private static BlockPos hull(GameTestHelper h, boolean rig, SailTrim trim) {
        for (int x = X0; x < X0 + BEAM; x++) {
            for (int z = Z0; z <= Z0 + 16; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == X0 || x == X0 + BEAM - 1 || z == Z0 || z == Z0 + 16;
                    boolean keel = y == 5 && x == X0 + BEAM / 2;
                    h.setBlock(new BlockPos(x, y, z), keel ? Blocks.COBBLESTONE : shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(X0 + BEAM / 2, 9, Z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(X0 + 1, 9, Z0 + 15), SailingBlocks.CAPSTAN.get());
        if (rig) {
            SailingGameTestsShips.rig(h, X0 + BEAM / 2, Z0 + 8, 2, 3, trim);
        }
        return helm;
    }

    /** Pins every anchor value these tests depend on to its default. */
    private static void pin(GameTestHelper h, double holding) {
        ConfigOverrides.during(h, AnchorConfig.SINK_SPEED, 4.0);
        ConfigOverrides.during(h, AnchorConfig.WATER_DRAG, 2.0);
        ConfigOverrides.during(h, AnchorConfig.CHAIN_STIFFNESS, AnchorChain.Params.DEFAULTS.stiffness());
        ConfigOverrides.during(h, AnchorConfig.CHAIN_DAMPING, AnchorChain.Params.DEFAULTS.damping());
        ConfigOverrides.during(h, AnchorConfig.HOLDING_FORCE, holding);
        ConfigOverrides.during(h, AnchorConfig.DRAG_SCRAPE_RATE, 1.0);
        ConfigOverrides.during(h, AnchorConfig.SETTLE_DRAG, 0.2);
        ConfigOverrides.during(h, AnchorConfig.HEEL_FACTOR, 0.25);
        ConfigOverrides.during(h, AnchorConfig.AT_REST_SPEED, 0.3);
        ConfigOverrides.during(h, AnchorConfig.RAISE_SPEED, 2.5);
        ConfigOverrides.during(h, SailingConfig.ANCHOR_CHAIN_LENGTH, 32);
        ConfigOverrides.during(h, SailingConfig.ANCHOR_ENABLED, true);
    }

    private static BlockPos capstan(Fixture f) {
        for (BlockPos p : f.ship().plotBlocks()) {
            if (f.ship().level().getBlockState(p).is(SailingBlocks.CAPSTAN.get())) {
                return p;
            }
        }
        throw new AssertionError("no capstan on the ship");
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static double speed(ShipBody ship) {
        Vector3d lin = new Vector3d();
        ship.velocities(lin, new Vector3d());
        return Math.hypot(lin.x, lin.z);
    }

    private static String f2(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    /**
     * A run at speed: settles, pushes the hull to {@link #SPEED} along its bow and drops the anchor two ticks later.
     * Records every tick from the drop: speed, heading, the anchor and its status.
     */
    private static final class Run {
        final Fixture f;
        final BlockPos capstan;
        long drop = -1;
        double v0;
        double heading0;
        Vec3 dropHawse;
        long landed = -1;
        Vec3 landing;
        Vec3 hawseAtLanding;
        long taut = -1;
        double headingAtTaut;
        double prevSpeed = -1;
        double worstLoss;
        boolean everHolding;
        boolean everDragging;
        boolean everForce;
        double maxStretch = Double.NEGATIVE_INFINITY;
        /** Largest stretch beyond the holding stretch ({@link AnchorChain#dragExcess}) while resting. */
        double maxExcess = Double.NEGATIVE_INFINITY;
        /** Least height of the hull's lowest corner above the seabed from the push on [blocks] (PHY1: it floats free). */
        double minClearance = Double.POSITIVE_INFINITY;
        final double drive;

        Run(GameTestHelper h, double drive) {
            this.drive = drive;
            basin(h);
            f = SailingGameTestsShips.assemble(h, hull(h, false, SailTrim.FURLED));
            capstan = capstan(f);
        }

        /** Per-tick bookkeeping; {@code t} is the test tick. */
        void tick(GameTestHelper h, long t) {
            if (f.ship().isRemoved()) {
                return;
            }
            if (t >= SETTLE && t % 5 == 0) {
                minClearance = Math.min(minClearance,
                        SailingGameTestsShips.hullBottomY(f.ship()) - (h.absolutePos(BlockPos.ZERO).getY() + 2.0));
            }
            if (t >= SETTLE && drive > 0.0) {
                f.ship().addVelocity(new Vector3d(0.0, 0.0, drive * 0.05), new Vector3d()); // the sails, wind from astern
            }
            if (t == SETTLE) {
                f.ship().addVelocity(new Vector3d(0.0, 0.0, SPEED), new Vector3d()); // bow +Z
                return;
            }
            if (t == SETTLE + 2) {
                v0 = speed(f.ship());
                heading0 = f.runtime().headingDegrees(f.ship());
                Component c = ShipControls.useCapstan(h.getLevel(), capstan);
                h.assertTrue(key(c).equals(ShipControls.KEY_DROPPING), "drop refused: " + c.getString());
                ShipAnchor a = f.runtime().anchor();
                dropHawse = f.ship().toWorld(a.hawse());
                drop = t;
                prevSpeed = v0;
                return;
            }
            if (drop < 0) {
                return;
            }
            double v = speed(f.ship());
            if (prevSpeed >= 0.2 * v0 && prevSpeed > 1.0e-3) {
                worstLoss = Math.max(worstLoss, (prevSpeed - v) / prevSpeed);
            }
            prevSpeed = v;
            ShipAnchor a = f.runtime().anchor();
            AnchorStatus st = f.runtime().anchorStatus();
            if (a == null || st == null) {
                return;
            }
            if (landed < 0 && a.state().phase() == AnchorState.Phase.HOLDING) {
                landed = t;
                landing = a.position();
                hawseAtLanding = st.hawse();
            }
            if (taut < 0 && st.resting() && st.taut()) {
                taut = t;
                headingAtTaut = f.runtime().headingDegrees(f.ship());
            }
            everHolding |= st.holding();
            everDragging |= st.dragging();
            everForce |= AnchorPhysics.lastForce(h.getLevel(), f.ship().id()) != null;
            if (st.resting()) {
                maxStretch = Math.max(maxStretch, st.distance() - st.paidOut());
                Vec3 d = st.ring().subtract(st.hawse());
                double horizontal = st.distance() > 1.0e-9 ? Math.hypot(d.x, d.z) / st.distance() : 0.0;
                maxExcess = Math.max(maxExcess, AnchorChain.dragExcess(st.distance() - st.paidOut(), horizontal, AnchorConfig.chainParams()));
            }
        }

        /** Heading change since the chain came taut, positive = clockwise = to starboard [degrees]. */
        double yawSinceTaut() {
            return WindSample.normalizeDegrees(f.runtime().headingDegrees(f.ship()) - headingAtTaut + 180.0) - 180.0;
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * (a, b) The hard turn: a hull with way on, its sails still drawing, drops its anchor at the starboard bow. Its way
     * (the speed along its heading) falls below 20 % of the speed at the drop within 10 s, no tick losing more than 15 %
     * of its speed (no instant stop), and it yaws to starboard by at least 30° within 5 s of the chain coming taut; what
     * motion is left after 10 s is the swing around the anchor toward head to the chain. The anchor, which kept the ship's speed when let go, lands astern
     * of the hawse by at least 2 blocks (the offset the human asked for: where it was on the boat against where it lies
     * a few seconds later). Then the capstan winds it back in from that offset and stows it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 900, batch = "pirates_n_ships_config_anchor_physics_turn")
    public static void anchorAtSpeedSwingsTheShipAndSlowsIt(GameTestHelper h) {
        pin(h, AnchorChain.Params.DEFAULTS.holding());
        Run r = new Run(h, DRIVE);
        long[] raised = {-1};
        double[] at5 = new double[1];
        double[] yaw = {Double.NaN};
        h.onEachTick(() -> {
            long t = h.getTick();
            r.tick(h, t);
            if (r.drop < 0) {
                return;
            }
            long dt = t - r.drop;
            if (r.taut >= 0 && t == r.taut + 100) {
                yaw[0] = r.yawSinceTaut();
            }
            if (dt == 100) {
                at5[0] = speed(r.f.ship());
            }
            if (dt == 200) {
                double v10 = speed(r.f.ship());
                // the way: speed along the ship's own heading; the rest of its motion is the swing around the anchor
                double way = Math.abs(r.f.runtime().shipFrameVelocity(r.f.ship(), new Vector3d()).z);
                Vec3 heading = new Vec3(Math.sin(Math.toRadians(r.heading0)), 0, -Math.cos(Math.toRadians(r.heading0)));
                double astern = r.landing == null ? Double.NaN : r.hawseAtLanding.subtract(r.landing).dot(heading);
                double ahead = r.landing == null ? Double.NaN : r.landing.subtract(r.dropHawse).dot(heading);
                Constants.LOG.info("[anchor test] turn: v0 {} b/s, after 5 s {}, after 10 s {} ({} %; way along the heading {}, {} %), "
                                + "worst tick loss {} %, landed after {} ticks {} blocks astern of the hawse ({} blocks ahead of the drop "
                                + "point in the world), taut after {} ticks, yaw {}° to starboard 5 s later, {}° after 10 s, max stretch {}, "
                                + "least clearance over the seabed {}",
                        f2(r.v0), f2(at5[0]), f2(v10), f2(100 * v10 / r.v0), f2(way), f2(100 * way / r.v0), f2(100 * r.worstLoss),
                        r.landed - r.drop, f2(astern), f2(ahead), r.taut - r.drop, f2(yaw[0]), f2(r.yawSinceTaut()), f2(r.maxStretch),
                        f2(r.minClearance));
                h.assertTrue(r.v0 >= 4.0, "the hull was not pushed to 4 blocks/s: " + r.v0);
                h.assertTrue(r.landed > 0 && astern >= 2.0, "the anchor did not land astern of the hawse: " + astern);
                h.assertTrue(r.taut > 0, "the chain never came taut");
                h.assertTrue(way < 0.2 * r.v0, "still making " + f2(way) + " of " + f2(r.v0) + " blocks/s along the heading after 10 s");
                h.assertTrue(r.worstLoss <= 0.15, "one tick lost " + f2(100 * r.worstLoss) + " % of the speed");
                h.assertTrue(yaw[0] >= 30.0, "yawed only " + f2(yaw[0]) + "° to starboard within 5 s of the chain coming taut");
                h.assertTrue(r.everForce, "no chain force");
                Component c = ShipControls.useCapstan(h.getLevel(), r.capstan);
                h.assertTrue(key(c).equals(ShipControls.KEY_RAISING), "raise refused: " + c.getString());
                raised[0] = t;
            }
            if (raised[0] >= 0 && r.f.runtime().anchor() == null) {
                AnchorEntity e = AnchorEntities.of(h.getLevel(), r.f.ship().id());
                h.assertTrue(e != null && !e.isOut(), "not stowed after raising");
                Constants.LOG.info("[anchor test] turn: raised from the offset in {} ticks", t - raised[0]);
                h.succeed();
            }
            if (raised[0] >= 0) {
                h.assertTrue(t - raised[0] < 400, "not stowed within 20 s of raising: " + r.f.runtime().anchor());
            }
        });
    }

    /**
     * (c) Holding power below the pull: with {@code holding_force} {@link #WEAK}, less than the sails' drive, the anchor drags over the seabed toward the ship
     * (it moves, it counts as dragging, never as holding while the ship is fast), the chain stays within
     * {@link AnchorChain#DRAG_SLACK} of the holding stretch, and the ship still slows.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = "pirates_n_ships_config_anchor_physics_drag")
    public static void weakAnchorDragsButSlowsTheShip(GameTestHelper h) {
        pin(h, WEAK);
        Run r = new Run(h, DRIVE);
        double[] atTaut = {-1};
        h.onEachTick(() -> {
            long t = h.getTick();
            r.tick(h, t);
            if (r.taut >= 0 && atTaut[0] < 0) {
                atTaut[0] = speed(r.f.ship());
            }
            if (r.drop >= 0 && t - r.drop == 100) { // 5 s: before the dragging ship reaches the basin's wall
                ShipAnchor a = r.f.runtime().anchor();
                double moved = a == null || r.landing == null ? 0.0 : Math.hypot(a.position().x - r.landing.x, a.position().z - r.landing.z);
                double v = speed(r.f.ship());
                Constants.LOG.info("[anchor test] drag: v0 {}, at taut {}, after 5 s {}, anchor dragged {} blocks, max stretch {} ({} past the holding stretch)",
                        f2(r.v0), f2(atTaut[0]), f2(v), f2(moved), f2(r.maxStretch), f2(r.maxExcess));
                h.assertTrue(r.taut > 0, "the chain never came taut");
                h.assertTrue(r.everDragging && moved >= 1.0, "the anchor did not drag: moved " + f2(moved));
                h.assertTrue(r.maxExcess <= AnchorChain.DRAG_SLACK + 0.2, "the chain stretched " + f2(r.maxExcess) + " past its holding stretch");
                h.assertTrue(v < atTaut[0], "the ship did not slow: " + f2(atTaut[0]) + " -> " + f2(v));
                h.succeed();
            }
        });
    }

    /**
     * (f) No holding power: the chain never pulls (no force is recorded), the anchor never holds, the ship is never
     * anchored, and the anchor follows the ship on its chain instead of stretching it. The ship's own slowdown is the
     * control for (a).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = "pirates_n_ships_config_anchor_physics_zero")
    public static void zeroHoldingPowerNeverHolds(GameTestHelper h) {
        pin(h, 0.0);
        Run r = new Run(h, DRIVE);
        boolean[] anchored = new boolean[1];
        h.onEachTick(() -> {
            long t = h.getTick();
            r.tick(h, t);
            anchored[0] |= r.f.runtime().isAnchored();
            if (r.drop >= 0 && t - r.drop == 60) { // 3 s: before the free ship reaches the basin's wall
                double v = speed(r.f.ship());
                Constants.LOG.info("[anchor test] zero holding (control): v0 {}, after 3 s {} ({} %), max stretch {}",
                        f2(r.v0), f2(v), f2(100 * v / r.v0), f2(r.maxStretch));
                h.assertTrue(r.landed > 0, "the anchor never landed");
                h.assertTrue(!r.everForce, "the chain pulled with no holding power");
                h.assertTrue(!r.everHolding && !anchored[0], "the anchor held with no holding power");
                h.assertTrue(r.maxStretch <= AnchorChain.DRAG_SLACK + 0.2, "the chain stretched " + f2(r.maxStretch));
                h.assertTrue(r.f.runtime().anchor() != null, "the anchor is gone");
                h.assertTrue(v > 0.6 * r.v0, "the ship slowed with no holding power: " + f2(v));
                h.succeed();
            }
        });
    }

    /**
     * (d, e) At rest under a moderate sail force (6 blocks/s of wind from astern on a full sail), the chain holds: for
     * 20 s the hawse stays within 1 block of the chain's reach (its stretch beyond the paid-out length), and the ship
     * counts as anchored. Raising the anchor stows it and the ship sails free.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 1100, batch = "pirates_n_ships_config_anchor_physics_sail")
    public static void chainHoldsUnderSailAndRaisingSetsTheShipFree(GameTestHelper h) {
        pin(h, AnchorChain.Params.DEFAULTS.holding());
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, 6.0, h.getLevel().getGameTime() + 1100);
        basin(h);
        Fixture f = SailingGameTestsShips.assemble(h, hull(h, true, SailTrim.FULL));
        BlockPos capstan = capstan(f);
        long[] holdFrom = {-1};
        long[] raised = {-1};
        long[] stowed = {-1};
        double[] maxStretch = {Double.NEGATIVE_INFINITY};
        boolean[] anchoredAtEnd = new boolean[1];
        Vec3[] freeFrom = new Vec3[1];
        double[] fwd = new double[2];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (f.ship().isRemoved()) {
                return;
            }
            if (t == 10) {
                Component c = ShipControls.useCapstan(h.getLevel(), capstan);
                h.assertTrue(key(c).equals(ShipControls.KEY_DROPPING), "drop refused: " + c.getString());
                return;
            }
            AnchorStatus st = f.runtime().anchorStatus();
            if (holdFrom[0] < 0 && st != null && st.resting() && st.taut()) {
                holdFrom[0] = t + 60; // let the first jerk settle
            }
            if (holdFrom[0] >= 0 && raised[0] < 0 && t >= holdFrom[0] && st != null) {
                maxStretch[0] = Math.max(maxStretch[0], st.distance() - st.paidOut());
                if (t == holdFrom[0] + 400) {
                    anchoredAtEnd[0] = f.runtime().isAnchored();
                    Component c = ShipControls.useCapstan(h.getLevel(), capstan);
                    h.assertTrue(key(c).equals(ShipControls.KEY_RAISING), "raise refused: " + c.getString());
                    raised[0] = t;
                }
            }
            if (raised[0] >= 0 && stowed[0] < 0 && f.runtime().anchor() == null) {
                stowed[0] = t;
                freeFrom[0] = f.ship().toWorld(Vec3.atCenterOf(capstan));
            }
            if (stowed[0] >= 0 && t > stowed[0] + 40 && t <= stowed[0] + 200) {
                fwd[0] += f.runtime().shipFrameVelocity(f.ship(), new Vector3d()).z;
                fwd[1]++;
            }
            if (stowed[0] >= 0 && t == stowed[0] + 200) {
                WindOverride.clear(h.getLevel().dimension().location().toString());
                Vec3 end = f.ship().toWorld(Vec3.atCenterOf(capstan));
                double mean = fwd[0] / fwd[1];
                Constants.LOG.info("[anchor test] sail: held 20 s with max stretch {} blocks, anchored {}, raised in {} ticks, "
                                + "then {} m/s forward and {} blocks sailed in 8 s", f2(maxStretch[0]), anchoredAtEnd[0],
                        stowed[0] - raised[0], f2(mean), f2(Math.hypot(end.x - freeFrom[0].x, end.z - freeFrom[0].z)));
                h.assertTrue(maxStretch[0] <= 1.0, "the ship pulled the chain " + f2(maxStretch[0]) + " blocks past its length");
                h.assertTrue(anchoredAtEnd[0], "the held ship did not count as anchored");
                h.assertTrue(mean > 0.1, "the ship did not sail free after raising: " + f2(mean) + " m/s");
                h.succeed();
            }
            if (holdFrom[0] < 0) {
                h.assertTrue(t < 200, "the chain never came taut under sail");
            }
        });
    }
}

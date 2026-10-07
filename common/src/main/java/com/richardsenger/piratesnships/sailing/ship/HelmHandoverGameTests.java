package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.HelmRules;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * HL1b (docs/design.md §4.1, §5.3): the sailing runtime's rudder follows the ship record's steering helm. A second helm
 * placed or broken beside it changes nothing; when the steering helm breaks, the ship is helmless until the second helm
 * is used, and then that helm steers from midships. A ship Sable moves to a new body keeps its bow.
 *
 * <p>The deck is a 6×6 plank plate on stone with the helm amidships (facing east, so the bow is not the default).
 */
public final class HelmHandoverGameTests {

    private static final String BATCH = "pirates_n_ships_helm_handover_";
    private static final BlockPos HELM = new BlockPos(5, 3, 5);
    private static final BlockPos OTHER = new BlockPos(3, 3, 6);

    private HelmHandoverGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HelmHandoverGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Deck(ShipBody ship, BlockPos helm, BlockPos other) { }

    private static BlockState helmState() {
        return AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
    }

    private static ShipBody assemble(GameTestHelper h, BlockPos helmRel) {
        AssemblyResult r = ShipTestCleanup.assemble(h, helmRel);
        ShipBody ship = r.shipId() == null ? null : SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        return ship;
    }

    private static BlockPos plotOf(GameTestHelper h, ShipBody ship, BlockPos rel) {
        return BlockPos.containing(ship.toPlot(Vec3.atCenterOf(h.absolutePos(rel))));
    }

    private static Deck deck(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.STEERING_ENABLED, true);
        ConfigOverrides.during(h, HelmConfig.DRAG_STEERING, true);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 7; x++) {
            for (int z = 2; z <= 7; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        h.setBlock(HELM, helmState());
        ShipBody ship = assemble(h, HELM);
        return new Deck(ship, plotOf(h, ship, HELM), plotOf(h, ship, OTHER));
    }

    private static List<ShipBody> shipsHere(GameTestHelper h) {
        AABB area = h.getBounds().inflate(4);
        List<ShipBody> out = new ArrayList<>();
        for (ShipBody s : SableShips.all(h.getLevel())) {
            if (!s.isRemoved() && s.worldBounds().intersects(area)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Tracks every ship that appears in the test area for removal at the end (split pieces included). */
    private static void trackPieces(GameTestHelper h) {
        Set<UUID> seen = new HashSet<>();
        h.onEachTick(() -> {
            for (ShipBody s : shipsHere(h)) {
                if (seen.add(s.id())) {
                    ShipTestCleanup.track(h, s.id());
                }
            }
        });
    }

    private static SailingRuntime runtime(GameTestHelper h, ShipBody ship) {
        SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
        h.assertTrue(rt != null, "the ship has no sailing runtime");
        return rt;
    }

    // ------------------------------------------------------------------ tests

    /** A second helm placed and broken again leaves the steering helm's rudder alone. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "second_broken")
    public static void breakingASecondHelmKeepsTheFirstSteering(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            h.assertTrue(d.helm().equals(rt.helm()), "the runtime does not steer from the assembly helm: " + rt.helm());
            level.setBlock(d.other(), helmState(), 3);
            h.assertTrue(ShipHelm.role(d.ship(), d.other()) == HelmRules.Role.SECOND, "the placed helm is not a second helm");
            h.assertTrue(d.helm().equals(rt.helm()), "placing a second helm moved the runtime's helm to " + rt.helm());
            ShipControls.setWheel(level, d.helm(), 30.0);
            level.destroyBlock(d.other(), false);
            h.assertTrue(d.helm().equals(rt.helm()), "breaking the second helm took the runtime's helm: " + rt.helm());
            h.assertTrue(rt.wheelAngle() == 30.0, "breaking the second helm reset the wheel: " + rt.wheelAngle());
        });
        h.runAfterDelay(6, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            h.assertTrue(d.helm().equals(rt.helm()), "the first helm lost the steering: " + rt.helm());
            h.assertTrue(rt.rudderAngle() != 0.0, "the first helm's wheel no longer turns the rudder");
            h.succeed();
        });
    }

    /**
     * The steering helm breaks with the wheel turned: the ship is helmless and the rudder midships; the second helm's
     * first use makes it the steering helm, and the runtime takes it at midships and follows its wheel.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "first_broken")
    public static void theSecondHelmTakesOverAtMidshipsWhenTheFirstBreaks(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            level.setBlock(d.other(), helmState(), 3);
            ShipControls.setWheel(level, d.helm(), 40.0);
            level.destroyBlock(d.helm(), false);
            h.assertTrue(ShipHelm.steering(d.ship()) == null, "the ship should be helmless until the second helm is used");
            h.assertTrue(rt.helm() == null && rt.wheelAngle() == 0.0, "the helmless runtime kept a rudder: " + rt.helm()
                    + " at " + rt.wheelAngle());
        });
        h.runAfterDelay(5, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            h.assertTrue(rt.helm() == null && rt.rudderAngle() == 0.0, "the helmless ship steers: " + rt.rudderAngle());
            // the second helm's first use (HelmBlock#useWithoutItem claims it before the wheel session starts)
            h.assertTrue(ShipHelm.claim(d.ship(), d.other()) == HelmRules.Role.ATTACHES, "the second helm did not attach");
        });
        h.runAfterDelay(7, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            h.assertTrue(d.other().equals(rt.helm()), "the runtime did not take the second helm: " + rt.helm());
            h.assertTrue(rt.wheelAngle() == 0.0 && rt.rudderStep() == 0 && rt.rudderAngle() == 0.0,
                    "the second helm did not take over at midships: wheel " + rt.wheelAngle() + ", rudder " + rt.rudderAngle());
            ShipControls.setWheel(level, d.other(), 30.0);
        });
        h.runAfterDelay(10, () -> {
            SailingRuntime rt = runtime(h, d.ship());
            h.assertTrue(d.other().equals(rt.helm()), "the second helm lost the steering: " + rt.helm());
            h.assertTrue(rt.rudderAngle() != 0.0, "the second helm's wheel does not turn the rudder");
            h.succeed();
        });
    }

    /** A fresh scan steers from the ship record's steering helm, not from the first helm it finds. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "scan")
    public static void aScanPrefersTheSteeringHelm(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            level.setBlock(d.other(), helmState(), 3);
            for (BlockPos steering : List.of(d.other(), d.helm(), d.other())) {
                ShipHelm.setSteering(d.ship(), steering);
                SailingRuntimes.onShipRemoved(level, d.ship().id(), false);
                SailingRuntime rt = runtime(h, d.ship());
                h.assertTrue(steering.equals(rt.helm()), "the scan steers from " + rt.helm() + ", the record says " + steering);
            }
            // a recorded helm that is gone leaves the ship helmless, even with a second helm standing
            level.destroyBlock(d.other(), false);
            SailingRuntimes.onShipRemoved(level, d.ship().id(), false);
            h.assertTrue(runtime(h, d.ship()).helm() == null, "the scan of a helmless ship found a helm");
            h.succeed();
        });
    }

    /**
     * HL1's emptied-body fixture (see {@code HelmReplaceGameTests#theShipFollowsTheBodySableMovesItTo}): after the
     * helm and then the remaining root break, Sable moves the rest of the ship into a new body. That body keeps the
     * bow the ship had, although it has no helm to derive one from.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "bow_moved")
    public static void theBowSurvivesTheBodyMove(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        for (int x = 8; x <= 10; x++) {
            for (int z = 4; z <= 6; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        BlockPos bridge = new BlockPos(7, 2, 5);
        h.setBlock(bridge, helmState());
        h.setBlock(new BlockPos(3, 3, 5), SailingBlocks.SAIL_WINCH.get());
        h.setBlock(new BlockPos(5, 3, 5), SailingBlocks.CAPSTAN.get());
        ShipBody ship = assemble(h, bridge);
        UUID original = ship.id();
        BlockPos helm = plotOf(h, ship, bridge);
        trackPieces(h);
        String[] bow = new String[1];
        h.runAfterDelay(2, () -> {
            bow[0] = runtime(h, ship).bow().name();
            h.assertTrue(!bow[0].equals(BowFrame.SOUTH.name()), "the fixture's bow is the default, the test would prove nothing");
            h.assertTrue(bow[0].equals(ship.userData(SailingRuntimes.USER_DATA_KEY).getString("bow")), "the bow is not stored");
        });
        h.runAfterDelay(3, () -> level.destroyBlock(helm, true));
        h.runAfterDelay(30, () -> {
            ShipBody body = SableShips.byId(level, original);
            h.assertTrue(body != null && body.plotBlocks().size() == 17, "the first split did not leave the 17-block plate: "
                    + (body == null ? null : body.plotBlocks().size()));
            BlockPos root = SableShips.heatMapRoot(body);
            h.assertTrue(root != null, "no heat-map root found");
            level.destroyBlock(root, true);
        });
        h.runAfterDelay(60, () -> {
            ShipBody keeper = null;
            for (ShipBody s : shipsHere(h)) {
                if (ShipHelm.isOurShip(s) && !ShipSplits.isWreck(s)) {
                    h.assertTrue(keeper == null, "two bodies are the ship");
                    keeper = s;
                }
            }
            h.assertTrue(keeper != null, "no body is the ship any more");
            h.assertTrue(!keeper.id().equals(original), "Sable did not move the rest into a new body (the case under test)");
            h.assertTrue(bow[0].equals(keeper.userData(SailingRuntimes.USER_DATA_KEY).getString("bow")),
                    "the new body's stored bow is " + keeper.userData(SailingRuntimes.USER_DATA_KEY));
            SailingRuntime rt = runtime(h, keeper);
            h.assertTrue(bow[0].equals(rt.bow().name()), "the moved ship's runtime faces " + rt.bow().name() + ", not " + bow[0]);
            h.assertTrue(!keeper.userData(SailingRuntimes.USER_DATA_KEY).contains(ShipControls.ANCHOR_TAG),
                    "a plot-bound anchor was carried to the new body");
            h.succeed();
        });
    }
}

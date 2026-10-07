package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.SableSplits;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * RS1: a ship whose only connecting block is destroyed splits (Sable), and our rules give each piece its role. The
 * "dumbbell" is two 3×3 plank plates joined by one plank, the helm on plate A; Sable sees blocks as connected over
 * faces and edges, so the plates (two blocks apart) touch only through the joint.
 *
 * <p>Every test has its own batch: a split creates bodies that other tests in the area must not see, the wind override
 * is level-wide, and one test switches Sable's splitting off for the whole server.
 */
public final class SplitGameTests {

    private static final String BATCH = "pirates_n_ships_split_";
    /** Plate A (with the helm) at x 4..6, the joint at x 7, plate B at x 8..10; z 4..6; planks at y 2 on stone. */
    private static final BlockPos HELM = new BlockPos(5, 3, 5);
    private static final BlockPos JOINT = new BlockPos(7, 2, 5);
    private static final String NAME = "Black Gull";
    private static final String FLAG = "jolly_roger";

    private SplitGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SplitGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void plate(GameTestHelper h, int x0, int z0, int y, Block block) {
        for (int x = x0; x < x0 + 3; x++) {
            for (int z = z0; z < z0 + 3; z++) {
                h.setBlock(new BlockPos(x, y, z), block);
            }
        }
    }

    /** The dumbbell on a stone floor; returns the assembled ship, named and flagged. */
    private static ShipBody dumbbell(GameTestHelper h) {
        floor(h, 24);
        plate(h, 4, 4, 2, Blocks.OAK_PLANKS);
        h.setBlock(JOINT, Blocks.OAK_PLANKS);
        plate(h, 8, 4, 2, Blocks.OAK_PLANKS);
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return assembleNamed(h, HELM);
    }

    private static ShipBody assembleNamed(GameTestHelper h, BlockPos helm) {
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) {
            throw new AssertionError("no ship after assembly");
        }
        ShipAssembler.name(ship, NAME);
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        ShipData d = registry.find(ship.id()).orElseThrow();
        registry.put(new ShipData(d.id(), d.name(), d.owner(), d.crew(), FLAG, d.dimension()));
        return ship;
    }

    /** The plot position of the ship block that was at test-relative {@code rel} when the ship was assembled. */
    private static BlockPos plotOf(GameTestHelper h, ShipBody ship, BlockPos rel) {
        return BlockPos.containing(ship.toPlot(Vec3.atCenterOf(h.absolutePos(rel))));
    }

    /** Ships whose world bounds touch the test area. */
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

    private static @Nullable ShipBody withHelm(GameTestHelper h, List<ShipBody> ships) {
        for (ShipBody s : ships) {
            for (BlockPos p : s.plotBlocks()) {
                if (h.getLevel().getBlockState(p).getBlock() instanceof HelmBlock) {
                    return s;
                }
            }
        }
        return null;
    }

    private static ShipData record(GameTestHelper h, UUID id) {
        return ShipRegistry.get(h.getLevel().getServer()).find(id)
                .orElseThrow(() -> new AssertionError("no ship record for " + id));
    }

    // ------------------------------------------------------------------ tests

    /**
     * The joint is destroyed: two bodies within a few ticks. The helm side keeps the id (the helm is Sable's heat-map
     * root, see ShipAssembler), the name and the flag; the other is a nameless wreck whose origin is the ship.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "dumbbell")
    public static void dumbbellSplitLeavesTheShipOnTheHelmSide(GameTestHelper h) {
        ShipBody ship = dumbbell(h);
        UUID original = ship.id();
        BlockPos joint = plotOf(h, ship, JOINT);
        trackPieces(h);
        h.runAfterDelay(2, () -> h.getLevel().destroyBlock(joint, false));
        h.succeedWhen(() -> {
            List<ShipBody> ships = shipsHere(h);
            h.assertTrue(ships.size() == 2, "expected 2 bodies after the split, found " + ships.size());
            ShipBody keeper = withHelm(h, ships);
            h.assertTrue(keeper != null, "no body holds the helm");
            ShipBody wreck = ships.get(0) == keeper ? ships.get(1) : ships.get(0);
            h.assertTrue(keeper.id().equals(original), "the helm side has a new id " + keeper.id() + ", expected " + original);
            ShipData kept = record(h, keeper.id());
            h.assertTrue(NAME.equals(kept.name()) && FLAG.equals(kept.flag()), "the ship lost its name or flag: " + kept);
            ShipSplits.Lineage k = ShipSplits.lineage(keeper);
            h.assertTrue(!k.wreck() && k.origin().equals(original), "the keeper's line is wrong: " + k);
            ShipData wr = record(h, wreck.id());
            h.assertTrue(wr.name().isEmpty() && wr.flag().isEmpty(), "the wreck has a name or flag: " + wr);
            ShipSplits.Lineage w = ShipSplits.lineage(wreck);
            h.assertTrue(w.wreck() && w.origin().equals(original) && NAME.equals(w.wreckOf()), "the wreck's line is wrong: " + w);
            h.assertTrue(ShipSplits.isWreck(wreck) && !ShipSplits.isWreck(keeper), "isWreck disagrees with the line");
            h.assertTrue(wreck.plotBlocks().size() == 9, "the wreck holds " + wreck.plotBlocks().size() + " blocks, expected plate B");
        });
    }

    /**
     * After a reload Sable's root can be on the loose side, so Sable leaves the helm side in the new body: the identity
     * moves to it. Fed as a batch of two real bodies (the helm plate as the piece cut off the plain plate).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = BATCH + "identity_moves")
    public static void identityMovesToTheNewBodyWithTheHelm(GameTestHelper h) {
        floor(h, 24);
        ServerLevel level = h.getLevel();
        // the "parent": plate B alone, made a ship directly (no helm to assemble from)
        plate(h, 8, 4, 2, Blocks.OAK_PLANKS);
        List<BlockPos> plateB = new ArrayList<>();
        for (int x = 8; x < 11; x++) {
            for (int z = 4; z < 7; z++) {
                plateB.add(h.absolutePos(new BlockPos(x, 2, z)));
            }
        }
        ShipBody parent = SableShips.assemble(level, plateB.get(4), plateB, plateB.get(0), plateB.get(8));
        h.assertTrue(parent != null, "no parent body");
        ShipTestCleanup.track(h, parent.id());
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        registry.put(new ShipData(parent.id(), NAME, java.util.Optional.empty(), List.of(), FLAG, level.dimension().location()));
        // the "piece": plate A with the helm
        plate(h, 4, 4, 2, Blocks.OAK_PLANKS);
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        AssemblyResult r = ShipTestCleanup.assemble(h, HELM);
        h.assertTrue(r.shipId() != null, "piece assembly failed: " + r);
        ShipBody piece = SableShips.byId(level, r.shipId());
        List<BlockPos> pieceBlocks = piece.plotBlocks();

        ShipSplits.SplitEvent e = ShipSplits.process(level, parent.id(),
                List.of(new SableSplits.Piece(piece.id(), pieceBlocks, BlockPos.ZERO)));
        h.assertTrue(e != null && e.keeper().equals(piece.id()), "the helm piece is not the keeper: " + e);
        ShipData kept = record(h, piece.id());
        h.assertTrue(NAME.equals(kept.name()) && FLAG.equals(kept.flag()), "the identity did not move: " + kept);
        ShipSplits.Lineage k = ShipSplits.lineage(piece);
        h.assertTrue(!k.wreck() && k.origin().equals(parent.id()), "the keeper's origin is not the original: " + k);
        ShipSplits.Lineage w = ShipSplits.lineage(parent);
        h.assertTrue(w.wreck() && w.origin().equals(parent.id()) && NAME.equals(w.wreckOf()), "the parent is not a wreck: " + w);
        h.assertTrue(record(h, parent.id()).name().isEmpty(), "the wreck kept the name");
        ShipSplits.Relocation moved = ShipSplits.relocate(level, parent.id(), pieceBlocks.get(0));
        h.assertTrue(moved != null && moved.ship().equals(piece.id()) && moved.keeper(), "relocation of a moved block: " + moved);
        BlockPos stayed = parent.plotBlocks().get(0);
        ShipSplits.Relocation still = ShipSplits.relocate(level, parent.id(), stayed);
        h.assertTrue(still != null && still.ship().equals(parent.id()) && !still.keeper(), "relocation of a wreck block: " + still);
        h.succeed();
    }

    /** A 2-block stub cut off at the same time breaks up into items; no third body remains. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "tiny")
    public static void tinyPieceBreaksUpIntoItems(GameTestHelper h) {
        floor(h, 24);
        plate(h, 4, 4, 2, Blocks.OAK_PLANKS);
        h.setBlock(JOINT, Blocks.OAK_PLANKS);
        plate(h, 8, 4, 2, Blocks.OAK_PLANKS);
        BlockPos stubJoint = new BlockPos(5, 2, 3);
        h.setBlock(stubJoint, Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(5, 2, 2), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(5, 2, 1), Blocks.OAK_LOG);
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        ShipBody ship = assembleNamed(h, HELM);
        BlockPos joint = plotOf(h, ship, JOINT);
        BlockPos joint2 = plotOf(h, ship, stubJoint);
        Vec3 stubWorld = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 1)));
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            h.getLevel().destroyBlock(joint, false);
            h.getLevel().destroyBlock(joint2, false);
        });
        h.succeedWhen(() -> {
            List<ShipBody> ships = shipsHere(h);
            h.assertTrue(ships.size() == 2, "expected the ship and one wreck, found " + ships.size() + " bodies");
            for (ShipBody s : ships) {
                h.assertTrue(s.plotBlocks().size() > 3, "a tiny piece is still a body: " + s.plotBlocks().size() + " blocks");
            }
            int logs = 0;
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(stubWorld, stubWorld).inflate(4))) {
                if (item.getItem().is(Items.OAK_LOG)) {
                    logs += item.getItem().getCount();
                }
            }
            h.assertTrue(logs == 2, "expected the stub's 2 logs as items near it, found " + logs);
        });
    }

    /**
     * A crew member at a station on the loose piece is released (its order ends with the station) and stays aboard
     * that piece; one at a station on the helm side keeps its station.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "crew")
    public static void crewOnTheLoosePieceStaysAboardWithoutOrders(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.SEAT_CHECK_INTERVAL, 5);
        floor(h, 24);
        plate(h, 4, 4, 2, Blocks.OAK_PLANKS);
        h.setBlock(JOINT, Blocks.OAK_PLANKS);
        plate(h, 8, 4, 2, Blocks.OAK_PLANKS);
        BlockPos looseWinch = new BlockPos(9, 3, 5);
        BlockPos keptWinch = new BlockPos(4, 3, 4);
        h.setBlock(looseWinch, SailingBlocks.SAIL_WINCH.get());
        h.setBlock(keptWinch, SailingBlocks.SAIL_WINCH.get());
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        ShipBody ship = assembleNamed(h, HELM);
        ServerLevel level = h.getLevel();
        BlockPos loosePlot = plotOf(h, ship, looseWinch);
        BlockPos keptPlot = plotOf(h, ship, keptWinch);
        CrewMember loose = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(9, 3, 6));
        CrewMember kept = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(4, 3, 5));
        h.assertTrue(CrewStations.assign(level, loose, loosePlot) == CrewStations.AssignResult.ASSIGNED, "could not assign the loose crew");
        h.assertTrue(CrewStations.assign(level, kept, keptPlot) == CrewStations.AssignResult.ASSIGNED, "could not assign the kept crew");
        BlockPos joint = plotOf(h, ship, JOINT);
        trackPieces(h);
        h.runAfterDelay(5, () -> level.destroyBlock(joint, false));
        h.runAfterDelay(40, () -> {
            List<ShipBody> ships = shipsHere(h);
            h.assertTrue(ships.size() == 2, "expected 2 bodies, found " + ships.size());
            ShipBody keeper = withHelm(h, ships);
            ShipBody wreck = ships.get(0) == keeper ? ships.get(1) : ships.get(0);
            h.assertTrue(loose.assignment() == null && !loose.isPassenger(), "the crew on the wreck still mans its station: "
                    + loose.assignment());
            BlockPos winchOnWreck = null;
            for (BlockPos p : wreck.plotBlocks()) {
                if (level.getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())) {
                    winchOnWreck = p;
                }
            }
            h.assertTrue(winchOnWreck != null, "the winch is not on the wreck");
            StationState<Object> st = Stations.state(new StationRef(wreck.id(), winchOnWreck));
            h.assertTrue(st == null || !st.isOccupiedBy(loose.getUUID()), "the wreck's station is still occupied");
            double off = wreck.toPlot(loose.position()).distanceTo(Vec3.atBottomCenterOf(winchOnWreck));
            h.assertTrue(off < 2.5, "the crew member left the wreck: " + off + " blocks from the winch (plot frame)");
            h.assertTrue(kept.assignment() != null && kept.assignment().ship().equals(keeper.id()) && kept.isAtStation(),
                    "the crew on the ship lost its station: " + kept.assignment());
            loose.discard();
            kept.discard();
            h.succeed();
        });
    }

    /**
     * A wreck's sails give no drive. Two ballasted 5×4×5 hulls joined by a plank float in a basin with the wind from
     * astern; hull B carries a full square sail, hull A the helm. One pair is split at once, the other stays whole
     * (control). The wreck has a sailing runtime with its sail set, yet does not move; the control sails.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "wreck_sails")
    public static void wreckSailsGiveNoDrive(GameTestHelper h) {
        String dim = h.getLevel().dimension().location().toString();
        WindOverride.set(dim, 0.0, 6.0, h.getLevel().getGameTime() + 400);
        SailingGameTestsShips.basin(h, true);
        ShipBody split = pair(h, 3);
        ShipBody control = pair(h, 22);
        BlockPos joint = plotOf(h, split, new BlockPos(13, 8, 5));
        trackPieces(h);
        h.runAfterDelay(3, () -> h.getLevel().destroyBlock(joint, false));
        double[] wreckSum = new double[2];
        double[] controlSum = new double[2];
        UUID[] wreckId = new UUID[1];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                for (ShipBody s : shipsHere(h)) {
                    if (ShipSplits.isWreck(s)) {
                        wreckId[0] = s.id();
                    }
                }
            }
            if (t >= 40 && t < 140 && wreckId[0] != null) {
                ShipBody w = SableShips.byId(h.getLevel(), wreckId[0]);
                if (w != null) {
                    Vector3d v = w.linearVelocity();
                    wreckSum[0] += Math.hypot(v.x, v.z);
                    wreckSum[1]++;
                }
                Vector3d c = control.linearVelocity();
                controlSum[0] += Math.hypot(c.x, c.z);
                controlSum[1]++;
            }
        });
        h.runAfterDelay(141, () -> {
            WindOverride.clear(dim);
            h.assertTrue(wreckId[0] != null, "no wreck after the split");
            SailingRuntime rt = SailingRuntimes.get(h.getLevel(), wreckId[0]);
            h.assertTrue(rt != null && rt.unfurledCount() > 0, "the wreck has no sailing runtime with a set sail");
            double wreck = wreckSum[0] / Math.max(1, wreckSum[1]);
            double ctrl = controlSum[0] / Math.max(1, controlSum[1]);
            Constants.LOG.info("[split test] wreck mean speed {} m/s, control {} m/s", String.format("%.3f", wreck), String.format("%.3f", ctrl));
            // measured: wreck 0.001 m/s, control 0.20 m/s
            h.assertTrue(ctrl > 0.1, "the control pair did not sail: " + ctrl);
            h.assertTrue(wreck < 0.05 && wreck < ctrl / 3, "the wreck sails: " + wreck + " m/s (control " + ctrl + ")");
            h.succeed();
        });
    }

    /** Hull A at x 8..12 with the helm, hull B at x 14..18 with a full sail, both ballasted; a deck plank joins them. */
    private static ShipBody pair(GameTestHelper h, int z0) {
        hull(h, 8, z0);
        hull(h, 14, z0);
        SailingGameTestsShips.ballast(h, 8, z0);
        SailingGameTestsShips.ballast(h, 14, z0);
        h.setBlock(new BlockPos(13, 8, z0 + 2), Blocks.OAK_PLANKS);
        SailingGameTestsShips.rig(h, 16, z0 + 2, 1, 3, SailTrim.FULL);
        BlockPos helm = new BlockPos(10, 9, z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return assembleNamed(h, helm);
    }

    private static void hull(GameTestHelper h, int x0, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
    }

    /** With {@code assembly.split.enabled} off, Sable's splitting is held off: the cut ship stays one body. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = BATCH + "disabled")
    public static void splittingDisabledKeepsOneBody(GameTestHelper h) {
        ConfigOverrides.during(h, AssemblyConfig.SPLIT_ENABLED, false);
        ShipSplits.syncSableSplitting();
        h.assertTrue(!SableSplits.sableSplitting(), "Sable's sub_level_splitting is still on");
        ShipBody ship = dumbbell(h);
        BlockPos joint = plotOf(h, ship, JOINT);
        trackPieces(h);
        h.runAfterDelay(2, () -> h.getLevel().destroyBlock(joint, false));
        h.runAfterDelay(30, () -> {
            List<ShipBody> ships = shipsHere(h);
            h.assertTrue(ships.size() == 1 && ships.get(0).id().equals(ship.id()), "the ship split: " + ships.size() + " bodies");
            h.assertTrue(ship.plotBlocks().size() == 19, "the ship holds " + ship.plotBlocks().size() + " blocks, expected 19");
            h.succeed();
        });
    }
}

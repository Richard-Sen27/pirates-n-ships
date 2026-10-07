package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/**
 * RS2: rejoining a split-off piece to its ship with the Shipwright's Toolkit. The "dumbbell" of the RS1 tests (two 3×3
 * plank plates joined by one plank, the helm on plate A) is split by breaking the joint; the helm side stays the ship
 * (the keeper, original id) and plate B becomes a wreck. The wreck is then posed against the keeper through
 * {@link ShipBody#placeAt}, as the rigging tests pose ships, and a mock player marks the keeper and uses the toolkit on
 * the wreck.
 *
 * <p>Every test has its own batch: splits create bodies other tests must not see, and several override config values.
 */
public final class RejoinGameTests {

    private static final String BATCH = "pirates_n_ships_rejoin_";
    /** Plate A (with the helm) at x 4..6, the joint at x 7, plate B at x 8..10; z 4..6; planks at y 2 on stone. */
    private static final BlockPos HELM = new BlockPos(5, 3, 5);
    private static final BlockPos JOINT = new BlockPos(7, 2, 5);
    private static final BlockPos B_CENTER = new BlockPos(9, 2, 5);
    private static final BlockPos A_EDGE = new BlockPos(6, 2, 5);

    private RejoinGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RejoinGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** The split dumbbell: the keeper (helm side, original id), the wreck, and plot lookups of the original layout. */
    private record Pieces(GameTestHelper h, ShipBody keeper, ShipBody wreck, Map<BlockPos, BlockPos> originalPlot) {

        /** The keeper's plot position of the block that was at test-relative {@code rel} (the keeper never moved). */
        BlockPos keeperPlot(BlockPos rel) {
            return originalPlot.computeIfAbsent(rel, r -> BlockPos.containing(keeper.toPlot(Vec3.atCenterOf(h.absolutePos(r)))));
        }

        /** The wreck's plot position of a plate B block that was at test-relative {@code rel}. */
        BlockPos wreckPlot(BlockPos rel) {
            ShipSplits.Relocation r = ShipSplits.relocate(h.getLevel(), keeper.id(), originalPlot.get(rel));
            if (r == null || !r.ship().equals(wreck.id())) {
                throw new AssertionError("block " + rel + " is not on the wreck: " + r);
            }
            return r.pos();
        }

        /**
         * Poses the wreck so that its block from {@code rel} lies on the keeper's cell {@code keeperRel} (test-relative
         * original layout), turned by {@code yawDegrees} about the vertical against the keeper, shifted by {@code shift}
         * (world blocks).
         */
        void pose(BlockPos rel, BlockPos keeperRel, double yawDegrees, Vec3 shift) {
            Quaterniond o = keeper.orientation().mul(new Quaterniond().rotateY(Math.toRadians(yawDegrees)));
            Vec3 world = keeper.toWorld(Vec3.atCenterOf(keeperPlot(keeperRel))).add(shift);
            wreck.addVelocity(wreck.linearVelocity().negate(), wreck.angularVelocity().negate());
            wreck.placeAt(Vec3.atCenterOf(wreckPlot(rel)), world, o);
        }

        /** The original layout, wreck in place: back where it broke off. */
        void poseBack() {
            pose(B_CENTER, B_CENTER, 0, Vec3.ZERO);
        }
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void plate(GameTestHelper h, int x0, Block block) {
        for (int x = x0; x < x0 + 3; x++) {
            for (int z = 4; z < 7; z++) {
                h.setBlock(new BlockPos(x, 2, z), block);
            }
        }
    }

    private static void helm(GameTestHelper h, BlockPos at) {
        h.setBlock(at, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    private static ShipBody assemble(GameTestHelper h, BlockPos helm) {
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) {
            throw new AssertionError("no ship after assembly");
        }
        ShipAssembler.name(ship, "Black Gull");
        return ship;
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

    /**
     * Builds the dumbbell ({@code extras} adds blocks before assembly), assembles it, breaks the joint and calls
     * {@code then} once the split is done (two bodies, the wreck with its record).
     */
    private static void splitDumbbell(GameTestHelper h, Consumer<GameTestHelper> extras, Consumer<Pieces> then) {
        floor(h);
        plate(h, 4, Blocks.OAK_PLANKS);
        h.setBlock(JOINT, Blocks.OAK_PLANKS);
        plate(h, 8, Blocks.OAK_PLANKS);
        helm(h, HELM);
        extras.accept(h);
        ShipBody ship = assemble(h, HELM);
        Map<BlockPos, BlockPos> plot = new HashMap<>();
        for (int x = 4; x <= 10; x++) {
            for (int y = 2; y <= 3; y++) {
                for (int z = 4; z <= 6; z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    plot.put(rel, BlockPos.containing(ship.toPlot(Vec3.atCenterOf(h.absolutePos(rel)))));
                }
            }
        }
        BlockPos joint = plot.get(JOINT);
        trackPieces(h);
        h.runAfterDelay(2, () -> h.getLevel().destroyBlock(joint, false));
        boolean[] done = new boolean[1];
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 4) {
                return;
            }
            List<ShipBody> ships = shipsHere(h);
            if (ships.size() != 2) {
                return;
            }
            ShipBody keeper = null;
            ShipBody wreck = null;
            for (ShipBody s : ships) {
                if (s.id().equals(ship.id())) {
                    keeper = s;
                } else {
                    wreck = s;
                }
            }
            if (keeper == null || wreck == null || ShipRegistry.get(h.getLevel().getServer()).find(wreck.id()).isEmpty()) {
                return;
            }
            done[0] = true;
            then.accept(new Pieces(h, keeper, wreck, plot));
        });
    }

    private static Player player(GameTestHelper h, int nails) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack toolkit = new ItemStack(AssemblyContent.SHIPWRIGHT_TOOLKIT.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, toolkit);
        if (nails > 0) {
            p.getInventory().add(new ItemStack(AssemblyContent.NAILS.get(), nails));
        }
        return p;
    }

    private static int nails(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(AssemblyContent.NAILS.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Marks {@code keeperBlock} and uses the toolkit on {@code wreckBlock} (plot positions). */
    private static ShipRejoin.Result markAndUse(GameTestHelper h, Player p, BlockPos keeperBlock, BlockPos wreckBlock) {
        ServerLevel level = h.getLevel();
        ShipRejoin.Result m = ShipRejoin.mark(level, p, p.getMainHandItem(), keeperBlock);
        h.assertTrue(m.outcome() == ShipRejoin.Outcome.MARKED, "marking failed: " + m);
        return ShipRejoin.use(level, p, InteractionHand.MAIN_HAND, wreckBlock);
    }

    private static void expect(GameTestHelper h, ShipRejoin.Result r, ShipRejoin.Outcome expected) {
        h.assertTrue(r.outcome() == expected, "expected " + expected + ", got " + r);
    }

    /** Waits for the hammering to end, then checks the outcome and runs {@code then}. */
    private static void whenDone(GameTestHelper h, Player p, Runnable then) {
        boolean[] done = new boolean[1];
        h.onEachTick(() -> {
            if (done[0] || ShipRejoin.working(h.getLevel(), p)) {
                return;
            }
            done[0] = true;
            ShipRejoin.Result r = ShipRejoin.lastResult(p.getUUID());
            h.assertTrue(r != null && r.outcome() == ShipRejoin.Outcome.REJOINED, "the rejoin did not finish: " + r);
            then.run();
        });
    }

    private static @Nullable BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tests

    /**
     * The wreck is posed back where it broke off and a plank bridges the joint: one body afterwards with all blocks, the
     * chest's contents intact, the crew member who stood on the wreck still aboard, nails and durability used.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "dumbbell")
    public static void rejoinedWreckBecomesOneShipAgain(GameTestHelper h) {
        BlockPos chestRel = new BlockPos(9, 3, 5);
        splitDumbbell(h, g -> {
            g.setBlock(chestRel, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
            if (g.getBlockEntity(chestRel) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.COD, 7));
                chest.setItem(5, new ItemStack(Items.GOLD_INGOT, 3));
            }
        }, pieces -> {
            ServerLevel level = h.getLevel();
            ShipBody keeper = pieces.keeper();
            int keeperBefore = keeper.plotBlocks().size();
            int wreckBlocks = pieces.wreck().plotBlocks().size();
            h.assertTrue(keeperBefore == 10 && wreckBlocks == 10, "unexpected pieces: keeper " + keeperBefore + ", wreck " + wreckBlocks);
            level.setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState()); // the new plank
            pieces.poseBack();
            CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(10, 3, 6));
            Player p = player(h, 8);
            ShipRejoin.Result r = markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER));
            expect(h, r, ShipRejoin.Outcome.STARTED);
            h.assertTrue(!ShipRejoin.seam(level, keeper).isEmpty(), "the marked keeper shows no seam");
            UUID wreckId = pieces.wreck().id();
            whenDone(h, p, () -> {
                List<ShipBody> ships = shipsHere(h);
                h.assertTrue(ships.size() == 1 && ships.get(0).id().equals(keeper.id()), "expected only the keeper, found " + ships.size());
                h.assertTrue(SableShips.byId(level, wreckId) == null, "the wreck body is still there");
                h.assertTrue(ShipRegistry.get(level.getServer()).find(wreckId).isEmpty(), "the wreck's record is still there");
                h.assertTrue(ShipRegistry.get(level.getServer()).find(keeper.id()).map(d -> d.name().equals("Black Gull")).orElse(false),
                        "the keeper lost its record or name");
                int all = keeper.plotBlocks().size();
                h.assertTrue(all == keeperBefore + 1 + wreckBlocks, "the ship holds " + all + " blocks, expected " + (keeperBefore + 1 + wreckBlocks));
                // every block of the original layout is where it was, in the keeper's plot
                for (int x = 4; x <= 10; x++) {
                    for (int z = 4; z <= 6; z++) {
                        if (x == 7 && z != 5) {
                            continue; // the joint is one plank wide
                        }
                        BlockPos cell = pieces.keeperPlot(new BlockPos(x, 2, z));
                        h.assertTrue(level.getBlockState(cell).is(Blocks.OAK_PLANKS), "no plank at " + x + ",2," + z + ": " + level.getBlockState(cell));
                    }
                }
                BlockPos chestAt = pieces.keeperPlot(chestRel);
                h.assertTrue(level.getBlockEntity(chestAt) instanceof ChestBlockEntity, "the chest is not at its place");
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestAt);
                h.assertTrue(chest.getItem(0).is(Items.COD) && chest.getItem(0).getCount() == 7
                        && chest.getItem(5).is(Items.GOLD_INGOT) && chest.getItem(5).getCount() == 3,
                        "the chest lost its contents: " + chest.getItem(0) + ", " + chest.getItem(5));
                h.assertTrue(nails(p) == 4, "expected 4 nails left, found " + nails(p));
                h.assertTrue(p.getMainHandItem().getDamageValue() == 1, "toolkit damage " + p.getMainHandItem().getDamageValue());
                // the crew member stands on the keeper's deck where it stood on the wreck
                h.runAfterDelay(10, () -> {
                    Vec3 local = keeper.toPlot(crew.position());
                    BlockPos below = BlockPos.containing(local.subtract(0, 0.2, 0));
                    h.assertTrue(!level.getBlockState(below).isAir(), "the crew member is not on the deck: " + local);
                    double off = local.distanceTo(Vec3.atBottomCenterOf(pieces.keeperPlot(new BlockPos(10, 3, 6))));
                    h.assertTrue(off < 1.0, "the crew member moved " + off + " blocks off its place");
                    crew.discard();
                    h.succeed();
                });
            });
        });
    }

    /**
     * Turned a quarter: plate B posed at its old footprint but rotated 90° about the vertical (counter-clockwise seen
     * from above). Its corner marker lands one corner further, the chest's facing turns from north to west, and a
     * station position on the wreck is relocated into the keeper the same way.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "quarter")
    public static void quarterTurnedPieceSnapsRotated(GameTestHelper h) {
        BlockPos markerRel = new BlockPos(10, 2, 4);
        BlockPos chestRel = new BlockPos(9, 3, 5);
        splitDumbbell(h, g -> {
            g.setBlock(markerRel, Blocks.SPRUCE_PLANKS);
            g.setBlock(chestRel, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        }, pieces -> {
            ServerLevel level = h.getLevel();
            level.setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState());
            BlockPos markerOnWreck = pieces.wreckPlot(markerRel);
            pieces.pose(B_CENTER, B_CENTER, 90, Vec3.ZERO);
            Player p = player(h, 4);
            expect(h, markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.STARTED);
            UUID wreckId = pieces.wreck().id();
            whenDone(h, p, () -> {
                // (10,2,4) is (+1, −1) from the center; a counter-clockwise quarter turn makes it (−1, −1): (8,2,4)
                BlockPos expectedMarker = pieces.keeperPlot(new BlockPos(8, 2, 4));
                h.assertTrue(level.getBlockState(expectedMarker).is(Blocks.SPRUCE_PLANKS),
                        "the marker is not at the turned corner: " + level.getBlockState(expectedMarker));
                h.assertTrue(level.getBlockState(pieces.keeperPlot(markerRel)).is(Blocks.OAK_PLANKS), "the old corner is not a plank");
                var chest = level.getBlockState(pieces.keeperPlot(chestRel));
                h.assertTrue(chest.is(Blocks.CHEST) && chest.getValue(ChestBlock.FACING) == Direction.WEST,
                        "the chest did not turn to the west: " + chest);
                ShipSplits.Relocation moved = ShipSplits.relocate(level, wreckId, markerOnWreck);
                h.assertTrue(moved != null && moved.ship().equals(pieces.keeper().id()) && moved.keeper()
                        && moved.pos().equals(expectedMarker), "a wreck position is not relocated into the keeper: " + moved);
                h.assertTrue(nails(p) == 0, "expected all 4 nails used, " + nails(p) + " left");
                h.succeed();
            });
        });
    }

    /** Back in place without a plank: "Add planks"; 1.2 blocks further away: "Bring the pieces together". */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "gap")
    public static void pieceAwayIsRefused(GameTestHelper h) {
        splitDumbbell(h, g -> { }, pieces -> {
            Player p = player(h, 8);
            pieces.poseBack();
            expect(h, markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.ADD_PLANKS);
            pieces.pose(B_CENTER, B_CENTER, 0, new Vec3(1.2, 0, 0));
            expect(h, ShipRejoin.use(h.getLevel(), p, InteractionHand.MAIN_HAND, pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.TOO_FAR);
            h.assertTrue(!ShipRejoin.working(h.getLevel(), p) && nails(p) == 8, "a refused rejoin started or used nails");
            h.assertTrue(shipsHere(h).size() == 2, "a refused rejoin changed the pieces");
            h.succeed();
        });
    }

    /** A piece turned 30° against the keeper is not lined up. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "turned")
    public static void turnedPieceIsRefused(GameTestHelper h) {
        splitDumbbell(h, g -> { }, pieces -> {
            h.getLevel().setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState());
            pieces.pose(B_CENTER, B_CENTER, 30, Vec3.ZERO);
            Player p = player(h, 8);
            expect(h, markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.NOT_ALIGNED);
            h.succeed();
        });
    }

    /** A piece bigger than {@code max_piece_blocks} is refused, before alignment. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = "pirates_n_ships_config_assembly_rejoin_size")
    public static void bigPieceIsRefused(GameTestHelper h) {
        ConfigOverrides.during(h, AssemblyConfig.REJOIN_MAX_PIECE_BLOCKS, 5);
        splitDumbbell(h, g -> { }, pieces -> {
            pieces.pose(B_CENTER, B_CENTER, 30, Vec3.ZERO); // also misaligned: size is checked first
            Player p = player(h, 8);
            ShipRejoin.Result r = markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER));
            expect(h, r, ShipRejoin.Outcome.TOO_BIG);
            h.succeed();
        });
    }

    /** Two ships that never were one refuse, even side by side. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = BATCH + "unrelated")
    public static void unrelatedShipsAreRefused(GameTestHelper h) {
        floor(h);
        plate(h, 4, Blocks.OAK_PLANKS);
        helm(h, HELM);
        plate(h, 9, Blocks.OAK_PLANKS);
        helm(h, new BlockPos(10, 3, 5));
        ShipBody a = assemble(h, HELM);
        ShipBody b = assemble(h, new BlockPos(10, 3, 5));
        h.assertTrue(!a.id().equals(b.id()), "one ship");
        Player p = player(h, 8);
        BlockPos onA = BlockPos.containing(a.toPlot(Vec3.atCenterOf(h.absolutePos(A_EDGE))));
        BlockPos onB = BlockPos.containing(b.toPlot(Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 2, 5)))));
        expect(h, markAndUse(h, p, onA, onB), ShipRejoin.Outcome.DIFFERENT_SHIP);
        expect(h, ShipRejoin.use(h.getLevel(), p, InteractionHand.MAIN_HAND, onA), ShipRejoin.Outcome.SAME_PIECE);
        h.succeed();
    }

    /** {@code assembly.rejoin.enabled = false}: the toolkit neither marks nor joins. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = "pirates_n_ships_config_assembly_rejoin")
    public static void disabledRejoinIsRefused(GameTestHelper h) {
        splitDumbbell(h, g -> { }, pieces -> {
            h.getLevel().setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState());
            pieces.poseBack();
            Player p = player(h, 8);
            ShipRejoin.Result m = ShipRejoin.mark(h.getLevel(), p, p.getMainHandItem(), pieces.keeperPlot(A_EDGE));
            h.assertTrue(m.outcome() == ShipRejoin.Outcome.MARKED, "marking failed: " + m);
            ConfigOverrides.during(h, AssemblyConfig.REJOIN_ENABLED, false);
            expect(h, ShipRejoin.use(h.getLevel(), p, InteractionHand.MAIN_HAND, pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.DISABLED);
            expect(h, ShipRejoin.mark(h.getLevel(), p, p.getMainHandItem(), pieces.keeperPlot(A_EDGE)), ShipRejoin.Outcome.DISABLED);
            h.assertTrue(shipsHere(h).size() == 2 && nails(p) == 8, "a disabled rejoin changed something");
            h.succeed();
        });
    }

    /** Lined up and touching, but without enough nails: refused, nothing changes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "no_nails")
    public static void withoutNailsIsRefused(GameTestHelper h) {
        splitDumbbell(h, g -> { }, pieces -> {
            h.getLevel().setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState());
            pieces.poseBack();
            Player p = player(h, 3);
            expect(h, markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.NO_NAILS);
            h.assertTrue(!ShipRejoin.working(h.getLevel(), p) && nails(p) == 3, "a refused rejoin started or used nails");
            h.assertTrue(shipsHere(h).size() == 2, "a refused rejoin changed the pieces");
            h.succeed();
        });
    }

    /**
     * A crew member put at a station (a sail winch) on the wreck after the split keeps manning it once the wreck is
     * rejoined: its assignment moves to the winch's new place in the keeper's plot and it sits there again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240, batch = "pirates_n_ships_config_assembly_rejoin_crew")
    public static void crewAtAStationOnThePieceFollowsIt(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.SEAT_CHECK_INTERVAL, 5);
        BlockPos winchRel = new BlockPos(9, 3, 5);
        splitDumbbell(h, g -> g.setBlock(winchRel, SailingBlocks.SAIL_WINCH.get()), pieces -> {
            ServerLevel level = h.getLevel();
            BlockPos winchOnWreck = pieces.wreckPlot(winchRel);
            h.assertTrue(level.getBlockState(winchOnWreck).is(SailingBlocks.SAIL_WINCH.get()), "the winch is not on the wreck");
            CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(9, 3, 6));
            h.assertTrue(CrewStations.assign(level, crew, winchOnWreck) == CrewStations.AssignResult.ASSIGNED,
                    "could not assign the crew to the wreck's winch");
            level.setBlockAndUpdate(pieces.keeperPlot(JOINT), Blocks.OAK_PLANKS.defaultBlockState());
            h.runAfterDelay(10, () -> {
                h.assertTrue(crew.isAtStation(), "the crew member did not take the wreck's winch");
                pieces.poseBack();
                Player p = player(h, 4);
                expect(h, markAndUse(h, p, pieces.keeperPlot(A_EDGE), pieces.wreckPlot(B_CENTER)), ShipRejoin.Outcome.STARTED);
                whenDone(h, p, () -> {
                    BlockPos winchOnKeeper = pieces.keeperPlot(winchRel);
                    h.assertTrue(level.getBlockState(winchOnKeeper).is(SailingBlocks.SAIL_WINCH.get()), "the winch is not back in place");
                    StationRef expected = new StationRef(pieces.keeper().id(), winchOnKeeper);
                    h.succeedWhen(() -> {
                        h.assertTrue(expected.equals(crew.assignment()), "the crew's station did not follow: " + crew.assignment());
                        h.assertTrue(crew.isAtStation(), "the crew member is not seated at the winch again");
                        StationState<Object> st = Stations.state(expected);
                        h.assertTrue(st != null && st.isOccupiedBy(crew.getUUID()), "the winch is not occupied by the crew member");
                        crew.discard();
                    });
                });
            });
        });
    }
}

package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ship's name on name boards (design.md §4.8, G8 and NAME2 "Ship name boards"): naming and renaming an assembled
 * ship reach the row's master, a board placed on an already named ship, disassembly (the board is cleared), the
 * {@code ship_identity.nameplate_shows_name} toggle; joining (a row of three with one master, extending at both ends,
 * splitting, no vertical join, a player placing boards on a hull side), a row through assembly and a turned
 * disassembly, and a G8 nameplate from an old save loading as a board. Test hulls stand on stone (terrain, never
 * gathered) like {@code AssemblyGameTests}.
 */
public final class NameplateGameTests {

    private static final BlockPos HELM = new BlockPos(4, 3, 4);
    /** The middle board of the row on the north edge of the deck, hanging from the planks south of it, facing north. */
    private static final BlockPos PLATE = new BlockPos(4, 2, 2);
    /** The row along the deck's north edge (x 3..5); facing north the viewer's left is east, so x 5 is the master. */
    private static final List<BlockPos> ROW = List.of(new BlockPos(5, 2, 2), new BlockPos(4, 2, 2), new BlockPos(3, 2, 2));
    /** {@link #PLATE} relative to {@link #HELM}. */
    private static final BlockPos PLATE_FROM_HELM = PLATE.subtract(HELM);

    private NameplateGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NameplateGameTests.class);
    }

    // ------------------------------------------------------------------ the name (G8)

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void namingAndRenamingAShipUpdatesItsNameplate(GameTestHelper helper) {
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        BlockPos plate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plate != null, "nameplate not assembled with the ship");
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(text(helper, plate).isEmpty(), "an unnamed ship's plate shows '" + text(helper, plate) + "'");
                    helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
                })
                .thenWaitUntil(() -> assertText(helper, plate, "Black Gull"))
                .thenExecute(() -> helper.assertTrue(ShipAssembler.name(ship, "Sea Wolf").success(), "renaming failed"))
                .thenWaitUntil(() -> assertText(helper, plate, "Sea Wolf"))
                .thenSucceed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void plateHungOnANamedShipShowsItsName(GameTestHelper helper) {
        buildHull(helper, false);
        ShipBody ship = assemble(helper);
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        BlockPos helm = find(ship, AssemblyContent.HELM.get());
        helper.assertTrue(helm != null, "helm not in the plot");
        BlockPos plate = helm.offset(PLATE_FROM_HELM);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getBlockState(plate.south()).is(Blocks.OAK_PLANKS), "no deck plank to hang the plate on");
        level.setBlock(plate, boardState(Direction.NORTH), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(plate).is(ShipDecor.NAMEPLATE.get()), "plate not placed on the ship");
        helper.succeedWhen(() -> assertText(helper, plate, "Black Gull"));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300)
    public static void disassemblyClearsTheNameplate(GameTestHelper helper) {
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        UUID id = ship.id();
        BlockPos plotPlate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plotPlate != null, "nameplate not assembled with the ship");
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        AtomicReference<BlockPos> worldPlate = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> assertText(helper, plotPlate, "Black Gull"))
                .thenWaitUntil(() -> {
                    ShipBody s = SableShips.byId(helper.getLevel(), id);
                    helper.assertTrue(s != null, "ship vanished before disassembly");
                    AssemblyResult r = ShipAssembler.disassemble(s, find(s, AssemblyContent.HELM.get()), null);
                    if (!r.success()) throw new GameTestAssertException("not yet disassembled: " + r.outcome());
                })
                .thenExecute(() -> {
                    // The ship may have settled a little: look for the plate within a block of its old spot
                    for (BlockPos d : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
                        if (helper.getBlockState(PLATE.offset(d)).is(ShipDecor.NAMEPLATE.get())) worldPlate.set(helper.absolutePos(PLATE.offset(d)));
                    }
                    helper.assertTrue(worldPlate.get() != null, "nameplate not back near " + PLATE);
                })
                .thenWaitUntil(() -> assertText(helper, worldPlate.get(), ""))
                .thenSucceed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = "pirates_n_ships_config_decor_nameplate")
    public static void plateStaysEmptyWhenTheToggleIsOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, ShipIdentityConfig.NAMEPLATE_SHOWS_NAME, false);
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        BlockPos plate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plate != null, "nameplate not assembled with the ship");
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        // Longer than a refresh interval (20 ticks by default)
        helper.runAfterDelay(50, () -> {
            assertText(helper, plate, "");
            if (helper.getLevel().getBlockEntity(plate) instanceof NameplateBlockEntity be) be.refresh();
            assertText(helper, plate, "");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ joining (NAME2)

    /** Three boards side by side on a wall join into one board: the leftmost (east, facing north) is the master. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void aRowOfThreeJoinsWithOneMaster(GameTestHelper helper) {
        wall(helper, 1, 7, 2, 3, 3);
        for (int x = 3; x <= 5; x++) helper.setBlock(new BlockPos(x, 2, 2), boardState(Direction.NORTH));
        // a fourth board above the middle one: a vertical stack does not join
        helper.setBlock(new BlockPos(4, 3, 2), boardState(Direction.NORTH));
        helper.succeedWhen(() -> {
            assertRow(helper, List.of(new BlockPos(5, 2, 2), new BlockPos(4, 2, 2), new BlockPos(3, 2, 2)));
            assertRow(helper, List.of(new BlockPos(4, 3, 2)));
        });
    }

    /**
     * A board added at the right end extends the row, one added at the left end becomes its master, breaking a board
     * in the middle splits the row in two; a player placing boards on a hull side gets them facing out and joined.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void rowsExtendAtBothEndsAndSplit(GameTestHelper helper) {
        wall(helper, 0, 8, 2, 3, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int x = 2; x <= 4; x++) place(helper, player, new BlockPos(x, 2, 3), Direction.NORTH);
        helper.startSequence()
                .thenWaitUntil(() -> assertRow(helper, List.of(new BlockPos(4, 2, 2), new BlockPos(3, 2, 2), new BlockPos(2, 2, 2))))
                // the right end (west): the master stays
                .thenExecute(() -> place(helper, player, new BlockPos(1, 2, 3), Direction.NORTH))
                .thenWaitUntil(() -> assertRow(helper, List.of(new BlockPos(4, 2, 2), new BlockPos(3, 2, 2), new BlockPos(2, 2, 2),
                        new BlockPos(1, 2, 2))))
                // the left end (east): the new master
                .thenExecute(() -> place(helper, player, new BlockPos(5, 2, 3), Direction.NORTH))
                .thenWaitUntil(() -> assertRow(helper, List.of(new BlockPos(5, 2, 2), new BlockPos(4, 2, 2), new BlockPos(3, 2, 2),
                        new BlockPos(2, 2, 2), new BlockPos(1, 2, 2))))
                // break the middle board
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, 2, 2)), true))
                .thenWaitUntil(() -> {
                    assertRow(helper, List.of(new BlockPos(5, 2, 2), new BlockPos(4, 2, 2)));
                    assertRow(helper, List.of(new BlockPos(2, 2, 2), new BlockPos(1, 2, 2)));
                })
                .thenSucceed();
    }

    /** The name reaches the master of a row of three on a ship, and a rename at the helm too. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void theNameReachesTheRowsMasterAfterARename(GameTestHelper helper) {
        buildHull(helper, false);
        for (BlockPos p : ROW) helper.setBlock(p, boardState(Direction.NORTH));
        ShipBody ship = assemble(helper);
        BlockPos helm = find(ship, AssemblyContent.HELM.get());
        helper.assertTrue(helm != null, "helm not in the plot");
        List<BlockPos> plotRow = new ArrayList<>();
        for (BlockPos p : ROW) plotRow.add(helm.offset(p.subtract(HELM)));
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertRowAbsolute(helper, plotRow);
                    assertText(helper, plotRow.get(0), "Black Gull");
                })
                .thenExecute(() -> helper.assertTrue(ShipAssembler.name(ship, "Sea Wolf").success(), "renaming failed"))
                .thenWaitUntil(() -> assertText(helper, plotRow.get(0), "Sea Wolf"))
                .thenSucceed();
    }

    /** A row survives assembly and a disassembly turned a quarter round: still one row, the same board its master. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void aRowSurvivesAssemblyAndATurnedDisassembly(GameTestHelper helper) {
        buildHull(helper, false);
        for (BlockPos p : ROW) helper.setBlock(p, boardState(Direction.NORTH));
        ShipBody ship = assemble(helper);
        BlockPos helm = find(ship, AssemblyContent.HELM.get());
        helper.assertTrue(helm != null, "helm not in the plot");
        List<BlockPos> plotRow = new ArrayList<>();
        for (BlockPos p : ROW) plotRow.add(helm.offset(p.subtract(HELM)));
        AtomicReference<List<BlockPos>> worldRow = new AtomicReference<>();
        // turned a quarter counter-clockwise: north-facing boards face west, the old master is still the leftmost
        Direction facing = Rotation.COUNTERCLOCKWISE_90.rotate(Direction.NORTH);
        helper.startSequence()
                .thenWaitUntil(() -> assertRowAbsolute(helper, plotRow))
                .thenExecute(() -> {
                    ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
                    BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(helm)));
                    int turns = DisassemblyMath.quarterTurns(ship.orientation());
                    helper.assertTrue(turns == 1, "expected one quarter turn, got " + turns);
                    List<BlockPos> targets = new ArrayList<>();
                    for (BlockPos p : plotRow) targets.add(DisassemblyMath.target(p, helm, goal, turns));
                    worldRow.set(targets);
                    AssemblyResult d = ShipAssembler.disassemble(ship, helm, null);
                    helper.assertTrue(d.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + d);
                })
                .thenWaitUntil(() -> {
                    for (BlockPos p : worldRow.get()) {
                        BlockState s = helper.getLevel().getBlockState(p);
                        if (!s.is(ShipDecor.NAMEPLATE.get()) || s.getValue(NameBoardBlock.FACING) != facing) {
                            throw new GameTestAssertException("no board facing " + facing + " at " + p + ": " + s);
                        }
                    }
                    assertRowAbsolute(helper, worldRow.get());
                })
                .thenSucceed();
    }

    /**
     * A G8 nameplate from an old save loads as a board: the block id and its facing are the same, the new joins start
     * unjoined and the block entity's data has no row. Written straight into the chunk (no placement, no neighbour
     * update), as a chunk loads from disk; the boards' first tick links the row.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void oldNameplatesLoadAsBoards(GameTestHelper helper) {
        wall(helper, 1, 7, 2, 3, 3);
        ServerLevel level = helper.getLevel();
        BlockState old = ShipDecor.NAMEPLATE.get().defaultBlockState().setValue(NameBoardBlock.FACING, Direction.NORTH);
        for (BlockPos rel : ROW) {
            BlockPos p = helper.absolutePos(rel);
            LevelChunk chunk = level.getChunkAt(p);
            chunk.getSection(chunk.getSectionIndex(p.getY())).setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, old);
            CompoundTag g8 = new CompoundTag();
            g8.putString("text", "Black Gull");
            NameplateBlockEntity be = new NameplateBlockEntity(p, old);
            be.loadWithComponents(g8, level.registryAccess());
            level.setBlockEntity(be);
        }
        helper.assertTrue(level.getBlockEntity(helper.absolutePos(ROW.get(1))) instanceof NameplateBlockEntity be
                && be.rowLength() == 1 && be.rowIndex() == 0, "a G8 plate should load as a board on its own");
        helper.succeedWhen(() -> {
            assertRow(helper, ROW);
            // off a ship the boards show nothing, as the plate did
            for (BlockPos rel : ROW) assertText(helper, helper.absolutePos(rel), "");
        });
    }

    // ------------------------------------------------------------------ helpers

    /** A 3×3 plank deck on stone with the helm in the middle and, if {@code withPlate}, a board on its north edge. */
    private static void buildHull(GameTestHelper helper, boolean withPlate) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
        }
        helper.setBlock(HELM, AssemblyContent.HELM.get());
        if (withPlate) helper.setBlock(PLATE, boardState(Direction.NORTH));
    }

    /** A plank wall at z {@code z}, x {@code x0..x1}, y {@code y0..y1}. */
    private static void wall(GameTestHelper helper, int x0, int x1, int y0, int y1, int z) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
        }
    }

    private static BlockState boardState(Direction facing) {
        return ShipDecor.NAMEPLATE.get().defaultBlockState().setValue(NameBoardBlock.FACING, facing);
    }

    /** A player clicks the {@code face} side of the block at {@code clicked} with a name board. */
    private static void place(GameTestHelper helper, Player player, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(ShipDecor.NAMEPLATE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setYRot(face.getOpposite().toYRot());
        BlockPos abs = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, abs, false);
        InteractionResult r = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(r.consumesAction(), "placing the board on " + face + " of " + clicked + " failed: " + r);
        helper.assertBlockProperty(clicked.relative(face), NameBoardBlock.FACING, face);
    }

    /** {@code row} (test-relative, from the viewer's left) is exactly one row: indices, the master's length, the joins. */
    private static void assertRow(GameTestHelper helper, List<BlockPos> row) {
        List<BlockPos> abs = new ArrayList<>();
        for (BlockPos p : row) abs.add(helper.absolutePos(p));
        assertRowAbsolute(helper, abs);
    }

    private static void assertRowAbsolute(GameTestHelper helper, List<BlockPos> row) {
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < row.size(); i++) {
            BlockPos p = row.get(i);
            BlockState s = level.getBlockState(p);
            if (!s.is(ShipDecor.NAMEPLATE.get()) || !(level.getBlockEntity(p) instanceof NameplateBlockEntity be)) {
                throw new GameTestAssertException("no name board at " + p + ": " + s);
            }
            if (be.rowIndex() != i) throw new GameTestAssertException("board " + p + " has index " + be.rowIndex() + ", expected " + i);
            if (i == 0 && be.rowLength() != row.size()) {
                throw new GameTestAssertException("master " + p + " knows a row of " + be.rowLength() + ", expected " + row.size());
            }
            if (!be.masterPos().equals(row.get(0))) throw new GameTestAssertException("board " + p + " names master " + be.masterPos());
            boolean left = i > 0, right = i < row.size() - 1;
            if (s.getValue(NameBoardBlock.LEFT) != left || s.getValue(NameBoardBlock.RIGHT) != right) {
                throw new GameTestAssertException("board " + p + " joins left " + s.getValue(NameBoardBlock.LEFT) + " right "
                        + s.getValue(NameBoardBlock.RIGHT) + ", expected " + left + " " + right);
            }
        }
    }

    private static ShipBody assemble(GameTestHelper helper) {
        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.success() && r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(helper.getLevel(), r.shipId());
        helper.assertTrue(ship != null, "no sub-level for " + r.shipId());
        return ship;
    }

    private static String text(GameTestHelper helper, BlockPos absolute) {
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof NameplateBlockEntity be)) {
            throw new GameTestAssertException("no nameplate block entity at " + absolute);
        }
        return be.text();
    }

    private static void assertText(GameTestHelper helper, BlockPos absolute, String expected) {
        String actual = text(helper, absolute);
        if (!actual.equals(expected)) throw new GameTestAssertException("plate shows '" + actual + "', expected '" + expected + "'");
    }

    private static BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) return p;
        }
        return null;
    }
}

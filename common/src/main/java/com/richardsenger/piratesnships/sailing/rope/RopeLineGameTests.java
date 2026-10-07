package com.richardsenger.piratesnships.sailing.rope;

import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.MooringRingBlock;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * RP1 in a real server: decorative rope lines between cleats and mooring rings (docs/design.md §5.2 "Ropes on
 * cleats"). Ship tests use the closed 5×4×5 hull of {@link DryHullGameTests} at x 9..13, z 9..13 (deck top y 8, helm
 * at (11, 9, 11)); land tests build on a stone floor.
 */
public final class RopeLineGameTests {

    private static final String LINES_OFF_BATCH = "pirates_n_ships_config_sailing_rope_lines";

    private RopeLineGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RopeLineGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static final int HULL_X = 9;

    private static BlockState cleat(AttachFace face, Direction facing) {
        return SailingGameTestsShips.cleat(face, facing);
    }

    private static BlockState ring(AttachFace face, Direction facing) {
        return GrappleContent.MOORING_RING.get().defaultBlockState().setValue(MooringRingBlock.FACE, face).setValue(MooringRingBlock.FACING, facing);
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    /** The plot position of a block placed at {@code rel} before the hull with its helm at {@code helmRel} was assembled. */
    private static BlockPos plot(Fixture f, BlockPos helmRel, BlockPos rel) {
        return f.helmPlot().offset(rel.subtract(helmRel));
    }

    private static int ropesDropped(GameTestHelper h) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(s -> s.is(TriangularSailContent.ROPE.get())).mapToInt(ItemStack::getCount).sum();
    }

    private static int ropeCount(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RopeAnchorBlockEntity be ? be.ropeCount() : -1;
    }

    private static TriangleCloth cloth(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CleatBlockEntity be ? be.cloth() : null;
    }

    /** A player breaking the block at {@code pos} by hand, as {@code ServerPlayerGameMode#destroyBlock} does. */
    private static void breakByHand(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
    }

    // ------------------------------------------------------------------ lines on a ship

    /**
     * On an assembled ship: a rope between two deck cleats at the same height is a line (no sail, no cloth, the ship has
     * no sail), and a rope from one of them to a mooring ring on the same deck is a line too, stored in the ring.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void sameHeightCleatsAndARingOnAShipMakeLinesAndNoSail(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmRel = DryHullGameTests.hull(h, HULL_X, false);
        BlockPos aRel = new BlockPos(10, 9, 10), bRel = new BlockPos(12, 9, 10), ringRel = new BlockPos(12, 9, 12);
        h.setBlock(aRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(bRel, cleat(AttachFace.FLOOR, Direction.EAST));
        h.setBlock(ringRel, ring(AttachFace.FLOOR, Direction.NORTH));
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos a = plot(f, helmRel, aRel), b = plot(f, helmRel, bRel), ring = plot(f, helmRel, ringRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 4);

        h.assertTrue(key(RopeItem.use(level, rope, a, null)).equals(RopeItem.KEY_TIED), "no tie at the first cleat");
        Component line = RopeItem.use(level, rope, b, null);
        h.assertTrue(key(line).equals(RopeItem.KEY_LINE), "two cleats at the same height made no line: " + line.getString());
        h.assertTrue(rope.getCount() == 3, "the line did not use up a rope");
        h.assertTrue(RopeLines.joined(level, a, b) && RopeLines.partners(level, a).equals(List.of(b)), "the cleats do not agree on the line");
        h.assertTrue(cloth(level, a) == null && cloth(level, b) == null, "a line has cloth");
        h.assertTrue(TriangularSails.partner(level, a) == null, "a flat line counts as a stay");
        h.assertTrue(SailingRuntimes.getOrCreate(f.ship()).sailCount() == 0, "the ship has a sail from a line");

        h.assertTrue(key(RopeItem.use(level, rope, b, null)).equals(RopeItem.KEY_TIED), "no tie at the second cleat");
        Component toRing = RopeItem.use(level, rope, ring, null);
        h.assertTrue(key(toRing).equals(RopeItem.KEY_LINE), "cleat to ring made no line: " + toRing.getString());
        h.assertTrue(RopeLines.joined(level, b, ring), "the cleat and the ring do not agree on the line");
        h.assertTrue(ropeCount(level, ring) == 1 && ropeCount(level, b) == 2, "rope counts: ring " + ropeCount(level, ring) + ", cleat " + ropeCount(level, b));
        h.assertTrue(key(RopeItem.use(level, rope, a, null)).equals(RopeItem.KEY_TIED)
                && key(RopeItem.use(level, rope, b, null)).equals(RopeItem.KEY_ALREADY), "a second rope between the same two was not refused");
        h.assertTrue(rope.getCount() == 2, "a refused rope was used up");
        h.succeed();
    }

    /** A cleat on land and a cleat on a ship cannot be roped together; the rope keeps its first anchor. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void cleatOnLandAndCleatOnAShipAreRefused(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmRel = DryHullGameTests.hull(h, HULL_X, false);
        BlockPos shipRel = new BlockPos(10, 9, 10);
        BlockPos landRel = new BlockPos(0, 9, 10); // on the basin's west wall
        h.setBlock(shipRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(landRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos onShip = plot(f, helmRel, shipRel), onLand = h.absolutePos(landRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 2);

        h.assertTrue(key(RopeItem.use(level, rope, onLand, null)).equals(RopeItem.KEY_TIED), "no tie on land");
        Component refused = RopeItem.use(level, rope, onShip, null);
        h.assertTrue(key(refused).equals(RopeItem.KEY_OTHER_BODY), "land to ship was not refused: " + refused.getString());
        h.assertTrue(rope.getCount() == 2 && ropeCount(level, onLand) == 0 && ropeCount(level, onShip) == 0, "a refused rope was rigged");
        h.assertTrue(rope.has(TriangularSailContent.ROPE_START.get()) && onLand.equals(rope.get(TriangularSailContent.ROPE_START.get()).pos()),
                "the rope forgot its first anchor");
        h.succeed();
    }

    // ------------------------------------------------------------------ line and stay

    /**
     * On land: a rope from a head cleat on a mast down to a tack cleat is a line without a sail; a third cleat placed
     * straight below the head makes it a stay with a sail (the head gets the cloth), and breaking that cleat turns it
     * back into a line (the rope stays, the cloth goes).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60)
    public static void aThirdCleatUpgradesTheLineToAStayAndBack(GameTestHelper h) {
        floor(h, 24);
        for (int y = 2; y <= 7; y++) h.setBlock(new BlockPos(5, y, 5), Blocks.OAK_LOG);
        BlockPos headRel = new BlockPos(5, 7, 6), tackRel = new BlockPos(5, 2, 11), clewRel = new BlockPos(5, 2, 6);
        h.setBlock(headRel, cleat(AttachFace.WALL, Direction.SOUTH));
        h.setBlock(tackRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        ServerLevel level = h.getLevel();
        BlockPos head = h.absolutePos(headRel), tack = h.absolutePos(tackRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 2);
        RopeItem.use(level, rope, head, null);
        Component rigged = RopeItem.use(level, rope, tack, null);
        h.assertTrue(key(rigged).equals(RopeItem.KEY_RIGGED), "a rope that passes the stay rule: " + rigged.getString());
        h.assertTrue(RopeLines.joined(level, head, tack), "no rope");
        h.assertTrue(cloth(level, head) == null && TriangularSails.sailHeadedAt(level, head, SailingConfig.stayRules()) == null,
                "a sail without a clew");

        h.setBlock(clewRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        TriangleCloth expected = new TriangleCloth(0, -5, 5, 5);
        h.assertTrue(expected.equals(cloth(level, head)), "the clew did not make the sail: " + cloth(level, head));
        h.assertTrue(tack.equals(TriangularSails.partner(level, head)), "the rope is not the head's stay");

        level.destroyBlock(h.absolutePos(clewRel), false);
        h.assertTrue(cloth(level, head) == null, "the sail survived its clew");
        h.assertTrue(RopeLines.joined(level, head, tack), "removing the clew took the rope down");
        h.succeed();
    }

    // ------------------------------------------------------------------ breaking

    /**
     * Breaking an anchor by hand returns one rope per rope on it and the other ends forget it: a cleat holding a line to
     * a cleat and one to a ring drops two ropes; a ring holding a line drops one; in creative mode nothing drops.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void breakingAnAnchorReturnsItsRopes(GameTestHelper h) {
        floor(h, 9);
        BlockPos aRel = new BlockPos(1, 2, 4), bRel = new BlockPos(4, 2, 4), ringRel = new BlockPos(7, 2, 4), cRel = new BlockPos(4, 2, 7);
        h.setBlock(aRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(bRel, cleat(AttachFace.FLOOR, Direction.WEST));
        h.setBlock(ringRel, ring(AttachFace.FLOOR, Direction.SOUTH));
        h.setBlock(cRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(aRel), b = h.absolutePos(bRel), ring = h.absolutePos(ringRel), c = h.absolutePos(cRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 8);
        for (BlockPos[] pair : new BlockPos[][] {{a, b}, {b, ring}, {ring, c}}) {
            RopeItem.use(level, rope, pair[0], null);
            Component r = RopeItem.use(level, rope, pair[1], null);
            h.assertTrue(key(r).equals(RopeItem.KEY_LINE), "no line " + pair[0] + " - " + pair[1] + ": " + r.getString());
        }
        h.assertTrue(rope.getCount() == 5, "three lines did not use three ropes");

        Player survival = h.makeMockPlayer(GameType.SURVIVAL);
        breakByHand(level, b, survival);
        h.assertTrue(ropesDropped(h) == 2, "breaking a cleat with two lines dropped " + ropesDropped(h) + " ropes");
        h.assertTrue(ropeCount(level, a) == 0 && ropeCount(level, ring) == 1 && RopeLines.joined(level, ring, c),
                "the other ends did not forget the broken cleat");

        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        breakByHand(level, c, creative);
        h.assertTrue(ropesDropped(h) == 2, "a creative player got a rope back");
        h.assertTrue(ropeCount(level, ring) == 0, "the ring kept the line to the broken cleat");

        RopeItem.use(level, rope, a, null);
        h.assertTrue(key(RopeItem.use(level, rope, ring, null)).equals(RopeItem.KEY_LINE), "no line from the cleat to the ring");
        breakByHand(level, ring, survival);
        h.assertTrue(ropesDropped(h) == 3, "breaking the ring did not return its rope");
        h.assertTrue(ropeCount(level, a) == 0, "the cleat kept the line to the broken ring");
        h.succeed();
    }

    // ------------------------------------------------------------------ assembly

    /**
     * Lines between a cleat, a second cleat and a ring on a hull survive assembly, a disassembly turned a quarter round
     * (the offsets are stored in each anchor's own frame) and the next assembly.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void linesSurviveATurnedDisassemblyAndReassembly(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, false);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmRel = DryHullGameTests.hull(h, HULL_X, false);
        BlockPos aRel = new BlockPos(10, 9, 10), bRel = new BlockPos(12, 9, 10), ringRel = new BlockPos(12, 9, 12);
        h.setBlock(aRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(bRel, cleat(AttachFace.FLOOR, Direction.EAST));
        h.setBlock(ringRel, ring(AttachFace.FLOOR, Direction.WEST));
        ServerLevel level = h.getLevel();
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 4);
        RopeItem.use(level, rope, h.absolutePos(aRel), null);
        RopeItem.use(level, rope, h.absolutePos(bRel), null);
        RopeItem.use(level, rope, h.absolutePos(bRel), null);
        RopeItem.use(level, rope, h.absolutePos(ringRel), null);
        h.assertTrue(rope.getCount() == 2, "the lines were not rigged on land");

        Fixture f = DryHullGameTests.assemble(h, helmRel);
        BlockPos a = plot(f, helmRel, aRel), b = plot(f, helmRel, bRel), ring = plot(f, helmRel, ringRel);
        h.assertTrue(RopeLines.joined(level, a, b) && RopeLines.joined(level, b, ring), "the lines did not survive assembly");

        ShipBody ship = f.ship();
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(f.helmPlot())));
        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        h.assertTrue(turns == 1, "expected one quarter turn, got " + turns);
        BlockPos aW = DisassemblyMath.target(a, f.helmPlot(), goal, turns);
        BlockPos bW = DisassemblyMath.target(b, f.helmPlot(), goal, turns);
        BlockPos ringW = DisassemblyMath.target(ring, f.helmPlot(), goal, turns);
        AssemblyResult r = ShipAssembler.disassemble(ship, f.helmPlot(), null);
        h.assertTrue(r.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + r);

        h.runAfterDelay(2, () -> {
            h.assertTrue(RopeLines.joined(level, aW, bW), "the cleat line did not survive the turn: " + RopeLines.partners(level, aW));
            h.assertTrue(RopeLines.joined(level, bW, ringW), "the ring line did not survive the turn: " + RopeLines.partners(level, ringW));
            AssemblyResult again = ShipAssembler.assemble(level, goal, null);
            h.assertTrue(again.shipId() != null, "reassembly failed: " + again);
            ShipTestCleanup.track(h, again.shipId());
            int found = 0;
            for (BlockPos p : com.richardsenger.piratesnships.ship.sable.SableShips.byId(level, again.shipId()).plotBlocks()) {
                if (level.getBlockState(p).getBlock() instanceof RopeAnchor) {
                    found += RopeLines.partners(level, p).size();
                }
            }
            h.assertTrue(found == 4, "after reassembly the anchors hold " + found + " rope ends, expected 4");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ config

    /**
     * With {@code sailing.sails.rope_lines} off: a flat rope between cleats is refused (the F5b rule), a ring takes no
     * rope, stays are rigged as before, and a line rigged while lines were on still returns its rope when broken.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = LINES_OFF_BATCH)
    public static void ropeLinesOffRigsOnlyStays(GameTestHelper h) {
        floor(h, 24);
        BlockPos oldARel = new BlockPos(2, 2, 2), oldBRel = new BlockPos(6, 2, 2);
        h.setBlock(oldARel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(oldBRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        ServerLevel level = h.getLevel();
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 8);
        BlockPos oldA = h.absolutePos(oldARel), oldB = h.absolutePos(oldBRel);
        RopeItem.use(level, rope, oldA, null);
        h.assertTrue(key(RopeItem.use(level, rope, oldB, null)).equals(RopeItem.KEY_LINE), "no line while lines are on");

        ConfigOverrides.during(h, SailingConfig.ROPE_LINES, false);
        for (int y = 2; y <= 7; y++) h.setBlock(new BlockPos(10, y, 10), Blocks.OAK_LOG);
        BlockPos headRel = new BlockPos(10, 7, 11), tackRel = new BlockPos(10, 2, 16), flatRel = new BlockPos(14, 2, 16);
        BlockPos ringRel = new BlockPos(14, 2, 20);
        h.setBlock(headRel, cleat(AttachFace.WALL, Direction.SOUTH));
        h.setBlock(tackRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(flatRel, cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(ringRel, ring(AttachFace.FLOOR, Direction.NORTH));
        BlockPos head = h.absolutePos(headRel), tack = h.absolutePos(tackRel), flat = h.absolutePos(flatRel), ring = h.absolutePos(ringRel);

        h.assertTrue(!RopeItem.takesRope(level, ring) && RopeItem.takesRope(level, flat), "with lines off a ring still takes a rope");
        RopeItem.use(level, rope, tack, null);
        Component flatR = RopeItem.use(level, rope, flat, null);
        h.assertTrue(key(flatR).equals(RopeItem.KEY_TOO_FLAT), "a flat rope was not refused: " + flatR.getString());
        Component toRing = RopeItem.use(level, rope, ring, null);
        h.assertTrue(key(toRing).equals(RopeItem.KEY_NO_LINES), "a rope from a cleat to a ring was not refused: " + toRing.getString());
        Component stay = RopeItem.use(level, rope, head, null);
        h.assertTrue(key(stay).equals(RopeItem.KEY_RIGGED) && RopeLines.joined(level, head, tack), "no stay with lines off: " + stay.getString());
        h.assertTrue(rope.getCount() == 6, "expected one line and one stay used, " + rope.getCount() + " left");

        breakByHand(level, oldA, h.makeMockPlayer(GameType.SURVIVAL));
        h.assertTrue(ropesDropped(h) == 1, "breaking an old line's cleat with lines off returned " + ropesDropped(h) + " ropes");
        h.assertTrue(ropeCount(level, oldB) == 0, "the old line's other end kept it");
        h.succeed();
    }
}

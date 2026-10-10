package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Placement;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Map;

/**
 * RL5 in a real server (docs/design.md §4.8 "Net sheets and railing latches"): the net item on a railing sets an iron
 * latch and strings from it, the latch goes with its net (cell, hoop or railing broken), a latch or a chainplate takes
 * no second strand, gates, ports, unrailed edges and {@code rigging.railing_latches = false} are refused, a latched net
 * is climbed to the top on land, on a still ship (strung on land, through assembly) and on a ship under way; a rectangle
 * of three strands to three hoops is climbed in its middle column and off the top from a fill cell between two hoops,
 * breaking its middle strand re-joins the outer two; a trapezoid of three into two and RL4's triangle of three into one
 * from latches; a boarding net between two latches along the rail (RAIL5).
 */
public final class RatlineSheetGameTests {

    private static final String BATCH = "pirates_n_ships_config_rigging_";
    /** Ticks after assembly before a player boards. */
    private static final int SETTLE = 40;
    /** The rows of the land rigs: railings at x 2 (railed west), the wall of logs at x 6 with the hoops on its west face. */
    private static final int RAIL_X = 2;
    private static final int WALL_X = 6;
    private static final int TOP = 6;

    private RatlineSheetGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RatlineSheetGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** An oak railing railed on {@code edge}. */
    static BlockState railing(Direction edge) {
        return RailingContent.RAILINGS.lead().get().defaultBlockState().setValue(RailingBlock.edge(edge), true);
    }

    /**
     * Where a climber is put to board a net at a latch on a railing's west edge whose ring is {@code ring}: in the net
     * end's cell, 0.55 inboard of the ring, so its box stays 0.2 clear of the rail's collision (a ship's tick of 4 m/s).
     */
    static Vec3 besideLatch(Point ring) {
        return new Vec3(ring.x() + 0.55, ring.y() + 0.05, ring.z());
    }

    private static String key(net.minecraft.network.chat.Component c) {
        return RatlineNetGameTests.key(c);
    }

    /** Uses a net on the railing at {@code rail} (its west edge), then on the fitting {@code hub}; returns the second answer. */
    private static String fromRail(ServerLevel level, BlockPos rail, BlockPos hub, ItemStack stack) {
        String first = key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.WEST, null));
        if (!first.equals(RatlineNetItem.KEY_MARKED)) {
            return first;
        }
        return key(RatlineNetItem.use(level, stack, hub, null));
    }

    /**
     * A land rig in a 24 block test: a stone floor, a wall of logs at x {@link #WALL_X} from y 1 to {@link #TOP} along
     * z 1..7, hoops on its west face at y {@link #TOP} at {@code hoopZ}, and west-railed railings at x {@link #RAIL_X},
     * y 1 along z 1..7.
     */
    private static void rig(GameTestHelper h, int... hoopZ) {
        RatlineNetGameTests.floor(h, 10);
        for (int z = 1; z <= 7; z++) {
            for (int y = 1; y <= TOP; y++) {
                h.setBlock(new BlockPos(WALL_X, y, z), Blocks.OAK_LOG);
            }
            h.setBlock(new BlockPos(RAIL_X, 1, z), railing(Direction.WEST));
        }
        for (int z : hoopZ) {
            h.setBlock(new BlockPos(WALL_X - 1, TOP, z), RatlineNetGameTests.hoop(Direction.WEST));
        }
    }

    private static BlockPos rail(GameTestHelper h, int z) {
        return h.absolutePos(new BlockPos(RAIL_X, 1, z));
    }

    private static BlockPos hoopAt(GameTestHelper h, int z) {
        return h.absolutePos(new BlockPos(WALL_X - 1, TOP, z));
    }

    /** Strings rail z to hoop z for each pair {rail z, hoop z}, asserting each is strung. */
    private static void stringAll(GameTestHelper h, int[][] pairs) {
        for (int[] p : pairs) {
            String r = fromRail(h.getLevel(), rail(h, p[0]), hoopAt(h, p[1]), new ItemStack(RiggingContent.NET_ITEM.get()));
            h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "rail " + p[0] + " to hoop " + p[1] + " not strung: " + r);
        }
    }

    /**
     * Null when every cell the group of {@code hub} wants stands as a net cell owned as it should be (owner strand, its
     * hub, fill, the second hub), else what is wrong; {@code counts} receives {fill cells, fill cells with a second hub}.
     */
    static String sheetProblem(ServerLevel level, BlockPos hub, int[] counts) {
        counts[0] = 0;
        counts[1] = 0;
        for (Map.Entry<BlockPos, Placement> e : RatlineNets.sheetAt(level, hub).cells().entrySet()) {
            BlockPos p = e.getKey();
            Placement pl = e.getValue();
            if (pl.fill() && RatlineNets.skipsFill(level.getBlockState(p))) {
                continue;
            }
            Net n = pl.owner().net();
            if (!level.getBlockState(p).is(RiggingContent.NET_CELL.get())
                    || !(level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity cell)
                    || !cell.ownedBy(n.a(), n.b(), pl.owner().hub(), pl.fill())
                    || !java.util.Objects.equals(cell.hub2(), pl.hub2())) {
                return "cell " + p + " (" + pl + ") is " + level.getBlockState(p);
            }
            if (pl.fill()) {
                counts[0]++;
                if (pl.hub2() != null) {
                    counts[1]++;
                }
            }
        }
        return null;
    }

    /** The edge of the railing at {@code rail} that carries a latch (the latch block over it), or null. */
    private static @org.jetbrains.annotations.Nullable Direction latchOf(ServerLevel level, BlockPos rail) {
        BlockState s = level.getBlockState(rail.above());
        return s.is(RiggingContent.RAILING_LATCH.get()) ? s.getValue(RailingLatchBlock.FACING) : null;
    }

    // ------------------------------------------------------------------ the latch

    /**
     * The first use on the railing only remembers it; the second (on the hoop) sets the latch (the railing's west edge,
     * the net end over it) and strings the net from the latch's ring. Breaking a cell, the hoop or the railing takes the
     * net and the latch away, each time one net item drops; no latch is left behind.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60)
    public static void aLatchIsSetWithItsNetAndGoesWithIt(GameTestHelper h) {
        rig(h, 4);
        ServerLevel level = h.getLevel();
        BlockPos rail = rail(h, 4), end = rail.above(), hub = hoopAt(h, 4);
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 4);
        String first = key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.WEST, null));
        h.assertTrue(first.equals(RatlineNetItem.KEY_MARKED), "the railing was not tied: " + first);
        h.assertTrue(latchOf(level, rail) == null && level.getBlockState(end).isAir(), "a latch was set before the net");
        String second = key(RatlineNetItem.use(level, stack, hub, null));
        h.assertTrue(second.equals(RatlineNetItem.KEY_STRUNG), "not strung: " + second);
        h.assertTrue(latchOf(level, rail) == Direction.WEST, "no latch on the rail's west edge");
        h.assertTrue(level.getBlockState(end).is(BlockTags.CLIMBABLE), "the latch's block is not climbable");
        Net net = RatlineNets.netOf(level, end);
        h.assertTrue(net != null && net.a().equals(end) && net.b().equals(hub), "the latch does not hold the net: " + net);
        Point ring = net.ringA();
        h.assertTrue(Math.abs(ring.x() - (rail.getX() + RailingLatchBlock.RING_INSET)) < 1e-9
                && Math.abs(ring.y() - (rail.getY() + 1 + RailingLatchBlock.RING_HEIGHT)) < 1e-9
                && Math.abs(ring.z() - (rail.getZ() + 0.5)) < 1e-9, "the ring is not the eye bolt's: " + ring);
        h.assertTrue(stack.getCount() == 3, "one net was not used up");
        int cells = RatlineNets.standingCells(level, net).size();
        h.assertTrue(cells > 0 && cells == net.cells().size(), "the cells do not stand: " + cells);

        // a cell broken
        RatlineNetRules.Cell mid = net.cells().get(cells / 2);
        level.destroyBlock(new BlockPos(mid.x(), mid.y(), mid.z()), false);
        h.assertTrue(RatlineNets.standingCells(level, net).isEmpty(), "cells stayed");
        h.assertTrue(latchOf(level, rail) == null && level.getBlockState(end).isAir(), "the latch stayed after its net broke");

        // the hoop broken
        h.assertTrue(fromRail(level, rail, hub, stack).equals(RatlineNetItem.KEY_STRUNG), "not strung again");
        level.destroyBlock(hub, false);
        h.assertTrue(latchOf(level, rail) == null && level.getBlockState(end).isAir(), "the latch stayed after the hoop broke");

        // the railing broken
        level.setBlockAndUpdate(hub, RatlineNetGameTests.hoop(Direction.WEST));
        h.assertTrue(fromRail(level, rail, hub, stack).equals(RatlineNetItem.KEY_STRUNG), "not strung a third time");
        Net third = RatlineNets.netOf(level, end);
        level.destroyBlock(rail, false);
        h.assertTrue(level.getBlockState(end).isAir(), "the net end stayed without its railing");
        h.assertTrue(RatlineNets.standingCells(level, third).isEmpty() && RatlineNetGameTests.netCount(level, hub) == 0,
                "the net stayed without its railing");
        h.runAfterDelay(2, () -> {
            int dropped = RatlineNetGameTests.droppedNets(h);
            h.assertTrue(dropped == 3, "expected three dropped nets, found " + dropped);
            h.succeed();
        });
    }

    /**
     * Capacity: a latch and a chainplate hold one strand; a second one tied from another hoop is refused at both, also
     * on another edge of the latched railing; a strand whose foot is not in a row with its hoop's other strand is
     * strung alone and says so.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aSecondStrandOnALatchOrAChainplateIsRefused(GameTestHelper h) {
        rig(h, 4);
        h.setBlock(new BlockPos(WALL_X - 1, TOP, 2), RatlineNetGameTests.hoop(Direction.WEST));
        h.setBlock(new BlockPos(RAIL_X, 1, 9), RatlineNetGameTests.chainplate(AttachFace.FLOOR, Direction.EAST));
        ServerLevel level = h.getLevel();
        BlockPos rail = rail(h, 4), hub = hoopAt(h, 4), hub2 = hoopAt(h, 2);
        BlockPos plate = h.absolutePos(new BlockPos(RAIL_X, 1, 9));
        stringAll(h, new int[][] {{4, 4}});
        // the chainplate to the same hoop: its foot is not at the latch's height, so it is strung alone
        String alone = RatlineNetGameTests.string(level, plate, hub, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(alone.equals(RatlineNetItem.KEY_NOT_IN_ROW), "the chainplate's strand did not say it is alone: " + alone);
        // a second strand at the latch, from the other hoop
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        h.assertTrue(key(RatlineNetItem.use(level, stack, hub2, null)).equals(RatlineNetItem.KEY_MARKED), "hoop 2 was not tied");
        String latch = key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.WEST, null));
        h.assertTrue(latch.equals(RatlineNetItem.KEY_BUSY), "a second strand on the latch was not refused: " + latch);
        String otherEdge = key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.NORTH, null));
        h.assertTrue(otherEdge.equals(RatlineNetItem.KEY_BUSY), "a second latch on the railing was not refused: " + otherEdge);
        String chain = key(RatlineNetItem.use(level, stack, plate, null));
        h.assertTrue(chain.equals(RatlineNetItem.KEY_BUSY), "a second strand on the chainplate was not refused: " + chain);
        h.assertTrue(stack.getCount() == 1 && RatlineNetGameTests.netCount(level, hub2) == 0, "something was strung to hoop 2");
        // tied first, a full latch or chainplate refuses too
        h.assertTrue(key(RatlineNetItem.useOnRailing(level, new ItemStack(RiggingContent.NET_ITEM.get()), rail, Direction.WEST, null))
                .equals(RatlineNetItem.KEY_BUSY), "a full latch could be tied");
        h.assertTrue(key(RatlineNetItem.use(level, new ItemStack(RiggingContent.NET_ITEM.get()), plate, null))
                .equals(RatlineNetItem.KEY_BUSY), "a full chainplate could be tied");
        h.succeed();
    }

    /**
     * A railing gate, a railing port and a bulwark gun port take no latch, nor an edge without a rail or a rail with a
     * block over it; a bulwark's cap does. Nothing is set by a refusal and no net is used.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void gatesPortsAndUnrailedEdgesAreRefused(GameTestHelper h) {
        RatlineNetGameTests.floor(h, 10);
        h.setBlock(new BlockPos(1, 1, 1), RailingContent.GATES.lead().get().defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 1), RailingContent.PORTS.lead().get().defaultBlockState());
        h.setBlock(new BlockPos(5, 1, 1), RailingContent.BULWARK_PORTS.lead().get().defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 4), railing(Direction.WEST));
        h.setBlock(new BlockPos(3, 1, 4), railing(Direction.WEST));
        h.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        h.setBlock(new BlockPos(5, 1, 4), RailingContent.BULWARKS.lead().get().defaultBlockState()
                .setValue(RailingBlock.edge(Direction.WEST), true));
        ServerLevel level = h.getLevel();
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        for (BlockPos p : new BlockPos[] {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1)}) {
            String r = key(RatlineNetItem.useOnRailing(level, stack, h.absolutePos(p), Direction.NORTH, null));
            h.assertTrue(r.equals(RatlineNetItem.KEY_NO_LATCH), "the gate or port at " + p + " was not refused: " + r);
        }
        String unrailed = key(RatlineNetItem.useOnRailing(level, stack, h.absolutePos(new BlockPos(1, 1, 4)), Direction.EAST, null));
        h.assertTrue(unrailed.equals(RatlineNetItem.KEY_NO_RAIL), "an unrailed edge was not refused: " + unrailed);
        String blocked = key(RatlineNetItem.useOnRailing(level, stack, h.absolutePos(new BlockPos(3, 1, 4)), Direction.WEST, null));
        h.assertTrue(blocked.equals(RatlineNetItem.KEY_BLOCKED), "a rail under a block was not refused: " + blocked);
        h.assertTrue(!stack.has(RiggingContent.NET_START.get()) && stack.getCount() == 1, "a refusal tied or used the net");
        String bulwark = key(RatlineNetItem.useOnRailing(level, stack, h.absolutePos(new BlockPos(5, 1, 4)), Direction.WEST, null));
        h.assertTrue(bulwark.equals(RatlineNetItem.KEY_MARKED), "the bulwark's cap was refused: " + bulwark);
        BlockPos.betweenClosedStream(h.getBounds()).forEach(p -> h.assertTrue(
                !level.getBlockState(p).is(RiggingContent.RAILING_LATCH.get()), "a latch was set at " + p));
        h.succeed();
    }

    /** {@code rigging.railing_latches = false}: a railing is refused and sets nothing; a chainplate still takes a net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = BATCH + "latches_off")
    public static void latchesOffRefusesRailings(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.RAILING_LATCHES, false);
        rig(h, 4);
        h.setBlock(new BlockPos(RAIL_X + 1, 1, 4), RatlineNetGameTests.chainplate(AttachFace.FLOOR, Direction.EAST));
        ServerLevel level = h.getLevel();
        BlockPos rail = rail(h, 4), hub = hoopAt(h, 4);
        String r = key(RatlineNetItem.useOnRailing(level, new ItemStack(RiggingContent.NET_ITEM.get()), rail, Direction.WEST, null));
        h.assertTrue(r.equals(RatlineNetItem.KEY_LATCHES_OFF), "not refused while latches are off: " + r);
        h.assertTrue(latchOf(level, rail) == null && level.getBlockState(rail.above()).isAir(), "a latch was set");
        String plate = RatlineNetGameTests.string(level, h.absolutePos(new BlockPos(RAIL_X + 1, 1, 4)), hub,
                new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(plate.equals(RatlineNetItem.KEY_STRUNG), "the chainplate's net was not strung: " + plate);
        h.succeed();
    }

    // ------------------------------------------------------------------ climbing from a latch

    /** A 43° net from a latch to a hoop on land: a player put in the net end's cell climbs it to the top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160)
    public static void playerClimbsALatchedNetOnLand(GameTestHelper h) {
        rig(h, 4);
        stringAll(h, new int[][] {{4, 4}});
        BlockPos end = rail(h, 4).above();
        Point ring = RatlineNets.netOf(h.getLevel(), end).ringA();
        RatlineNetGameTests.climb(h, 0, () -> RatlineNetGameTests.playerInLevel(h, besideLatch(ring), Direction.EAST.toYRot()),
                () -> RatlineNets.netOf(h.getLevel(), end), v -> v, false, true, 120, "latched net on land");
    }

    /**
     * A net strung on land from a latch on the deck's railing to the mast's hoop survives assembly (the latch, its net
     * end and the cells come into the plot), and a player put at the latch on the still ship climbs it to the top.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260)
    public static void playerClimbsALatchedNetOnAStillShip(GameTestHelper h) {
        int railX = 10, hoopY = 13;
        BlockPos helmRel = RatlineNetGameTests.shipRig(h, railX, hoopY, 24, true);
        ServerLevel level = h.getLevel();
        BlockPos railRel = new BlockPos(railX, 9, 10), hoopRel = new BlockPos(12, hoopY, 10);
        String r = fromRail(level, h.absolutePos(railRel), h.absolutePos(hoopRel), new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "not strung on land: " + r);
        int cells = RatlineNets.standingCells(level, RatlineNets.netOf(level, h.absolutePos(railRel.above()))).size();
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        BlockPos rail = RatlineNetGameTests.plot(f, helmRel, railRel), hoopPos = RatlineNetGameTests.plot(f, helmRel, hoopRel);
        BlockPos end = rail.above();
        h.assertTrue(latchOf(level, rail) == Direction.WEST, "the railing lost its latch in assembly");
        Net net = RatlineNets.netOf(level, end);
        h.assertTrue(net != null && net.b().equals(hoopPos), "the latched net did not survive assembly: " + net);
        h.assertTrue(RatlineNets.standingCells(level, net).size() == cells, "cells were lost in assembly");
        ShipBody ship = f.ship();
        Point ring = net.ringA();
        RatlineNetGameTests.climb(h, SETTLE, () -> {
            Vec3 at = ship.toWorld(besideLatch(ring));
            return RatlineNetGameTests.playerInLevel(h, at, Direction.EAST.toYRot());
        }, () -> RatlineNets.netOf(level, end), ship::toPlot, false, true, 130, "latched net on a still ship");
    }

    /**
     * A 52° net from a latch on a ship sailing at a steady 4 m/s (RL3's pattern): the climber stands on the deck in the
     * railing's block, jumps onto the net and climbs it while Sable carries it with the ship.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void playerClimbsALatchedNetOnAShipUnderWay(GameTestHelper h) {
        RatlineNetGameTests.underWayClimb(h, new Vec3(4, 0, 0), 0, true, true, "latched net from the deck, ship at 4 m/s");
    }

    /** The same with the climber put straight into the net end's cell, never touching the deck: the assist carries it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void untrackedClimberKeepsUpWithALatchedNetUnderWay(GameTestHelper h) {
        RatlineNetGameTests.underWayClimb(h, new Vec3(4, 0, 0), 0, false, true, "latched net from its end, ship at 4 m/s");
    }

    // ------------------------------------------------------------------ sheets

    /**
     * Three latches two blocks apart along the rail to three hoops two blocks apart on the wall: one sheet, a
     * rectangle: every strand is strung, the pairs are stored on the hoops, every wanted cell stands with the columns
     * between the strands filled (naming both hoops); a player at the middle latch climbs the middle column to its hoop.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160)
    public static void aRectangleOfThreeStrandsIsClimbedInTheMiddleColumn(GameTestHelper h) {
        rig(h, 2, 4, 6);
        stringAll(h, new int[][] {{2, 2}, {4, 4}, {6, 6}});
        ServerLevel level = h.getLevel();
        RatlineNets.Sheet sheet = RatlineNets.sheetAt(level, hoopAt(h, 4));
        h.assertTrue(sheet.hubs().size() == 3 && sheet.strands().size() == 3, "not one group of three: " + sheet.hubs());
        int pairs = 0;
        for (int p : sheet.partner()) {
            pairs += p >= 0 ? 1 : 0;
        }
        h.assertTrue(pairs == 2, "expected two pairs, found " + pairs);
        int[] counts = {0, 0};
        String problem = sheetProblem(level, hoopAt(h, 4), counts);
        h.assertTrue(problem == null, "the sheet does not stand: " + problem);
        h.assertTrue(counts[1] > 0, "no fill between two hoops");
        for (int z : new int[] {3, 5}) {
            BlockPos atRail = rail(h, z).above();
            h.assertTrue(level.getBlockEntity(atRail) instanceof RatlineNetCellBlockEntity c && c.isFill() && c.hub2() != null,
                    "the cell over the rail at z " + z + " is not a fill cell between two hoops");
            RatlineNetCellBlockEntity c = (RatlineNetCellBlockEntity) level.getBlockEntity(atRail);
            Vec3 d = c.climbDirection();
            h.assertTrue(d != null && Math.abs(d.z) < 1e-9 && d.y > 0, "the fill at z " + z + " does not climb up its column: " + d);
        }
        BlockPos end = rail(h, 4).above();
        Point ring = RatlineNets.netOf(level, end).ringA();
        RatlineNetGameTests.climb(h, 0, () -> RatlineNetGameTests.playerInLevel(h, besideLatch(ring), Direction.EAST.toYRot()),
                () -> RatlineNets.netOf(level, end), v -> v, false, true, 120, "the middle column of a rectangle");
    }

    /**
     * RL3's exit along the whole top edge: a player put in the fill over the rail between the first two latches of the
     * rectangle climbs the fill (towards the top edge between two hoops, where no hoop is) and off the top onto the
     * wall's top.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void theTopExitWorksFromAFillCellOfARectangle(GameTestHelper h) {
        rig(h, 2, 4, 6);
        stringAll(h, new int[][] {{2, 2}, {4, 4}, {6, 6}});
        ServerLevel level = h.getLevel();
        Point ring = RatlineNets.netOf(level, rail(h, 2).above()).ringA();
        Vec3 start = besideLatch(new Point(ring.x(), ring.y(), ring.z() + 1));
        Player[] player = {null};
        double[] highest = {Double.NEGATIVE_INFINITY};
        int wallX = h.absolutePos(new BlockPos(WALL_X, 0, 0)).getX();
        int topY = h.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 0) {
                player[0] = RatlineNetGameTests.playerInLevel(h, start, Direction.EAST.toYRot());
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            p.zza = 1f;
            highest[0] = Math.max(highest[0], p.getY());
            BlockPos feet = BlockPos.containing(p.position());
            if (feet.getX() == wallX && feet.getY() == topY && p.onGround() && p.getY() - topY < 0.3) {
                Constants.LOG.info("[RL5] off the top of a rectangle after {} ticks at z {}", t, feet.getZ());
                h.succeed();
            }
        });
        h.runAfterDelay(170, () -> h.fail("never stood on the wall's top (feet rose to y " + highest[0] + ", the top is " + topY + ")"));
    }

    /**
     * Breaking the middle latch's net end drops its strand only (and its latch); the outer two, four blocks apart and
     * still in a row, re-join into one sheet with the middle columns filled.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void breakingTheMiddleStrandReJoinsTheRest(GameTestHelper h) {
        rig(h, 2, 4, 6);
        stringAll(h, new int[][] {{2, 2}, {4, 4}, {6, 6}});
        ServerLevel level = h.getLevel();
        level.destroyBlock(rail(h, 4).above(), false);
        h.assertTrue(latchOf(level, rail(h, 4)) == null, "the middle railing kept its latch");
        h.runAfterDelay(3, () -> {
            h.assertTrue(RatlineNetGameTests.droppedNets(h) == 1, "expected one dropped net, found " + RatlineNetGameTests.droppedNets(h));
            RatlineNets.Sheet sheet = RatlineNets.sheetAt(level, hoopAt(h, 2));
            h.assertTrue(sheet.strands().size() == 2 && (sheet.partner()[0] >= 0 || sheet.partner()[1] >= 0),
                    "the outer strands did not re-join: " + java.util.Arrays.toString(sheet.partner()));
            int[] counts = {0, 0};
            String problem = sheetProblem(level, hoopAt(h, 2), counts);
            h.assertTrue(problem == null, "the re-joined sheet does not stand: " + problem);
            h.assertTrue(level.getBlockEntity(rail(h, 4).above()) instanceof RatlineNetCellBlockEntity c && c.isFill(),
                    "the middle net end's cell is not filled");
            h.succeed();
        });
    }

    /**
     * A trapezoid: three latches into two hoops (the first two share one: RL4's triangle; the last two span both: a
     * quadrilateral); and RL4's triangle of three latches into one hoop.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aTrapezoidAndATriangleFromLatches(GameTestHelper h) {
        rig(h, 3, 5);
        stringAll(h, new int[][] {{2, 3}, {4, 3}, {6, 5}});
        ServerLevel level = h.getLevel();
        int[] counts = {0, 0};
        String problem = sheetProblem(level, hoopAt(h, 3), counts);
        h.assertTrue(problem == null, "the trapezoid does not stand: " + problem);
        h.assertTrue(counts[0] > counts[1] && counts[1] > 0, "expected triangle and quadrilateral fill, found " + counts[0]
                + " fill cells, " + counts[1] + " between two hoops");
        RatlineNets.Sheet sheet = RatlineNets.sheetAt(level, hoopAt(h, 5));
        h.assertTrue(sheet.hubs().size() == 2 && sheet.strands().size() == 3, "not one group: " + sheet.hubs());
        h.succeed();
    }

    /** RL4's triangle from latches: three latches two blocks apart into one hoop. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void threeLatchesIntoOneHoopMakeATriangle(GameTestHelper h) {
        rig(h, 4);
        stringAll(h, new int[][] {{2, 4}, {4, 4}, {6, 4}});
        ServerLevel level = h.getLevel();
        h.assertTrue(RatlineNetGameTests.netCount(level, hoopAt(h, 4)) == 3, "the hoop does not hold three");
        int[] counts = {0, 0};
        String problem = sheetProblem(level, hoopAt(h, 4), counts);
        h.assertTrue(problem == null && counts[0] > 0 && counts[1] == 0, "the triangle does not stand: " + problem + ", " + counts[0]
                + " fill cells, " + counts[1] + " with two hubs");
        for (int z : new int[] {3, 5}) {
            h.assertTrue(level.getBlockEntity(rail(h, z).above()) instanceof RatlineNetCellBlockEntity c && c.isFill(),
                    "the cell over the rail at z " + z + " is not fill");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ boarding nets (RAIL5)

    /**
     * RAIL5 with latches: the net used on two railings of one west-railed run four blocks apart hangs along the rail as
     * a boarding net through the row over it (both latch ends set); latches on a railing that turns the corner refuse
     * a boarding net along two edges.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aBoardingNetHangsBetweenTwoLatches(GameTestHelper h) {
        rig(h);
        ServerLevel level = h.getLevel();
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        h.assertTrue(key(RatlineNetItem.useOnRailing(level, stack, rail(h, 2), Direction.WEST, null)).equals(RatlineNetItem.KEY_MARKED),
                "the first railing was not tied");
        String r = key(RatlineNetItem.useOnRailing(level, stack, rail(h, 6), Direction.WEST, null));
        h.assertTrue(r.equals(RatlineNetItem.KEY_BOARDING_STRUNG), "no boarding net: " + r);
        h.assertTrue(latchOf(level, rail(h, 2)) == Direction.WEST && latchOf(level, rail(h, 6)) == Direction.WEST, "a latch is missing");
        for (int z = 3; z <= 5; z++) {
            BlockState c = level.getBlockState(rail(h, z).above());
            h.assertTrue(c.is(RiggingContent.NET_CELL.get()) && c.getValue(RatlineNetCellBlock.BOARDING)
                    && c.getValue(RatlineNetCellBlock.FACING) == Direction.WEST, "the cell over the rail at z " + z + " is " + c);
        }
        // a latch on the north edge of a corner railing and one on the west edge of the run: not along one edge
        h.setBlock(new BlockPos(RAIL_X, 1, 8), railing(Direction.WEST).setValue(RailingBlock.edge(Direction.NORTH), true));
        ItemStack second = new ItemStack(RiggingContent.NET_ITEM.get());
        RatlineNetItem.useOnRailing(level, second, h.absolutePos(new BlockPos(RAIL_X, 1, 8)), Direction.NORTH, null);
        String along = key(RatlineNetItem.useOnRailing(level, second, rail(h, 7), Direction.WEST, null));
        h.assertTrue(along.equals(RatlineNetItem.KEY_NOT_ALONG), "latches on two edges took a boarding net: " + along);
        h.assertTrue(latchOf(level, h.absolutePos(new BlockPos(RAIL_X, 1, 8))) == null
                && latchOf(level, rail(h, 7)) == null, "a refused net left a latch");
        h.succeed();
    }
}

package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boarding planks, the gangway (BRD1, BRD2, BRD4, docs/design.md §8.3, §8.4), in a real server.
 *
 * <p>Ship to ship: two closed 5×4×5 plank hulls of {@link DryHullGameTests} float in one basin: ship A at x 2..6, ship B
 * three blocks east of it at x 10..14 (or eight blocks, at x 15..19). Both have their deck (the hull's top layer) at
 * relative y 8 and float level and still, so a plank laid from the top of A's east wall runs four cells east at y 9
 * and its last cell rests on B's west wall.
 *
 * <p>Ship and pier: ship A alone; once it has settled, a stone pier is built in the basin a few blocks east of A's east
 * wall ({@link #pier}), its top level with A's rail top, or one, two or four blocks above it. Its rows are counted from
 * the world row of A's rail block ({@link Rail}).
 *
 * <p>Plank cells are laid {@link #SETTLE} ticks after assembly, when Sable has the ship's bounds and the bobbing has died
 * down. Ships are removed by {@code ShipTestCleanup}.
 */
public final class BoardingGameTests {

    private static final String BATCH = "pirates_n_ships_config_boarding_";
    /** Ticks after assembly before laying a plank. */
    private static final int SETTLE = 40;

    private BoardingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BoardingGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ships(Fixture a, Fixture b) {
    }

    /** Ship A at x 2..6 and ship B with {@code gap} blocks of water between them, both afloat. */
    private static Ships twoShips(GameTestHelper h, int gap) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 7 + gap, false);
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Ship A alone at x 2..6, afloat. */
    private static Fixture oneShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        return DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 2, false));
    }

    /** Plot position of the top block of A's east wall, level with the helm. */
    private static BlockPos eastGunwale(Fixture a) {
        return a.helmPlot().offset(2, -1, 0);
    }

    /** Plot position of the top block of B's west wall, level with the helm. */
    private static BlockPos westGunwale(Fixture b) {
        return b.helmPlot().offset(-2, -1, 0);
    }

    /** Lays a plank from the top face of A's east gunwale, facing east. */
    private static BoardingPlanks.Laid layFromTop(GameTestHelper h, Fixture a) {
        return BoardingPlanks.lay(h.getLevel(), eastGunwale(a), Direction.UP, Direction.EAST);
    }

    /** Plot position (A's plot) of cell {@code i} of a level run laid by {@link #layFromTop}. */
    private static BlockPos topCell(Fixture a, int i) {
        return eastGunwale(a).above().east(1 + i);
    }

    /** World block of A's east rail now: {@code x} and {@code row} of the rail block, {@code z} of the helm's line. */
    private record Rail(int x, int row, int z) {
    }

    private static Rail rail(Fixture a) {
        BlockPos p = BlockPos.containing(a.ship().toWorld(Vec3.atCenterOf(eastGunwale(a))));
        return new Rail(p.getX(), p.getY(), p.getZ());
    }

    /**
     * A stone pier east of A in world blocks: {@code gap} blocks of water between A's rail and the pier, four blocks long
     * and three wide, from the basin floor up to the row {@code rise} above the rail's row (its top {@code rise} blocks
     * above A's rail top). Returns the world position of the pier's edge block on the rail's line.
     */
    private static BlockPos pier(GameTestHelper h, Rail r, int gap, int rise) {
        ServerLevel level = h.getLevel();
        int floor = h.absolutePos(new BlockPos(0, 2, 0)).getY();
        for (int x = r.x() + gap + 1; x <= r.x() + gap + 4; x++) {
            for (int z = r.z() - 1; z <= r.z() + 1; z++) {
                for (int y = floor; y <= r.row() + rise; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        return new BlockPos(r.x() + gap + 1, r.row() + rise, r.z());
    }

    private static int plankItems(GameTestHelper h, BlockPos plotNear) {
        return plankItemEntities(h, plotNear).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static Set<ItemEntity> plankItemEntities(GameTestHelper h, BlockPos plotNear) {
        ServerLevel level = h.getLevel();
        // the test's own structure only: the neighbouring tests of the batch lie right next to it
        AABB area = h.getBounds().expandTowards(0, 8, 0);
        // also where an item spawned in a plot would be if Sable had not moved it out
        AABB plot = new AABB(plotNear).inflate(8);
        // Sable's entity lookups also find world entities for a box in plot space, so one item can turn up in both
        // boxes: count each entity once
        Set<ItemEntity> seen = new HashSet<>();
        for (AABB box : List.of(area, plot)) {
            seen.addAll(level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive()
                    && e.getItem().is(BoardingContent.PLANK_ITEM.get())));
        }
        return seen;
    }

    private static void assertGone(GameTestHelper h, List<BlockPos> cells) {
        for (int i = 0; i < cells.size(); i++) {
            h.assertTrue(!h.getLevel().getBlockState(cells.get(i)).is(BoardingContent.PLANK.get()),
                    "plank segment " + i + " is still there");
        }
    }

    private static void assertRunGone(GameTestHelper h, Fixture a, int length) {
        for (int i = 0; i < length; i++) {
            h.assertTrue(!h.getLevel().getBlockState(topCell(a, i)).is(BoardingContent.PLANK.get()),
                    "plank segment " + i + " is still there");
        }
    }

    private static void assertThere(GameTestHelper h, List<BlockPos> cells, String when) {
        for (int i = 0; i < cells.size(); i++) {
            h.assertTrue(h.getLevel().getBlockState(cells.get(i)).is(BoardingContent.PLANK.get()),
                    "plank segment " + i + " broke " + when);
        }
    }

    private static void assertLaidOnB(GameTestHelper h, Ships s, BoardingPlanks.Laid laid) {
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome());
        h.assertTrue(laid.length() == 4, "expected a run of 4 cells, got " + laid.length());
        h.assertTrue(laid.landing() == PlankRun.Landing.LEVEL, "expected a level landing, got " + laid.landing());
        h.assertTrue(s.b().ship().id().equals(laid.farShip()), "the plank landed on " + laid.farShip() + ", not on B");
    }

    /** The run was laid as one straight board of {@code columns} columns rising {@code rise} blocks, landing so. */
    private static void assertOk(GameTestHelper h, BoardingPlanks.Laid laid, int columns, int rise, PlankRun.Landing landing) {
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome()
                + (laid.outcome() == BoardingPlanks.Outcome.BLOCKED ? " at " + laid.blockedAt() : ""));
        h.assertTrue(laid.columns() == columns && laid.rise() == rise && laid.landing() == landing,
                "expected " + columns + " columns rising " + rise + " landing " + landing + ", got " + laid.columns()
                        + " rising " + laid.rise() + " " + laid.landing());
        assertStraight(h, laid);
    }

    /**
     * Every laid cell's block entity carries the one straight line (BRD4): the board's underside at each cell's near and
     * far edge lies at {@code rise * u / columns} above segment 0's start, so the run has a single pitch.
     */
    private static void assertStraight(GameTestHelper h, BoardingPlanks.Laid laid) {
        BlockPos base = laid.base();
        BoardingPlankBlockEntity be0 = (BoardingPlankBlockEntity) h.getLevel().getBlockEntity(base);
        h.assertTrue(be0 != null && be0.isBase() && be0.cells().equals(laid.cells()), "segment 0 does not hold the run");
        Direction f = be0.facing();
        double y0 = base.getY() + be0.near();
        double slope = laid.rise() / (double) laid.columns();
        for (int k = 0; k < laid.length(); k++) {
            BlockPos c = laid.cells().get(k);
            BoardingPlankBlockEntity be = (BoardingPlankBlockEntity) h.getLevel().getBlockEntity(c);
            h.assertTrue(be != null && base.equals(be.base()), "cell " + k + " does not know its run");
            int u = (c.getX() - base.getX()) * f.getStepX() + (c.getZ() - base.getZ()) * f.getStepZ();
            h.assertTrue(Math.abs(c.getY() + be.near() - (y0 + slope * u)) < 1e-6
                            && Math.abs(c.getY() + be.far() - (y0 + slope * (u + 1))) < 1e-6,
                    "cell " + k + " is off the straight line: near " + be.near() + ", far " + be.far());
        }
    }

    /** Every cell of the run lies in {@code ship}'s plot. */
    private static void assertInPlot(GameTestHelper h, List<BlockPos> cells, ShipBody ship) {
        for (int i = 0; i < cells.size(); i++) {
            ShipBody owner = SableShips.containing(h.getLevel(), cells.get(i));
            h.assertTrue(owner != null && owner.id().equals(ship.id()), "segment " + i + " is not in the ship's plot");
        }
    }

    /**
     * Walks {@code e} (feet at {@code from}, world) east by small moves with a little fall each, the way walking feels
     * (vanilla's step-up of 0.6 blocks, Sable's collision with ship blocks), until its x passes {@code untilX}. Returns
     * {lowest feet y, last feet y, last x}.
     */
    private static double[] walkEast(Entity e, Vec3 from, double untilX) {
        return walk(e, from, 1, untilX);
    }

    /**
     * Walks {@code e} east ({@code sign} 1) or west (-1) as {@link #walkEast} does, until its x passes {@code untilX}.
     * Returns {lowest feet y, last feet y, last x, highest feet y, the largest rise of one move}.
     */
    private static double[] walk(Entity e, Vec3 from, int sign, double untilX) {
        e.moveTo(from.x, from.y, from.z, sign > 0 ? -90.0f : 90.0f, 0.0f);
        e.setOnGround(true);
        double lowest = e.getY();
        double highest = e.getY();
        double jump = 0.0;
        for (int step = 0; step < 300 && (sign > 0 ? e.getX() < untilX : e.getX() > untilX); step++) {
            double before = e.getY();
            e.move(MoverType.SELF, new Vec3(0.12 * sign, -0.1, 0.0));
            lowest = Math.min(lowest, e.getY());
            highest = Math.max(highest, e.getY());
            jump = Math.max(jump, e.getY() - before);
        }
        return new double[]{lowest, e.getY(), e.getX(), highest, jump};
    }

    // ------------------------------------------------------------------ ship to ship (BRD1)

    /**
     * From the top of A's east gunwale, facing east, the run lies in A's plot (segments 0..3, the last one the tip),
     * its far end over B's west gunwale, it has a board to stand on, {@link BoardingPlanks#between} lists it both
     * ways, and it stays while the ships lie still.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankLaidFromAReachesBsDeck(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        ServerLevel level = h.getLevel();
        ShipBody a = s.a().ship();
        ShipBody b = s.b().ship();
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = layFromTop(h, s.a());
            assertLaidOnB(h, s, laid);
            h.assertTrue(topCell(s.a(), 0).equals(laid.base()), "segment 0 is at " + laid.base());
            h.assertTrue(a.id().equals(laid.ship()), "the run does not lie in A");
            BoardingPlankBlock block = BoardingContent.PLANK.get();
            for (int i = 0; i < 4; i++) {
                BlockPos cell = topCell(s.a(), i);
                BlockState st = level.getBlockState(cell);
                h.assertTrue(st.is(block) && st.getValue(BoardingPlankBlock.SEGMENT) == i
                        && st.getValue(BoardingPlankBlock.FACING) == Direction.EAST, "segment " + i + " is " + st);
                h.assertTrue(!st.getCollisionShape(level, cell).isEmpty(), "segment " + i + " has no collision");
            }
            assertInPlot(h, laid.cells(), a);
            assertStraight(h, laid);
            Vec3 tip = a.toWorld(Vec3.atCenterOf(topCell(s.a(), 3)));
            Vec3 overB = b.toWorld(Vec3.atCenterOf(westGunwale(s.b()).above()));
            h.assertTrue(tip.distanceTo(overB) < 0.5, "the far end " + tip + " is not over B's gunwale " + overB);
            List<BoardingPlanks.Plank> ab = BoardingPlanks.between(a, b);
            List<BoardingPlanks.Plank> ba = BoardingPlanks.between(b, a);
            h.assertTrue(ab.size() == 1 && ba.size() == 1, "between(A,B) " + ab + ", between(B,A) " + ba);
            BoardingPlanks.Plank p = ab.get(0);
            h.assertTrue(p.fromShip().equals(a.id()) && p.toShip().equals(b.id()) && p.length() == 4
                    && p.toBlock().equals(westGunwale(s.b())), "wrong plank record: " + p);
        });
        h.runAfterDelay(SETTLE + 40, () -> {
            for (int i = 0; i < 4; i++) {
                h.assertTrue(level.getBlockState(topCell(s.a(), i)).is(BoardingContent.PLANK.get()),
                        "segment " + i + " broke while the ships lay still");
            }
            h.assertTrue(plankItems(h, eastGunwale(s.a())) == 0, "a plank dropped while the ships lay still");
            h.succeed();
        });
    }

    /** The item lays the run when used on the gunwale's top face and is used up (survival). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankItemIsUsedUpLayingARun(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.setYRot(-90.0f); // east
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BoardingContent.PLANK_ITEM.get(), 2));
            BlockPos clicked = eastGunwale(s.a());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false);
            InteractionResult r = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(r.consumesAction(), "using the plank did nothing: " + r);
            h.assertTrue(player.getMainHandItem().getCount() == 1, "the plank was not used up: " + player.getMainHandItem());
            h.assertTrue(h.getLevel().getBlockState(topCell(s.a(), 3)).is(BoardingContent.PLANK.get()), "no run was laid");
            h.succeed();
        });
    }

    /**
     * From the side face of A's gunwale the run starts a block lower than B's deck: one straight board over the three
     * columns of water climbs the block and meets B's gunwale top (BRD4; BRD2 ramped up in its last two cells, BRD1
     * ended it flat against B's gunwale).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankFromTheSideClimbsToAHigherDeck(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), eastGunwale(s.a()), Direction.EAST, Direction.NORTH);
            assertOk(h, laid, 3, 1, PlankRun.Landing.RAMP);
            h.assertTrue(eastGunwale(s.a()).east().equals(laid.base()), "segment 0 is at " + laid.base());
            h.assertTrue(s.b().ship().id().equals(laid.farShip()), "the plank did not land on B");
            h.succeed();
        });
    }

    /** Moving B three blocks away breaks the whole run within 20 ticks, dropping exactly one plank. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankBreaksWhenTheHullsPart(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        ShipBody b = s.b().ship();
        h.runAfterDelay(SETTLE, () -> {
            assertLaidOnB(h, s, layFromTop(h, s.a()));
            Vec3 helm = Vec3.atCenterOf(s.b().helmPlot());
            b.placeAt(helm, b.toWorld(helm).add(3.0, 0.0, 0.0), b.orientation());
        });
        h.runAfterDelay(SETTLE + 20, () -> {
            assertRunGone(h, s.a(), 4);
            int items = plankItems(h, eastGunwale(s.a()));
            h.assertTrue(items == 1, "expected one dropped plank, found " + items + ": "
                    + plankItemEntities(h, eastGunwale(s.a())).stream().map(e -> e.getId() + "@" + e.position()).toList());
            h.assertTrue(BoardingPlanks.between(s.a().ship(), b).isEmpty(), "between(A,B) still lists the plank");
            h.succeed();
        });
    }

    /** Breaking a middle segment takes the whole run down and drops one plank (the hammock pattern). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void breakingAnySegmentBreaksTheRun(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            assertLaidOnB(h, s, layFromTop(h, s.a()));
            h.getLevel().destroyBlock(topCell(s.a(), 2), true);
            assertRunGone(h, s.a(), 4);
        });
        h.runAfterDelay(SETTLE + 5, () -> {
            int items = plankItems(h, eastGunwale(s.a()));
            h.assertTrue(items == 1, "expected one dropped plank, found " + items + ": "
                    + plankItemEntities(h, eastGunwale(s.a())).stream().map(e -> e.getId() + "@" + e.position()).toList());
            h.succeed();
        });
    }

    /** With eight blocks of water between the hulls B's deck lies past the reach: refused as too far, nothing placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankIsRefusedWhenTheHullsAreFarApart(GameTestHelper h) {
        Ships s = twoShips(h, 8);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = layFromTop(h, s.a());
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.TOO_FAR, "expected TOO_FAR, got " + laid.outcome());
            assertRunGone(h, s.a(), 6);
            h.assertTrue(BoardingPlankItem.messageKey(laid.outcome()).equals(BoardingPlankItem.KEY_TOO_FAR), "wrong message");
            h.succeed();
        });
    }

    /** From the basin's stone rim between the ships, out over open water: nothing to land on, nothing placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankOverOpenWaterIsRefused(GameTestHelper h) {
        twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            BlockPos rim = h.absolutePos(new BlockPos(8, 8, 0));
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), rim, Direction.UP, Direction.SOUTH);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.NOTHING_TO_LAND, "expected NOTHING_TO_LAND, got " + laid.outcome());
            h.assertTrue(!h.getLevel().getBlockState(rim.above().south()).is(BoardingContent.PLANK.get()), "a plank was laid");
            h.succeed();
        });
    }

    /** {@code boarding.plank.enabled = false}: refused, nothing placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "disabled")
    public static void plankIsRefusedWhenDisabled(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.ENABLED, false);
        Ships s = twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = layFromTop(h, s.a());
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.DISABLED, "expected DISABLED, got " + laid.outcome());
            assertRunGone(h, s.a(), 4);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ship and pier (BRD2)

    /**
     * Ship to a pier one block above the rail top, three blocks of water between: one straight board in A's plot climbs
     * to the pier's top edge, landing on the world; a player walks from A's deck up onto the pier without ever dropping.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayFromAShipRampsUpOneBlockToAPier(GameTestHelper h) {
        Fixture a = oneShip(h);
        h.runAfterDelay(SETTLE, () -> {
            Rail r = rail(a);
            BlockPos edge = pier(h, r, 3, 1);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            assertOk(h, laid, 3, 1, PlankRun.Landing.RAMP);
            h.assertTrue(laid.farShip() == null && a.ship().id().equals(laid.ship()), "the run should lie in A and land on the world");
            assertInPlot(h, laid.cells(), a.ship());
            BoardingPlankBlockEntity be = (BoardingPlankBlockEntity) h.getLevel().getBlockEntity(laid.base());
            h.assertTrue(be != null && be.onShip() && be.farShip() == null && be.farBlock().equals(edge),
                    "segment 0 does not record the pier: " + (be == null ? null : be.farBlock()));
            // walk it: from a block inside the rail, east, until a block onto the pier
            Player walker = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 deck = a.ship().toWorld(Vec3.atBottomCenterOf(eastGunwale(a).above().west()));
            double[] w = walkEast(walker, deck.add(0, 0.05, 0), edge.getX() + 1.5);
            double pierTop = edge.getY() + 1.0;
            h.assertTrue(w[2] >= edge.getX() + 1.4, "the walker got stuck at x " + w[2] + " (pier edge " + edge.getX() + ")");
            h.assertTrue(Math.abs(w[1] - pierTop) < 0.2, "the walker ended at y " + w[1] + ", the pier top is " + pierTop);
            h.assertTrue(w[0] > deck.y - 0.3, "the walker dropped to y " + w[0] + " on the way (deck " + deck.y + ")");
            h.succeed();
        });
    }

    /**
     * Ship to a pier two blocks above the rail top, four blocks of water between: one straight board rising two blocks
     * over four columns (26.6 degrees), its last two cells a block up. Breaking its second cell takes the whole run down
     * across the step to the next level and drops one plank.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayRampsUpTwoBlocksAndBreaksAcrossTheStep(GameTestHelper h) {
        Fixture a = oneShip(h);
        List<BlockPos> cells = new java.util.ArrayList<>();
        h.runAfterDelay(SETTLE, () -> {
            Rail r = rail(a);
            BlockPos edge = pier(h, r, 4, 2);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            assertOk(h, laid, 4, 2, PlankRun.Landing.RAMP);
            h.assertTrue(laid.cells().get(2).getY() == laid.cells().get(1).getY() + 1, "the second block of ramp is not a level up");
            // walk it
            Player walker = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 deck = a.ship().toWorld(Vec3.atBottomCenterOf(eastGunwale(a).above().west()));
            double[] w = walkEast(walker, deck.add(0, 0.05, 0), edge.getX() + 1.5);
            h.assertTrue(w[2] >= edge.getX() + 1.4 && Math.abs(w[1] - (edge.getY() + 1.0)) < 0.2,
                    "the walker did not reach the pier top: x " + w[2] + ", y " + w[1] + " (edge " + edge + ")");
            cells.addAll(laid.cells());
            h.getLevel().destroyBlock(cells.get(1), true);
        });
        h.runAfterDelay(SETTLE + 6, () -> {
            assertGone(h, cells);
            int items = plankItems(h, cells.get(0));
            h.assertTrue(items == 1, "expected one dropped plank, found " + items);
            h.succeed();
        });
    }

    /** A pier four blocks above the rail top is out of a plank's climb: refused as too steep. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayToAPierFourBlocksUpIsTooSteep(GameTestHelper h) {
        Fixture a = oneShip(h);
        h.runAfterDelay(SETTLE, () -> {
            pier(h, rail(a), 3, 4);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.TOO_STEEP, "expected TOO_STEEP, got " + laid.outcome());
            h.assertTrue(!h.getLevel().getBlockState(topCell(a, 0)).is(BoardingContent.PLANK.get()), "a plank was laid");
            h.succeed();
        });
    }

    /** A fence post in the second cell toward a level pier: refused as blocked two blocks out. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayWithAPostInTheWayIsBlocked(GameTestHelper h) {
        Fixture a = oneShip(h);
        h.runAfterDelay(SETTLE, () -> {
            Rail r = rail(a);
            pier(h, r, 3, 0);
            h.getLevel().setBlock(new BlockPos(r.x() + 2, r.row() + 1, r.z()), Blocks.OAK_FENCE.defaultBlockState(), 3);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.BLOCKED && laid.blockedAt() == 1,
                    "expected BLOCKED at cell 1, got " + laid.outcome() + " at " + laid.blockedAt());
            h.assertTrue(BoardingPlankItem.message(laid).getString().contains("2"), "the message does not say where: "
                    + BoardingPlankItem.message(laid).getString());
            h.succeed();
        });
    }

    /**
     * From a level pier toward the ship: the run is planned from the pier but laid in A's plot from A's end outward
     * (segment 0 over A's rail, laid east), so it follows the ship; its far end rests on the pier.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayFromAPierIsLaidInTheShipsPlot(GameTestHelper h) {
        Fixture a = oneShip(h);
        h.runAfterDelay(SETTLE, () -> {
            BlockPos edge = pier(h, rail(a), 3, 0);
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), edge, Direction.UP, Direction.WEST);
            assertOk(h, laid, 4, 0, PlankRun.Landing.LEVEL);
            h.assertTrue(a.ship().id().equals(laid.ship()) && laid.farShip() == null, "the run should lie in A, resting on the world");
            assertInPlot(h, laid.cells(), a.ship());
            h.assertTrue(eastGunwale(a).above().equals(laid.base()), "segment 0 is at " + laid.base() + ", not over A's rail");
            h.assertTrue(h.getLevel().getBlockState(laid.base()).getValue(BoardingPlankBlock.FACING) == Direction.EAST,
                    "the run is not laid from A's end outward");
            BoardingPlankBlockEntity be = (BoardingPlankBlockEntity) h.getLevel().getBlockEntity(laid.base());
            h.assertTrue(be != null && be.farBlock().equals(edge) && be.baseSupport().equals(eastGunwale(a)),
                    "wrong ends: far " + (be == null ? null : be.farBlock()) + ", base " + (be == null ? null : be.baseSupport()));
            h.succeed();
        });
    }

    /**
     * Picking up from the far end: a sneaking player with an empty hand uses the pier end of a run laid from the pier;
     * the whole run goes, one plank lands in the inventory, none is dropped.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void gangwayIsPickedUpFromThePierEnd(GameTestHelper h) {
        Fixture a = oneShip(h);
        List<BlockPos> cells = new java.util.ArrayList<>();
        h.runAfterDelay(SETTLE, () -> {
            BlockPos edge = pier(h, rail(a), 3, 0);
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), edge, Direction.UP, Direction.WEST);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome());
            cells.addAll(laid.cells());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.setShiftKeyDown(true);
            BlockPos tip = cells.get(cells.size() - 1);
            BlockState st = h.getLevel().getBlockState(tip);
            InteractionResult r = st.useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(tip), Direction.UP, tip, false));
            h.assertTrue(r.consumesAction(), "sneak-use did nothing: " + r);
            assertGone(h, cells);
            h.assertTrue(player.getInventory().countItem(BoardingContent.PLANK_ITEM.get()) == 1,
                    "expected one plank back, have " + player.getInventory().countItem(BoardingContent.PLANK_ITEM.get()));
        });
        h.runAfterDelay(SETTLE + 5, () -> {
            assertGone(h, cells);
            h.assertTrue(plankItems(h, cells.get(0)) == 0, "a plank was dropped as well");
            h.succeed();
        });
    }

    /**
     * A gangway resting on a level pier holds through a 1.3-block heave (WAV4's storm heave of a moored hull): lifting A
     * by 1.3 blocks reads a vertical drift of about 1.3 and no break, and the run is still there 20 ticks later, its ends
     * checked every tick while the hull settles back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "every_tick")
    public static void gangwayAtAPierRidesOutAHeave(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.CHECK_INTERVAL_TICKS, 1);
        Fixture a = oneShip(h);
        List<BlockPos> cells = new java.util.ArrayList<>();
        h.runAfterDelay(SETTLE, () -> {
            pier(h, rail(a), 3, 0);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            assertOk(h, laid, 4, 0, PlankRun.Landing.LEVEL);
            cells.addAll(laid.cells());
            ShipBody ship = a.ship();
            Vec3 helm = Vec3.atCenterOf(a.helmPlot());
            ship.placeAt(helm, ship.toWorld(helm).add(0.0, 1.3, 0.0), ship.orientation());
            BoardingPlankBlockEntity be = (BoardingPlankBlockEntity) h.getLevel().getBlockEntity(cells.get(0));
            BoardingPlankBlockEntity.Check c = be.check(h.getLevel());
            h.assertTrue(Math.abs(c.vertical() - 1.3) < 0.2 && c.horizontal() < 0.3 && !c.breaks(),
                    "after the heave: vertical " + c.vertical() + ", horizontal " + c.horizontal() + ", breaks " + c.breaks());
        });
        h.runAfterDelay(SETTLE + 20, () -> {
            assertThere(h, cells, "in a 1.3-block heave");
            h.succeed();
        });
    }

    /**
     * The same gangway breaks when the ship drifts two blocks off the pier (sideways allowance 1.5), dropping one plank.
     * The ends are checked every tick here: a ship moved by {@code placeAt} drifts back toward where its body was within
     * about six ticks, sooner than the default check interval of ten.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "every_tick")
    public static void gangwayAtAPierBreaksWhenTheShipDriftsOff(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.CHECK_INTERVAL_TICKS, 1);
        Fixture a = oneShip(h);
        List<BlockPos> cells = new java.util.ArrayList<>();
        h.runAfterDelay(SETTLE, () -> {
            pier(h, rail(a), 3, 0);
            BoardingPlanks.Laid laid = layFromTop(h, a);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome());
            cells.addAll(laid.cells());
            ShipBody ship = a.ship();
            Vec3 helm = Vec3.atCenterOf(a.helmPlot());
            ship.placeAt(helm, ship.toWorld(helm).add(-2.0, 0.0, 0.0), ship.orientation());
            BoardingPlankBlockEntity.Check c = ((BoardingPlankBlockEntity) h.getLevel().getBlockEntity(cells.get(0))).check(h.getLevel());
            h.assertTrue(c.horizontal() > 1.5 && c.breaks(), "after the drift: horizontal " + c.horizontal() + ", breaks " + c.breaks());
        });
        h.runAfterDelay(SETTLE + 20, () -> {
            assertGone(h, cells);
            int items = plankItems(h, cells.get(0));
            h.assertTrue(items == 1, "expected one dropped plank, found " + items);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ dock to dock (BRD2)

    /** Two stone docks, the far one a block higher, three blocks of air between, x 2..5 (top y 4) and x 9..12 (top y 5). */
    private static void docks(GameTestHelper h) {
        for (int z = 9; z <= 13; z++) {
            for (int x = 2; x <= 5; x++) {
                for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            for (int x = 9; x <= 12; x++) {
                for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    /**
     * Dock to a dock a block higher: plain world blocks, one straight board rising a block over the three columns of the
     * gap (no ship), segment 0 recording both docks; a crew member paths from the low dock up the board onto the high one.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void gangwayBetweenDocksIsPlainBlocksAndCrewWalkIt(GameTestHelper h) {
        docks(h);
        ServerLevel level = h.getLevel();
        BoardingPlanks.Laid laid = BoardingPlanks.lay(level, h.absolutePos(new BlockPos(5, 4, 11)), Direction.UP, Direction.EAST);
        assertOk(h, laid, 3, 1, PlankRun.Landing.RAMP);
        h.assertTrue(laid.ship() == null && laid.farShip() == null, "a dock-to-dock run touches no ship");
        for (BlockPos c : laid.cells()) {
            h.assertTrue(SableShips.containing(level, c) == null, "a cell is in a plot");
        }
        h.assertTrue(laid.base().equals(h.absolutePos(new BlockPos(6, 5, 11))), "segment 0 is at " + laid.base());
        BoardingPlankBlockEntity be = (BoardingPlankBlockEntity) level.getBlockEntity(laid.base());
        h.assertTrue(be != null && !be.onShip() && be.farBlock().equals(h.absolutePos(new BlockPos(9, 5, 11))),
                "segment 0 does not record the far dock");
        CrewMember c = StationContent.CREW_MEMBER.get().create(level);
        if (c == null) throw new AssertionError("no crew member");
        Vec3 from = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 5, 11)));
        Vec3 to = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11, 6, 11)));
        c.moveTo(from.x, from.y, from.z, -90.0f, 0.0f);
        c.setStationary(true);
        level.addFreshEntity(c);
        h.onEachTick(() -> {
            if (c.getNavigation().isDone()) {
                c.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
        });
        h.succeedWhen(() -> {
            // the navigation stops within a block or so of its target: anywhere on the high dock past its edge will do
            double dockB = h.absolutePos(new BlockPos(9, 5, 11)).getX();
            h.assertTrue(c.position().distanceTo(to) < 1.6 && c.getX() > dockB + 0.5,
                    "the crew member is at " + c.position() + ", not on the high dock near " + to);
            h.assertTrue(Math.abs(c.getY() - to.y) < 0.2, "the crew member is not on the high dock");
            c.discard();
        });
    }

    /** A dock edge facing along its own dock has nothing to bridge: no plank from here. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void gangwayAlongADockIsRefused(GameTestHelper h) {
        docks(h);
        BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), h.absolutePos(new BlockPos(3, 4, 11)), Direction.UP, Direction.SOUTH);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.NO_START, "expected NO_START, got " + laid.outcome());
        h.succeed();
    }

    // ------------------------------------------------------------------ one straight board (BRD4)

    /**
     * Two stone docks, the far one two blocks higher, six blocks of air between: x 1..3 (top y 5) and x 10..13 (top y
     * 7), z 9..13.
     */
    private static void docksTwoApart(GameTestHelper h) {
        for (int z = 9; z <= 13; z++) {
            for (int x = 1; x <= 3; x++) {
                for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            for (int x = 10; x <= 13; x++) {
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    /**
     * The human's case: a dock two blocks lower than the far one, six blocks apart. The plank is one straight board at a
     * single pitch over the six columns (18.4 degrees: the first three cells on the low level, the last three a block
     * up, every cell's board on the one line), not a level stretch and then a steep ramp; a mock player walks up it
     * onto the high dock and back down onto the low one, never jumping more than a step and never dropping.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void aPlankTwoUpOverSixIsOneStraightBoardWalkedUpAndDown(GameTestHelper h) {
        docksTwoApart(h);
        ServerLevel level = h.getLevel();
        BoardingPlanks.Laid laid = BoardingPlanks.lay(level, h.absolutePos(new BlockPos(3, 4, 11)), Direction.UP, Direction.EAST);
        assertOk(h, laid, 6, 2, PlankRun.Landing.RAMP);
        h.assertTrue(Math.abs(laid.pitchDegrees() - 18.43) < 0.01, "pitch " + laid.pitchDegrees());
        for (int i = 0; i < 6; i++) {
            BlockPos expected = h.absolutePos(new BlockPos(4 + i, i < 3 ? 5 : 6, 11));
            h.assertTrue(laid.cells().get(i).equals(expected), "cell " + i + " is at " + laid.cells().get(i) + ", not " + expected);
        }
        // the collision follows the board: the cells' tops climb with it
        double last = -1;
        for (BlockPos c : laid.cells()) {
            double top = c.getY() + level.getBlockState(c).getCollisionShape(level, c).max(Direction.Axis.Y);
            h.assertTrue(top > last, "the collision does not climb at " + c + ": top " + top + " after " + last);
            last = top;
        }
        Player walker = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 low = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 5, 11)));
        Vec3 high = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12, 7, 11)));
        double[] up = walk(walker, low.add(0, 0.05, 0), 1, high.x);
        h.assertTrue(up[2] >= high.x - 0.1 && Math.abs(up[1] - high.y) < 0.2,
                "the walker did not reach the high dock: x " + up[2] + ", y " + up[1] + " (dock " + high + ")");
        h.assertTrue(up[0] > low.y - 0.1, "the walker dropped to y " + up[0] + " on the way up");
        h.assertTrue(up[4] <= 0.6, "the walker rose " + up[4] + " in one move (more than a vanilla step)");
        double[] down = walk(walker, high.add(0, 0.05, 0), -1, low.x);
        h.assertTrue(down[2] <= low.x + 0.1 && Math.abs(down[1] - low.y) < 0.2,
                "the walker did not get down onto the low dock: x " + down[2] + ", y " + down[1] + " (dock " + low + ")");
        h.assertTrue(down[0] > low.y - 0.1, "the walker dropped to y " + down[0] + " on the way down");
        h.succeed();
    }

    /** A crew member paths across the same straight board, from the low dock up onto the high one. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void crewWalkAStraightBoardTwoUpOverSix(GameTestHelper h) {
        docksTwoApart(h);
        ServerLevel level = h.getLevel();
        BoardingPlanks.Laid laid = BoardingPlanks.lay(level, h.absolutePos(new BlockPos(3, 4, 11)), Direction.UP, Direction.EAST);
        assertOk(h, laid, 6, 2, PlankRun.Landing.RAMP);
        CrewMember c = StationContent.CREW_MEMBER.get().create(level);
        if (c == null) throw new AssertionError("no crew member");
        Vec3 from = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 5, 11)));
        Vec3 to = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12, 7, 11)));
        c.moveTo(from.x, from.y, from.z, -90.0f, 0.0f);
        c.setStationary(true);
        level.addFreshEntity(c);
        h.onEachTick(() -> {
            if (c.getNavigation().isDone()) {
                c.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
        });
        h.succeedWhen(() -> {
            double dock = h.absolutePos(new BlockPos(10, 7, 11)).getX();
            h.assertTrue(c.position().distanceTo(to) < 1.6 && c.getX() > dock + 0.5,
                    "the crew member is at " + c.position() + ", not on the high dock near " + to);
            h.assertTrue(Math.abs(c.getY() - to.y) < 0.2, "the crew member is not on the high dock");
            c.discard();
        });
    }

    /**
     * A gangway laid from a level pier onto A (in A's plot) still breaks when the ship heaves past {@code break_rise}:
     * lifted 3 blocks, the check reads a vertical drift of about 3 and breaks, and the run is gone with one plank dropped.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "every_tick")
    public static void gangwayFromAPierBreaksWhenTheShipHeavesPastBreakRise(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.CHECK_INTERVAL_TICKS, 1);
        Fixture a = oneShip(h);
        List<BlockPos> cells = new java.util.ArrayList<>();
        h.runAfterDelay(SETTLE, () -> {
            BlockPos edge = pier(h, rail(a), 3, 0);
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), edge, Direction.UP, Direction.WEST);
            assertOk(h, laid, 4, 0, PlankRun.Landing.LEVEL);
            h.assertTrue(a.ship().id().equals(laid.ship()), "the run should lie in A");
            cells.addAll(laid.cells());
            ShipBody ship = a.ship();
            Vec3 helm = Vec3.atCenterOf(a.helmPlot());
            ship.placeAt(helm, ship.toWorld(helm).add(0.0, 3.0, 0.0), ship.orientation());
            BoardingPlankBlockEntity.Check c = ((BoardingPlankBlockEntity) h.getLevel().getBlockEntity(cells.get(0))).check(h.getLevel());
            h.assertTrue(Math.abs(c.vertical()) > BoardingConfig.BREAK_RISE.get() && c.breaks(),
                    "after the heave: vertical " + c.vertical() + ", breaks " + c.breaks());
        });
        h.runAfterDelay(SETTLE + 20, () -> {
            assertGone(h, cells);
            int items = plankItems(h, cells.get(0));
            h.assertTrue(items == 1, "expected one dropped plank, found " + items);
            h.succeed();
        });
    }
}

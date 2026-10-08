package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Boarding planks between two hulls (BRD1, docs/design.md §8.3) in a real server. Two closed 5×4×5 plank hulls of
 * {@link DryHullGameTests} float in one basin: ship A at x 2..6, ship B three blocks east of it at x 10..14 (or eight
 * blocks, at x 15..19). Both have their deck (the hull's top layer) at relative y 8 and float level and still, so a
 * plank laid from the top of A's east wall runs four cells east at y 9 and its last cell rests on B's west wall. The
 * plank is laid {@link #SETTLE} ticks after assembly, when Sable has the ships' bounds and the bobbing has died down.
 * Ships are removed by {@code ShipTestCleanup}.
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

    /** Plot position (A's plot) of cell {@code i} of a run laid by {@link #layFromTop}. */
    private static BlockPos topCell(Fixture a, int i) {
        return eastGunwale(a).above().east(1 + i);
    }

    private static int plankItems(GameTestHelper h, Fixture a) {
        return plankItemEntities(h, a).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static java.util.Set<ItemEntity> plankItemEntities(GameTestHelper h, Fixture a) {
        ServerLevel level = h.getLevel();
        // the test's own structure only: the neighbouring tests of the batch lie right next to it
        AABB area = h.getBounds().expandTowards(0, 8, 0);
        // also where an item spawned in A's plot would be if Sable had not moved it out
        AABB plot = new AABB(eastGunwale(a)).inflate(6);
        // Sable's entity lookups also find world entities for a box in plot space, so one item can turn up in both
        // boxes: count each entity once
        java.util.Set<ItemEntity> seen = new java.util.HashSet<>();
        for (AABB box : List.of(area, plot)) {
            seen.addAll(level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive()
                    && e.getItem().is(BoardingContent.PLANK_ITEM.get())));
        }
        return seen;
    }

    private static void assertRunGone(GameTestHelper h, Fixture a, int length) {
        for (int i = 0; i < length; i++) {
            h.assertTrue(!h.getLevel().getBlockState(topCell(a, i)).is(BoardingContent.PLANK.get()),
                    "plank segment " + i + " is still there");
        }
    }

    private static void assertLaidOnB(GameTestHelper h, Ships s, BoardingPlanks.Laid laid) {
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome());
        h.assertTrue(laid.length() == 4, "expected a run of 4 cells, got " + laid.length());
        h.assertTrue(laid.landing() == PlankRun.Landing.LEVEL, "expected a level landing, got " + laid.landing());
        h.assertTrue(s.b().ship().id().equals(laid.farShip()), "the plank landed on " + laid.farShip() + ", not on B");
    }

    // ------------------------------------------------------------------ tests

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
            BoardingPlankBlock block = BoardingContent.PLANK.get();
            for (int i = 0; i < 4; i++) {
                BlockPos cell = topCell(s.a(), i);
                BlockState st = level.getBlockState(cell);
                h.assertTrue(st.is(block) && st.getValue(BoardingPlankBlock.SEGMENT) == i
                        && st.getValue(BoardingPlankBlock.FACING) == Direction.EAST
                        && st.getValue(BoardingPlankBlock.TIP) == (i == 3), "segment " + i + " is " + st);
                ShipBody owner = SableShips.containing(level, cell);
                h.assertTrue(owner != null && owner.id().equals(a.id()), "segment " + i + " is not in A's plot");
                h.assertTrue(!st.getCollisionShape(level, cell).isEmpty(), "segment " + i + " has no collision");
            }
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
            h.assertTrue(plankItems(h, s.a()) == 0, "a plank dropped while the ships lay still");
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

    /** From the side face of A's gunwale the run lies a block lower and ends against B's gunwale (B one block higher). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankFromTheSideEndsAgainstAHigherDeck(GameTestHelper h) {
        Ships s = twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), eastGunwale(s.a()), Direction.EAST, Direction.NORTH);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the plank was refused: " + laid.outcome());
            h.assertTrue(laid.length() == 3 && laid.landing() == PlankRun.Landing.STEP_UP,
                    "expected 3 cells stepping up, got " + laid.length() + " " + laid.landing());
            h.assertTrue(eastGunwale(s.a()).east().equals(laid.base()), "segment 0 is at " + laid.base());
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
            int items = plankItems(h, s.a());
            h.assertTrue(items == 1, "expected one dropped plank, found " + items + ": "
                    + plankItemEntities(h, s.a()).stream().map(e -> e.getId() + "@" + e.position()).toList());
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
            int items = plankItems(h, s.a());
            h.assertTrue(items == 1, "expected one dropped plank, found " + items + ": "
                    + plankItemEntities(h, s.a()).stream().map(e -> e.getId() + "@" + e.position()).toList());
            h.succeed();
        });
    }

    /** With eight blocks of water between the hulls nothing is in reach: refused, nothing placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankIsRefusedWhenTheHullsAreFarApart(GameTestHelper h) {
        Ships s = twoShips(h, 8);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = layFromTop(h, s.a());
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.NO_DECK, "expected NO_DECK, got " + laid.outcome());
            assertRunGone(h, s.a(), 4);
            h.assertTrue(BoardingPlankItem.messageKey(laid.outcome()).equals(BoardingPlankItem.KEY_NO_DECK), "wrong message");
            h.succeed();
        });
    }

    /** A plank cannot be laid from a block that is not part of a ship. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void plankNeedsAShip(GameTestHelper h) {
        twoShips(h, 3);
        h.runAfterDelay(SETTLE, () -> {
            BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), h.absolutePos(new BlockPos(8, 8, 0)), Direction.UP, Direction.SOUTH);
            h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.NOT_ON_SHIP, "expected NOT_ON_SHIP, got " + laid.outcome());
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
}

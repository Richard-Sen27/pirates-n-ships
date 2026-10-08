package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tall poles (VIS1a, docs/design.md §4.7 "Tall poles"): a shaft forwards its use to the head, a flagpole stacked on a
 * head takes over its flag and pending action, breaking the head or a shaft, the height limit, the
 * {@code flags.stacked_poles} toggle, and a stacked pole on a ship (allegiance, assembly and disassembly).
 * <p>Every test pins the config it relies on with {@link ConfigOverrides}: the batch {@code ..._stacked} sets stacking
 * on, a limit of 4 and a 20-tick delay in every test (the same values, so the tests may share it); the toggle-off test
 * has its own batch.
 */
public final class FlagStackGameTests {

    private static final String BATCH = "pirates_n_ships_config_flags_stacked";
    private static final String BATCH_OFF = "pirates_n_ships_config_flags_stacked_off";
    private static final int DELAY = 20;
    private static final int MAX = 4;

    private FlagStackGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FlagStackGameTests.class);
    }

    // --- helpers ---

    private static void stackedConfig(GameTestHelper helper) {
        ConfigOverrides.during(helper, FlagConfig.STACKED_POLES, true);
        ConfigOverrides.during(helper, FlagConfig.MAX_POLE_HEIGHT, MAX);
        ConfigOverrides.during(helper, FlagConfig.HOIST_DELAY_TICKS, DELAY);
    }

    /**
     * Flagpoles at {@code x, y0..y1, z} without a player (as a command would place them), with the parts their first
     * tick would put.
     */
    private static void column(GameTestHelper helper, int x, int y0, int y1, int z) {
        column(helper, x, y0, y1, z, true);
    }

    private static void column(GameTestHelper helper, int x, int y0, int y1, int z, boolean fixParts) {
        for (int y = y0; y <= y1; y++) helper.setBlock(new BlockPos(x, y, z), ShipDecor.FLAGPOLE.get());
        if (!fixParts) return;
        for (int y = y0; y <= y1; y++) {
            BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
            BlockState s = helper.getLevel().getBlockState(abs);
            helper.getLevel().setBlock(abs, s.setValue(FlagpoleBlock.PART, FlagpoleBlock.partAt(helper.getLevel(), abs)), 3);
        }
    }

    /** A player places a flagpole on the top face of the block at {@code onto} (as vanilla does: {@code ItemStack#useOn}). */
    private static InteractionResult placeOnTop(GameTestHelper helper, Player player, BlockPos onto) {
        ItemStack held = new ItemStack(ShipDecor.FLAGPOLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = helper.absolutePos(onto);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Uses the pole the way vanilla does: {@code useItemOn}, then {@code useWithoutItem} if that passes. */
    private static void use(GameTestHelper helper, Player player, BlockPos rel, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = helper.getLevel().getBlockState(abs);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        ItemInteractionResult r = state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) state.useWithoutItem(helper.getLevel(), player, hit);
    }

    private static FlagpoleBlockEntity be(GameTestHelper helper, BlockPos rel) {
        if (!(helper.getBlockEntity(rel) instanceof FlagpoleBlockEntity be)) throw new GameTestAssertException("no flagpole at " + rel);
        return be;
    }

    private static void assertReading(GameTestHelper helper, BlockPos rel, FlagReading expected) {
        FlagReading actual = be(helper, rel).reading();
        helper.assertTrue(actual.equals(expected), "pole " + rel + " shows " + actual + ", expected " + expected);
        helper.assertBlockProperty(rel, FlagpoleBlock.FLAG, expected.shown());
    }

    private static void assertEmpty(GameTestHelper helper, BlockPos rel) {
        FlagpoleState s = be(helper, rel).state();
        helper.assertTrue(s.equals(FlagpoleState.EMPTY), "shaft " + rel + " holds " + s);
        helper.assertBlockProperty(rel, FlagpoleBlock.FLAG, FlagKind.NONE);
    }

    private static void assertPart(GameTestHelper helper, BlockPos rel, FlagpolePart part) {
        helper.assertBlockProperty(rel, FlagpoleBlock.PART, part);
    }

    // --- tests ---

    /** Every use of a shaft goes to the head: a flag item hoists there, an empty hand strikes there. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH)
    public static void useOfAShaftWorksTheHead(GameTestHelper helper) {
        stackedConfig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos foot = new BlockPos(1, 1, 1), middle = new BlockPos(1, 2, 1), head = new BlockPos(1, 3, 1);
        column(helper, 1, 1, 3, 1, false); // the first tick puts the parts (checked below)
        use(helper, player, foot, new ItemStack(Flags.NAVY_FLAG.get()));
        helper.assertTrue(be(helper, head).state().pending().map(p -> p.kind() == FlagKind.NAVY).orElse(false),
                "the hoist did not start at the head: " + be(helper, head).state());
        assertEmpty(helper, foot);
        helper.startSequence()
                .thenExecuteAfter(DELAY + 2, () -> {
                    assertReading(helper, head, FlagReading.flying(FlagKind.NAVY));
                    assertEmpty(helper, foot);
                    assertEmpty(helper, middle);
                    assertPart(helper, foot, FlagpolePart.BOTTOM);
                    assertPart(helper, middle, FlagpolePart.MIDDLE);
                    assertPart(helper, head, FlagpolePart.TOP);
                    use(helper, player, middle, ItemStack.EMPTY);
                })
                .thenExecuteAfter(DELAY + 2, () -> {
                    assertReading(helper, head, FlagReading.struck(FlagKind.NAVY));
                    assertEmpty(helper, middle);
                    player.setShiftKeyDown(true);
                    use(helper, player, foot, ItemStack.EMPTY);
                    player.setShiftKeyDown(false);
                })
                .thenExecuteAfter(DELAY + 2, () -> {
                    assertReading(helper, head, FlagReading.NO_FLAG);
                    helper.assertTrue(player.getInventory().countItem(Flags.NAVY_FLAG.get()) == 1, "the flag did not come back to the player");
                })
                .thenSucceed();
    }

    /**
     * A flagpole placed on a flying head takes the flag up with it (struck state, who hoisted it); the old head becomes
     * an empty shaft and drops nothing, and the event is reported at the new head.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void stackingOnAFlyingHeadMovesTheFlagUp(GameTestHelper helper) {
        stackedConfig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos foot = new BlockPos(1, 1, 1), oldHead = new BlockPos(1, 2, 1), newHead = new BlockPos(1, 3, 1);
        column(helper, 1, 1, 2, 1);
        UUID hoister = UUID.randomUUID();
        be(helper, oldHead).commandSet(FlagKind.JOLLY_ROGER, false, hoister);
        be(helper, oldHead).commandStrike(true);
        FlagpoleState before = be(helper, oldHead).state();
        List<FlagpoleEvents.FlagChange> seen = new ArrayList<>();
        FlagpoleEvents.Listener listener = seen::add;
        FlagpoleEvents.register(listener);
        try {
            helper.assertTrue(placeOnTop(helper, player, oldHead).consumesAction(), "placing on the head failed");
        } finally {
            FlagpoleEvents.unregister(listener);
        }
        helper.assertTrue(be(helper, newHead).state().equals(before), "new head holds " + be(helper, newHead).state() + ", expected " + before);
        assertReading(helper, newHead, FlagReading.struck(FlagKind.JOLLY_ROGER));
        assertEmpty(helper, oldHead);
        helper.assertItemEntityNotPresent(Flags.JOLLY_ROGER_FLAG.get(), oldHead, 3.0);
        assertPart(helper, foot, FlagpolePart.BOTTOM);
        assertPart(helper, oldHead, FlagpolePart.MIDDLE);
        assertPart(helper, newHead, FlagpolePart.TOP);
        BlockPos abs = helper.absolutePos(newHead);
        List<FlagpoleEvents.FlagChange> moved = seen.stream().filter(c -> c.pos().equals(abs)).toList();
        helper.assertTrue(moved.size() == 1 && moved.get(0).cause() == FlagpoleMachine.Cause.MOVED
                        && moved.get(0).after().equals(FlagReading.struck(FlagKind.JOLLY_ROGER)) && player.getUUID().equals(moved.get(0).actor()),
                "expected one MOVED event at the new head, got " + seen);
        helper.assertTrue(seen.stream().noneMatch(c -> c.pos().equals(helper.absolutePos(oldHead))), "an event at the old head: " + seen);
        helper.succeed();
    }

    /** A pending hoist moves with the head and completes there, with its actor; the old head drops nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH)
    public static void aPendingHoistMovesWithTheHeadAndCompletesThere(GameTestHelper helper) {
        stackedConfig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos oldHead = new BlockPos(1, 1, 1), newHead = new BlockPos(1, 2, 1);
        column(helper, 1, 1, 1, 1);
        be(helper, oldHead).commandSet(FlagKind.NAVY, false, null);
        use(helper, player, oldHead, new ItemStack(Flags.MERCHANT_FLAG.get()));
        FlagpoleState.Pending pending = be(helper, oldHead).state().pending().orElseThrow(() -> new GameTestAssertException("no hoist pending"));
        helper.runAfterDelay(DELAY / 2, () -> {
            helper.assertTrue(placeOnTop(helper, player, oldHead).consumesAction(), "placing on the head failed");
            FlagpoleState moved = be(helper, newHead).state();
            helper.assertTrue(moved.pending().equals(java.util.Optional.of(pending)) && moved.kind() == FlagKind.NAVY,
                    "the pending hoist did not move up: " + moved);
            assertEmpty(helper, oldHead);
            helper.startSequence()
                    .thenExecuteAfter(DELAY / 2 + 3, () -> {
                        assertReading(helper, newHead, FlagReading.flying(FlagKind.MERCHANT));
                        helper.assertTrue(be(helper, newHead).state().hoistedBy().equals(java.util.Optional.of(player.getUUID())), "hoisted by whom?");
                        assertEmpty(helper, oldHead);
                        helper.assertTrue(player.getInventory().countItem(Flags.NAVY_FLAG.get()) == 1, "the replaced navy flag did not come back");
                        helper.assertItemEntityNotPresent(Flags.MERCHANT_FLAG.get(), oldHead, 3.0);
                        helper.assertItemEntityNotPresent(Flags.NAVY_FLAG.get(), oldHead, 3.0);
                    })
                    .thenSucceed();
        });
    }

    /** Breaking the head drops its flag; the block below becomes an empty head that works on its own. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH)
    public static void breakingTheHeadDropsTheFlagAndTheShaftBelowLeads(GameTestHelper helper) {
        stackedConfig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos foot = new BlockPos(1, 1, 1), middle = new BlockPos(1, 2, 1), head = new BlockPos(1, 3, 1);
        column(helper, 1, 1, 3, 1);
        be(helper, head).commandSet(FlagKind.JOLLY_ROGER, false, null);
        helper.destroyBlock(head);
        helper.assertItemEntityPresent(Flags.JOLLY_ROGER_FLAG.get(), head, 3.0);
        assertPart(helper, middle, FlagpolePart.TOP);
        assertEmpty(helper, middle);
        helper.assertTrue(FlagpoleRun.head(helper.getLevel(), helper.absolutePos(foot)).equals(helper.absolutePos(middle)), "the middle is not the head");
        use(helper, player, foot, new ItemStack(Flags.NAVY_FLAG.get()));
        helper.runAfterDelay(DELAY + 2, () -> {
            assertReading(helper, middle, FlagReading.flying(FlagKind.NAVY));
            assertEmpty(helper, foot);
            helper.succeed();
        });
    }

    /** Breaking a shaft splits the pole: the run above keeps its head and flag, the run below gets its own head. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void breakingAShaftSplitsThePole(GameTestHelper helper) {
        stackedConfig(helper);
        column(helper, 1, 1, 4, 1);
        BlockPos y1 = new BlockPos(1, 1, 1), y2 = new BlockPos(1, 2, 1), y3 = new BlockPos(1, 3, 1), y4 = new BlockPos(1, 4, 1);
        be(helper, y4).commandSet(FlagKind.NAVY, false, null);
        helper.destroyBlock(y2);
        assertReading(helper, y4, FlagReading.flying(FlagKind.NAVY));
        helper.assertItemEntityNotPresent(Flags.NAVY_FLAG.get(), y2, 3.0);
        assertPart(helper, y1, FlagpolePart.SINGLE);
        assertPart(helper, y3, FlagpolePart.BOTTOM);
        assertPart(helper, y4, FlagpolePart.TOP);
        helper.assertTrue(FlagpoleRun.head(helper.getLevel(), helper.absolutePos(y3)).equals(helper.absolutePos(y4)), "y3 lost its head");
        helper.assertTrue(FlagpoleRun.head(helper.getLevel(), helper.absolutePos(y1)).equals(helper.absolutePos(y1)), "y1 is not its own head");
        helper.assertTrue(FlagpoleRun.height(helper.getLevel(), helper.absolutePos(y4)) == 2, "the upper pole is not two blocks");
        helper.succeed();
    }

    /** A pole may grow to {@code flags.max_pole_height}; one more block is refused and stays in the hand. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void theHeightLimitRefusesATallerPole(GameTestHelper helper) {
        stackedConfig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        for (int y = 0; y < MAX; y++) {
            helper.assertTrue(placeOnTop(helper, player, new BlockPos(1, y, 1)).consumesAction(), "segment " + (y + 1) + " was refused");
        }
        ItemStack held = new ItemStack(ShipDecor.FLAGPOLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos top = new BlockPos(1, MAX, 1);
        BlockPos abs = helper.absolutePos(top);
        InteractionResult r = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
        helper.assertTrue(!r.consumesAction(), "a pole of " + (MAX + 1) + " blocks was placed: " + r);
        helper.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), top.above());
        helper.assertTrue(held.getCount() == 1, "the refused flagpole was used up");
        // joining two poles counts both: a gap between 2 and 2 blocks may be filled only up to the limit
        column(helper, 4, 1, 2, 4);
        column(helper, 4, 4, 5, 4);
        held = new ItemStack(ShipDecor.FLAGPOLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos below = helper.absolutePos(new BlockPos(4, 2, 4));
        r = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        helper.assertTrue(!r.consumesAction(), "joining 2 + 1 + 2 blocks was allowed with a limit of " + MAX);
        helper.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), new BlockPos(4, 3, 4));
        helper.succeed();
    }

    /**
     * With {@code flags.stacked_poles} off every block is its own pole: no forwarding, no moving of the flag, any block
     * may fly a flag, no height limit; the parts still follow the neighbours.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH_OFF)
    public static void withStackingOffEveryBlockIsItsOwnPole(GameTestHelper helper) {
        ConfigOverrides.during(helper, FlagConfig.STACKED_POLES, false);
        ConfigOverrides.during(helper, FlagConfig.MAX_POLE_HEIGHT, 2);
        ConfigOverrides.during(helper, FlagConfig.HOIST_DELAY_TICKS, DELAY);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos foot = new BlockPos(1, 1, 1), top = new BlockPos(1, 2, 1);
        column(helper, 1, 1, 1, 1);
        be(helper, foot).commandSet(FlagKind.NAVY, false, null);
        helper.assertTrue(placeOnTop(helper, player, foot).consumesAction(), "placing on the pole failed");
        helper.assertTrue(placeOnTop(helper, player, top).consumesAction(), "the height limit applied with stacking off");
        assertReading(helper, foot, FlagReading.flying(FlagKind.NAVY));
        assertEmpty(helper, top);
        assertPart(helper, foot, FlagpolePart.BOTTOM);
        assertPart(helper, top, FlagpolePart.MIDDLE);
        use(helper, player, top, new ItemStack(Flags.MERCHANT_FLAG.get()));
        use(helper, player, foot, ItemStack.EMPTY);
        helper.runAfterDelay(DELAY + 2, () -> {
            assertReading(helper, top, FlagReading.flying(FlagKind.MERCHANT));
            assertReading(helper, foot, FlagReading.struck(FlagKind.NAVY));
            helper.succeed();
        });
    }

    /**
     * A ship with a stacked pole: the allegiance reads the flag at the head through {@link FlagSelection} (a lower lone
     * pole loses to it), assembly moves the pole with the flag at the head and no drop, and so does disassembly.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 400, batch = BATCH)
    public static void aStackedPoleSurvivesAssemblyWithItsFlagAtTheHead(GameTestHelper helper) {
        stackedConfig(helper);
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
        }
        BlockPos helm = new BlockPos(4, 3, 4);
        helper.setBlock(helm, AssemblyContent.HELM.get());
        column(helper, 3, 3, 5, 3);
        be(helper, new BlockPos(3, 5, 3)).commandSet(FlagKind.JOLLY_ROGER, false, null);
        helper.setBlock(new BlockPos(5, 3, 5), ShipDecor.FLAGPOLE.get());
        be(helper, new BlockPos(5, 3, 5)).commandSet(FlagKind.NAVY, false, null);

        AssemblyResult r = ShipTestCleanup.assemble(helper, helm);
        helper.assertTrue(r.success() && r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(helper.getLevel(), r.shipId());
        helper.assertTrue(ship != null, "no sub-level for " + r.shipId());
        helper.assertTrue(ShipAllegiance.read(ship).equals(FlagReading.flying(FlagKind.JOLLY_ROGER)),
                "the ship shows " + ShipAllegiance.read(ship) + ", expected the Jolly Roger at the stacked pole's head");
        assertColumnOnShip(helper, ship);
        helper.assertItemEntityNotPresent(Flags.JOLLY_ROGER_FLAG.get(), new BlockPos(3, 4, 3), 4.0);
        UUID id = ship.id();
        AtomicReference<BlockPos> worldHead = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ShipBody s = SableShips.byId(helper.getLevel(), id);
                    helper.assertTrue(s != null, "ship vanished before disassembly");
                    BlockPos plotHelm = s.plotBlocks().stream().filter(p -> helper.getLevel().getBlockState(p).is(AssemblyContent.HELM.get()))
                            .findFirst().orElseThrow(() -> new GameTestAssertException("helm not in the plot"));
                    AssemblyResult d = ShipAssembler.disassemble(s, plotHelm, null);
                    if (!d.success()) throw new GameTestAssertException("not yet disassembled: " + d.outcome());
                })
                .thenExecute(() -> {
                    // The ship may have settled a little: look for the stacked pole's head within a block of its old spot
                    for (BlockPos d : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
                        BlockPos p = new BlockPos(3, 5, 3).offset(d);
                        if (helper.getBlockState(p).is(ShipDecor.FLAGPOLE.get()) && helper.getBlockState(p.below()).is(ShipDecor.FLAGPOLE.get())) {
                            worldHead.set(p.immutable());
                        }
                    }
                    helper.assertTrue(worldHead.get() != null, "the stacked pole is not back near its spot");
                    BlockPos h = worldHead.get();
                    assertReading(helper, h, FlagReading.flying(FlagKind.JOLLY_ROGER));
                    assertEmpty(helper, h.below());
                    assertEmpty(helper, h.below(2));
                    helper.assertItemEntityNotPresent(Flags.JOLLY_ROGER_FLAG.get(), h, 4.0);
                })
                .thenSucceed();
    }

    /** In the plot: three flagpoles in a column, the flag at the top, the shafts empty. */
    private static void assertColumnOnShip(GameTestHelper helper, ShipBody ship) {
        BlockPos plotHead = null;
        for (BlockPos p : ship.plotBlocks()) {
            if (helper.getLevel().getBlockEntity(p) instanceof FlagpoleBlockEntity be && be.reading().kind() == FlagKind.JOLLY_ROGER) plotHead = p.immutable();
        }
        helper.assertTrue(plotHead != null, "the Jolly Roger was not assembled into the ship");
        helper.assertTrue(!FlagpoleRun.isPole(helper.getLevel(), plotHead.above()), "the flag is not at the top of the pole in the plot");
        for (int i = 1; i <= 2; i++) {
            helper.assertTrue(helper.getLevel().getBlockEntity(plotHead.below(i)) instanceof FlagpoleBlockEntity be && be.state().equals(FlagpoleState.EMPTY),
                    "shaft " + i + " below the head is missing or holds something in the plot");
        }
        helper.assertTrue(FlagpoleRun.height(helper.getLevel(), plotHead) == 3, "the pole is not three blocks in the plot");
    }
}

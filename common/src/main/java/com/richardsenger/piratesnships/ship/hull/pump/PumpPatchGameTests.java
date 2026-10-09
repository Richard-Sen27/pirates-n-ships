package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.OutsidePort;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bilge pump and hull patch on real ships (docs/design.md §4.5, G4). The ship is the closed 5×4×5 plank hull of
 * {@link DryHullGameTests} afloat in its basin (hold 3×2×3 = 18 cells, compartment 0), with a bilge pump on the hold
 * floor at hold (1, −3, 1) and a second one on the deck at hold (−1, 0, −1), two cells above the hold.
 */
public final class PumpPatchGameTests {

    private PumpPatchGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PumpPatchGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, BlockPos holdPump, BlockPos deckPump) {
        double water() {
            return f.runtime().simulation().totalVolume();
        }
    }

    private static Ship ship(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(12, 6, 12), HullRepairContent.BILGE_PUMP.get());
        h.setBlock(new BlockPos(10, 9, 10), HullRepairContent.BILGE_PUMP.get());
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos hold = f.hold(1, -3, 1), deck = f.hold(-1, 0, -1);
        ServerLevel level = h.getLevel();
        h.assertTrue(level.getBlockState(hold).is(HullRepairContent.BILGE_PUMP.get())
                && level.getBlockState(deck).is(HullRepairContent.BILGE_PUMP.get()), "the pumps are not in the plot");
        h.assertTrue(f.runtime().pumps().contains(hold) && f.runtime().pumps().contains(deck),
                "the hull runtime did not find the pumps: " + f.runtime().pumps().positions());
        HullAnalysis a = f.runtime().simulation().analysis();
        h.assertTrue(a.compartments().size() == 1 && a.compartments().get(0).volume() == 18,
                "the pump must not take room from the hold: " + a.compartments());
        return new Ship(f, hold, deck);
    }

    private static int gridIndex(Fixture f, BlockPos plot) {
        HullGrid g = f.runtime().simulation().analysis().grid();
        return g.index(plot.getX() - g.originX(), plot.getY() - g.originY(), plot.getZ() - g.originZ());
    }

    /** Uses {@code stack} (held by {@code player}) on the top face of plot block {@code below}, as a right-click would. */
    private static InteractionResult useOnTop(Player player, ItemStack stack, BlockPos below) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    // ------------------------------------------------------------------ pump

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void pumpDrainsAtThePumpRateWhileUsed(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        double perTick = FloodingConfig.params().pumpPerTick();
        s.f().runtime().simulation().setVolume(0, 10);
        double[] seen = new double[4];
        // one use every 4 ticks, as holding the use key does, from tick 10 to tick 50
        for (int t = 10; t <= 50; t += 4) {
            int tick = t;
            h.runAfterDelay(t, () -> {
                BilgePumps.Use use = BilgePumps.operate(level, s.holdPump(), player);
                h.assertTrue(use.outcome() == BilgePumps.Outcome.PUMPING, "the pump did not pump: " + use);
                if (tick == 14) seen[0] = s.water();
                if (tick == 46) seen[1] = s.water();
            });
        }
        h.runAfterDelay(66, () -> seen[2] = s.water());
        h.runAfterDelay(100, () -> {
            seen[3] = s.water();
            double drop = seen[0] - seen[1], expected = 32 * perTick;
            h.assertTrue(Math.abs(drop - expected) < 0.5 * perTick,
                    "over 32 ticks of pumping " + drop + " blocks went, expected " + expected);
            h.assertTrue(seen[2] < seen[1], "no water went after tick 46");
            h.assertTrue(seen[2] == seen[3], "the pump kept working after the player stopped: " + seen[2] + " -> " + seen[3]);
            h.assertTrue(seen[3] > 7, "more water went than pumped: " + seen[3]);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void pumpOnDeckReachesTheHoldAndReportsADryBilge(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        h.runAfterDelay(5, () -> {
            h.assertTrue(s.f().runtime().pumpIntake(s.deckPump()) == 0, "the deck pump does not reach the hold");
            BilgePumps.Use dry = BilgePumps.operate(level, s.deckPump(), null);
            h.assertTrue(dry.outcome() == BilgePumps.Outcome.DRY, "a dry hold was not reported: " + dry);
            h.assertFalse(s.f().runtime().pumps().usedAt(s.deckPump(), level.getGameTime()), "a dry pump started working");
            s.f().runtime().simulation().setVolume(0, 2);
            BilgePumps.Use wet = BilgePumps.operate(level, s.deckPump(), null);
            h.assertTrue(wet.outcome() == BilgePumps.Outcome.PUMPING, "the deck pump did not pump the hold: " + wet);
            h.assertTrue(BilgePumps.operate(level, h.absolutePos(new BlockPos(3, 9, 3)), null).outcome()
                    == BilgePumps.Outcome.NOT_ON_SHIP, "a position off the ship pumped");
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(s.water() < 2, "the deck pump left the hold full: " + s.water());
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260)
    public static void crewMemberAtThePumpDrainsTheHold(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        StationRef ref = new StationRef(s.f().ship().id(), s.holdPump());
        UUID crew = UUID.randomUUID();
        s.f().runtime().simulation().setVolume(0, 4);
        h.runAfterDelay(5, () -> {
            h.assertTrue(Stations.occupy(level, ref, new StationState.Occupant(crew, false)) == StationState.OccupyResult.OCCUPIED,
                    "the pump is not a station");
            Stations.OrderResult r = Stations.order(level, ref, PumpOrder.PUMP);
            h.assertTrue(r == Stations.OrderResult.STARTED, "the pumping order did not start: " + r);
        });
        h.runAfterDelay(45, () -> h.assertTrue(s.water() < 4 - 30 * FloodingConfig.params().pumpPerTick(),
                "the crew member did not pump: " + s.water()));
        h.runAfterDelay(46, () -> h.succeedWhen(() -> {
            h.assertTrue(s.water() <= PumpIntake.DRY, "water left: " + s.water());
            Stations.OrderResult again = Stations.order(level, ref, PumpOrder.PUMP);
            h.assertTrue(again == Stations.OrderResult.NOTHING_TO_DO, "a dry bilge still gave work: " + again);
            Stations.release(ref, crew);
        }));
    }

    // ------------------------------------------------------------------ handle sync (PMP1)

    private static BilgePumpBlockEntity pumpEntity(GameTestHelper h, BlockPos plot) {
        if (h.getLevel().getBlockEntity(plot) instanceof BilgePumpBlockEntity be) {
            return be;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("no pump block entity at " + plot);
    }

    /** A client's copy of the pump as a chunk load creates it: a new block entity loaded from the update tag. */
    private static boolean freshClientCopyPumping(ServerLevel level, BilgePumpBlockEntity be) {
        BilgePumpBlockEntity copy = new BilgePumpBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(be.getUpdateTag(level.registryAccess()), level.registryAccess());
        return copy.pumping();
    }

    /** A client's existing copy updated by the block entity data packet a block update sends. */
    private static boolean packetCopyPumping(ServerLevel level, BilgePumpBlockEntity be) {
        BilgePumpBlockEntity copy = new BilgePumpBlockEntity(be.getBlockPos(), be.getBlockState());
        ClientboundBlockEntityDataPacket packet = (ClientboundBlockEntityDataPacket) be.getUpdatePacket();
        copy.loadWithComponents(packet.getTag(), level.registryAccess());
        return copy.pumping();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void pumpingFlagReachesTheClientWhilePlayerPumps(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        s.f().runtime().simulation().setVolume(0, 10);
        BilgePumpBlockEntity be = pumpEntity(h, s.holdPump());
        BilgePumpBlockEntity deck = pumpEntity(h, s.deckPump());
        h.runAfterDelay(5, () -> h.assertFalse(freshClientCopyPumping(level, be), "an idle pump reads as pumping"));
        // one use every 4 ticks, as holding the use key does, from tick 10 to tick 50
        for (int t = 10; t <= 50; t += 4) {
            h.runAfterDelay(t, () -> h.assertTrue(BilgePumps.operate(level, s.holdPump(), player).outcome()
                    == BilgePumps.Outcome.PUMPING, "the pump did not pump"));
        }
        h.runAfterDelay(13, () -> {
            h.assertTrue(be.pumping(), "the flag is not on right after the first strokes");
            h.assertTrue(freshClientCopyPumping(level, be), "a freshly loaded client copy does not see the pumping");
            h.assertTrue(packetCopyPumping(level, be), "the block update does not carry the pumping");
            h.assertFalse(deck.pumping(), "the idle deck pump reads as pumping");
        });
        h.runAfterDelay(52, () -> {
            h.assertTrue(freshClientCopyPumping(level, be), "the flag dropped while the player kept pumping");
            h.assertTrue(be.activity().syncs() == 1, "more than one update while pumping went on: " + be.activity().syncs());
        });
        h.runAfterDelay(90, () -> {
            h.assertFalse(be.pumping(), "the flag stayed on after the player stopped");
            h.assertFalse(freshClientCopyPumping(level, be), "a fresh client copy still sees pumping after it stopped");
            h.assertFalse(packetCopyPumping(level, be), "the block update still says pumping after it stopped");
            h.assertTrue(be.activity().syncs() == 2, "expected one update to start and one to stop: " + be.activity().syncs());
            h.assertTrue(deck.activity().syncs() == 0, "the idle deck pump sent updates");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260)
    public static void pumpingFlagFollowsACrewMemberAtThePump(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        StationRef ref = new StationRef(s.f().ship().id(), s.holdPump());
        UUID crew = UUID.randomUUID();
        s.f().runtime().simulation().setVolume(0, 4);
        BilgePumpBlockEntity be = pumpEntity(h, s.holdPump());
        h.runAfterDelay(5, () -> {
            h.assertTrue(Stations.occupy(level, ref, new StationState.Occupant(crew, false)) == StationState.OccupyResult.OCCUPIED,
                    "the pump is not a station");
            h.assertTrue(Stations.order(level, ref, PumpOrder.PUMP) == Stations.OrderResult.STARTED, "no pumping order");
        });
        h.runAfterDelay(25, () -> h.assertTrue(freshClientCopyPumping(level, be), "the crew's pumping does not reach the client"));
        h.runAfterDelay(26, () -> h.succeedWhen(() -> {
            h.assertTrue(s.water() <= PumpIntake.DRY, "water left: " + s.water());
            h.assertFalse(freshClientCopyPumping(level, be), "the flag stayed on after the bilge ran dry");
            h.assertTrue(be.activity().syncs() == 2, "expected one update to start and one to stop: " + be.activity().syncs());
            Stations.release(ref, crew);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = "pirates_n_ships_config_hull_pump_disabled")
    public static void disabledPumpIsInert(GameTestHelper h) {
        ConfigOverrides.during(h, FloodingConfig.PUMP_ENABLED, false);
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        s.f().runtime().simulation().setVolume(0, 5);
        for (int t = 10; t <= 30; t += 4) {
            h.runAfterDelay(t, () -> {
                BilgePumps.Use use = BilgePumps.operate(level, s.holdPump(), player);
                h.assertTrue(use.outcome() == BilgePumps.Outcome.DISABLED, "a disabled pump answered " + use);
            });
        }
        h.runAfterDelay(40, () -> {
            h.assertTrue(s.water() == 5, "a disabled pump removed water: " + s.water());
            StationRef ref = new StationRef(s.f().ship().id(), s.holdPump());
            UUID crew = UUID.randomUUID();
            Stations.occupy(level, ref, new StationState.Occupant(crew, false));
            Stations.OrderResult r = Stations.order(level, ref, PumpOrder.PUMP);
            Stations.release(ref, crew);
            h.assertTrue(r == Stations.OrderResult.NOT_APPLICABLE, "a disabled pump took a crew order: " + r);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ patch

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void patchRefusesAPlaceThatIsNoBreach(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos floor = s.f().hold(0, -4, 0), air = s.f().hold(0, -3, 0);
        h.runAfterDelay(5, () -> {
            ItemStack stack = new ItemStack(HullRepairContent.HULL_PATCH.get(), 4);
            InteractionResult r = useOnTop(player, stack, floor);
            h.assertFalse(r.consumesAction(), "the patch was used on a sound hull: " + r);
            h.assertTrue(level.getBlockState(air).isAir(), "a patch block was placed in the hold");
            h.assertTrue(stack.getCount() == 4, "a patch was used up");
            h.assertTrue(HullPatchItem.check(level, air, true) == PatchTarget.Result.NOT_A_BREACH, "the hold is a breach?");
            h.assertTrue(HullPatchItem.check(level, h.absolutePos(new BlockPos(3, 9, 3)), true) == PatchTarget.Result.NOT_ON_SHIP,
                    "a place off the ship is patchable");
            h.succeed();
        });
    }

    /**
     * A hull block below the waterline is removed: the hold floods through the breach. A patch closes it (the inflow
     * stops at once, and the re-analysis has plain hull there), and the pump then empties the hold. Ten times the
     * default inflow and pump rates keep the test short.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = "pirates_n_ships_config_hull_patch")
    public static void patchClosesABreachAndThePumpEmptiesTheHold(GameTestHelper h) {
        ConfigOverrides.during(h, FloodingConfig.INFLOW_RATE, 10.0);
        ConfigOverrides.during(h, FloodingConfig.PUMP_RATE, 10.0);
        Ship s = ship(h);
        Fixture f = s.f();
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos wall = f.hold(-2, -3, 0), below = f.hold(-2, -4, 0);
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            h.assertTrue(f.runtime().breaches().contains(wall), "the removed hull block is not a breach");
        });
        int[] phase = {0};
        long[] patchedAt = {0};
        ItemStack stack = new ItemStack(HullRepairContent.HULL_PATCH.get(), 4);
        h.onEachTick(() -> {
            long now = h.getTick();
            switch (phase[0]) {
                case 0 -> { // flooding through the breach
                    if (now > 20 && s.water() > 3) {
                        InteractionResult r = useOnTop(player, stack, below);
                        h.assertTrue(r.consumesAction(), "the patch was refused: " + r);
                        h.assertTrue(level.getBlockState(wall).is(HullRepairContent.HULL_PATCH_BLOCK.get()), "no patch block in the breach");
                        h.assertTrue(stack.getCount() == 3, "the patch was not used up: " + stack.getCount());
                        h.assertFalse(f.runtime().breaches().contains(wall), "the patched breach is still tracked");
                        patchedAt[0] = now;
                        phase[0] = 1;
                    }
                }
                case 1 -> { // the inflow stops at once; wait for the re-analysis to see plain hull
                    if (now >= patchedAt[0] + 2) {
                        h.assertTrue(f.runtime().lastReport() != null && f.runtime().lastReport().inflow() == 0,
                                "water still comes in after the patch");
                    }
                    HullAnalysis a = f.runtime().simulation().analysis();
                    if (!f.runtime().isAnalysing() && a.grid().kind(gridIndex(f, wall)) == CellKind.SOLID) {
                        for (Compartment c : a.compartments()) {
                            for (OutsidePort p : c.ports()) {
                                h.assertTrue(p.openingCell() != gridIndex(f, wall), "the analysis still has the breach: " + c.ports());
                            }
                        }
                        h.assertTrue(s.water() > 1, "the water vanished with the re-analysis: " + s.water());
                        phase[0] = 2;
                    }
                }
                case 2 -> { // pump it dry, one use every 4 ticks
                    h.assertTrue(f.runtime().lastReport().inflow() == 0, "water came in again");
                    if (s.water() <= PumpIntake.DRY) {
                        h.assertTrue(BilgePumps.operate(level, s.holdPump(), player).outcome() == BilgePumps.Outcome.DRY,
                                "the empty hold is not reported dry");
                        phase[0] = 3;
                        h.succeed();
                    } else if (now % 4 == 0) {
                        BilgePumps.operate(level, s.holdPump(), player);
                    }
                }
                default -> { }
            }
        });
    }
}

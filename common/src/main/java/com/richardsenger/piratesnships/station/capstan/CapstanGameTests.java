package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.station.StationCommands;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan station of CRW3 (docs/design.md §6, §7.5): a crew member at the capstan lets the anchor go after
 * {@code crew_stations.capstan.drop_ticks} and heaves it in on order, pushing the bars ({@link CrewPose#CAPSTAN_PUSH}),
 * from the whistle, the command and the job board. The ship is the 5×4×5 test hull of {@link SailingGameTestsShips}
 * afloat in its 40×40 basin over a stone floor (water to y = 7, the anchor falls about six blocks), with the capstan on
 * the deck forward of the mast at relative (19, 9, 6), as in {@code SailingGameTestsControls}. Crew members stand on
 * the deck without AI so that they cannot stroll off before they are seated. Tests that change config or use the
 * command (its {@code order_radius} reaches neighbouring tests) have batches of their own.
 */
public final class CapstanGameTests {

    private static final String BATCH = "pirates_n_ships_station_capstan_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_station_capstan_";
    /** The anchor lands within 4 s of its drop in this basin (AN2a's tests measure the same). */
    private static final int LAND_TICKS = 80;

    private CapstanGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CapstanGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Rig(Fixture f, BlockPos capstan, BlockPos helm) {
        StationRef ref() {
            return new StationRef(f.ship().id(), capstan);
        }

        @Nullable ShipAnchor anchor() {
            return f.runtime().anchor();
        }

        @Nullable AnchorState.Phase phase() {
            ShipAnchor a = anchor();
            return a == null ? null : a.state().phase();
        }
    }

    private static Rig rig(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED);
        h.setBlock(new BlockPos(19, 9, 6), SailingBlocks.CAPSTAN.get());
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        return new Rig(f, find(f, SailingBlocks.CAPSTAN.get()), find(f, AssemblyContent.HELM.get()));
    }

    private static BlockPos find(Fixture f, Block block) {
        for (BlockPos p : f.ship().plotBlocks()) {
            if (f.ship().level().getBlockState(p).is(block)) return p;
        }
        throw new AssertionError("no " + block + " on the ship");
    }

    /** A crew member without AI standing on the deck above the plot cell {@code plot}. */
    private static CrewMember onDeck(GameTestHelper h, Rig r, BlockPos plot) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = r.f().ship().toWorld(Vec3.atBottomCenterOf(plot));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    /** A crew member seated at the capstan. */
    private static CrewMember manned(GameTestHelper h, Rig r) {
        CrewMember c = onDeck(h, r, r.capstan().west());
        CrewStations.AssignResult a = CrewStations.assign(h.getLevel(), c, r.capstan());
        h.assertTrue(a == CrewStations.AssignResult.ASSIGNED, "assign: " + a);
        h.assertTrue(c.isAtStation(), "the crew member does not ride the capstan's seat");
        return c;
    }

    /** A mock captain with a whistle on the deck beside the helm. */
    private static Player captain(GameTestHelper h, Rig r) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = r.f().ship().toWorld(Vec3.atBottomCenterOf(r.helm().east()));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    private static boolean working(StationRef ref) {
        StationState<Object> st = Stations.state(ref);
        return st != null && st.phase() == StationState.Phase.OPERATING;
    }

    private static int remaining(StationRef ref) {
        StationState<Object> st = Stations.state(ref);
        return st == null ? -1 : st.remaining();
    }

    private static CapstanBlock.Phase shown(GameTestHelper h, BlockPos plotPos) {
        return h.getLevel().getBlockState(plotPos).getValue(CapstanBlock.ANCHOR);
    }

    // ------------------------------------------------------------------ tests

    /**
     * The whole cycle at a manned capstan: "weigh anchor" with the anchor stowed is unable; "let go the anchor" works
     * for {@code drop_ticks} with the anchor still stowed and the crew member pushing the bars, then the anchor falls
     * and lands; a second drop has nothing to do; the whistle's "Weigh anchor" starts the winding at once, the crew
     * member pushes while it lasts, and the anchor is stowed within twice the work time the station estimated.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 500, batch = BATCH + "cycle")
    public static void crewDropsAndRaisesTheAnchor(GameTestHelper h) {
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        StationRef ref = r.ref();
        Player p = captain(h, r);
        int[] stage = {0};
        long[] since = {0};
        int[] work = {0};
        boolean[] pushed = {false};
        h.onEachTick(() -> {
            long t = h.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (t < 3) return;
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.RAISE_ANCHOR) == Stations.OrderResult.NOT_APPLICABLE,
                            "weighing a stowed anchor was not refused");
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.STARTED, "drop not started");
                    work[0] = remaining(ref);
                    h.assertTrue(work[0] >= CapstanConfig.DROP_TICKS.get(), "drop work time " + work[0] + " < drop_ticks");
                    h.assertTrue(r.anchor() == null, "the anchor went at the order, not after the work: " + r.anchor());
                    since[0] = t;
                    stage[0] = 1;
                }
                case 1 -> {
                    long dt = t - since[0];
                    if (working(ref)) {
                        h.assertTrue(r.anchor() == null && shown(h, r.capstan()) == CapstanBlock.Phase.RAISED,
                                "the anchor went before the work was done, at " + dt + ": " + r.anchor());
                        if (dt >= 2) {
                            h.assertTrue(c.isWorking(), "the crew member does not work at " + dt);
                            h.assertTrue(c.stationPose() == CrewPose.CAPSTAN_PUSH, "not pushing the bars: " + c.stationPose());
                        }
                        h.assertTrue(dt <= work[0], "still working after " + dt + " of " + work[0] + " ticks");
                    } else {
                        h.assertTrue(dt >= work[0], "the work ended after " + dt + " of " + work[0] + " ticks");
                        h.assertTrue(r.phase() == AnchorState.Phase.DROPPING || r.phase() == AnchorState.Phase.HOLDING,
                                "the anchor did not go after the work: " + r.anchor());
                        since[0] = t;
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (r.phase() != AnchorState.Phase.HOLDING) {
                        h.assertTrue(r.phase() == AnchorState.Phase.DROPPING, "not dropping: " + r.anchor());
                        h.assertTrue(t - since[0] < LAND_TICKS, "the anchor did not land within 4 s: " + r.anchor());
                        return;
                    }
                    h.assertTrue(t - since[0] < 2 || c.stationPose() == null, "still pushing after the drop: " + c.stationPose());
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.NOTHING_TO_DO,
                            "a second drop was not 'nothing to do'");
                    WhistleOrders.Result w = WhistleOrders.handle(p, WhistleOrder.RAISE_ANCHOR.id());
                    h.assertTrue(w.outcome() == WhistleOrders.Outcome.ISSUED && w.crew() == 1, "whistle weigh anchor: " + w);
                    h.assertTrue(r.phase() == AnchorState.Phase.RAISING, "the winding did not start with the order: " + r.anchor());
                    h.assertTrue(shown(h, r.capstan()) == CapstanBlock.Phase.RAISING, "the capstan does not show the winding");
                    work[0] = remaining(ref);
                    double estimate = ShipControls.raiseTicks(ShipControls.chainOut(level, r.capstan()));
                    h.assertTrue(work[0] >= CapstanConfig.MIN_RAISE_TICKS.get() && work[0] >= estimate - 1,
                            "raise work time " + work[0] + " for an estimate of " + estimate);
                    since[0] = t;
                    stage[0] = 3;
                }
                case 3 -> {
                    long dt = t - since[0];
                    if (dt >= 2 && working(ref) && c.stationPose() == CrewPose.CAPSTAN_PUSH) pushed[0] = true;
                    if (r.anchor() == null) {
                        h.assertTrue(dt <= 2L * work[0], "stowed after " + dt + " ticks, more than twice the estimate " + work[0]);
                        h.assertTrue(pushed[0], "the crew member did not push the bars while raising");
                        h.assertTrue(shown(h, r.capstan()) == CapstanBlock.Phase.RAISED, "the capstan does not show the anchor stowed");
                        stage[0] = 4;
                    } else {
                        h.assertTrue(r.phase() == AnchorState.Phase.RAISING, "not raising: " + r.anchor());
                        h.assertTrue(dt <= 2L * work[0], "not stowed within twice the estimate " + work[0] + ": " + r.anchor());
                    }
                }
                case 4 -> {
                    if (working(ref)) return; // the order may run a little past the stowing; it ends by itself
                    h.assertTrue(r.anchor() == null, "the anchor came out again: " + r.anchor());
                    h.assertTrue(c.isAtStation(), "the crew member left the capstan");
                    stage[0] = 5;
                    c.discard();
                    h.succeed();
                }
                default -> { }
            }
        });
    }

    /** A chain shorter than the water is deep: the drop is unable (the crew says why), nothing moves. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "no_ground")
    public static void noGroundWithinTheChainIsUnable(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.ANCHOR_CHAIN_LENGTH, 3);
        Rig r = rig(h);
        CrewMember c = manned(h, r);
        h.runAfterDelay(3, () -> {
            h.assertTrue(ShipControls.dropCheck(h.getLevel(), r.capstan()).result() == ShipControls.AnchorResult.NO_GROUND,
                    "drop check: " + ShipControls.dropCheck(h.getLevel(), r.capstan()));
            h.assertTrue(Stations.workTicks(h.getLevel(), r.ref(), AnchorOrder.DROP_ANCHOR) < 0, "a drop without ground has work");
            h.assertTrue(CrewStations.order(h.getLevel(), c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.NOT_APPLICABLE,
                    "the drop was not refused");
            h.assertTrue(!working(r.ref()) && r.anchor() == null, "something moved without ground");
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(r.anchor() == null && !c.isWorking(), "the anchor went without ground: " + r.anchor());
            c.discard();
            h.succeed();
        });
    }

    /**
     * Nobody mans the capstan: the whistle's "Drop anchor" (the payload's server handler) posts it as an open job, the
     * free hand on deck claims it in the board's pass, and the anchor goes after the work.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "job_board")
    public static void whistleDropAnchorIsAJobForAFreeHand(GameTestHelper h) {
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        h.runAfterDelay(3, () -> {
            CrewMember c = onDeck(h, r, r.capstan().west());
            Player p = captain(h, r);
            WhistleOrders.Result w = WhistleOrders.handle(p, "drop_anchor");
            h.assertTrue(w.outcome() == WhistleOrders.Outcome.ISSUED && w.crew() == 0 && w.jobs() == 1, "whistle drop anchor: " + w);
            h.assertTrue(JobBoard.jobs(r.f().ship().id()).containsKey(r.capstan()), "the unmanned capstan is no open job");
            h.assertTrue(r.anchor() == null, "posting the job moved the anchor");
            JobBoard.pass(level, r.f().ship().id());
            h.assertTrue(c.assignment() != null && c.assignment().pos().equals(r.capstan()), "nobody claimed the capstan: " + c.assignment());
            h.assertTrue(working(r.ref()), "the claiming hand does not work the capstan");
            h.assertTrue(JobBoard.jobs(r.f().ship().id()).isEmpty(), "the claimed job is still on the board");
            h.succeedWhen(() -> {
                h.assertTrue(r.phase() == AnchorState.Phase.DROPPING || r.phase() == AnchorState.Phase.HOLDING,
                        "the anchor did not go: " + r.anchor());
                c.discard();
            });
        });
    }

    /**
     * {@link Stations#workTicks} (the job board's query) never moves the anchor, whatever it asks; and a crew member
     * released mid-raise does not stop the winding, the anchor comes up as it does for a player.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "query")
    public static void workTicksIsAQueryAndTheWindingGoesOnAlone(GameTestHelper h) {
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        int[] stage = {0};
        long[] since = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (t < 3) return;
                    for (int i = 0; i < 5; i++) {
                        h.assertTrue(Stations.workTicks(level, r.ref(), AnchorOrder.DROP_ANCHOR) > 0, "a stowed anchor has no drop work");
                        h.assertTrue(Stations.workTicks(level, r.ref(), AnchorOrder.RAISE_ANCHOR) < 0, "a stowed anchor has raise work");
                        h.assertTrue(ShipControls.dropCheck(level, r.capstan()).result() == ShipControls.AnchorResult.DROPPING, "drop check");
                    }
                    h.assertTrue(r.anchor() == null && shown(h, r.capstan()) == CapstanBlock.Phase.RAISED, "a query moved the anchor");
                    ShipControls.CapstanResult d = ShipControls.dropAnchor(level, r.capstan());
                    h.assertTrue(d.result() == ShipControls.AnchorResult.DROPPING, "drop by hand: " + d);
                    since[0] = t;
                    stage[0] = 1;
                }
                case 1 -> {
                    if (r.phase() != AnchorState.Phase.HOLDING) {
                        h.assertTrue(t - since[0] < LAND_TICKS, "the anchor did not land: " + r.anchor());
                        return;
                    }
                    for (int i = 0; i < 5; i++) {
                        h.assertTrue(Stations.workTicks(level, r.ref(), AnchorOrder.RAISE_ANCHOR) > 0, "a holding anchor has no raise work");
                        h.assertTrue(Stations.workTicks(level, r.ref(), AnchorOrder.DROP_ANCHOR) == 0, "a holding anchor has drop work");
                    }
                    h.assertTrue(r.phase() == AnchorState.Phase.HOLDING, "a query raised the anchor: " + r.anchor());
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.RAISE_ANCHOR) == Stations.OrderResult.STARTED, "raise not started");
                    since[0] = t;
                    stage[0] = 2;
                }
                case 2 -> {
                    if (t - since[0] < 10) return;
                    h.assertTrue(r.phase() == AnchorState.Phase.RAISING, "not raising: " + r.anchor());
                    CrewStations.release(level, c);
                    h.assertTrue(c.assignment() == null && !Stations.isManned(r.ref()), "the crew member was not released");
                    since[0] = t;
                    stage[0] = 3;
                }
                case 3 -> {
                    if (r.anchor() != null) {
                        h.assertTrue(r.phase() == AnchorState.Phase.RAISING, "the release stopped the winding: " + r.anchor());
                        h.assertTrue(t - since[0] < 300, "not stowed: " + r.anchor());
                        return;
                    }
                    stage[0] = 4;
                    c.discard();
                    h.succeed();
                }
                default -> { }
            }
        });
    }

    /** {@code /pirates crew order drop_anchor} near the ship reaches the crew member at the capstan. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "command")
    public static void commandDropAnchorReachesTheCapstanCrew(GameTestHelper h) {
        Rig r = rig(h);
        CrewMember c = manned(h, r);
        h.runAfterDelay(3, () -> {
            List<Component> feedback = command(h, c.position(), "pirates crew order drop_anchor");
            h.assertTrue(feedback.size() == 1 && feedback.get(0).getContents() instanceof TranslatableContents t
                    && t.getKey().equals(StationCommands.KEY_ORDERED), "not the order feedback: " + feedback);
            Object[] args = ((TranslatableContents) feedback.get(0).getContents()).getArgs();
            h.assertTrue(args[1].equals(1) && args[2].equals(1), "expected 1 of 1 crew, got " + args[1] + " of " + args[2]);
            h.assertTrue(working(r.ref()) && Stations.state(r.ref()).order() == AnchorOrder.DROP_ANCHOR, "the command's order did not start");
            h.succeedWhen(() -> {
                h.assertTrue(r.anchor() != null, "the anchor did not go");
                c.discard();
            });
        });
    }

    /** With {@code crew_stations.capstan.enabled} off the capstan takes no crew orders and posts no jobs; players still use it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "disabled")
    public static void disabledCapstanStationIsUnable(GameTestHelper h) {
        ConfigOverrides.during(h, CapstanConfig.ENABLED, false);
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        h.runAfterDelay(3, () -> {
            h.assertTrue(CrewStations.order(level, c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.NOT_APPLICABLE, "drop not refused");
            h.assertTrue(Stations.workTicks(level, r.ref(), AnchorOrder.DROP_ANCHOR) < 0, "a disabled capstan has work");
            CrewStations.release(level, c);
            WhistleOrders.Result w = WhistleOrders.handle(captain(h, r), WhistleOrder.DROP_ANCHOR.id());
            h.assertTrue(w.jobs() == 0 && JobBoard.jobs(r.f().ship().id()).isEmpty(), "a disabled capstan became a job: " + w);
            h.assertTrue(r.anchor() == null, "the anchor went");
            h.assertTrue(ShipControls.dropAnchor(level, r.capstan()).result() == ShipControls.AnchorResult.DROPPING,
                    "the player's capstan stopped working");
            c.discard();
            h.succeed();
        });
    }

    /** Runs a command as an operator at {@code pos} and returns its feedback. */
    private static List<Component> command(GameTestHelper h, Vec3 pos, String command) {
        List<Component> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.add(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withSource(capture).withLevel(h.getLevel()).withPosition(pos).withPermission(4);
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return out;
    }
}

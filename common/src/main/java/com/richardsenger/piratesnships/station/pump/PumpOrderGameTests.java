package com.richardsenger.piratesnships.station.pump;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumps;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.ship.hull.pump.PumpIntake;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationCommands;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrderPayload;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import com.richardsenger.piratesnships.station.winch.SailOrder;
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
import net.minecraft.world.phys.Vec3;

/**
 * The pump order of G7 (docs/design.md §6, §7.2): the whistle's "Pump" entry and {@code /pirates crew order pump}
 * reach the crew member at a bilge pump, who pumps until the bilge is dry; orders and stations of other kinds do not
 * mix. The ship is the closed 5×4×5 plank hull of {@link DryHullGameTests} afloat in its basin (hold 3×2×3, one
 * compartment) with a bilge pump on the hold floor at hold (1, −3, 1) and a sail winch on the deck at hold (−1, 0, −1)
 * (no sails: the winch is only there as a station of another kind). No config is changed: crew stations and pumps are
 * on by default. The tests that use the command without a crew argument run in batches of their own, because its
 * {@code order_radius} (64 blocks) reaches the crew of neighbouring tests.
 */
public final class PumpOrderGameTests {

    /** Water put into the hold: more than one crew batch ({@link BilgePumps#CREW_BATCH_TICKS}) at the default rate. */
    private static final double FLOOD = 7;

    private PumpOrderGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PumpOrderGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, BlockPos pump, BlockPos winch) {
        double water() {
            return f.runtime().simulation().totalVolume();
        }
    }

    private static Ship ship(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(12, 6, 12), HullRepairContent.BILGE_PUMP.get());
        h.setBlock(new BlockPos(10, 9, 10), SailingBlocks.SAIL_WINCH.get());
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos pump = f.hold(1, -3, 1), winch = f.hold(-1, 0, -1);
        ServerLevel level = h.getLevel();
        h.assertTrue(level.getBlockState(pump).is(HullRepairContent.BILGE_PUMP.get()), "the pump is not in the plot");
        h.assertTrue(level.getBlockState(winch).is(SailingBlocks.SAIL_WINCH.get()), "the winch is not in the plot");
        h.assertTrue(f.runtime().pumps().contains(pump), "the hull runtime did not find the pump");
        return new Ship(f, pump, winch);
    }

    /** A crew member spawned on the deck and assigned to the station at plot position {@code station}. */
    private static CrewMember manned(GameTestHelper h, Ship s, BlockPos station) {
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(11, 10, 11));
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, station);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        h.assertTrue(c.isAtStation(), "the crew member does not ride the station seat");
        return c;
    }

    /** A mock player on the deck next to the helm, holding a whistle. */
    private static Player captain(GameTestHelper h, Ship s) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = s.f().ship().toWorld(Vec3.atBottomCenterOf(s.f().helmPlot()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    private static Object orderAt(StationRef ref) {
        StationState<Object> st = Stations.state(ref);
        return st == null ? null : st.order();
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

    /** Asserts that {@code c} is the translatable "ordered" feedback with these started / addressed counts. */
    private static void assertOrdered(GameTestHelper h, List<Component> feedback, int started, int addressed) {
        h.assertTrue(feedback.size() == 1, "expected one feedback line: " + feedback);
        Component c = feedback.get(0);
        h.assertTrue(c.getContents() instanceof TranslatableContents t && t.getKey().equals(StationCommands.KEY_ORDERED),
                "not the order feedback: " + c);
        Object[] args = ((TranslatableContents) c.getContents()).getArgs();
        h.assertTrue(args[1].equals(started) && args[2].equals(addressed),
                "expected " + started + " of " + addressed + " crew, got " + args[1] + " of " + args[2]);
    }

    // ------------------------------------------------------------------ tests

    /**
     * The whistle menu's "Pump" (through the payload's server handler) sets the crew member at the pump to work; it
     * keeps pumping over more than one order batch until the hold is dry, and a second "Pump" then finds nothing to do.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void whistlePumpOrderDrainsTheHold(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, s, s.pump());
        StationRef ref = c.assignment();
        Player p = captain(h, s);
        s.f().runtime().simulation().setVolume(0, FLOOD);
        double perTick = FloodingConfig.params().pumpPerTick();
        h.assertTrue(FLOOD > BilgePumps.CREW_BATCH_TICKS * perTick, "the flood fits into one batch: the test proves nothing");
        WhistleOrders.Result r = WhistleOrders.handle(p, new WhistleOrderPayload(WhistleOrder.PUMP).order());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "menu order pump: " + r);
        h.assertTrue(orderAt(ref) == PumpOrder.PUMP, "the pump order did not reach the crew member at the pump");
        h.assertTrue(BilgePumps.crewOperating(ref.ship(), ref.pos()), "the hull does not see the crew pumping");
        h.assertTrue(WhistleOrders.heldWhistle(p).get(StationContent.WHISTLE_ORDER.get()) == null,
                "the pump order was remembered as a sail order");
        h.runAfterDelay(BilgePumps.CREW_BATCH_TICKS + 10, () -> {
            h.assertTrue(s.water() > PumpIntake.DRY, "the hold was dry after one batch: " + s.water());
            h.assertTrue(orderAt(ref) == PumpOrder.PUMP, "the crew member stopped after one batch with water left");
        });
        h.runAfterDelay(BilgePumps.CREW_BATCH_TICKS + 11, () -> h.succeedWhen(() -> {
            h.assertTrue(s.water() <= PumpIntake.DRY, "water left: " + s.water());
            h.assertTrue(orderAt(ref) == null, "still pumping a dry bilge");
            WhistleOrders.Result again = WhistleOrders.handle(p, WhistleOrder.PUMP.id());
            h.assertTrue(again.outcome() == WhistleOrders.Outcome.ISSUED && again.crew() == 0, "pump on a dry bilge: " + again);
            h.assertTrue(c.isAtStation(), "the crew member left the pump");
            c.discard();
        }));
    }

    /** {@code /pirates crew order pump} near the ship reaches the crew member at the pump, and the hold drains. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_station_pump_command")
    public static void commandPumpOrderDrainsTheHold(GameTestHelper h) {
        Ship s = ship(h);
        CrewMember c = manned(h, s, s.pump());
        StationRef ref = c.assignment();
        s.f().runtime().simulation().setVolume(0, 3);
        List<Component> feedback = command(h, c.position(), "pirates crew order pump");
        assertOrdered(h, feedback, 1, 1);
        h.assertTrue(orderAt(ref) == PumpOrder.PUMP, "the command's pump order did not reach the crew member");
        h.runAfterDelay(1, () -> h.succeedWhen(() -> {
            h.assertTrue(s.water() <= PumpIntake.DRY, "water left: " + s.water());
            c.discard();
        }));
    }

    /**
     * Orders and stations of different kinds do not mix, in both directions: a ship-wide order (whistle, command
     * without a crew argument) reaches only the crew at stations that take it, the others ignore it and keep their
     * work; an order addressed to one crew member at the wrong kind of station is refused ({@code WRONG_STATION}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_station_pump_mixed")
    public static void ordersGoOnlyToStationsThatTakeThem(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        CrewMember atPump = manned(h, s, s.pump());
        CrewMember atWinch = manned(h, s, s.winch());
        StationRef pump = atPump.assignment(), winch = atWinch.assignment();
        Player p = captain(h, s);
        s.f().runtime().simulation().setVolume(0, FLOOD);

        // ship-wide pump order: the pump crew only
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.PUMP.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "menu order pump: " + r);
        h.assertTrue(orderAt(pump) == PumpOrder.PUMP, "the pump crew does not pump");
        h.assertTrue(orderAt(winch) == null, "the winch crew took a pump order");

        // ship-wide sail order: the winch crew answers (no sails here), the pump crew is not addressed and keeps pumping
        WhistleOrders.Result hoist = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(hoist.outcome() == WhistleOrders.Outcome.ISSUED && hoist.crew() == 0, "menu order hoist: " + hoist);
        h.assertTrue(orderAt(pump) == PumpOrder.PUMP, "a sail order stopped the pump crew");

        // addressed to one crew member at the wrong station: refused, the work in progress goes on
        h.assertTrue(CrewStations.order(level, atWinch, PumpOrder.PUMP) == Stations.OrderResult.WRONG_STATION,
                "a pump order at the winch was not refused");
        h.assertTrue(CrewStations.order(level, atPump, SailOrder.HOIST) == Stations.OrderResult.WRONG_STATION,
                "a sail order at the pump was not refused");
        h.assertTrue(orderAt(pump) == PumpOrder.PUMP, "the refused sail order stopped the pump crew");
        h.assertTrue(orderAt(winch) == null, "the refused pump order gave the winch crew work");

        // the command: without a crew argument only the pump crew is addressed; named winch crew refuses
        assertOrdered(h, command(h, atPump.position(), "pirates crew order pump"), 1, 1);
        assertOrdered(h, command(h, atPump.position(), "pirates crew order pump " + atWinch.getStringUUID()), 0, 1);
        assertOrdered(h, command(h, atPump.position(), "pirates crew order furl"), 0, 1);
        h.assertTrue(orderAt(pump) == PumpOrder.PUMP && orderAt(winch) == null, "the commands mixed the stations up");
        h.assertTrue(atPump.isAtStation() && atWinch.isAtStation(), "a refused order released a crew member");
        List<Component> unknown = command(h, atPump.position(), "pirates crew order bail");
        // a failure arrives as a red, empty component with the message as its sibling
        h.assertTrue(unknown.size() == 1 && unknown.get(0).getSiblings().stream().anyMatch(sib ->
                sib.getContents() instanceof TranslatableContents t && t.getKey().equals(StationCommands.KEY_UNKNOWN_ORDER)),
                "unknown order feedback: " + unknown);
        atPump.discard();
        atWinch.discard();
        h.succeed();
    }
}

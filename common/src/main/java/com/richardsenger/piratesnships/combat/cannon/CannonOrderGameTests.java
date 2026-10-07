package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The cannon's "Fire!" crew order of G13 (docs/design.md §6, §7.2, §8.2): the whistle's "Fire!" entry and
 * {@code /pirates crew order fire} reach the crew at the cannons; crew at a loaded cannon fires it after the fuse, crew
 * at an unloaded one answers that it is not loaded, crew at other stations is not addressed, and a fire order given to
 * one crew member at the wrong kind of station is refused. The ship is the closed 5×4×5 plank hull of
 * {@link DryHullGameTests} afloat in its basin, with cannons on the deck at hold (−1, 0, −1) and (−1, 0, 1), both facing
 * west towards a stone backstop inside the test area (as in {@link CannonGameTests}), and a sail winch at hold
 * (1, 0, −1) as a station of another kind. Fired balls are removed when the test ends. The command test without a crew
 * argument runs in a batch of its own: its {@code order_radius} reaches the crew of neighbouring tests.
 */
public final class CannonOrderGameTests {

    private CannonOrderGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CannonOrderGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, BlockPos loaded, BlockPos unloaded, BlockPos winch) {
    }

    private static Ship ship(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(10, 9, 10), CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.WEST));
        h.setBlock(new BlockPos(10, 9, 12), CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.WEST));
        h.setBlock(new BlockPos(12, 9, 10), SailingBlocks.SAIL_WINCH.get());
        for (int z = 1; z < 23; z++) {
            for (int y = 9; y <= 11; y++) h.setBlock(new BlockPos(2, y, z), Blocks.STONE); // backstop for the shots
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos loaded = f.hold(-1, 0, -1), unloaded = f.hold(-1, 0, 1), winch = f.hold(1, 0, -1);
        ServerLevel level = h.getLevel();
        h.assertTrue(level.getBlockState(loaded).is(CannonContent.CANNON.get()), "the first cannon is not in the plot");
        h.assertTrue(level.getBlockState(unloaded).is(CannonContent.CANNON.get()), "the second cannon is not in the plot");
        h.assertTrue(level.getBlockState(winch).is(SailingBlocks.SAIL_WINCH.get()), "the winch is not in the plot");
        return new Ship(f, loaded, unloaded, winch);
    }

    /** A crew member spawned on the deck and assigned to the station at plot position {@code station}. */
    private static CrewMember manned(GameTestHelper h, BlockPos station) {
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

    /** Loads powder and ball with a creative player (no items used). */
    private static void loadFully(GameTestHelper h, BlockPos pos) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonService.load(level, pos, creative, new ItemStack(Items.GUNPOWDER)).outcome() == CannonService.Outcome.POWDER_IN,
                "powder was refused");
        h.assertTrue(CannonService.load(level, pos, creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the ball was refused");
    }

    private static CannonLoad load(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockState(pos).getValue(CannonBlock.LOAD);
    }

    private static Object orderAt(StationRef ref) {
        StationState<Object> st = Stations.state(ref);
        return st == null ? null : st.order();
    }

    private static void cleanup(GameTestHelper h, CrewMember... crew) {
        h.getEntities(CannonContent.CANNONBALL.get()).forEach(CannonballEntity::discard);
        for (CrewMember c : crew) c.discard();
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

    /** Asserts that the feedback is the one "ordered" line with these started / addressed counts. */
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
     * The whistle's "Fire!" (through the payload's server handler) reaches the crew at both cannons: the loaded one
     * fires after the fuse, the unloaded one stays empty and its crew has nothing to do; the winch crew is not
     * addressed. The order is not remembered on the whistle (only sail orders are). Given to the winch crew directly,
     * the fire order is refused as the wrong station.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void whistleFireOrderFiresTheLoadedCannons(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        CrewMember atLoaded = manned(h, s.loaded());
        CrewMember atUnloaded = manned(h, s.unloaded());
        CrewMember atWinch = manned(h, s.winch());
        StationRef loadedRef = atLoaded.assignment(), unloadedRef = atUnloaded.assignment(), winchRef = atWinch.assignment();
        Player p = captain(h, s);
        loadFully(h, s.loaded());

        WhistleOrders.Result r = WhistleOrders.handle(p, new WhistleOrderPayload(WhistleOrder.FIRE).order());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "menu order fire: " + r);
        h.assertTrue(orderAt(loadedRef) == CannonOrder.FIRE, "the fire order did not reach the crew at the loaded cannon");
        h.assertTrue(orderAt(unloadedRef) == null, "the crew at the unloaded cannon took the fire order");
        h.assertTrue(orderAt(winchRef) == null, "the winch crew took a fire order");
        h.assertTrue(WhistleOrders.heldWhistle(p).get(StationContent.WHISTLE_ORDER.get()) == null,
                "the fire order was remembered as a sail order");

        // addressed directly: the unloaded cannon has nothing to do, the winch is the wrong station
        h.assertTrue(CrewStations.order(level, atUnloaded, CannonOrder.FIRE) == Stations.OrderResult.NOTHING_TO_DO,
                "the unloaded cannon took a fire order");
        h.assertTrue(CrewStations.order(level, atWinch, CannonOrder.FIRE) == Stations.OrderResult.WRONG_STATION,
                "a fire order at the winch was not refused");
        h.assertTrue(atWinch.isAtStation() && atUnloaded.isAtStation(), "a refused order released a crew member");

        h.runAfterDelay(1, () -> h.succeedWhen(() -> {
            h.assertTrue(load(h, s.loaded()) == CannonLoad.EMPTY, "the crew member has not fired yet");
            h.assertTrue(load(h, s.unloaded()) == CannonLoad.EMPTY, "the unloaded cannon changed");
            h.assertTrue(orderAt(loadedRef) == null, "the fire order is still running");
            h.assertTrue(atLoaded.isAtStation(), "the gunner left the cannon after firing");
            cleanup(h, atLoaded, atUnloaded, atWinch);
        }));
    }

    /** "Fire!" with every cannon unloaded: issued, nobody fires, the crew stays at the guns. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void whistleFireOrderAtUnloadedCannonsDoesNothing(GameTestHelper h) {
        Ship s = ship(h);
        CrewMember atUnloaded = manned(h, s.unloaded());
        StationRef ref = atUnloaded.assignment();
        Player p = captain(h, s);
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.FIRE.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 0, "menu order fire: " + r);
        h.assertTrue(orderAt(ref) == null, "an unloaded cannon took the fire order");
        h.assertTrue(CannonOrder.FIRE.nothingToDoKey().endsWith("crew.cannon_not_loaded"), "the crew's answer key");
        h.runAfterDelay(CannonStation.FUSE_TICKS + 5, () -> {
            h.assertTrue(h.getEntities(CannonContent.CANNONBALL.get()).isEmpty(), "an unloaded cannon fired");
            h.assertTrue(load(h, s.unloaded()) == CannonLoad.EMPTY, "the cannon's load changed");
            h.assertTrue(atUnloaded.isAtStation(), "the crew member left the cannon");
            cleanup(h, atUnloaded);
            h.succeed();
        });
    }

    /**
     * {@code /pirates crew order fire} near the ship reaches only the crew at the cannon (the winch crew is not
     * addressed), and the cannon fires; named, the winch crew refuses it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_combat_cannon_fire_command")
    public static void commandFireOrderFiresTheLoadedCannon(GameTestHelper h) {
        Ship s = ship(h);
        CrewMember atLoaded = manned(h, s.loaded());
        CrewMember atWinch = manned(h, s.winch());
        StationRef ref = atLoaded.assignment(), winch = atWinch.assignment();
        loadFully(h, s.loaded());

        assertOrdered(h, command(h, atLoaded.position(), "pirates crew order fire"), 1, 1);
        h.assertTrue(orderAt(ref) == CannonOrder.FIRE, "the command's fire order did not reach the gunner");
        assertOrdered(h, command(h, atLoaded.position(), "pirates crew order fire " + atWinch.getStringUUID()), 0, 1);
        h.assertTrue(orderAt(winch) == null, "the winch crew took a fire order");

        h.runAfterDelay(1, () -> h.succeedWhen(() -> {
            h.assertTrue(load(h, s.loaded()) == CannonLoad.EMPTY, "the crew member has not fired yet");
            cleanup(h, atLoaded, atWinch);
        }));
    }
}

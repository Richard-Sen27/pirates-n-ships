package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Crew loading of C9 (docs/design.md §6, §8.2): a crew member at a cannon or swivel gun loads it on "Load!" from a
 * chest on the same ship within {@code cannons.crew.supply_range}, after the work time, taking one gunpowder and the
 * gun's ammo from the chest; refused without a supply, out of range, at a loaded gun and with crew loading off; with
 * {@code auto_reload} the crew loads again after its shot, so the gun fires again on the next "Fire!".
 *
 * <p>The ship is the closed plank hull of {@link DryHullGameTests} afloat in its basin, with a cannon on the deck at
 * hold (−1, 0, −1) facing west towards a stone backstop, a swivel gun at (−1, 0, 1), and a chest at (1, 0, −1): two
 * blocks from the cannon's master, √8 from the swivel. Tests that change config run in batches of their own
 * ({@link ConfigOverrides}); the others share one. Fired balls and crew are removed at the end; the ship by
 * {@code ShipTestCleanup}.
 */
public final class CannonCrewGameTests {

    private static final String BATCH = "pirates_n_ships_combat_cannon_crew";

    private CannonCrewGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CannonCrewGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, BlockPos cannon, BlockPos swivel, BlockPos chest) {
    }

    private static Ship ship(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        CannonGameTests.cannon(h, new BlockPos(10, 9, 10), Direction.WEST); // rear at x 11
        h.setBlock(new BlockPos(10, 9, 12), CannonContent.SWIVEL_GUN.get()); // on the deck plank
        h.setBlock(new BlockPos(12, 9, 10), Blocks.CHEST);
        for (int z = 1; z < 23; z++) {
            for (int y = 9; y <= 11; y++) h.setBlock(new BlockPos(2, y, z), Blocks.STONE); // backstop for the shots
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos cannon = f.hold(-1, 0, -1), swivel = f.hold(-1, 0, 1), chest = f.hold(1, 0, -1);
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonBlock.isMaster(level.getBlockState(cannon)), "the cannon is not in the plot");
        h.assertTrue(level.getBlockState(swivel).getBlock() instanceof SwivelGunBlock, "the swivel gun is not in the plot");
        h.assertTrue(level.getBlockEntity(chest) instanceof Container, "the chest is not in the plot");
        return new Ship(f, cannon, swivel, chest);
    }

    /** A crew member spawned on the deck and assigned to the station at plot position {@code station}. */
    private static CrewMember manned(GameTestHelper h, BlockPos station) {
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(11, 10, 11));
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, station);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        h.assertTrue(c.isAtStation(), "the crew member does not ride the station seat");
        return c;
    }

    private static Container chest(GameTestHelper h, Ship s) {
        if (h.getLevel().getBlockEntity(s.chest()) instanceof Container c) return c;
        throw new GameTestAssertException("no chest at " + s.chest());
    }

    /** Fills the chest's first slots with {@code stacks} (the rest stays empty). */
    private static void fill(GameTestHelper h, Ship s, ItemStack... stacks) {
        Container c = chest(h, s);
        c.clearContent();
        for (int i = 0; i < stacks.length; i++) c.setItem(i, stacks[i]);
    }

    private static int count(GameTestHelper h, Ship s, Item item) {
        Container c = chest(h, s);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    private static void assertChest(GameTestHelper h, Ship s, int powder, int balls) {
        int p = count(h, s, Items.GUNPOWDER), b = count(h, s, CombatContent.CANNONBALL.get());
        h.assertTrue(p == powder && b == balls, "expected " + powder + " powder / " + balls + " balls in the chest, found " + p + " / " + b);
    }

    private static void supplied(GameTestHelper h, Ship s) {
        fill(h, s, new ItemStack(Items.GUNPOWDER, 5), new ItemStack(CombatContent.CANNONBALL.get(), 5));
    }

    private static CannonLoad cannonLoad(GameTestHelper h, Ship s) {
        return h.getLevel().getBlockState(s.cannon()).getValue(CannonBlock.LOAD);
    }

    private static CannonLoad swivelLoad(GameTestHelper h, Ship s) {
        return h.getLevel().getBlockState(s.swivel()).getValue(SwivelGunBlock.LOAD);
    }

    private static Object orderAt(StationRef ref) {
        StationState<Object> st = Stations.state(ref);
        return st == null ? null : st.order();
    }

    /** A mock player on the deck next to the helm, holding a whistle. */
    private static Player captain(GameTestHelper h, Ship s) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = s.f().ship().toWorld(Vec3.atBottomCenterOf(s.f().helmPlot()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    /** Loads the cannon with a creative player (no items used). */
    private static void loadByHand(GameTestHelper h, Ship s) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonService.load(level, s.cannon(), creative, new ItemStack(Items.GUNPOWDER)).outcome()
                == CannonService.Outcome.POWDER_IN, "powder was refused");
        h.assertTrue(CannonService.load(level, s.cannon(), creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the ball was refused");
    }

    /**
     * The end of the cannon's reload cooldown: 0 until its first shot, then the shot's game time plus the reload time
     * (a ball that hits the backstop is gone at once, so shots are told by this).
     */
    private static long shotAt(GameTestHelper h, Ship s) {
        if (h.getLevel().getBlockEntity(s.cannon()) instanceof CannonBlockEntity be) return be.reloadUntil();
        throw new GameTestAssertException("no cannon block entity at " + s.cannon());
    }

    private static void cleanup(GameTestHelper h, CrewMember... crew) {
        h.getEntities(CannonContent.CANNONBALL.get()).forEach(CannonballEntity::discard);
        for (CrewMember c : crew) c.discard();
    }

    // ------------------------------------------------------------------ loading

    /**
     * The whistle's "Load!" reaches the crew at the cannon: nothing happens before the work time, then the cannon is
     * loaded (powder and ball, the block state a player's load gives) and the chest lost one gunpowder and one ball.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH)
    public static void loadOrderLoadsTheCannonFromAChestAfterTheWorkTime(GameTestHelper h) {
        Ship s = ship(h);
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        StationRef ref = gunner.assignment();

        WhistleOrders.Result r = WhistleOrders.handle(captain(h, s), WhistleOrder.LOAD.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 1, "menu order load: " + r);
        h.assertTrue(orderAt(ref) == CannonOrder.LOAD, "the load order did not reach the gunner");

        int work = CannonConfig.CREW_LOAD_TICKS.get();
        h.runAfterDelay(work - 10, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY, "loaded before the work time was over");
            assertChest(h, s, 5, 5);
        });
        h.runAfterDelay(work + 2, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.LOADED, "not loaded after the work time: " + cannonLoad(h, s));
            assertChest(h, s, 4, 4);
            h.assertTrue(orderAt(ref) == null, "the load order is still running");
            h.assertTrue(gunner.isAtStation(), "the gunner left the cannon");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** Refused (the station's "unable" answer) with no powder in reach and with no container at all; nothing changes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH)
    public static void loadOrderIsRefusedWithoutASupply(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        CrewMember gunner = manned(h, s.cannon());

        fill(h, s, new ItemStack(CombatContent.CANNONBALL.get(), 5)); // balls but no powder
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.LOAD) == Stations.OrderResult.NOT_APPLICABLE,
                "loading without powder was not refused");
        fill(h, s, new ItemStack(Items.GUNPOWDER, 5)); // powder but no balls
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.LOAD) == Stations.OrderResult.NOT_APPLICABLE,
                "loading without balls was not refused");
        assertChest(h, s, 5, 0);
        level.removeBlock(s.chest(), false); // no container at all
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.LOAD) == Stations.OrderResult.NOT_APPLICABLE,
                "loading without a supply was not refused");
        h.assertTrue(orderAt(gunner.assignment()) == null, "a refused load is running");
        h.runAfterDelay(5, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY, "the cannon's load changed");
            h.assertTrue(gunner.isAtStation(), "a refused order released the gunner");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** A loaded cannon has nothing to load: the order is answered "already loaded" and the chest keeps its items. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH)
    public static void loadOrderAtALoadedCannonHasNothingToDo(GameTestHelper h) {
        Ship s = ship(h);
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        loadByHand(h, s);
        h.assertTrue(CrewStations.order(h.getLevel(), gunner, CannonOrder.LOAD) == Stations.OrderResult.NOTHING_TO_DO,
                "a loaded cannon took a load order");
        h.runAfterDelay(5, () -> {
            assertChest(h, s, 5, 5);
            h.assertTrue(cannonLoad(h, s) == CannonLoad.LOADED, "the cannon's load changed");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /**
     * A cannon a player powdered only needs the ball: the crew takes one ball and no powder. "Load!" through
     * {@code /pirates crew order load <crew>} (the command takes every crew order).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH)
    public static void aPowderedCannonOnlyTakesTheBall(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        CannonService.load(level, s.cannon(), creative, new ItemStack(Items.GUNPOWDER));
        h.assertTrue(cannonLoad(h, s) == CannonLoad.POWDER, "the powder did not go in");

        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withLevel(level).withPosition(gunner.position()).withPermission(4).withSuppressedOutput(),
                "pirates crew order load " + gunner.getStringUUID());
        h.assertTrue(orderAt(gunner.assignment()) == CannonOrder.LOAD, "the command's load order did not reach the gunner");
        h.runAfterDelay(CannonConfig.CREW_LOAD_TICKS.get() + 2, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.LOADED, "not loaded: " + cannonLoad(h, s));
            assertChest(h, s, 5, 4);
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** With {@code supply_range} 1, the chest two blocks away is out of reach: refused. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = "pirates_n_ships_config_cannon_crew_range")
    public static void aSupplyOutOfRangeIsRefused(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CREW_SUPPLY_RANGE, 1);
        Ship s = ship(h);
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        h.assertTrue(CrewStations.order(h.getLevel(), gunner, CannonOrder.LOAD) == Stations.OrderResult.NOT_APPLICABLE,
                "a chest out of range was used");
        ConfigOverrides.during(h, CannonConfig.CREW_SUPPLY_RANGE, 2); // the chest is exactly at the edge now
        h.assertTrue(CrewStations.order(h.getLevel(), gunner, CannonOrder.LOAD) == Stations.OrderResult.STARTED,
                "a chest at the edge of the range was not used");
        Stations.state(gunner.assignment()).interrupt();
        h.runAfterDelay(5, () -> {
            assertChest(h, s, 5, 5);
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY, "the cannon's load changed");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** With {@code cannons.crew.enabled} off, "Load!" is refused; the crew still fires a loaded gun but does not reload. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = "pirates_n_ships_config_cannon_crew_disabled")
    public static void crewLoadingCanBeDisabled(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CREW_ENABLED, false);
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        StationRef ref = gunner.assignment();
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.LOAD) == Stations.OrderResult.NOT_APPLICABLE,
                "loading was not refused while crew loading is off");
        loadByHand(h, s);
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.FIRE) == Stations.OrderResult.STARTED, "fire was refused");
        h.runAfterDelay(CannonStation.FUSE_TICKS + 3, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY && shotAt(h, s) > 0, "the crew did not fire");
            h.assertTrue(orderAt(ref) == null, "the crew reloads although crew loading is off");
            assertChest(h, s, 5, 5);
            cleanup(h, gunner);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ firing and reloading

    /**
     * "Fire!" at a loaded, supplied cannon: the crew fires, starts loading again by itself, has the gun loaded when the
     * reload cooldown is over (from the chest), and fires again on the next "Fire!".
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = BATCH)
    public static void afterFiringTheCrewReloadsAndCanFireAgain(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        StationRef ref = gunner.assignment();
        loadByHand(h, s);
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.FIRE) == Stations.OrderResult.STARTED, "fire was refused");

        int fuse = CannonStation.FUSE_TICKS;
        int reload = Math.max(CannonConfig.RELOAD_TICKS.get(), CannonConfig.CREW_LOAD_TICKS.get());
        long[] first = new long[1];
        h.runAfterDelay(fuse + 3, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY && shotAt(h, s) > 0, "the first shot did not leave");
            h.assertTrue(orderAt(ref) == CannonOrder.LOAD, "the crew did not start reloading: " + orderAt(ref));
            assertChest(h, s, 5, 5);
            first[0] = shotAt(h, s);
        });
        h.runAfterDelay(fuse + reload + 4, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.LOADED, "not reloaded within the reload time: " + cannonLoad(h, s));
            assertChest(h, s, 4, 4);
            h.assertTrue(CrewStations.order(level, gunner, CannonOrder.FIRE) == Stations.OrderResult.STARTED,
                    "the second fire was refused");
        });
        h.runAfterDelay(fuse + reload + 4 + fuse + 3, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY && shotAt(h, s) > first[0], "the second shot did not leave");
            h.assertTrue(orderAt(ref) == CannonOrder.LOAD, "the crew did not reload after the second shot");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** "Fire!" while the crew is loading: the load is finished, then the gun fires, then the crew reloads. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH)
    public static void fireDuringALoadFiresOnceLoaded(GameTestHelper h) {
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        StationRef ref = gunner.assignment();
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.LOAD) == Stations.OrderResult.STARTED, "load was refused");
        int work = CannonConfig.CREW_LOAD_TICKS.get();
        h.runAfterDelay(20, () -> {
            h.assertTrue(CrewStations.order(level, gunner, CannonOrder.FIRE) == Stations.OrderResult.STARTED,
                    "fire during the load was refused");
            h.assertTrue(orderAt(ref) == CannonOrder.FIRE, "the fire order did not replace the load");
        });
        h.runAfterDelay(work - 5, () -> h.assertTrue(shotAt(h, s) == 0, "fired before the load was done"));
        h.runAfterDelay(work + CannonStation.FUSE_TICKS + 3, () -> {
            h.assertTrue(shotAt(h, s) > 0 && cannonLoad(h, s) == CannonLoad.EMPTY, "the loaded gun did not fire");
            assertChest(h, s, 4, 4);
            h.assertTrue(orderAt(ref) == CannonOrder.LOAD, "the crew did not reload after the shot");
            cleanup(h, gunner);
            h.succeed();
        });
    }

    /** With {@code auto_reload} off the crew fires and leaves the gun empty. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_config_cannon_crew_auto_reload")
    public static void withoutAutoReloadTheGunStaysEmpty(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CREW_AUTO_RELOAD, false);
        Ship s = ship(h);
        ServerLevel level = h.getLevel();
        supplied(h, s);
        CrewMember gunner = manned(h, s.cannon());
        StationRef ref = gunner.assignment();
        loadByHand(h, s);
        h.assertTrue(CrewStations.order(level, gunner, CannonOrder.FIRE) == Stations.OrderResult.STARTED, "fire was refused");
        int after = CannonStation.FUSE_TICKS + Math.max(CannonConfig.RELOAD_TICKS.get(), CannonConfig.CREW_LOAD_TICKS.get()) + 10;
        h.runAfterDelay(CannonStation.FUSE_TICKS + 3, () -> {
            h.assertTrue(shotAt(h, s) > 0, "the crew did not fire");
            h.assertTrue(orderAt(ref) == null, "the crew reloads with auto_reload off");
        });
        h.runAfterDelay(after, () -> {
            h.assertTrue(cannonLoad(h, s) == CannonLoad.EMPTY, "the gun was reloaded with auto_reload off");
            assertChest(h, s, 5, 5);
            cleanup(h, gunner);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the swivel gun

    /**
     * The swivel gun takes powder and {@code cannons.swivel.ammo_count} (here 3) of its ammo, gathered from partial
     * stacks in slot order, after the swivel's work time; the shot in the barrel is recorded as 3.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_config_cannon_crew_swivel_ammo")
    public static void theSwivelLoadsItsConfiguredAmmoCount(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.SWIVEL_AMMO, SwivelAmmo.CANNONBALL);
        ConfigOverrides.during(h, CannonConfig.SWIVEL_AMMO_COUNT, 3);
        Ship s = ship(h);
        Item ball = CombatContent.CANNONBALL.get();
        fill(h, s, new ItemStack(Items.GUNPOWDER, 1), new ItemStack(ball, 2), new ItemStack(Items.GUNPOWDER, 1),
                new ItemStack(ball, 2));
        CrewMember gunner = manned(h, s.swivel());
        h.assertTrue(CrewStations.order(h.getLevel(), gunner, CannonOrder.LOAD) == Stations.OrderResult.STARTED, "load was refused");
        int work = CannonConfig.CREW_SWIVEL_LOAD_TICKS.get();
        h.runAfterDelay(work - 10, () -> h.assertTrue(swivelLoad(h, s) == CannonLoad.EMPTY, "loaded before the work time"));
        h.runAfterDelay(work + 2, () -> {
            h.assertTrue(swivelLoad(h, s) == CannonLoad.LOADED, "the swivel is not loaded: " + swivelLoad(h, s));
            assertChest(h, s, 1, 1);
            Container c = chest(h, s);
            h.assertTrue(c.getItem(0).isEmpty() && c.getItem(1).isEmpty() && c.getItem(3).getCount() == 1,
                    "not taken from the first stacks first");
            if (!(h.getLevel().getBlockEntity(s.swivel()) instanceof SwivelGunBlockEntity be)) {
                throw new GameTestAssertException("no swivel block entity");
            }
            h.assertTrue(be.shot().is(ball) && be.shot().getCount() == 3, "the recorded shot is " + be.shot());
            cleanup(h, gunner);
            h.succeed();
        });
    }
}

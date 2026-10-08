package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The armed sloops and their stocked guns (WS4c) in 48×48 water basins (stone floor, water y=2..7, the barrier ceiling
 * removed for the mast): the armed navy sloop floats upright with its four guns, its gun crews are seated at all four
 * and load from its shot locker; a materialised navy or pirate voyage appears with {@code cannon_rounds} rounds per gun
 * in the locker and loaded guns, a merchant on the same hull with none, and dematerialising ignores the ammunition.
 * Each test has its own batch (the stocking tests change or read config values).
 */
public final class ArmedShipGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_armed_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_worldsim_armed_";
    private static final int SURFACE = 7;

    private ArmedShipGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ArmedShipGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void basin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 9; y <= 20; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /** A SAILING test voyage of {@code template} in the basin's middle, heading north, 100 blocks along its route. */
    private static Voyage voyage(GameTestHelper h, VoyageKind kind, Faction faction, ResourceLocation template,
                                 Map<ResourceLocation, Integer> cargo) {
        BlockPos m = h.absolutePos(new BlockPos(24, SURFACE, 24));
        List<Lane.Point> route = List.of(new Lane.Point(m.getX(), m.getZ() + 100), new Lane.Point(m.getX(), m.getZ() - 100));
        ResourceLocation from = Constants.id("gametest/ws4c_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), kind, faction, template, from, from, route, cargo, h.getLevel().getGameTime())
                .withProgress(100);
        VoyageData.get(h.getLevel().getServer()).put(v);
        return v;
    }

    private static ShipBody materialize(GameTestHelper h, Voyage v) {
        MinecraftServer server = h.getLevel().getServer();
        Materializer.Outcome o = Materializer.materialize(server, v.id());
        Voyage m = Voyages.get(server, v.id()).orElseThrow();
        m.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
        h.assertTrue(o == Materializer.Outcome.SPAWNED, "materialize: " + o);
        ShipBody ship = SableShips.byId(h.getLevel(), m.shipId().orElseThrow());
        h.assertTrue(ship != null, "no ship");
        return ship;
    }

    private static void finish(GameTestHelper h, Voyage v) {
        Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
        VoyageCrew.discard(h.getLevel(), v.id());
    }

    private static ResourceLocation firstDefault(List<String> templates) {
        return ResourceLocation.parse(templates.get(0));
    }

    private static Container locker(GameTestHelper h, ShipBody ship) {
        List<BlockPos> lockers = VoyageGuns.lockers(h.getLevel(), ship);
        h.assertTrue(lockers.size() == 1, "shot lockers " + lockers);
        if (h.getLevel().getBlockEntity(lockers.get(0)) instanceof Container c) return c;
        throw new GameTestAssertException("no container at " + lockers.get(0));
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static List<String> loads(GameTestHelper h, ShipBody ship) {
        List<String> out = new ArrayList<>();
        for (BlockPos g : VoyageGuns.guns(h.getLevel(), ship)) out.add(VoyageGuns.describe(h.getLevel(), g));
        return out;
    }

    /** Roll (+ = starboard side up) and pitch (+ = bow up) [degrees] of a sloop bow-north template, from its deck row. */
    private static double[] attitude(ShipBody ship) {
        BlockPos helm = ShipHelm.steering(ship);
        Vec3 port = ship.toWorld(Vec3.atCenterOf(helm.offset(-4, -4, -8)));
        Vec3 starboard = ship.toWorld(Vec3.atCenterOf(helm.offset(4, -4, -8)));
        Vec3 bow = ship.toWorld(Vec3.atCenterOf(helm.offset(0, -4, -19)));
        Vec3 stern = ship.toWorld(Vec3.atCenterOf(helm.offset(0, -4, 4)));
        double roll = Math.toDegrees(Math.atan2(starboard.y - port.y, Math.hypot(starboard.x - port.x, starboard.z - port.z)));
        double pitch = Math.toDegrees(Math.atan2(bow.y - stern.y, Math.hypot(bow.x - stern.x, bow.z - stern.z)));
        return new double[] {roll, pitch};
    }

    // ------------------------------------------------------------------ the template

    /**
     * The armed navy sloop placed and assembled from its template: four guns (masters facing outboard) and one shot
     * locker within reach of all of them; after 120 ticks afloat it lies upright (roll and pitch under 2°) with its
     * main deck clear of the water; a crew member is seated at each gun (WS4a's station spots beside the carriage), and
     * the crew of one gun loads it from the locker.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = BATCH + "template")
    public static void armedSloopFloatsUprightAndItsGunsAreManned(GameTestHelper h) {
        basin(h);
        BlockPos feet = h.absolutePos(new BlockPos(24, 8, 44));
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.NAVY_SLOOP_ARMED_ID, feet, Direction.NORTH,
                true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException("not placed and assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        h.assertTrue(ship != null, "no ship");
        List<BlockPos> guns = VoyageGuns.guns(h.getLevel(), ship);
        h.assertTrue(guns.size() == 4, "guns " + guns);
        h.assertTrue(loads(h, ship).stream().allMatch("empty"::equals), "a template gun is loaded: " + loads(h, ship));
        List<BlockPos> lockers = VoyageGuns.lockers(h.getLevel(), ship);
        int[] of = VoyageGuns.lockerOf(guns, lockers);
        h.assertTrue(lockers.size() == 1 && java.util.Arrays.stream(of).allMatch(i -> i == 0),
                "lockers " + lockers + ", locker of each gun " + java.util.Arrays.toString(of));
        double[] max = {0, 0};
        h.onEachTick(() -> {
            if (h.getTick() < 80 || ship.isRemoved()) return;
            double[] at = attitude(ship);
            max[0] = Math.max(max[0], Math.abs(at[0]));
            max[1] = Math.max(max[1], Math.abs(at[1]));
        });
        List<CrewMember> crew = new ArrayList<>();
        h.runAfterDelay(120, () -> {
            double[] at = attitude(ship);
            Vec3 deck = ship.toWorld(Vec3.atLowerCornerOf(ShipHelm.steering(ship).offset(0, -4, -8)));
            double water = h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() + 1.0;
            Constants.LOG.info("[armed sloop] roll {}°, pitch {}° (largest over ticks 80..120: {}°, {}°), deck bottom {} above the water",
                    fmt(at[0]), fmt(at[1]), fmt(max[0]), fmt(max[1]), fmt(deck.y - water));
            h.assertTrue(max[0] < 2.0 && max[1] < 2.0, "not upright: roll " + max[0] + "°, pitch " + max[1] + "°");
            h.assertTrue(deck.y - water > 0.5, "the main deck is awash: " + (deck.y - water));
            for (BlockPos g : guns) {
                CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
                if (c == null) throw new GameTestAssertException("no crew member");
                Vec3 p = ship.toWorld(Vec3.atBottomCenterOf(ShipHelm.steering(ship).offset(-1, -3, -8)));
                c.moveTo(p.x, p.y, p.z, 0, 0);
                h.getLevel().addFreshEntity(c);
                crew.add(c);
                CrewStations.AssignResult res = CrewStations.assign(h.getLevel(), c, g);
                h.assertTrue(res == CrewStations.AssignResult.ASSIGNED, "gun " + g + ": " + res);
            }
            Container box = locker(h, ship);
            box.setItem(0, new ItemStack(Items.GUNPOWDER, 1));
            box.setItem(1, new ItemStack(CombatContent.CANNONBALL.get(), 1));
            CrewStations.order(h.getLevel(), crew.get(0), CannonOrder.LOAD);
        });
        h.succeedWhen(() -> {
            if (crew.isEmpty()) throw new GameTestAssertException("settling");
            h.assertTrue(h.getLevel().getBlockState(guns.get(0)).getValue(CannonBlock.LOAD) == CannonLoad.LOADED,
                    "the crew has not loaded gun " + guns.get(0) + " from the locker: " + loads(h, ship));
            Container box = locker(h, ship);
            h.assertTrue(count(box, Items.GUNPOWDER) == 0 && count(box, CombatContent.CANNONBALL.get()) == 0, "the locker was not used");
            crew.forEach(c -> {
                if (c.assignment() != null) CrewStations.release(h.getLevel(), c);
                c.discard();
            });
        });
    }

    // ------------------------------------------------------------------ stocking

    /**
     * A navy patrol on the armed sloop with {@code cannon_rounds} 5: its locker holds 4 × 5 gunpowder and cannonballs
     * and every gun is loaded; dematerialised, the record's cargo is still empty (the ammunition is not cargo).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = CONFIG_BATCH + "rounds")
    public static void aNavyPatrolAppearsWithStockedLoadedGuns(GameTestHelper h) {
        ConfigOverrides.during(h, MaterializeConfig.CANNON_ROUNDS, 5);
        basin(h);
        Voyage v = voyage(h, VoyageKind.PATROL, Faction.NAVY, ShipTemplates.NAVY_SLOOP_ARMED_ID, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            Container box = locker(h, ship[0]);
            h.assertTrue(count(box, Items.GUNPOWDER) == 20 && count(box, CombatContent.CANNONBALL.get()) == 20,
                    "locker: " + count(box, Items.GUNPOWDER) + " powder, " + count(box, CombatContent.CANNONBALL.get()) + " shot");
            h.assertTrue(loads(h, ship[0]).equals(List.of("loaded", "loaded", "loaded", "loaded")), "guns " + loads(h, ship[0]));
            h.assertTrue(VoyageShips.read(h.getLevel(), ship[0]).isEmpty(), "ammunition read as cargo: " + VoyageShips.read(h.getLevel(), ship[0]));
            h.assertTrue(Materializer.dematerialize(h.getLevel().getServer(), v.id()), "dematerialize refused");
            Voyage r = Voyages.get(h.getLevel().getServer(), v.id()).orElseThrow();
            h.assertTrue(r.cargo().isEmpty(), "the record carries the ammunition as cargo: " + r.cargo());
            finish(h, v);
            h.succeed();
        });
    }

    /**
     * The default pirate template is the armed pirate sloop, and a pirate raider on it appears with the default
     * {@code cannon_rounds} (12) per gun and loaded guns.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "pirate")
    public static void aPirateRaiderAppearsWithTheDefaultRounds(GameTestHelper h) {
        basin(h);
        ResourceLocation template = firstDefault(VoyageConfig.PIRATE_TEMPLATES.defaultValue());
        h.assertTrue(template.equals(ShipTemplates.PIRATE_SLOOP_ARMED_ID), "default pirate template " + template);
        h.assertTrue(firstDefault(VoyageConfig.NAVY_TEMPLATES.defaultValue()).equals(ShipTemplates.NAVY_SLOOP_ARMED_ID),
                "default navy template " + VoyageConfig.NAVY_TEMPLATES.defaultValue());
        Voyage v = voyage(h, VoyageKind.RAID, Faction.PIRATES, template, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            int rounds = 4 * MaterializeConfig.CANNON_ROUNDS.defaultValue();
            Container box = locker(h, ship[0]);
            h.assertTrue(count(box, Items.GUNPOWDER) == rounds && count(box, CombatContent.CANNONBALL.get()) == rounds,
                    "locker: " + count(box, Items.GUNPOWDER) + " powder, " + count(box, CombatContent.CANNONBALL.get()) + " shot, expected " + rounds);
            h.assertTrue(loads(h, ship[0]).stream().allMatch("loaded"::equals), "guns " + loads(h, ship[0]));
            finish(h, v);
            h.succeed();
        });
    }

    /** A merchant convoy on the same armed hull carries no powder and shot, its guns stay empty, its cargo is as given. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "merchant")
    public static void aMerchantOnAnArmedHullCarriesNoShot(GameTestHelper h) {
        basin(h);
        Map<ResourceLocation, Integer> cargo = new LinkedHashMap<>();
        cargo.put(TradeGoods.SUGAR, 64);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, ShipTemplates.NAVY_SLOOP_ARMED_ID, cargo);
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            Container box = locker(h, ship[0]);
            h.assertTrue(box.isEmpty(), "a merchant's locker holds " + count(box, Items.GUNPOWDER) + " powder");
            h.assertTrue(loads(h, ship[0]).stream().allMatch("empty"::equals), "guns " + loads(h, ship[0]));
            h.assertTrue(VoyageShips.read(h.getLevel(), ship[0]).equals(cargo), "cargo " + VoyageShips.read(h.getLevel(), ship[0]));
            finish(h, v);
            h.succeed();
        });
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}

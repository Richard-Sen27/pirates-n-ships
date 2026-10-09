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
import com.richardsenger.piratesnships.station.lookout.CrowsNestBlock;
import com.richardsenger.piratesnships.station.lookout.Lookouts;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
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
 * TPL2: a navy patrol keeps a lookout in its crow's nest ({@code man_lookout}), and a player climbs the ratlines from
 * the quarterdeck to the nest.
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

    /** World y of the bottom of the main deck row amidships (template [4, 4, 14]). */
    private static double deckY(ShipBody ship) {
        return ship.toWorld(Vec3.atLowerCornerOf(ShipHelm.steering(ship).offset(0, -4, -8))).y;
    }

    // ------------------------------------------------------------------ the template

    /**
     * The armed navy sloop placed and assembled from its template beside the plain starter sloop (the baseline): four
     * guns (masters facing outboard) and one shot locker within reach of all of them; after 120 ticks afloat it lies
     * like the plain sloop (roll within 1°, pitch within 1.5°, draft within a quarter block; measured 0.01°, 0.9° and
     * 0.14: the guns sit forward of the centre of mass and ease the plain sloop's 4° bow-up trim, which is SH1's
     * business, not the guns'), with no list (roll under 2°); a crew
     * member is seated at each gun (WS4a's station spots beside the carriage), and the crew of one gun loads it from the
     * locker.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = BATCH + "template")
    public static void armedSloopFloatsUprightAndItsGunsAreManned(GameTestHelper h) {
        basin(h);
        ShipTemplatePlacer.Result plain = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.STARTER_SLOOP_ID,
                h.absolutePos(new BlockPos(35, 8, 44)), Direction.NORTH, true, true, null);
        if (plain.assembly() == null || plain.assembly().shipId() == null) throw new GameTestAssertException("no plain sloop: " + plain.outcome());
        ShipTestCleanup.track(h, plain.assembly().shipId());
        ShipBody base = SableShips.byId(h.getLevel(), plain.assembly().shipId());
        h.assertTrue(base != null, "no plain sloop");
        BlockPos feet = h.absolutePos(new BlockPos(12, 8, 44));
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
        double[] max = {0, 0, 0, 0}; // largest |roll|, |roll - plain|, |pitch - plain|, |draft - plain| over ticks 80..120
        h.onEachTick(() -> {
            if (h.getTick() < 80 || h.getTick() > 120 || ship.isRemoved() || base.isRemoved()) return;
            double[] at = attitude(ship);
            double[] ref = attitude(base);
            max[0] = Math.max(max[0], Math.abs(at[0]));
            max[1] = Math.max(max[1], Math.abs(at[0] - ref[0]));
            max[2] = Math.max(max[2], Math.abs(at[1] - ref[1]));
            max[3] = Math.max(max[3], Math.abs(deckY(ship) - deckY(base)));
        });
        List<CrewMember> crew = new ArrayList<>();
        h.runAfterDelay(120, () -> {
            double[] at = attitude(ship);
            double[] ref = attitude(base);
            double water = h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() + 1.0;
            Constants.LOG.info("[armed sloop] armed: roll {}°, pitch {}°, deck {} above the water; plain: roll {}°, pitch {}°, deck {}; "
                            + "largest over ticks 80..120: |roll| {}°, roll diff {}°, pitch diff {}°, draft diff {}",
                    fmt(at[0]), fmt(at[1]), fmt(deckY(ship) - water), fmt(ref[0]), fmt(ref[1]), fmt(deckY(base) - water),
                    fmt(max[0]), fmt(max[1]), fmt(max[2]), fmt(max[3]));
            h.assertTrue(max[0] < 2.0, "the armed sloop lists: " + max[0] + "°");
            h.assertTrue(max[1] < 1.0 && max[2] < 1.5, "the guns change the trim: roll " + max[1] + "°, pitch " + max[2] + "° off the plain sloop");
            h.assertTrue(max[3] < 0.25, "the guns sink the hull: " + max[3] + " blocks deeper than the plain sloop");
            h.assertTrue(deckY(ship) - water > 0.0, "the main deck is awash: " + (deckY(ship) - water));
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

    // ------------------------------------------------------------------ the lookout (TPL2)

    /** The crew members of {@code v} aboard, by whether they sit in the ship's crow's nest. */
    private static List<CrewMember> crewInTheNest(GameTestHelper h, ShipBody ship, Voyage v, boolean inNest) {
        BlockPos nest = VoyageCrew.nest(h.getLevel(), ship);
        List<CrewMember> out = new ArrayList<>();
        for (var e : VoyageCrew.alive(h.getLevel(), ship, v.id(), VoyageCrew.CREW_TAG)) {
            if (!(e instanceof CrewMember c)) continue;
            boolean there = nest != null && c.assignment() != null && c.assignment().pos().equals(nest);
            if (there == inNest) out.add(c);
        }
        return out;
    }

    /**
     * TPL2: a navy patrol on the armed sloop appears with one of its deckhands seated in the crow's nest, pinned there
     * (the job board leaves him), so {@link Lookouts#isManned} sees the nest manned (LAW4's bonus applies); the crew is
     * still {@code crew_per_ship} + the helmsman, who keeps the helm.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "lookout")
    public static void aNavyPatrolKeepsALookoutInItsNest(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.PATROL, Faction.NAVY, ShipTemplates.NAVY_SLOOP_ARMED_ID, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(25, () -> {
            BlockPos nest = VoyageCrew.nest(h.getLevel(), ship[0]);
            h.assertTrue(nest != null && h.getLevel().getBlockState(nest).getBlock() instanceof CrowsNestBlock, "no crow's nest on the patrol");
            List<CrewMember> up = crewInTheNest(h, ship[0], v, true);
            List<CrewMember> down = crewInTheNest(h, ship[0], v, false);
            h.assertTrue(up.size() == 1, "lookouts in the nest: " + up.size());
            CrewMember lookout = up.get(0);
            h.assertTrue(lookout.isAtStation() && lookout.isPinned(), "the lookout is not seated and pinned: seated "
                    + lookout.isAtStation() + ", pinned " + lookout.isPinned());
            Vec3 floor = ship[0].toWorld(Vec3.atBottomCenterOf(nest));
            h.assertTrue(lookout.position().distanceTo(floor) < 0.6, "the lookout stands at " + lookout.position() + ", the nest at " + floor);
            h.assertTrue(Lookouts.isManned(h.getLevel(), ship[0]), "Lookouts.isManned does not see the voyage's lookout");
            h.assertValueEqual(up.size() + down.size(), MaterializeConfig.CREW_PER_SHIP.get() + 1, "crew aboard");
            BlockPos helm = ShipHelm.steering(ship[0]);
            h.assertTrue(down.stream().anyMatch(c -> c.assignment() != null && c.assignment().pos().equals(helm) && c.isAtStation()),
                    "nobody at the helm");
            finish(h, v);
            h.succeed();
        });
    }

    /** TPL2: with {@code materialize.man_lookout} off nobody goes up: the nest stays empty and unmanned, the crew is the same. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = CONFIG_BATCH + "lookout")
    public static void withManLookoutOffTheNestStaysEmpty(GameTestHelper h) {
        ConfigOverrides.during(h, MaterializeConfig.MAN_LOOKOUT, false);
        basin(h);
        Voyage v = voyage(h, VoyageKind.PATROL, Faction.NAVY, ShipTemplates.NAVY_SLOOP_ARMED_ID, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(25, () -> {
            h.assertTrue(VoyageCrew.nest(h.getLevel(), ship[0]) != null, "no crow's nest on the patrol");
            h.assertTrue(crewInTheNest(h, ship[0], v, true).isEmpty(), "somebody sits in the nest");
            h.assertFalse(Lookouts.isManned(h.getLevel(), ship[0]), "the nest counts as manned");
            h.assertValueEqual(crewInTheNest(h, ship[0], v, false).size(), MaterializeConfig.CREW_PER_SHIP.get() + 1, "crew aboard");
            finish(h, v);
            h.succeed();
        });
    }

    /**
     * TPL2: a mock player climbs the port ratlines of the armed navy sloop, assembled and afloat, from the quarterdeck
     * to the crow's nest, steering as a player would (the RL1 climb pattern): up the sloped run walking toward the bow
     * (it rises like a stair) onto the upper yard's end at its top, stopping there, then facing the mast and holding
     * jump and forward up the net hung on it, and over into the nest. Each phase is chosen from the player's position in
     * the ship's frame. (Measured: the slope takes about 45 ticks, the hung net 25; the whole climb about 90.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = BATCH + "ratlines")
    public static void aPlayerClimbsTheRatlinesToTheNest(GameTestHelper h) {
        basin(h);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.NAVY_SLOOP_ARMED_ID,
                h.absolutePos(new BlockPos(24, 8, 44)), Direction.NORTH, true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException("not placed and assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        h.assertTrue(ship != null, "no ship");
        // template cell -> plot: the template's helm is at [4, 8, 22]
        BlockPos offset = ShipHelm.steering(ship).subtract(new BlockPos(4, 8, 22));
        BlockPos foot = new BlockPos(3, 8, 22).offset(offset);
        BlockPos nest = new BlockPos(4, 20, 13).offset(offset);
        h.assertTrue(h.getLevel().getBlockState(nest).getBlock() instanceof CrowsNestBlock, "no nest at " + nest);
        Player[] player = {null};
        double[] start = {0};
        String[] phase = {"walk"};
        double[] highest = {0};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 40) { // settled afloat (the RL1 tests' SETTLE)
                Vec3 at = ship.toWorld(new Vec3(foot.getX() + 0.5, foot.getY() + 0.02, foot.getZ() + 1.5));
                Player p = h.makeMockPlayer(GameType.SURVIVAL);
                p.moveTo(at.x, at.y, at.z, 0f, 0f);
                h.getLevel().addFreshEntity(p);
                player[0] = p;
                start[0] = ship.toPlot(p.position()).y;
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) return;
            Vec3 local = ship.toPlot(p.position());
            highest[0] = Math.max(highest[0], local.y);
            // keeps the climber on the hung net's column (z of the mast) while he faces the mast
            double toColumn = Math.max(-1, Math.min(1, 2 * (nest.getZ() + 0.5 - local.z)));
            switch (phase[0]) {
                case "walk" -> { // up the slope toward the bow, onto the yard's end
                    face(p, ship, local, 0, -1);
                    p.setJumping(false);
                    p.zza = 1f;
                    if (local.z < nest.getZ() + 0.65 && local.y > nest.getY() - 4.5) phase[0] = "stand";
                }
                case "stand" -> { // stop on the yard's end under the hung net
                    face(p, ship, local, 0, -1);
                    p.setJumping(false);
                    p.zza = 0f;
                    Vec3 dm = p.getDeltaMovement();
                    if (p.onGround() && Math.hypot(dm.x, dm.z) < 0.01) phase[0] = "climb";
                }
                case "climb" -> { // face the mast (east of the port run) and climb the hung net
                    face(p, ship, local, 1, toColumn);
                    p.setJumping(true);
                    p.zza = 1f;
                    if (local.y >= nest.getY()) phase[0] = "step";
                }
                default -> { // over into the nest
                    face(p, ship, local, 1, 0);
                    p.setJumping(false);
                    p.zza = 1f;
                }
            }
        });
        h.succeedWhen(() -> {
            Player p = player[0];
            if (p == null) throw new GameTestAssertException("settling");
            Lookouts.Nest in = Lookouts.nestOf(h.getLevel(), p);
            if (in == null || !in.pos().equals(nest)) {
                throw new GameTestAssertException("not in the nest yet: phase " + phase[0] + ", at " + ship.toPlot(p.position())
                        + " (nest " + nest + "), highest " + highest[0] + ", started at " + start[0]);
            }
            Constants.LOG.info("[TPL2] climbed from {} to the nest at {} in the ship's frame", start[0], nest);
            h.assertTrue(Lookouts.isManned(h.getLevel(), ship), "the player in the nest does not man it");
            p.discard();
        });
    }

    /** Turns {@code p} to look along the ship's plot direction ({@code dx}, {@code dz}) in world space. */
    private static void face(Player p, ShipBody ship, Vec3 local, double dx, double dz) {
        Vec3 a = ship.toWorld(local);
        Vec3 b = ship.toWorld(local.add(dx, 0, dz));
        float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}

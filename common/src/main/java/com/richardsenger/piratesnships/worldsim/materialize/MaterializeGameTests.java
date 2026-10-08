package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.helm.CourseEvent;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WS3b GameTests in 48×48 basins (stone floor, water y=2..7, the barrier ceiling removed for the sloop's mast). Test
 * voyages come from {@code gametest/ws3b_…} ports that are not registered (the overworld; the automatic check leaves
 * them alone), with a lane through the basin's middle at a 30° heading, and are driven by calling
 * {@link Materializer#materialize}, {@link Materializer#update} and {@link Materializer#dematerialize} directly.
 */
public final class MaterializeGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_materialize_";
    private static final double HEADING = 30.0;
    private static final int SURFACE = 7;
    private static final Map<UUID, List<VoyageEndings.Ending>> ENDINGS = new ConcurrentHashMap<>();

    static {
        VoyageEndings.onEnding(e -> {
            if (Materializer.isTest(e.voyage())) ENDINGS.computeIfAbsent(e.voyage().id(), k -> new CopyOnWriteArrayList<>()).add(e);
        });
    }

    private MaterializeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MaterializeGameTests.class);
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
                for (int y = 9; y <= 18; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /** The lane point in the basin's middle (world x, z). */
    private static Vec3 middle(GameTestHelper h) {
        BlockPos c = h.absolutePos(new BlockPos(24, SURFACE, 24));
        return new Vec3(c.getX(), c.getY(), c.getZ());
    }

    /**
     * A SAILING test voyage whose position is the basin's middle, heading {@link #HEADING}: from 100 blocks behind,
     * through a waypoint {@code 60} blocks ahead, to 200 blocks ahead.
     */
    private static Voyage voyage(GameTestHelper h, VoyageKind kind, Faction faction, Map<ResourceLocation, Integer> cargo) {
        Vec3 m = middle(h);
        double dx = Math.sin(Math.toRadians(HEADING));
        double dz = -Math.cos(Math.toRadians(HEADING));
        List<Lane.Point> route = List.of(point(m, dx, dz, -100), point(m, dx, dz, 60), point(m, dx, dz, 200));
        ResourceLocation from = Constants.id("gametest/ws3b_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), kind, faction, ShipTemplates.STARTER_SLOOP_ID, from, from, route, cargo,
                h.getLevel().getGameTime());
        // the exact progress of the middle (the points are rounded to blocks)
        v = v.withProgress(RouteMath.project(route, m.x, m.z, 100));
        VoyageData.get(h.getLevel().getServer()).put(v);
        return v;
    }

    private static Lane.Point point(Vec3 m, double dx, double dz, double d) {
        return new Lane.Point((int) Math.round(m.x + dx * d), (int) Math.round(m.z + dz * d));
    }

    private static Map<ResourceLocation, Integer> convoyCargo() {
        Map<ResourceLocation, Integer> cargo = new LinkedHashMap<>();
        cargo.put(TradeGoods.SUGAR, 64);
        cargo.put(TradeGoods.IRON, 32);
        return cargo;
    }

    /** Materialises {@code v} and tracks its ship for removal; fails the test unless it appeared. */
    private static ShipBody materialize(GameTestHelper h, Voyage v) {
        MinecraftServer server = h.getLevel().getServer();
        Materializer.Outcome o = Materializer.materialize(server, v.id());
        Voyage m = Voyages.get(server, v.id()).orElseThrow();
        m.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
        h.assertTrue(o == Materializer.Outcome.SPAWNED, "materialize: " + o);
        h.assertTrue(m.state() == Voyage.State.MATERIALISED, "state " + m.state());
        ShipBody ship = SableShips.byId(h.getLevel(), m.shipId().orElseThrow());
        h.assertTrue(ship != null, "no ship");
        return ship;
    }

    /** Ends the voyage (its ship and people go with it) and every leftover of it. */
    private static void finish(GameTestHelper h, Voyage v) {
        Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
        VoyageCrew.discard(h.getLevel(), v.id());
        ENDINGS.remove(v.id());
    }

    private static List<LivingEntity> people(GameTestHelper h, ShipBody ship, Voyage v, String role) {
        return VoyageCrew.alive(h.getLevel(), ship, v.id(), role);
    }

    private static int tagged(ServerLevel level, UUID voyage) {
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            if (e != null && e.isAlive() && e.getTags().contains(VoyageCrew.voyageTag(voyage))) n++;
        }
        return n;
    }

    private static double angle(double a, double b) {
        double d = Math.abs(((a - b) % 360 + 540) % 360 - 180);
        return d;
    }

    /** A mock player in the level, standing on the deck next to the helm (moved along every tick by the caller). */
    private static Player boarder(GameTestHelper h, ShipBody ship) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        keepAboard(p, ship);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    private static void keepAboard(Player p, ShipBody ship) {
        BlockPos helm = ShipHelm.steering(ship);
        Vec3 at = ship.toWorld(Vec3.atBottomCenterOf(helm.east()));
        p.moveTo(at.x, at.y + 0.05, at.z, 0f, 0f);
    }

    private static void flood(GameTestHelper h, ShipBody ship) {
        HullRuntime rt = HullRuntimes.get(h.getLevel(), ship.id());
        if (rt == null) return;
        for (Compartment c : rt.simulation().analysis().compartments()) rt.simulation().setVolume(c.id(), c.volume());
    }

    // ------------------------------------------------------------------ tests
    //
    // Every runAtTickTime / onEachTick is registered in the test body: GameTestInfo iterates its task map while
    // running a task, so registering from inside a task corrupts the iteration (a task ran twice, the server crashed).

    /**
     * A convoy appears on its lane point (centre within 2 blocks), turned to the leg heading (within 10°), under the
     * merchant flag, with its cargo in the containers, crew_per_ship + 1 crew and fighters_merchant sailors, linked in
     * the record and in the ship's user data. Ending the voyage takes ship and people along.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "place")
    public static void convoyAppearsOnItsLane(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            Vec3 c = Materializer.centre(ship[0]);
            Lane.Position p = v.position();
            double off = Math.hypot(c.x - p.x(), c.z - p.z());
            h.assertTrue(off <= 2.0, "centre " + off + " blocks from the lane point");
        });
        h.runAtTickTime(15, () -> {
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship[0]);
            double heading = rt.headingDegrees(ship[0]);
            h.assertTrue(angle(heading, HEADING) <= 10.0, "heading " + heading + ", leg " + HEADING);
            h.assertTrue(ShipAllegiance.of(ship[0]).kind() == FlagKind.MERCHANT, "flag " + ShipAllegiance.of(ship[0]));
            Map<ResourceLocation, Integer> held = VoyageShips.read(h.getLevel(), ship[0]);
            h.assertTrue(held.equals(convoyCargo()), "cargo aboard " + held);
            int crew = people(h, ship[0], v, VoyageCrew.CREW_TAG).size();
            List<LivingEntity> fighters = people(h, ship[0], v, VoyageCrew.FIGHTER_TAG);
            h.assertTrue(crew == MaterializeConfig.CREW_PER_SHIP.get() + 1, "crew " + crew);
            h.assertTrue(fighters.size() == MaterializeConfig.FIGHTERS_MERCHANT.get()
                    && fighters.stream().allMatch(f -> f instanceof Sailor s && s.isStationary()), "fighters " + fighters);
            h.assertTrue(VoyageShips.readLink(ship[0]).map(VoyageLink::voyage).equals(Optional.of(v.id())), "user data link");
            h.assertTrue(VoyageShips.voyageOf(ship[0].id()).equals(Optional.of(v.id())), "memory link");
            h.assertTrue(ShipRegistry.get(h.getLevel().getServer()).find(ship[0].id()).map(ShipData::name).filter(n -> !n.isEmpty()).isPresent(),
                    "unnamed");
            finish(h, v);
            h.assertTrue(SableShips.byId(h.getLevel(), ship[0].id()) == null, "ship still there after the voyage ended");
            h.assertTrue(tagged(h.getLevel(), v.id()) == 0, "people of the voyage left behind");
            h.succeed();
        });
    }

    /** A pirate voyage flies the Jolly Roger and carries fighters_pirate pirates; a navy one has its officer. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "place")
    public static void piratesFlyTheJollyRoger(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.RAID, Faction.PIRATES, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            h.assertTrue(ShipAllegiance.of(ship[0]).kind() == FlagKind.JOLLY_ROGER, "flag " + ShipAllegiance.of(ship[0]));
            List<LivingEntity> fighters = people(h, ship[0], v, VoyageCrew.FIGHTER_TAG);
            h.assertTrue(fighters.size() == MaterializeConfig.FIGHTERS_PIRATE.get()
                    && fighters.stream().allMatch(f -> f instanceof Pirate), "fighters " + fighters);
            Entity officer = VoyageCrew.fighterType(Faction.NAVY, 0).create(h.getLevel());
            h.assertTrue(officer instanceof NavyOfficer, "navy officer first");
            officer.discard();
            finish(h, v);
            h.succeed();
        });
    }

    /**
     * Dematerialised after the ship was moved 6 blocks along its heading: the ship and every person are gone, the
     * record sails again with its progress 6 blocks further, its cargo and its crew and fighter counts.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "demat")
    public static void dematerialisedShipBecomesARecordAgain(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            Vec3 c = Materializer.centre(ship[0]);
            double before = RouteMath.project(v.waypoints(), c.x, c.z, v.progress());
            Vec3 plot = Materializer.plotCentre(ship[0]);
            double dx = Math.sin(Math.toRadians(HEADING)) * 6, dz = -Math.cos(Math.toRadians(HEADING)) * 6;
            ship[0].placeAt(plot, c.add(dx, 0, dz), ship[0].orientation());
            h.assertTrue(Materializer.dematerialize(server, v.id()), "dematerialize refused");
            Voyage r = Voyages.get(server, v.id()).orElseThrow();
            h.assertTrue(SableShips.byId(h.getLevel(), ship[0].id()) == null, "ship still there");
            h.assertTrue(r.state() == Voyage.State.SAILING && r.shipId().isEmpty(), "record " + r.state());
            h.assertTrue(Math.abs(r.progress() - (before + 6)) <= 1.0, "progress " + r.progress() + ", expected " + (before + 6));
            h.assertTrue(r.cargo().equals(convoyCargo()), "cargo " + r.cargo());
            h.assertTrue(r.crew() == MaterializeConfig.CREW_PER_SHIP.get() + 1 && r.fighters() == MaterializeConfig.FIGHTERS_MERCHANT.get(),
                    "crew " + r.crew() + ", fighters " + r.fighters());
            h.assertTrue(r.health() > 0.9, "health " + r.health());
            h.assertTrue(tagged(h.getLevel(), v.id()) == 0, "people of the voyage left behind");
            finish(h, v);
            h.succeed();
        });
    }

    /** After a restart (links forgotten, course lost) the loaded ship is adopted by its user data and steers again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "demat")
    public static void shipIsAdoptedAgainAfterAReload(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            UUID id = ship[0].id();
            VoyageShips.remove(v.id()); // as after a restart: only the record and the user data know the link
            HelmCourses.clear(h.getLevel(), id);
            h.assertTrue(HelmCourses.course(id) == null, "course not cleared");
            Materializer.adopt(server);
            h.assertTrue(VoyageShips.voyageOf(id).equals(Optional.of(v.id())), "not adopted");
            Materializer.update(server, v.id());
            h.assertTrue(HelmCourses.course(id) != null, "no course after the adoption");
            h.assertTrue(SableShips.byId(h.getLevel(), id) != null, "the adopted ship was removed");
            finish(h, v);
            h.succeed();
        });
    }

    /** A stuck ship dematerialises and its record skips to the waypoint it was heading for. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "demat")
    public static void stuckShipSkipsAWaypoint(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            Materializer.onCourseEvent(new CourseEvent(h.getLevel(), ship[0].id(), CourseEvent.Type.STUCK, 1));
            Materializer.update(server, v.id());
            Voyage r = Voyages.get(server, v.id()).orElseThrow();
            double waypoint = RouteMath.progressAt(v.waypoints(), 1);
            h.assertTrue(SableShips.byId(h.getLevel(), ship[0].id()) == null, "ship still there");
            h.assertTrue(r.state() == Voyage.State.SAILING, "state " + r.state());
            h.assertTrue(Math.abs(r.progress() - waypoint) < 0.01, "progress " + r.progress() + ", waypoint at " + waypoint);
            finish(h, v);
            h.succeed();
        });
    }

    /** Floods the ship and updates it every tick from tick 10 until an ending is heard, then checks it. */
    private static void sinkRun(GameTestHelper h, Voyage v, ShipBody[] ship, java.util.function.Consumer<VoyageEndings.Ending> check) {
        MinecraftServer server = h.getLevel().getServer();
        h.onEachTick(() -> {
            if (h.getTick() < 10 || ship[0] == null) return;
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            if (e.isEmpty()) {
                flood(h, ship[0]);
                Materializer.update(server, v.id());
                return;
            }
            check.accept(e.get(0));
            h.assertTrue(Voyages.get(server, v.id()).isEmpty(), "voyage still active");
            h.assertTrue(VoyageShips.readLink(ship[0]).isEmpty(), "the wreck keeps its link");
            h.assertTrue(SableShips.byId(h.getLevel(), ship[0].id()) != null, "the wreck was removed");
            finish(h, v);
            h.succeed();
        });
    }

    /** Flooded past sunk_flood_fraction after a player's hit: SUNK, the sink_merchant deed for that player. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "sink")
    public static void floodedConvoySinksAndBlamesTheShooter(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        UUID shooter = UUID.randomUUID();
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            VoyageEndings.recordHit(ship[0].id(), shooter, h.getLevel().getGameTime());
        });
        sinkRun(h, v, ship, end -> {
            h.assertTrue(end.outcome() == VoyageEndings.Outcome.SUNK, "outcome " + end.outcome());
            h.assertTrue(end.player().equals(Optional.of(shooter)), "blamed " + end.player());
            h.assertTrue(end.deed() == VoyageDeed.SINK_MERCHANT && end.worldEvent() == null, "deed " + end.deed());
        });
    }

    /** A navy ship sinking with nobody to blame reports the world event patrol_lost and no deed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "sink")
    public static void navyShipSunkByNobodyIsAWorldEvent(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.PATROL, Faction.NAVY, Map.of());
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        sinkRun(h, v, ship, end -> {
            h.assertTrue(end.outcome() == VoyageEndings.Outcome.SUNK && end.player().isEmpty(), "ending " + end);
            h.assertTrue(end.deed() == null && end.worldEvent() == FactionEvent.PATROL_LOST, "event " + end.worldEvent());
        });
    }

    /**
     * A player aboard takes 10 units: the plunder_merchant deed once, the record's cargo follows; a second take is no
     * second deed. The voyage sails on.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "plunder")
    public static void takingCargoAboardIsPlunder(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        Player[] p = new Player[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            p[0] = boarder(h, ship[0]);
        });
        h.onEachTick(() -> {
            if (p[0] != null && !p[0].isRemoved()) keepAboard(p[0], ship[0]);
        });
        h.runAtTickTime(15, () -> {
            Materializer.update(server, v.id());
            VoyageShips.containers(ship[0]).stream().filter(c -> c.count() >= 15).findFirst().orElseThrow().extract(10);
            Materializer.update(server, v.id());
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            h.assertTrue(e.size() == 1 && e.get(0).outcome() == VoyageEndings.Outcome.PLUNDERED
                    && e.get(0).deed() == VoyageDeed.PLUNDER_MERCHANT && e.get(0).player().equals(Optional.of(p[0].getUUID())), "endings " + e);
            Voyage r = Voyages.get(server, v.id()).orElseThrow();
            h.assertTrue(r.cargoUnits() == 96 - 10 && r.state() == Voyage.State.MATERIALISED, "record " + r.cargo() + " " + r.state());
            VoyageShips.containers(ship[0]).stream().filter(c -> c.count() >= 5).findFirst().orElseThrow().extract(5);
            Materializer.update(server, v.id());
            h.assertTrue(ENDINGS.getOrDefault(v.id(), List.of()).size() == 1, "a second plunder deed");
            h.assertTrue(Voyages.get(server, v.id()).orElseThrow().cargoUnits() == 96 - 15, "record after the second take");
            h.assertTrue(VoyageShips.readLink(ship[0]).map(VoyageLink::plundered).orElse(false), "plunder not saved in the link");
            p[0].discard();
            finish(h, v);
            h.succeed();
        });
    }

    /**
     * All fighters dead and a player aboard for capture_hold_ticks: the ship is the player's, the voyage ended
     * CAPTURED, the crew stays aboard released and untagged, the link is cleared.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = "pirates_n_ships_config_worldsim_materialize_capture")
    public static void boardedShipWithoutFightersIsCaptured(GameTestHelper h) {
        ConfigOverrides.during(h, MaterializeConfig.CAPTURE_HOLD_TICKS, 20);
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        Player[] p = new Player[1];
        List<LivingEntity> crew = new ArrayList<>();
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            crew.addAll(people(h, ship[0], v, VoyageCrew.CREW_TAG));
        });
        h.runAtTickTime(10, () -> {
            people(h, ship[0], v, VoyageCrew.FIGHTER_TAG).forEach(LivingEntity::kill);
            p[0] = boarder(h, ship[0]);
        });
        h.onEachTick(() -> {
            if (p[0] == null || p[0].isRemoved()) return;
            keepAboard(p[0], ship[0]);
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            if (e.isEmpty()) {
                Materializer.update(server, v.id());
                return;
            }
            h.assertTrue(e.get(0).outcome() == VoyageEndings.Outcome.CAPTURED && e.get(0).player().equals(Optional.of(p[0].getUUID())),
                    "ending " + e.get(0));
            h.assertTrue(h.getTick() >= 10 + 20, "captured after " + (h.getTick() - 10) + " ticks");
            h.assertTrue(ShipRegistry.get(server).find(ship[0].id()).flatMap(ShipData::owner).equals(Optional.of(p[0].getUUID())), "owner");
            h.assertTrue(Voyages.get(server, v.id()).isEmpty(), "voyage still active");
            h.assertTrue(SableShips.byId(h.getLevel(), ship[0].id()) != null, "the captured ship was removed");
            h.assertTrue(VoyageShips.readLink(ship[0]).isEmpty() && VoyageShips.voyageOf(ship[0].id()).isEmpty(), "still linked");
            h.assertTrue(crew.stream().allMatch(c -> c.isAlive() && !c.getTags().contains(VoyageCrew.CREW_TAG)
                    && (!(c instanceof CrewMember m) || m.assignment() == null)), "crew not released");
            p[0].discard();
            crew.forEach(Entity::discard);
            finish(h, v);
            h.succeed();
        });
    }

    /** With room for one more ship, a second voyage stays a record. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = "pirates_n_ships_config_worldsim_materialize_cap")
    public static void capKeepsTheSecondVoyageARecord(GameTestHelper h) {
        // one more than the ships real now (other batches may still hold one while they end)
        ConfigOverrides.during(h, MaterializeConfig.MAX_MATERIALIZED, VoyageShips.count() + 1);
        basin(h);
        Voyage a = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        Voyage b = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, convoyCargo());
        MinecraftServer server = h.getLevel().getServer();
        h.runAtTickTime(5, () -> {
            materialize(h, a);
            Materializer.Outcome o = Materializer.materialize(server, b.id());
            h.assertTrue(o == Materializer.Outcome.CAP, "second voyage: " + o);
            h.assertTrue(Voyages.get(server, b.id()).orElseThrow().state() == Voyage.State.SAILING, "second voyage state");
            finish(h, a);
            finish(h, b);
            h.succeed();
        });
    }
}

package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.faction.FactionPair;
import com.richardsenger.piratesnships.worldsim.faction.FactionState;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WS5 GameTests. Settlements are {@code gametest/ws5_…} ports that are not registered (the automatic tracker and
 * landing leave them alone); the tests drive {@link RaidTracker#minute}, {@link RaidPlanner#start} and
 * {@link RaidLanding#update} directly. Landing tests use a 48×48 basin (water y 2..7) whose east side (x 38..46) is
 * land up to y 8, with the settlement's berth at the last water column (37, 24).
 */
public final class RaidGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_raid_";
    private static final int SURFACE = 7;
    private static final int LAND_X = 38;
    private static final List<FactionEvent> HEARD = new CopyOnWriteArrayList<>();

    static {
        Factions.listen((server, event, before, after) -> HEARD.add(event));
    }

    private RaidGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RaidGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A settlement of {@code kind} covering the test area, its berth at relative (24, 1, 46) or {@code berth}. */
    private static Port settlement(GameTestHelper h, PortKind kind, BlockPos berth) {
        ResourceLocation id = Constants.id("gametest/ws5_" + UUID.randomUUID().toString().substring(0, 8));
        BlockPos centre = h.absolutePos(new BlockPos(24, 1, 24));
        BoundingBox box = BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(47, 15, 47)));
        return new Port(id, kind, h.getLevel().dimension(), centre, box, Climate.TEMPERATE,
                List.of(new Berth(h.absolutePos(berth), Direction.SOUTH)));
    }

    /** The basin with its land strip on the east side. */
    private static void basin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                boolean land = x >= LAND_X;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall || land ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 9; y <= 18; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /**
     * A raid on {@code port} with one RAID voyage sailing east through the basin, its position at relative (18, 24)
     * (19 blocks from the berth), tracked in {@link RaidData}.
     */
    private static Voyage raidingVoyage(GameTestHelper h, Port port) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos at = h.absolutePos(new BlockPos(18, SURFACE, 24));
        BlockPos berth = port.berths().get(0).pos();
        List<Lane.Point> route = List.of(new Lane.Point(at.getX() - 100, at.getZ()), new Lane.Point(berth.getX(), berth.getZ()));
        Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.RAID, Faction.PIRATES, ShipTemplates.STARTER_SLOOP_ID, port.id(),
                port.id(), route, Map.of(), h.getLevel().getGameTime());
        v = v.withProgress(RouteMath.project(route, at.getX() + 0.5, at.getZ() + 0.5, 100));
        VoyageData.get(server).put(v);
        RaidData.get(server).put(RaidData.ActiveRaid.start(port.id(), port.dimension(), berth.atY(SURFACE + h.absolutePos(BlockPos.ZERO).getY()),
                port.box(), h.getLevel().getGameTime(), List.of(v.id())));
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

    private static RaidData.Phase phase(MinecraftServer server, Voyage v) {
        return RaidData.get(server).raidOf(v.id()).map(r -> r.ships().get(v.id()).phase()).orElse(RaidData.Phase.DONE);
    }

    private static List<LivingEntity> fighters(ServerLevel level, Voyage v) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity l && l.isAlive() && e.getTags().contains(VoyageCrew.voyageTag(v.id()))
                    && e.getTags().contains(VoyageCrew.FIGHTER_TAG)) out.add(l);
        }
        return out;
    }

    private static void finish(GameTestHelper h, Port port, List<Voyage> voyages) {
        MinecraftServer server = h.getLevel().getServer();
        for (Voyage v : voyages) {
            Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
            VoyageCrew.discard(h.getLevel(), v.id());
        }
        RaidData.get(server).forget(port.id());
    }

    // ------------------------------------------------------------------ tests
    //
    // Every runAtTickTime / onEachTick is registered in the test body (see MaterializeGameTests).

    /**
     * A player inside an outpost with a bell for three minutes at growth 1.0: three minutes counted, the chance at the
     * cap, no raid at a high roll. A forced raid rings the bell, spawns raiders.ships RAID voyages of the Pirates whose
     * first is approach_distance out from the berth, starts the count over and the cooldown.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = "pirates_n_ships_config_worldsim_raid_tracker")
    public static void presenceRaisesTheChanceAndAForcedRaidRingsTheBell(GameTestHelper h) {
        ConfigOverrides.during(h, WorldSimConfig.RAID_CHANCE_GROWTH, 1.0);
        MinecraftServer server = h.getLevel().getServer();
        Port port = settlement(h, PortKind.NAVY_OUTPOST, new BlockPos(24, 1, 46));
        BlockPos bell = new BlockPos(10, 2, 10);
        h.setBlock(bell.below(), Blocks.STONE);
        h.setBlock(bell, Blocks.BELL);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(new BlockPos(20, 2, 20));
        p.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        h.getLevel().addFreshEntity(p);
        h.runAtTickTime(5, () -> {
            List<Voyage> voyages = new ArrayList<>();
            try {
                for (int i = 1; i <= 3; i++) {
                    RaidTracker.Minute m = RaidTracker.minute(server, port, 0.999);
                    h.assertTrue(m.present() && m.minutes() == i && !m.raided(), "minute " + i + ": " + m);
                }
                double cap = WorldSimConfig.RAID_CHANCE_CAP.get();
                h.assertTrue(RaidData.get(server).minutes(port.id()) == 3, "minutes " + RaidData.get(server).minutes(port.id()));
                h.assertTrue(Math.abs(RaidTracker.chance(server, port.id()) - Math.min(cap, 3.0 * RaidConfig.params(server).multiplier())) < 1e-9,
                        "chance " + RaidTracker.chance(server, port.id()) + ", cap " + cap);

                RaidPlanner.Started s = RaidPlanner.start(server, port, true, h.getLevel().getRandom()).orElseThrow();
                voyages.addAll(s.voyages());
                BlockPos bellAt = h.absolutePos(bell);
                h.assertTrue(s.bells().contains(bellAt), "bells found " + s.bells());
                h.assertTrue(h.getLevel().getBlockEntity(bellAt) instanceof BellBlockEntity be && be.shaking, "the bell does not ring");
                h.assertTrue(s.voyages().size() == RaidConfig.SHIPS.get(), "ships " + s.voyages().size());
                Voyage first = Voyages.get(server, s.voyages().get(0).id()).orElseThrow();
                h.assertTrue(first.kind() == VoyageKind.RAID && first.faction() == Faction.PIRATES && first.to().equals(port.id()),
                        "voyage " + first.kind() + " " + first.faction() + " to " + first.to());
                Lane.Position pos = first.position();
                BlockPos berth = port.berths().get(0).pos();
                double d = Math.hypot(pos.x() - berth.getX(), pos.z() - berth.getZ());
                h.assertTrue(Math.abs(d - RaidConfig.APPROACH_DISTANCE.get()) <= 1.5, "raider " + d + " blocks out");
                h.assertTrue(pos.z() > berth.getZ(), "raider not out at sea (south of the berth): " + pos);
                h.assertTrue(RaidData.get(server).minutes(port.id()) == 0, "count not started over");
                h.assertTrue(RaidTracker.chance(server, port.id()) == 0.0 || WorldSimConfig.RAID_COOLDOWN_DAYS.get() == 0.0, "no cooldown");
                h.assertTrue(RaidPlanner.start(server, port, true, h.getLevel().getRandom()).isEmpty(), "a second raid at once");
            } finally {
                finish(h, port, voyages);
                p.discard();
            }
            h.assertTrue(RaidData.get(server).raid(port.id()).isEmpty(), "raid still under way after its voyages ended");
            h.succeed();
        });
    }

    /** A real raider within landing_distance of the berth puts its pirates ashore within 100 ticks, free to move. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "landing")
    public static void raidersLandOnTheShore(GameTestHelper h) {
        basin(h);
        MinecraftServer server = h.getLevel().getServer();
        Port port = settlement(h, PortKind.NAVY_OUTPOST, new BlockPos(LAND_X - 1, 1, 24));
        Voyage v = raidingVoyage(h, port);
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.onEachTick(() -> {
            if (h.getTick() < 6 || ship[0] == null) return;
            RaidLanding.update(server, v.id());
            if (phase(server, v) != RaidData.Phase.LANDED) {
                if (h.getTick() > 105) {
                    finish(h, port, List.of(v));
                    h.fail("not landed after 100 ticks");
                }
                return;
            }
            try {
                List<LivingEntity> ashore = fighters(h.getLevel(), v);
                h.assertTrue(ashore.size() == MaterializeConfig.FIGHTERS_PIRATE.get(), "fighters " + ashore.size());
                for (LivingEntity f : ashore) {
                    BlockPos rel = f.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)); // vanilla's relativePos turns by 180°
                    h.assertTrue(rel.getX() >= LAND_X && rel.getY() == SURFACE + 2 && h.getBlockState(rel.below()).is(Blocks.STONE),
                            "fighter at " + rel + " not on the shore");
                    h.assertTrue(f instanceof SeafarerMob m && !m.isStationary(), "fighter still stationary");
                }
                h.assertTrue(HelmCourses.course(ship[0].id()) != null, "no course home held");
                Voyage r = Voyages.get(server, v.id()).orElseThrow();
                h.assertTrue(r.to().equals(v.from()), "route not turned for home");
            } finally {
                finish(h, port, List.of(v));
            }
            h.succeed();
        });
    }

    /**
     * The landed raiders all dead: the ship withdraws, the raid ends, RAID_REPELLED is reported and the settlement is
     * on cooldown from today.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "repel")
    public static void repelledRaidIsReportedAndStartsTheCooldown(GameTestHelper h) {
        basin(h);
        MinecraftServer server = h.getLevel().getServer();
        Port port = settlement(h, PortKind.NAVY_OUTPOST, new BlockPos(LAND_X - 1, 1, 24));
        Voyage v = raidingVoyage(h, port);
        ShipBody[] ship = new ShipBody[1];
        boolean[] killed = new boolean[1];
        h.runAtTickTime(5, () -> {
            HEARD.clear();
            ship[0] = materialize(h, v);
        });
        h.onEachTick(() -> {
            if (h.getTick() < 6 || ship[0] == null) return;
            RaidLanding.update(server, v.id());
            RaidData.Phase phase = phase(server, v);
            if (phase == RaidData.Phase.APPROACH) {
                if (h.getTick() > 105) {
                    finish(h, port, List.of(v));
                    h.fail("not landed after 100 ticks");
                }
                return;
            }
            if (phase == RaidData.Phase.LANDED) {
                h.assertFalse(killed[0], "still landed after its raiders died");
                fighters(h.getLevel(), v).forEach(LivingEntity::kill);
                killed[0] = true;
                return;
            }
            try {
                h.assertTrue(killed[0], "done before the raiders died");
                RaidData data = RaidData.get(server);
                h.assertTrue(data.raid(port.id()).isEmpty(), "raid still under way");
                h.assertTrue(HEARD.contains(FactionEvent.RAID_REPELLED) && !HEARD.contains(FactionEvent.RAID_SUCCEEDED),
                        "heard " + HEARD);
                h.assertTrue(data.lastRaidDay(port.id()) == Factions.currentDay(server), "last raid day " + data.lastRaidDay(port.id()));
                data.setMinutes(port.id(), 10);
                h.assertTrue(RaidTracker.chance(server, port.id()) == 0.0 || WorldSimConfig.RAID_COOLDOWN_DAYS.get() == 0.0,
                        "no cooldown: chance " + RaidTracker.chance(server, port.id()));
                h.assertTrue(Voyages.get(server, v.id()).map(Voyage::fighters).orElse(-1) == 0, "fighters left in the record");
            } finally {
                finish(h, port, List.of(v));
            }
            h.succeed();
        });
    }

    /** With retaliation_enabled off the Navy-Pirates tension does not change the chance; with it on it does. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, timeoutTicks = 40, batch = "pirates_n_ships_config_worldsim_raid_retaliation")
    public static void retaliationOffIgnoresTension(GameTestHelper h) {
        ConfigOverrides.during(h, WorldSimConfig.RETALIATION_ENABLED, false);
        ConfigOverrides.during(h, WorldSimConfig.RAID_CHANCE_GROWTH, 0.001);
        ConfigOverrides.during(h, WorldSimConfig.RAID_CHANCE_CAP, 1.0);
        ConfigOverrides.during(h, RaidConfig.RETALIATION_FACTOR, 1.0);
        MinecraftServer server = h.getLevel().getServer();
        ResourceLocation id = Constants.id("gametest/ws5_" + UUID.randomUUID().toString().substring(0, 8));
        FactionState before = Factions.state(server);
        h.runAtTickTime(2, () -> {
            try {
                Factions.set(server, before.withTension(FactionPair.NAVY_PIRATES, 0.8));
                RaidData.get(server).setMinutes(id, 10);
                double off = RaidTracker.chance(server, id);
                h.assertTrue(Math.abs(off - 0.01) < 1e-9, "chance with retaliation off " + off);
                h.assertTrue(RaidRules.multiplier(true, 1.0, 0.8) * 0.01 > off, "tension would have raised it");
            } finally {
                Factions.set(server, before);
                RaidData.get(server).forget(id);
            }
            h.succeed();
        });
    }
}

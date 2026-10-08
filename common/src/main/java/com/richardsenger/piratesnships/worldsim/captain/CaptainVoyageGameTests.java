package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.mob.captain.CaptainEntry;
import com.richardsenger.piratesnships.mob.captain.CaptainRegistry;
import com.richardsenger.piratesnships.mob.captain.IslandCaptains;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BOS2 GameTests in the WS3b materialise basin (48×48, stone floor, water y=2..7, the barrier ceiling removed for the
 * sloop's mast) with the captain's post on a stone platform in a corner. Each test makes its own island
 * ({@code gametest/bos2_…}, so the automatic checks leave its captain and voyage alone), sends the captain out on a
 * route through the basin's middle at a 30° heading, and drives the materialiser and the captain's voyage directly.
 * Everything is removed at the end (registry entry, bounties, sea record, voyage, ship, people).
 */
public final class CaptainVoyageGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_captain_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_worldsim_captain_";
    private static final double HEADING = 30.0;
    private static final int SURFACE = 7;
    private static final BlockPos POST = new BlockPos(3, 9, 3);

    private CaptainVoyageGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CaptainVoyageGameTests.class);
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
        // the captain's post: a platform in the north-west corner, well off the ship's lane
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) h.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
        }
    }

    private static ResourceLocation island() {
        return Constants.id("gametest/bos2_" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static Vec3 middle(GameTestHelper h) {
        BlockPos c = h.absolutePos(new BlockPos(24, SURFACE, 24));
        return new Vec3(c.getX(), c.getY(), c.getZ());
    }

    /** From 100 blocks behind the basin's middle, through a waypoint 60 ahead, to 200 ahead, at {@link #HEADING}. */
    private static List<Lane.Point> route(GameTestHelper h) {
        Vec3 m = middle(h);
        double dx = Math.sin(Math.toRadians(HEADING));
        double dz = -Math.cos(Math.toRadians(HEADING));
        return List.of(point(m, dx, dz, -100), point(m, dx, dz, 60), point(m, dx, dz, 200));
    }

    private static Lane.Point point(Vec3 m, double dx, double dz, double d) {
        return new Lane.Point((int) Math.round(m.x + dx * d), (int) Math.round(m.z + dz * d));
    }

    private static PirateCaptain captainAtPost(GameTestHelper h, ResourceLocation island) {
        PirateCaptain c = IslandCaptains.spawn(h.getLevel(), island, h.absolutePos(POST), Direction.SOUTH, 0);
        h.assertTrue(c != null, "the captain of " + island + " spawns");
        return c;
    }

    /** The captain puts to sea on the basin route; the record is moved to the basin's middle. */
    private static Voyage sail(GameTestHelper h, ResourceLocation island) {
        MinecraftServer server = h.getLevel().getServer();
        List<Lane.Point> route = route(h);
        CaptainVoyages.Departure d = CaptainVoyages.departOn(server, island, route, island, RandomSource.create(7));
        h.assertTrue(d.outcome().sailed() && d.voyage().isPresent(), "departure " + d.outcome());
        Vec3 m = middle(h);
        Voyage v = d.voyage().get().withProgress(RouteMath.project(route, m.x, m.z, 100));
        Voyages.update(server, v);
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

    private static PirateCaptain board(GameTestHelper h, Voyage v) {
        Optional<PirateCaptain> c = CaptainVoyages.board(h.getLevel().getServer(), v.id());
        h.assertTrue(c.isPresent(), "the captain did not go aboard");
        return c.get();
    }

    private static CaptainEntry entry(GameTestHelper h, ResourceLocation island) {
        return CaptainRegistry.get(h.getLevel().getServer()).get(island).orElseThrow();
    }

    private static int expectedFighters() {
        return Math.max(1, MaterializeConfig.fighters(Faction.PIRATES));
    }

    /** Captains within 3 blocks of the post. */
    private static List<PirateCaptain> atPost(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(PirateCaptain.class, new AABB(h.absolutePos(POST)).inflate(3), LivingEntity::isAlive);
    }

    /** Removes the island's entry, bounties and sea record first (so no homecoming), then the voyage and everyone of it. */
    private static void finish(GameTestHelper h, ResourceLocation island, UUID captain, Optional<Voyage> v) {
        MinecraftServer server = h.getLevel().getServer();
        LawService.withdrawBounties(server, captain);
        CaptainRegistry.get(server).remove(island);
        CaptainSeaData.get(server).removeSea(island);
        PortRegistry.get(server).remove(island);
        v.ifPresent(voyage -> {
            Voyages.end(server, voyage.id(), VoyageEnd.CANCELLED);
            VoyageCrew.discard(h.getLevel(), voyage.id());
            CaptainSeaData.get(server).removeChase(voyage.id());
        });
        PirateCaptain c = CaptainVoyages.loaded(h.getLevel(), captain);
        if (c != null) c.discard();
    }

    private static int proofsOf(Player p, UUID target) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ProofContent.BOUNTY_PROOF.get()) && BountyProofItem.proofOf(s).target().equals(target)) n += s.getCount();
        }
        return n;
    }

    /** A real server player with a mock connection, not in the level (as in the captain and reputation tests). */
    private static ServerPlayer serverPlayer(GameTestHelper helper, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 9, 2))));
        p.getInventory().clearContent();
        return p;
    }

    /** Whether {@code label} is the voyage label {@code key} naming {@code name} (no language needed on the server). */
    private static boolean labelled(net.minecraft.network.chat.Component label, String key, String name) {
        return label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(CaptainVoyages.KEY + key) && java.util.Arrays.asList(t.getArgs()).contains(name);
    }

    private static void flood(GameTestHelper h, ShipBody ship) {
        HullRuntime rt = HullRuntimes.get(h.getLevel(), ship.id());
        if (rt == null) return;
        for (Compartment c : rt.simulation().analysis().compartments()) rt.simulation().setVolume(c.id(), c.volume());
    }

    // ------------------------------------------------------------------ tests
    //
    // Every runAtTickTime / onEachTick is registered in the test body (see MaterializeGameTests).

    /**
     * A forced voyage: the registry marks him at sea and his post is empty (he is not lost); the materialised ship has
     * him aboard as one of its fighters, the same entity (UUID, name) in place of a pirate, with the voyage's tags.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "board")
    public static void forcedVoyageTakesTheCaptainAboard(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        PirateCaptain ashore = captainAtPost(h, island);
        UUID id = ashore.getUUID();
        String name = ashore.getName().getString();
        Voyage v = sail(h, island);
        h.assertTrue(v.kind() == VoyageKind.CAPTAIN && v.faction() == Faction.PIRATES && v.from().equals(island), "voyage " + v);
        h.assertValueEqual(v.fighters(), expectedFighters(), "fighters of the record");
        CaptainEntry e = entry(h, island);
        h.assertTrue(e.alive() && e.atSea() && e.voyage().equals(Optional.of(v.id())), "registry " + e);
        h.assertTrue(ashore.isRemoved(), "his copy at the post was not removed");
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> {
            h.assertTrue(atPost(h).isEmpty(), "his post is not empty while he is at sea");
            h.assertTrue(entry(h, island).alive(), "the registry lost him when he sailed");
            ship[0] = materialize(h, v);
            PirateCaptain c = board(h, v);
            h.assertValueEqual(c.getUUID(), id, "the same captain");
            h.assertValueEqual(c.getName().getString(), name, "his name");
            h.assertTrue(c.getTags().contains(VoyageCrew.voyageTag(v.id())) && c.getTags().contains(VoyageCrew.FIGHTER_TAG), "tags " + c.getTags());
            h.assertTrue(v.id().equals(c.seaVoyage()), "sea voyage " + c.seaVoyage());
            h.assertTrue(CaptainVoyages.board(h.getLevel().getServer(), v.id()).map(x -> x == c).orElse(false), "a second board made another copy");
        });
        h.runAtTickTime(15, () -> {
            List<LivingEntity> fighters = VoyageCrew.alive(h.getLevel(), ship[0], v.id(), VoyageCrew.FIGHTER_TAG);
            h.assertValueEqual(fighters.size(), expectedFighters(), "fighters aboard");
            h.assertValueEqual((int) fighters.stream().filter(f -> f instanceof PirateCaptain).count(), 1, "one captain among the fighters");
            h.assertTrue(fighters.stream().allMatch(f -> f instanceof Pirate), "all pirates " + fighters);
            h.assertTrue(CaptainVoyages.loaded(h.getLevel(), id) != null && atPost(h).isEmpty(), "the captain is aboard, not at his post");
            h.assertTrue(CaptainVoyages.label(h.getLevel().getServer(), v).map(l -> labelled(l, "label", name)).orElse(false),
                    "the voyage list names him");
            finish(h, island, id, Optional.of(v));
            h.succeed();
        });
    }

    /** Dematerialised and materialised again, the ship brings him back: same UUID, his health kept, still at sea. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "demat")
    public static void dematerialiseAndRematerialiseKeepHim(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        UUID id = captainAtPost(h, island).getUUID();
        Voyage v = sail(h, island);
        MinecraftServer server = h.getLevel().getServer();
        h.runAtTickTime(5, () -> {
            materialize(h, v);
            board(h, v).setHealth(25f);
        });
        h.runAtTickTime(15, () -> {
            h.assertTrue(Materializer.dematerialize(server, v.id()), "dematerialize refused");
            h.assertTrue(CaptainVoyages.loaded(h.getLevel(), id) == null, "his copy stayed in the world");
            CaptainEntry e = entry(h, island);
            h.assertTrue(e.alive() && e.voyage().equals(Optional.of(v.id())), "registry after dematerialising " + e);
            h.assertTrue(CaptainSeaData.get(server).sea(island).flatMap(CaptainSeaData.Sea::stash).isPresent(), "no stowed state");
            Voyage r = Voyages.get(server, v.id()).orElseThrow();
            h.assertTrue(r.state() == Voyage.State.SAILING && r.fighters() == expectedFighters(), "record " + r.state() + " fighters " + r.fighters());
            h.assertTrue(atPost(h).isEmpty(), "he went home instead of sailing on");
        });
        h.runAtTickTime(25, () -> {
            materialize(h, v);
            PirateCaptain c = board(h, v);
            h.assertValueEqual(c.getUUID(), id, "the same captain again");
            h.assertTrue(Math.abs(c.getHealth() - 25f) < 0.01f, "health " + c.getHealth());
            h.assertTrue(entry(h, island).atSea(), "at sea");
            h.assertTrue(CaptainSeaData.get(server).sea(island).flatMap(CaptainSeaData.Sea::stash).isEmpty(), "stowed state kept while aboard");
            finish(h, island, id, Voyages.get(server, v.id()));
            h.succeed();
        });
    }

    /**
     * Killed aboard by a player: the bounty proof goes to the killer, the registry marks him lost today as on land, and
     * the bounty waits for the proof. His voyage sails on without him and brings nobody home.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "death")
    public static void deathAtSeaPaysTheBountyAndMarksTheRegistry(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        UUID id = captainAtPost(h, island).getUUID();
        Voyage v = sail(h, island);
        MinecraftServer server = h.getLevel().getServer();
        h.runAtTickTime(5, () -> {
            materialize(h, v);
            PirateCaptain c = board(h, v);
            ServerPlayer killer = serverPlayer(h, "bos2_killer");
            c.hurt(killer.damageSources().playerAttack(killer), 1000f);
            h.assertTrue(c.isDeadOrDying(), "the captain fell");
            h.assertValueEqual(proofsOf(killer, id), 1, "the bounty proof");
            CaptainEntry e = entry(h, island);
            h.assertTrue(!e.alive() && !e.atSea(), "registry " + e);
            h.assertValueEqual(e.diedDay(), IslandCaptains.today(server), "lost today");
            h.assertTrue(LawService.hasBounty(server, id), "the bounty waits for the proof");
            String name = e.name();
            h.assertTrue(CaptainVoyages.label(server, Voyages.get(server, v.id()).orElseThrow())
                    .map(l -> labelled(l, "label_without", name)).orElse(false), "the voyage list shows him gone");
        });
        h.runAtTickTime(15, () -> {
            h.assertTrue(CaptainVoyages.board(server, v.id()).isEmpty(), "a dead captain went aboard again");
            Voyages.end(server, v.id(), VoyageEnd.ARRIVED);
            h.assertTrue(atPost(h).isEmpty(), "a dead captain came home");
            finish(h, island, id, Optional.empty());
            h.succeed();
        });
    }

    /** His ship sinks after a player's hit: he drowns with it, the player gets his bounty proof, the registry marks him lost. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "sink")
    public static void sinkingDrownsHimAndCreditsTheShooter(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        UUID id = captainAtPost(h, island).getUUID();
        Voyage v = sail(h, island);
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        Player[] shooter = new Player[1];
        PirateCaptain[] captain = new PirateCaptain[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            captain[0] = board(h, v);
            shooter[0] = h.makeMockPlayer(GameType.SURVIVAL);
            shooter[0].moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 9, 4))));
            h.getLevel().addFreshEntity(shooter[0]);
            VoyageEndings.recordHit(ship[0].id(), shooter[0].getUUID(), h.getLevel().getGameTime());
        });
        h.onEachTick(() -> {
            if (h.getTick() < 10 || ship[0] == null) return;
            if (Voyages.get(server, v.id()).isPresent()) {
                flood(h, ship[0]);
                Materializer.update(server, v.id());
                return;
            }
            h.assertTrue(captain[0].isDeadOrDying() || captain[0].isRemoved(), "the captain survived the sinking");
            CaptainEntry e = entry(h, island);
            h.assertTrue(!e.alive(), "registry " + e);
            h.assertValueEqual(proofsOf(shooter[0], id), 1, "the shooter's bounty proof");
            h.assertTrue(atPost(h).isEmpty(), "a drowned captain came home");
            shooter[0].discard();
            finish(h, island, id, Optional.empty());
            h.succeed();
        });
    }

    /** An abstract voyage that arrives brings him back to his post, the same captain with the health he left with. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "home")
    public static void homecomingPutsHimBackAtHisPost(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        PirateCaptain ashore = captainAtPost(h, island);
        UUID id = ashore.getUUID();
        ashore.setHealth(30f);
        Voyage v = sail(h, island);
        MinecraftServer server = h.getLevel().getServer();
        h.runAtTickTime(5, () -> {
            h.assertTrue(atPost(h).isEmpty(), "his post is not empty while he is at sea");
            Voyages.end(server, v.id(), VoyageEnd.ARRIVED);
            CaptainEntry e = entry(h, island);
            h.assertTrue(e.alive() && !e.atSea(), "registry " + e);
            List<PirateCaptain> home = atPost(h);
            h.assertTrue(home.size() == 1 && home.get(0).getUUID().equals(id), "at his post: " + home);
            h.assertTrue(Math.abs(home.get(0).getHealth() - 30f) < 0.01f, "health " + home.get(0).getHealth());
            h.assertTrue(home.get(0).seaVoyage() == null && home.get(0).getTags().stream().noneMatch(t -> t.startsWith("pirates_n_ships.voyage")),
                    "still tagged for the sea " + home.get(0).getTags());
            h.assertTrue(CaptainSeaData.get(server).sea(island).map(s -> !s.returning() && s.stash().isEmpty()).orElse(false), "sea record");
            finish(h, island, id, Optional.empty());
            h.succeed();
        });
    }

    /** A voyage that ends while his ship is real (cancelled) stows him with the ship and brings him home too. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "home")
    public static void voyageEndingWithItsShipBringsHimHome(GameTestHelper h) {
        basin(h);
        ResourceLocation island = island();
        UUID id = captainAtPost(h, island).getUUID();
        Voyage v = sail(h, island);
        MinecraftServer server = h.getLevel().getServer();
        h.runAtTickTime(5, () -> {
            materialize(h, v);
            board(h, v).setHealth(20f);
        });
        h.runAtTickTime(15, () -> {
            Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
            CaptainEntry e = entry(h, island);
            h.assertTrue(e.alive() && !e.atSea(), "registry " + e);
            List<PirateCaptain> home = atPost(h);
            h.assertTrue(home.size() == 1 && home.get(0).getUUID().equals(id), "at his post: " + home);
            h.assertTrue(Math.abs(home.get(0).getHealth() - 20f) < 0.01f, "health " + home.get(0).getHealth());
            finish(h, island, id, Optional.empty());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ config

    /** {@code world_simulation.captain.enabled} off: no departure, forced or scheduled; he keeps his post. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = CONFIG_BATCH + "enabled")
    public static void disabledCaptainsStayHome(GameTestHelper h) {
        ConfigOverrides.during(h, CaptainVoyageConfig.ENABLED, false);
        basin(h);
        ResourceLocation island = island();
        UUID id = captainAtPost(h, island).getUUID();
        MinecraftServer server = h.getLevel().getServer();
        CaptainVoyages.Departure d = CaptainVoyages.departOn(server, island, route(h), island, RandomSource.create(1));
        h.assertTrue(d.outcome() == CaptainVoyages.Outcome.DISABLED, "forced departure " + d.outcome());
        h.assertTrue(CaptainVoyages.chance(server, island, 0.0, RandomSource.create(1)) == CaptainVoyageRules.Verdict.DISABLED, "scheduled");
        h.assertTrue(!entry(h, island).atSea() && atPost(h).size() == 1, "he left his post");
        finish(h, island, id, Optional.empty());
        h.succeed();
    }

    /**
     * {@code voyage_days} 1 and {@code voyage_chance} 1: a new record waits a day, then on his day he puts to sea from
     * his island (a planned out-and-back route), using his chance; at sea he gets none.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = CONFIG_BATCH + "chance")
    public static void onHisDayHePutsToSea(GameTestHelper h) {
        ConfigOverrides.during(h, CaptainVoyageConfig.VOYAGE_DAYS, 1);
        ConfigOverrides.during(h, CaptainVoyageConfig.VOYAGE_CHANCE, 1.0);
        basin(h);
        ResourceLocation island = island();
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos centre = h.absolutePos(new BlockPos(24, SURFACE, 24));
        PortRegistry.get(server).add(new Port(island, PortKind.PIRATE_ISLAND, level.dimension(), centre,
                BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(47, 18, 47))),
                Climate.TROPICAL, List.of(), List.of()));
        UUID id = captainAtPost(h, island).getUUID();
        long today = IslandCaptains.today(server);
        h.assertTrue(CaptainVoyages.chance(server, island, 0.0, RandomSource.create(3)) == CaptainVoyageRules.Verdict.NOT_DUE, "first look");
        CaptainSeaData data = CaptainSeaData.get(server);
        data.putSea(island, data.sea(island).orElseThrow().withLastChanceDay(today - 1));
        h.assertTrue(CaptainVoyages.chance(server, island, 0.5, RandomSource.create(3)) == CaptainVoyageRules.Verdict.SAIL, "his day");
        CaptainEntry e = entry(h, island);
        h.assertTrue(e.atSea(), "registry " + e);
        Voyage v = Voyages.get(server, e.voyage().orElseThrow()).orElseThrow();
        h.assertTrue(v.kind() == VoyageKind.CAPTAIN && v.from().equals(island) && v.waypoints().size() >= 2, "voyage " + v);
        h.assertValueEqual(data.sea(island).orElseThrow().lastChanceDay(), today, "his chance used today");
        h.assertTrue(CaptainVoyages.chance(server, island, 0.0, RandomSource.create(3)) == CaptainVoyageRules.Verdict.AWAY, "at sea");
        finish(h, island, id, Optional.of(v));
        h.succeed();
    }
}

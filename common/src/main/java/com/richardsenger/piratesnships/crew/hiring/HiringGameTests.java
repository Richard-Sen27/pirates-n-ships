package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiring at the harbor desk (CRW1, docs/design.md §7.5): a hire puts a crew member on the player's ship moored at the
 * port and takes the fee, the bunks cap the crew, no ship means no hire, pirates and navy ratings ask for standing,
 * dismissal rights and the {@code crew.hiring.enabled} toggle. The ship is the 5×4×5 hull of {@link StationGameTests}
 * resting on land (deck top at relative y = 8, crew stand at y = 9) with one hammock in the hold; the port's box covers
 * the test area and the desk stands on the ground west of the hull. Ports are removed at the end with their market and
 * candidates.
 */
public final class HiringGameTests {

    private static final String BATCH = "pirates_n_ships_crew_hiring";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_crew_hiring";
    /** On the stone ground (top at y = 4) west of the hull at x 17..21, z 17..21. */
    private static final BlockPos DESK = new BlockPos(12, 5, 19);
    /** The desk of the 9×9 tests without a ship. */
    private static final BlockPos SMALL_DESK = new BlockPos(1, 1, 1);
    /** Ticks for Sable to fill the new ship's world bounds. */
    private static final int SETTLE = 3;

    private HiringGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HiringGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A real server player with a mock connection, not added to the level (the market tests' pattern), at the desk. */
    private static ServerPlayer player(GameTestHelper h, String name, long coins) {
        return player(h, name, coins, DESK);
    }

    private static ServerPlayer player(GameTestHelper h, String name, long coins, BlockPos desk) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(desk.south())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        MarketBackend.record(p.getUUID());
        return p;
    }

    /** A registered port of {@code kind} with the box {@code boxMin..boxMax}, and its desk at {@code desk}. */
    private static Port port(GameTestHelper h, PortKind kind, BlockPos boxMin, BlockPos boxMax, BlockPos desk) {
        Port port = new Port(Constants.id("gametest/hiring_" + kind.getSerializedName() + "_" + UUID.randomUUID().toString().substring(0, 8)),
                kind, h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 5, 20)),
                BoundingBox.fromCorners(h.absolutePos(boxMin), h.absolutePos(boxMax)), Climate.TEMPERATE, List.of());
        PortService.register(h.getLevel().getServer(), port);
        h.setBlock(desk, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(desk)).setPort(Optional.of(port.id()));
        return port;
    }

    /** A port of {@code kind} whose box covers the 40×40 test area, its desk at {@link #DESK}. */
    private static Port port(GameTestHelper h, PortKind kind) {
        return port(h, kind, BlockPos.ZERO, new BlockPos(39, 20, 39), DESK);
    }

    /** A port of {@code kind} over a 9×9 test, its desk at {@link #SMALL_DESK}. */
    private static Port smallPort(GameTestHelper h, PortKind kind) {
        return port(h, kind, BlockPos.ZERO, new BlockPos(8, 5, 8), SMALL_DESK);
    }

    /** The test ship with one hammock in its hold, owned by {@code owner}. */
    private static StationGameTests.Fixture ship(GameTestHelper h, ServerPlayer owner) {
        StationGameTests.Fixture f = StationGameTests.ship(h, false, x -> {
            x.setBlock(new BlockPos(18, 7, 19), Blocks.OAK_FENCE);
            BlockState foot = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.EAST);
            x.setBlock(new BlockPos(19, 7, 19), foot.setValue(HammockBlock.PART, BedPart.FOOT));
            x.setBlock(new BlockPos(20, 7, 19), foot.setValue(HammockBlock.PART, BedPart.HEAD));
        });
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.put(registry.find(f.ship().id()).orElseThrow().withOwner(Optional.of(owner.getUUID())));
        return f;
    }

    private static void cleanup(GameTestHelper h, Port port, ServerPlayer... players) {
        MinecraftServer server = h.getLevel().getServer();
        for (ServerPlayer p : players) {
            MarketBackend.close(p);
            MarketBackend.stopRecording(p.getUUID());
        }
        PortRegistry.get(server).remove(port.id());
        TradeData.get(server).removeMarket(port.id());
        HiringData.get(server).forget(port.id());
    }

    private static <T> List<T> sent(ServerPlayer p, Class<T> type) {
        List<CustomPacketPayload> rec = MarketBackend.record(p.getUUID());
        synchronized (rec) {
            return rec.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static CrewPayloads.CrewPayload lastCrew(GameTestHelper h, ServerPlayer p) {
        List<CrewPayloads.CrewPayload> all = sent(p, CrewPayloads.CrewPayload.class);
        h.assertFalse(all.isEmpty(), "a crew payload was sent");
        return all.get(all.size() - 1);
    }

    private static CrewPayloads.CrewView view(GameTestHelper h, ServerPlayer p, Port port) {
        return HiringBackend.view(p, port.id()).orElseThrow(() -> new AssertionError("no Crew tab"));
    }

    private static void assertKey(GameTestHelper h, Hiring.Result r, String key) {
        h.assertValueEqual(r.key(), key, "result");
        h.assertValueEqual(r.done(), HiringText.HIRED.equals(key) || HiringText.DISMISSED.equals(key), "done");
    }

    // ------------------------------------------------------------------ hiring

    /**
     * At a village desk: the Crew tab lists today's sailors with fee and wage; hiring one through the tab takes the fee,
     * puts a named crew member with start morale and its hirer on the ship's deck, and removes the candidate; the second
     * hire finds no free hammock (one hammock, one bunk). Without a desk session the hire is refused.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void hireAtTheDeskPutsCrewAboardUntilTheBunksAreFull(GameTestHelper h) {
        ServerPlayer p = player(h, "hiring_captain", 100);
        StationGameTests.Fixture f = ship(h, p);
        ShipBody ship = f.ship();
        Port port = port(h, PortKind.SEAFARER_VILLAGE);
        h.runAfterDelay(SETTLE, () -> {
            // no session yet: refused
            UUID any = Hiring.candidates(h.getLevel().getServer(), port).get(0).id();
            MarketBackend.handleCrew(p, new CrewPayloads.CrewAction(port.id(), any));
            h.assertValueEqual(lastCrew(h, p).result().orElseThrow().key(), HiringText.NO_SESSION, "refused without a session");

            h.assertTrue(MarketBackend.openDesk(p, port.id(), h.absolutePos(DESK)), "desk opened");
            CrewPayloads.CrewView v = lastCrew(h, p).view().orElseThrow(() -> new AssertionError("no Crew tab at the desk"));
            h.assertValueEqual(v.kind(), CandidateKind.SAILOR, "villages offer sailors");
            h.assertValueEqual(v.candidates().size(), HiringConfig.CANDIDATES_PER_PORT.get(), "candidates");
            h.assertValueEqual(v.wagePerDay(), CrewConfig.WAGE_PER_DAY.get(), "daily wage");
            h.assertTrue(v.refusal().isEmpty(), "no refusal: " + v.refusal());
            CrewPayloads.ShipLine line = v.ship().orElseThrow(() -> new AssertionError("the moored ship is not shown"));
            h.assertValueEqual(line.crew(), 0, "crew before");
            h.assertValueEqual(line.cap(), 1, "one hammock, one bunk");
            Candidate first = v.candidates().get(0);
            h.assertValueEqual(first.fee(), HiringConfig.FEE_SAILOR.get(), "sailor fee");

            MarketBackend.handleCrew(p, new CrewPayloads.CrewAction(port.id(), first.id()));
            CrewPayloads.CrewPayload answer = lastCrew(h, p);
            h.assertValueEqual(answer.result().orElseThrow().key(), HiringText.HIRED, "hired");
            h.assertValueEqual(Wallet.count(p), 100L - first.fee(), "fee taken");
            h.assertFalse(answer.view().orElseThrow().candidates().stream().anyMatch(c -> c.id().equals(first.id())), "candidate gone");
            List<CrewMember> aboard = ShipBunks.crewOf(h.getLevel(), ship);
            h.assertValueEqual(aboard.size(), 1, "one crew member aboard");
            CrewMember crew = aboard.get(0);
            h.assertValueEqual(crew.getCustomName() == null ? "" : crew.getCustomName().getString(), first.name(), "named");
            h.assertValueEqual(crew.hiredBy(), Optional.of(p.getUUID()), "hired by");
            h.assertValueEqual(CrewMorale.get(crew), CrewConfig.MORALE_START.get(), "start morale");
            Vec3 plot = ship.toPlot(crew.position());
            h.assertTrue(Math.abs(plot.y - f.helm().getY()) < 0.6, "stands on the deck (plot y " + plot.y + ", helm " + f.helm() + ")");

            Candidate second = answer.view().orElseThrow().candidates().get(0);
            Hiring.Result full = Hiring.hire(p, port.id(), second.id());
            assertKey(h, full, HiringText.NO_BUNK);
            h.assertValueEqual(Wallet.count(p), 100L - first.fee(), "no fee for a refused hire");
            h.assertTrue(Hiring.candidates(h.getLevel().getServer(), port).contains(second), "a refused candidate stays");
            cleanup(h, port, p);
            h.succeed();
        });
    }

    /** No ship of the player near the port: refused, and the tab says so; another player's ship does not count. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void noShipAtThePortIsRefused(GameTestHelper h) {
        ServerPlayer owner = player(h, "hiring_owner", 100);
        ServerPlayer other = player(h, "hiring_other", 100);
        ship(h, owner);
        // the port lies in the test area's north-west corner; the hull is within ship_radius of it
        Port port = port(h, PortKind.SEAFARER_VILLAGE, new BlockPos(0, 0, 0), new BlockPos(6, 10, 6), DESK);
        h.runAfterDelay(SETTLE, () -> {
            Candidate c = Hiring.candidates(h.getLevel().getServer(), port).get(0);
            assertKey(h, Hiring.hire(other, port.id(), c.id()), HiringText.NO_SHIP);
            h.assertTrue(view(h, other, port).ship().isEmpty(), "no ship shown to a stranger");
            h.assertValueEqual(Wallet.count(other), 100L, "no fee");
            // out of radius: the owner's ship no longer counts
            ConfigOverrides.during(h, HiringConfig.SHIP_RADIUS, 0);
            assertKey(h, Hiring.hire(owner, port.id(), c.id()), HiringText.NO_SHIP);
            cleanup(h, port, owner, other);
            h.succeed();
        });
    }

    /** An island with pirate reputation 0 and no infamy offers nothing; a Buccaneer gets pirates. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void pirateIslandsNeedFriendshipOrInfamy(GameTestHelper h) {
        ServerPlayer p = player(h, "hiring_pirate", 100, SMALL_DESK);
        Port island = smallPort(h, PortKind.PIRATE_ISLAND);
        Reputation.set(p, Faction.PIRATES, 0, "test");
        CrewPayloads.CrewView v = view(h, p, island);
        h.assertTrue(v.candidates().isEmpty(), "no candidates for a stranger: " + v.candidates());
        h.assertValueEqual(v.refusal(), Optional.of(HiringText.PIRATES_DISTRUST), "refusal");
        Candidate c = Hiring.candidates(h.getLevel().getServer(), island).get(0);
        h.assertValueEqual(c.kind(), CandidateKind.PIRATE, "islands offer pirates");
        h.assertValueEqual(c.fee(), HiringConfig.FEE_PIRATE.get(), "pirate fee");
        assertKey(h, Hiring.hire(p, island.id(), c.id()), HiringText.PIRATES_DISTRUST);
        Careers.setInfamy(p, InfamyRank.BUCCANEER);
        h.assertFalse(view(h, p, island).candidates().isEmpty(), "a Buccaneer sees pirates");
        // passes the standing check; there is no ship here
        assertKey(h, Hiring.hire(p, island.id(), c.id()), HiringText.NO_SHIP);
        Careers.setInfamy(p, InfamyRank.DECKHAND);
        Reputation.set(p, Faction.PIRATES, 80, "test");
        h.assertFalse(view(h, p, island).candidates().isEmpty(), "a friend of the pirates sees pirates");
        cleanup(h, island, p);
        h.succeed();
    }

    /** An outpost refuses a captain without a navy rank; a Midshipman hires a navy rating onto his ship. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void outpostsHireOnlyForTheEnlisted(GameTestHelper h) {
        ServerPlayer p = player(h, "hiring_officer", 100);
        ShipBody ship = ship(h, p).ship();
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        h.runAfterDelay(SETTLE, () -> {
            Careers.setNavy(p, NavyRank.NONE);
            Candidate c = Hiring.candidates(h.getLevel().getServer(), outpost).get(0);
            h.assertValueEqual(c.kind(), CandidateKind.NAVY, "outposts offer navy ratings");
            h.assertValueEqual(view(h, p, outpost).refusal(), Optional.of(HiringText.NOT_ENLISTED), "refusal");
            assertKey(h, Hiring.hire(p, outpost.id(), c.id()), HiringText.NOT_ENLISTED);
            Careers.setNavy(p, NavyRank.MIDSHIPMAN);
            Hiring.Result r = Hiring.hire(p, outpost.id(), c.id());
            assertKey(h, r, HiringText.HIRED);
            h.assertValueEqual(Wallet.count(p), 100L - HiringConfig.FEE_NAVY.get(), "navy fee");
            h.assertValueEqual(ShipBunks.crewOf(h.getLevel(), ship).size(), 1, "aboard");
            cleanup(h, outpost, p);
            h.succeed();
        });
    }

    /**
     * A stranger cannot dismiss a crew member; its owner sneak-uses the whistle on it and it becomes a neutral sailor
     * where it stood.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void onlyTheOwnerOrHirerDismisses(GameTestHelper h) {
        ServerPlayer owner = player(h, "hiring_dismiss_owner", 100);
        ServerPlayer stranger = player(h, "hiring_dismiss_stranger", 100);
        ShipBody ship = ship(h, owner).ship();
        Port port = port(h, PortKind.SEAFARER_VILLAGE);
        h.runAfterDelay(SETTLE, () -> {
            Hiring.Result hired = Hiring.hireNew(owner, port, CandidateKind.SAILOR);
            assertKey(h, hired, HiringText.HIRED);
            h.assertValueEqual(Wallet.count(owner), 100L, "the operator path is free");
            CrewMember crew = hired.crew();
            assertKey(h, Hiring.dismiss(stranger, crew), HiringText.NOT_YOURS);
            h.assertTrue(crew.isAlive(), "still aboard");
            Vec3 at = crew.position();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
            owner.setShiftKeyDown(true);
            owner.getItemInHand(InteractionHand.MAIN_HAND).interactLivingEntity(owner, crew, InteractionHand.MAIN_HAND);
            h.assertTrue(crew.isRemoved(), "the crew member is gone");
            List<Sailor> sailors = h.getLevel().getEntitiesOfClass(Sailor.class, new AABB(at, at).inflate(0.5));
            h.assertValueEqual(sailors.size(), 1, "a sailor stands where it stood");
            h.assertValueEqual(sailors.get(0).getCustomName() == null ? "" : sailors.get(0).getCustomName().getString(), hired.name(), "keeps its name");
            h.assertTrue(ShipBunks.crewOf(h.getLevel(), ship).isEmpty(), "no crew left");
            sailors.get(0).discard();
            cleanup(h, port, owner, stranger);
            h.succeed();
        });
    }

    /** Candidates are made once a day per port and kept; the operator command path is free. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void candidatesAreStableWithinADay(GameTestHelper h) {
        ServerPlayer p = player(h, "hiring_stable", 0, SMALL_DESK);
        Port port = smallPort(h, PortKind.SEAFARER_VILLAGE);
        MinecraftServer server = h.getLevel().getServer();
        List<Candidate> a = Hiring.candidates(server, port);
        HiringData.get(server).forget(port.id());
        List<Candidate> b = Hiring.candidates(server, port);
        h.assertValueEqual(b, a, "the same candidates again on the same day");
        h.assertTrue(HiringData.get(server).remove(port.id(), a.get(0).id()), "removed");
        h.assertValueEqual(Hiring.candidates(server, port).size(), a.size() - 1, "a hired candidate stays gone today");
        cleanup(h, port, p);
        h.succeed();
    }

    // ------------------------------------------------------------------ toggle

    /** {@code crew.hiring.enabled = false}: no Crew tab at the desk, hiring and dismissal refused. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH)
    public static void disabledHiringHasNoTab(GameTestHelper h) {
        ServerPlayer p = player(h, "hiring_off", 100, SMALL_DESK);
        Port port = smallPort(h, PortKind.SEAFARER_VILLAGE);
        Candidate c = Hiring.candidates(h.getLevel().getServer(), port).get(0);
        ConfigOverrides.during(h, HiringConfig.ENABLED, false);
        h.assertTrue(HiringBackend.view(p, port.id()).isEmpty(), "no tab while off");
        h.assertTrue(MarketBackend.openDesk(p, port.id(), h.absolutePos(SMALL_DESK)), "desk opened");
        h.assertTrue(sent(p, CrewPayloads.CrewPayload.class).isEmpty(), "no Crew payload at the desk");
        assertKey(h, Hiring.hire(p, port.id(), c.id()), HiringText.DISABLED);
        CrewMember crew = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (crew == null) throw new AssertionError("no crew member");
        crew.moveTo(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
        h.getLevel().addFreshEntity(crew);
        assertKey(h, Hiring.dismiss(p, crew), HiringText.DISABLED);
        h.assertFalse(crew.isRemoved(), "not dismissed while off");
        crew.discard();
        cleanup(h, port, p);
        h.succeed();
    }
}

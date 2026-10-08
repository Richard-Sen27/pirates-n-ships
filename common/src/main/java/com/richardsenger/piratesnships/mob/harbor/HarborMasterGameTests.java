package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Harbor masters in a real server (PRT1a): talking to one opens his bound desk and sneaking does not, a player who hit
 * him gets "not now", he walks back to his post, the registry replaces a lost one after {@code respawn_days},
 * {@code harbor_desks.direct_use} off sends desk users to him, and {@code mobs.harbor_master.enabled} off spawns none.
 * The world tests of the three ports check his placement through {@link #assertPlacedAtDesk}. Each test uses its own
 * fake port id and removes its registry entry at the end.
 */
public final class HarborMasterGameTests {

    private static final String BATCH = "pirates_n_ships_harbor_master_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_harbor_master_";

    /** The desk faces north (customer side); the harbor master stands behind it, the player in front. */
    private static final BlockPos DESK = new BlockPos(4, 1, 3);
    private static final BlockPos POST = new BlockPos(4, 1, 4);
    private static final BlockPos CUSTOMER = new BlockPos(4, 1, 2);

    private HarborMasterGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HarborMasterGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A fake port with an open market and a desk bound to it. */
    private static ResourceLocation portWithDesk(GameTestHelper h, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/harbor_master_" + UUID.randomUUID());
        TradeService.openMarket(h.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        h.setBlock(DESK, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(DESK)).setPort(Optional.of(id));
        return id;
    }

    private static HarborMaster spawnMaster(GameTestHelper h, ResourceLocation port) {
        HarborMaster m = HarborMasters.spawn(h.getLevel(), port, h.absolutePos(POST), Direction.NORTH);
        h.assertTrue(m != null, "the harbor master of " + port + " spawns");
        return m;
    }

    /** A real server player with a mock connection, not in the level (as in the desk tests), in front of the desk. */
    private static ServerPlayer player(GameTestHelper h, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(CUSTOMER)));
        p.getInventory().clearContent();
        MarketBackend.record(p.getUUID());
        return p;
    }

    private static void close(ServerPlayer... players) {
        for (ServerPlayer p : players) {
            MarketBackend.close(p);
            MarketBackend.stopRecording(p.getUUID());
        }
    }

    private static void cleanUp(GameTestHelper h, ResourceLocation port) {
        HarborMasterRegistry.get(h.getLevel().getServer()).remove(port);
    }

    // ------------------------------------------------------------------ world generation (called by the port tests)

    /**
     * Asserts that placing a port put exactly one harbor master inside {@code box}: stationary and persistent, bound
     * to {@code port} and posted where he stands, within reach of the desk at {@code desk}, on a free block, and
     * registered alive; then removes him and his registry entry so neighbouring tests see no stray mob.
     */
    public static void assertPlacedAtDesk(GameTestHelper h, ServerLevel level, BoundingBox box, BlockPos desk, ResourceLocation port) {
        List<HarborMaster> masters = level.getEntitiesOfClass(HarborMaster.class, AABB.of(box).inflate(1), HarborMaster::isAlive);
        h.assertValueEqual(masters.size(), 1, "one harbor master in the port");
        HarborMaster m = masters.get(0);
        h.assertTrue(m.isStationary() && m.isPersistenceRequired(), "he keeps his post and never despawns");
        h.assertValueEqual(m.port(), port, "his port");
        h.assertValueEqual(m.post(), m.blockPosition(), "he stands at his post");
        h.assertTrue(m.blockPosition().distManhattan(desk) <= 2, "his post " + m.blockPosition().toShortString()
                + " is behind the desk " + desk.toShortString());
        h.assertTrue(level.noCollision(m), "he is not stuck in a block");
        h.assertTrue(level.getBlockState(m.blockPosition().below()).isFaceSturdy(level, m.blockPosition().below(), Direction.UP),
                "he stands on a floor");
        HarborMasterRegistry.Entry entry = HarborMasterRegistry.get(level.getServer()).get(port).orElse(null);
        h.assertTrue(entry != null && entry.alive() && entry.id().equals(m.getUUID()), "registered: " + entry);
        m.discard();
        HarborMasterRegistry.get(level.getServer()).remove(port);
    }

    // ------------------------------------------------------------------ talking

    /**
     * Talking to him (main hand, standing) opens his desk's market for the player; sneaking passes; a player who just
     * hit him is told "not now" and gets no session.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "talk")
    public static void talkingOpensTheDeskSneakingPasses(GameTestHelper h) {
        floor(h, 9);
        ResourceLocation port = portWithDesk(h, PortKind.SEAFARER_VILLAGE);
        HarborMaster m = spawnMaster(h, port);
        h.assertValueEqual(m.kind(), MobKind.HARBOR_MASTER, "kind");
        h.assertValueEqual(m.faction(), com.richardsenger.piratesnships.mob.MobFaction.CIVILIAN, "a civilian");
        h.assertTrue(m.isStationary() && m.isPersistenceRequired(), "stationary and persistent");

        ServerPlayer sneaker = player(h, "harbor_sneaker");
        sneaker.setShiftKeyDown(true);
        h.assertValueEqual(HarborMasterInteractions.onEntityInteract(sneaker, m, InteractionHand.MAIN_HAND), InteractionResult.PASS,
                "sneaking passes");
        h.assertFalse(MarketBackend.isOpen(sneaker.getUUID(), port), "no session for the sneaker");
        h.assertValueEqual(HarborMasterInteractions.onEntityInteract(player(h, "harbor_off_hand"), m, InteractionHand.OFF_HAND),
                InteractionResult.PASS, "the off hand passes");

        ServerPlayer p = player(h, "harbor_customer");
        h.assertValueEqual(HarborMasterInteractions.onEntityInteract(p, m, InteractionHand.MAIN_HAND), InteractionResult.CONSUME,
                "talking consumes the click");
        h.assertTrue(MarketBackend.isOpen(p.getUUID(), port), "talking opened the desk's market");
        h.assertTrue(MarketBackend.canUse(p, port), "the session is the desk's");

        ServerPlayer rude = player(h, "harbor_rude");
        m.hurt(rude.damageSources().playerAttack(rude), 1.0f);
        h.assertTrue(m.resents(rude), "he remembers the blow");
        h.assertValueEqual(HarborMasterInteractions.talk(rude, m), HarborMasterInteractions.Talk.NOT_NOW, "not now");
        h.assertFalse(MarketBackend.isOpen(rude.getUUID(), port), "no session after a blow");

        close(sneaker, p, rude);
        m.discard();
        cleanUp(h, port);
        h.succeed();
    }

    // ------------------------------------------------------------------ the post

    /** Pushed 12 blocks from his post he walks back within 300 ticks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = BATCH + "post", timeoutTicks = 300)
    public static void pushedAwayHeWalksBackToHisPost(GameTestHelper h) {
        floor(h, 24);
        ResourceLocation port = Constants.id("gametest/harbor_master_" + UUID.randomUUID());
        HarborMaster m = spawnMaster(h, port);
        BlockPos post = h.absolutePos(POST);
        Vec3 away = Vec3.atBottomCenterOf(h.absolutePos(POST.east(12)));
        m.teleportTo(away.x, away.y, away.z);
        double slack = HarborMasterConfig.RETURN_DISTANCE.get() + 0.5;
        h.succeedWhen(() -> {
            h.assertTrue(post.distToCenterSqr(m.position()) <= slack * slack,
                    "back at his post, now " + m.position() + " (post " + post.toShortString() + ")");
            m.discard();
            cleanUp(h, port);
        });
    }

    /** A lost harbor master is replaced at his post once respawn_days have passed, not before. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "respawn")
    public static void aLostHarborMasterIsReplacedAfterRespawnDays(GameTestHelper h) {
        floor(h, 9);
        MinecraftServer server = h.getLevel().getServer();
        ResourceLocation port = Constants.id("gametest/harbor_master_" + UUID.randomUUID());
        HarborMaster m = spawnMaster(h, port);
        UUID old = m.getUUID();
        m.discard();
        HarborMasterRegistry.Entry lost = HarborMasterRegistry.get(server).get(port).orElseThrow();
        h.assertFalse(lost.alive(), "the discarded harbor master is lost");
        long today = HarborMasters.today(server);
        h.assertValueEqual(lost.diedDay(), today, "lost today");
        int days = HarborMasterConfig.RESPAWN_DAYS.get();
        if (days > 0) {
            HarborMasters.check(server);
            h.assertFalse(HarborMasterRegistry.get(server).get(port).orElseThrow().alive(), "no new one on the same day");
        }
        // the day edge: he was lost respawn_days ago
        HarborMasterRegistry.get(server).put(port, lost.dead(today - days));
        h.assertTrue(HarborMasters.check(server) >= 1, "the check places a new harbor master");
        HarborMasterRegistry.Entry next = HarborMasterRegistry.get(server).get(port).orElseThrow();
        h.assertTrue(next.alive() && !next.id().equals(old), "a new harbor master: " + next);
        HarborMaster successor = h.getLevel().getEntity(next.id()) instanceof HarborMaster hm ? hm : null;
        h.assertTrue(successor != null, "he stands in the world");
        h.assertValueEqual(successor.blockPosition(), h.absolutePos(POST), "at the old post");
        h.assertValueEqual(successor.postFacing(), Direction.NORTH, "facing the old way");
        h.assertValueEqual(successor.port(), port, "for the same port");
        successor.discard();
        cleanUp(h, port);
        h.succeed();
    }

    // ------------------------------------------------------------------ config

    /** With {@code harbor_desks.direct_use} off the desk sends players to the harbor master, who still opens it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "direct_use")
    public static void directUseOffSendsPlayersToTheHarborMaster(GameTestHelper h) {
        ConfigOverrides.during(h, TradeConfig.DESK_DIRECT_USE, false);
        floor(h, 9);
        ResourceLocation port = portWithDesk(h, PortKind.NAVY_OUTPOST);
        HarborMaster m = spawnMaster(h, port);
        ServerPlayer p = player(h, "harbor_direct");
        BlockPos desk = h.absolutePos(DESK);
        h.assertValueEqual(HarborDeskService.use(p, desk), HarborDeskService.Use.TALK_TO_MASTER, "the desk refuses");
        h.assertValueEqual(h.getBlockState(DESK).useWithoutItem(h.getLevel(), p,
                new BlockHitResult(Vec3.atCenterOf(desk), Direction.NORTH, desk, false)), InteractionResult.CONSUME, "the block consumes the click");
        h.assertFalse(MarketBackend.isOpen(p.getUUID(), port), "no session from the block");
        h.assertValueEqual(HarborMasterInteractions.talk(p, m), HarborMasterInteractions.Talk.DESK, "he serves");
        h.assertTrue(MarketBackend.isOpen(p.getUUID(), port), "talking to him opened the market");
        close(p);
        m.discard();
        cleanUp(h, port);
        h.succeed();
    }

    /** With {@code mobs.harbor_master.enabled} off none spawns and none is replaced; ports place none. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "enabled")
    public static void disabledHarborMastersSpawnNothing(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.enabled(MobKind.HARBOR_MASTER), false);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ResourceLocation port = Constants.id("gametest/harbor_master_" + UUID.randomUUID());
        h.assertTrue(HarborMasters.spawn(level, port, h.absolutePos(POST), Direction.NORTH) == null, "none while disabled");
        h.assertFalse(HarborMasters.placesAt(PortKind.SEAFARER_VILLAGE), "ports place none while disabled");
        HarborMasterRegistry.get(server).put(port, new HarborMasterRegistry.Entry(UUID.randomUUID(), false, -1000L,
                level.dimension(), h.absolutePos(POST), Direction.NORTH));
        h.assertValueEqual(HarborMasters.check(server), 0, "no replacement while disabled");
        h.assertTrue(level.getEntitiesOfClass(SeafarerMob.class, new AABB(h.absolutePos(POST)).inflate(2)).isEmpty(), "nobody there");
        cleanUp(h, port);
        h.succeed();
    }

    /** With {@code mobs.harbor_master.at_pirate_islands} off pirate islands place none; the other ports still do. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = CONFIG_BATCH + "islands")
    public static void pirateIslandsCanGoWithout(GameTestHelper h) {
        ConfigOverrides.during(h, HarborMasterConfig.AT_PIRATE_ISLANDS, false);
        h.assertFalse(HarborMasters.placesAt(PortKind.PIRATE_ISLAND), "no harbor master at pirate islands");
        h.assertTrue(HarborMasters.placesAt(PortKind.NAVY_OUTPOST) && HarborMasters.placesAt(PortKind.SEAFARER_VILLAGE),
                "villages and outposts keep theirs");
        h.succeed();
    }
}

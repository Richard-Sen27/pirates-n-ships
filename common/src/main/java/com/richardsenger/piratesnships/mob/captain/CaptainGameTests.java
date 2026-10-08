package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.apparel.ApparelContent;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.brig.CaptureRules;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.MobTestSupport;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.treasure.TreasureMapContent;
import com.richardsenger.piratesnships.world.treasure.TreasureMapService;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pirate captains in a real server (BOS1): spawning with a name, a registry entry and the standing bounty on the notice
 * boards; the duel's truce and the captain's single target; the kill's proof, deed and drops; the captain's tier at
 * the navy turn-in; the successor; the {@code mobs.captain.enabled} toggle. Each test uses its own fake island id and
 * removes its registry entry, bounties and port at the end.
 */
public final class CaptainGameTests {

    private static final String BATCH = "pirates_n_ships_captain_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_captain_";

    private CaptainGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CaptainGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation island() {
        return Constants.id("gametest/captain_" + UUID.randomUUID());
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static PirateCaptain spawnCaptain(GameTestHelper h, ResourceLocation island, int x, int z) {
        PirateCaptain c = IslandCaptains.spawn(h.getLevel(), island, h.absolutePos(new BlockPos(x, 1, z)), Direction.NORTH, 0);
        h.assertTrue(c != null, "the captain of " + island + " spawns");
        return c;
    }

    /** Removes the island's registry entry, every bounty on its captains and the fake port. */
    private static void cleanUp(GameTestHelper h, ResourceLocation island, UUID... captains) {
        MinecraftServer server = h.getLevel().getServer();
        CaptainRegistry registry = CaptainRegistry.get(server);
        registry.get(island).ifPresent(e -> LawService.withdrawBounties(server, e.id()));
        for (UUID id : captains) LawService.withdrawBounties(server, id);
        registry.remove(island);
        PortRegistry.get(server).remove(island);
    }

    /** A real server player with a mock connection, not in the level (as in the reputation tests). */
    private static ServerPlayer serverPlayer(GameTestHelper helper, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    private static int countDropped(GameTestHelper h, Item item, BlockPos centre) {
        AABB box = new AABB(h.absolutePos(centre)).inflate(4);
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static int count(Player player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ spawning, registry, bounty

    /**
     * A spawned captain is named after his island, wears the captain's hat and his own art, has 40 health, keeps his post, is in the
     * registry with the standing navy bounty that the notice boards list, and a second spawn for the same island is
     * refused while he lives.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "spawn")
    public static void spawnNamesRegistersAndPostsTheBounty(GameTestHelper h) {
        floor(h, 9);
        ResourceLocation island = island();
        MinecraftServer server = h.getLevel().getServer();
        PirateCaptain c = spawnCaptain(h, island, 4, 4);
        String name = CaptainNames.name(island.toString(), 0);
        h.assertValueEqual(c.getName().getString(), name, "the captain's name");
        h.assertTrue(c.getItemBySlot(EquipmentSlot.HEAD).is(ApparelContent.CAPTAINS_HAT.get()), "he wears the captain's hat");
        // ART6: his own model and texture, not the pirate's
        h.assertValueEqual(c.kind().artId(), "pirate_captain", "art id");
        h.assertTrue(c.getMainHandItem().is(CombatContent.CUTLASS.get()), "he carries a cutlass");
        h.assertValueEqual((double) c.getMaxHealth(), CaptainConfig.HEALTH.get(), "max health");
        h.assertValueEqual(c.kind(), MobKind.PIRATE_CAPTAIN, "kind");
        h.assertValueEqual(MobConfig.skill(MobKind.PIRATE_CAPTAIN), CaptainConfig.SKILL.get(), "duelist skill");
        h.assertTrue(c.isStationary() && c.isPersistenceRequired(), "stationary and persistent");
        h.assertValueEqual(c.port(), island, "his island");

        CaptainEntry entry = CaptainRegistry.get(server).get(island).orElse(null);
        h.assertTrue(entry != null && entry.alive() && entry.id().equals(c.getUUID()), "registry entry " + entry);
        h.assertValueEqual(entry.name(), name, "registered name");
        h.assertTrue(LawService.hasBounty(server, c.getUUID()), "the navy's bounty is on him");
        h.assertValueEqual(LawService.bountyTotal(server, c.getUUID()), (long) CaptainConfig.BOUNTY.get(), "bounty amount");
        // the score-driven navy bounty never withdraws the standing one
        LawService.syncNavyBounty(c);
        h.assertValueEqual(LawService.bountyTotal(server, c.getUUID()), (long) CaptainConfig.BOUNTY.get(), "bounty after a navy sync");
        boolean listed = NoticeBoardListing.lines(LawService.board(server), LawService.now(server)).stream()
                .anyMatch(l -> l.targetName().equals(name) && l.navy());
        h.assertTrue(listed, "the notice boards list " + name);

        h.assertTrue(IslandCaptains.spawn(h.getLevel(), island, h.absolutePos(new BlockPos(2, 1, 2)), Direction.NORTH, 0) == null,
                "never a second living captain for one island");

        // /pirates mob captain list
        int lines;
        try {
            lines = server.getCommands().getDispatcher().execute("pirates mob captain list",
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("/pirates mob captain list: " + e.getMessage());
        }
        h.assertTrue(lines >= 1, "/pirates mob captain list lists him, got " + lines);
        c.discard();
        h.assertFalse(CaptainRegistry.get(server).get(island).orElseThrow().alive(), "a removed captain is lost");
        h.assertFalse(LawService.hasBounty(server, c.getUUID()), "and his unclaimable bounty withdrawn");
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    // ------------------------------------------------------------------ the duel

    /**
     * Sneak-use with a cutlass challenges the captain: the pirate nearby stops attacking the challenger, the captain
     * targets him and nobody else (not even the navy); the captain's death ends the truce.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = BATCH + "duel")
    public static void challengeGivesTheCrewATruceUntilTheCaptainDies(GameTestHelper h) {
        floor(h, 24);
        ResourceLocation island = island();
        PirateCaptain c = spawnCaptain(h, island, 4, 4);
        Pirate crew = h.spawn(MobContent.PIRATE.get(), new BlockPos(10, 1, 4));
        Pirate far = h.spawn(MobContent.PIRATE.get(), new BlockPos(22, 1, 22)); // ~25 blocks away: outside the truce
        NavySoldier navy = h.spawn(MobContent.NAVY_SOLDIER.get(), new BlockPos(4, 1, 10));
        Player player = MobTestSupport.playerInLevel(h, new Vec3(6.5, 1, 6.5), 0f);
        h.assertTrue(crew.attacksOnSight(player), "before: the crew attack the player");
        h.assertTrue(c.attacksOnSight(navy), "before: the captain fights the navy");

        // without a sword or without sneaking it is no challenge
        player.setShiftKeyDown(true);
        h.assertValueEqual(c.interact(player, InteractionHand.MAIN_HAND), InteractionResult.PASS, "no sword, no challenge");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.CUTLASS.get()));
        player.setShiftKeyDown(false);
        h.assertValueEqual(c.interact(player, InteractionHand.MAIN_HAND), InteractionResult.PASS, "not sneaking, no challenge");
        h.assertFalse(c.inDuel(), "no duel yet");

        player.setShiftKeyDown(true);
        h.assertTrue(c.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "the challenge is taken");
        h.assertTrue(c.inDuel() && player.getUUID().equals(c.duelOpponent()), "the captain duels the player");
        h.assertTrue(c.getTarget() == player, "the captain targets the challenger");
        h.assertFalse(crew.attacksOnSight(player), "the crew keep out of the duel");
        h.assertTrue(crew.hasTruce(player), "the crew hold a truce");
        h.assertFalse(far.hasTruce(player), "a pirate beyond duel_truce_range has none");
        h.assertFalse(c.attacksOnSight(navy), "in a duel the captain fights the challenger only");
        h.assertTrue(c.attacksOnSight(player), "his challenger");
        h.assertTrue(crew.attacksOnSight(navy), "the crew still fight the navy");

        // another challenger is refused while the duel runs
        Player second = MobTestSupport.playerInLevel(h, new Vec3(3.5, 1, 6.5), 0f);
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.SABER.get()));
        second.setShiftKeyDown(true);
        h.assertValueEqual(DuelChallenge.challenge(c, second), DuelRules.Refusal.BUSY, "the captain is busy");

        // the truce ends with the captain
        c.hurt(h.getLevel().damageSources().playerAttack(player), 1000f);
        h.assertTrue(c.isDeadOrDying(), "the captain fell");
        h.assertFalse(crew.hasTruce(player), "his death ends the truce");
        h.assertTrue(crew.attacksOnSight(player), "the crew fight again");
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    /** The challenger's death ends the duel and the truce. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "duel_lost")
    public static void challengersDeathEndsTheDuel(GameTestHelper h) {
        floor(h, 9);
        ResourceLocation island = island();
        PirateCaptain c = spawnCaptain(h, island, 2, 2);
        Pirate crew = h.spawn(MobContent.PIRATE.get(), new BlockPos(6, 1, 2));
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.RAPIER.get()));
        player.setShiftKeyDown(true);
        h.assertValueEqual(DuelChallenge.challenge(c, player), DuelRules.Refusal.NONE, "accepted");
        h.assertTrue(crew.hasTruce(player), "truce");
        player.hurt(h.getLevel().damageSources().mobAttack(c), 1000f);
        h.assertTrue(player.isDeadOrDying(), "the challenger fell");
        h.assertFalse(c.inDuel(), "the duel is over");
        h.assertFalse(crew.hasTruce(player), "the truce ended");
        c.discard();
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    /** A player who struck the captain first is refused; a creative player is refused. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "duel_refused")
    public static void grudgeAndCreativeAreRefused(GameTestHelper h) {
        floor(h, 9);
        ResourceLocation island = island();
        PirateCaptain c = spawnCaptain(h, island, 2, 2);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.CUTLASS.get()));
        player.setShiftKeyDown(true);
        c.hurt(h.getLevel().damageSources().playerAttack(player), 1f);
        h.assertTrue(c.hasGrudge(player), "struck first");
        h.assertValueEqual(DuelChallenge.challenge(c, player), DuelRules.Refusal.GRUDGE, "no honour, no duel");
        h.assertFalse(c.inDuel(), "no duel");
        c.discard();
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    // ------------------------------------------------------------------ death and drops

    /**
     * A player's kill: the proof goes to the killer, the kill is the kill_pirate deed, the captain drops his hat, a purse
     * of doubloons and a map bound to his island's treasure, and the registry marks him lost.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "kill")
    public static void killGivesProofDeedAndDrops(GameTestHelper h) {
        floor(h, 9);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ResourceLocation island = island();
        BlockPos treasure = h.absolutePos(new BlockPos(7, 0, 7));
        PortRegistry.get(server).add(new Port(island, PortKind.PIRATE_ISLAND, level.dimension(), treasure,
                BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 5, 8))),
                Climate.TROPICAL, List.of(), List.of(new TreasureSite(treasure, false))));
        PirateCaptain c = spawnCaptain(h, island, 4, 4);
        ServerPlayer killer = serverPlayer(h, "captain_killer");
        int pirates = Reputation.get(killer, Faction.PIRATES);
        c.hurt(killer.damageSources().playerAttack(killer), 1000f);
        h.assertTrue(c.isDeadOrDying(), "the captain fell");

        h.assertValueEqual(count(killer, ProofContent.BOUNTY_PROOF.get()), 1, "the proof is in the killer's inventory");
        ItemStack proof = killer.getInventory().items.stream().filter(s -> s.getItem() instanceof BountyProofItem).findFirst().orElseThrow();
        h.assertValueEqual(BountyProofItem.proofOf(proof).target(), c.getUUID(), "proof of the captain");
        if (Reputation.enabled()) {
            int expected = pirates + ReputationConfig.DEED_DELTAS.get(Deed.ATTACK_PIRATE).get(Faction.PIRATES).get()
                    + ReputationConfig.DEED_DELTAS.get(Deed.KILL_PIRATE).get(Faction.PIRATES).get();
            h.assertValueEqual(Reputation.get(killer, Faction.PIRATES), Math.max(-100, Math.min(100, expected)), "the kill_pirate deed");
        }
        BlockPos at = new BlockPos(4, 1, 4);
        h.assertValueEqual(countDropped(h, ApparelContent.CAPTAINS_HAT.get(), at), 1, "his hat");
        int coins = countDropped(h, TradeContent.DOUBLOON.get(), at);
        h.assertTrue(coins >= com.richardsenger.piratesnships.mob.MobLoot.CAPTAIN_DOUBLOONS_MIN
                && coins <= com.richardsenger.piratesnships.mob.MobLoot.CAPTAIN_DOUBLOONS_MAX, "a purse of doubloons, got " + coins);
        ItemEntity map = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(4),
                e -> e.getItem().is(TreasureMapContent.TREASURE_MAP.get())).stream().findFirst().orElse(null);
        h.assertTrue(map != null, "a treasure map");
        var data = TreasureMapService.data(map.getItem());
        h.assertTrue(data != null && data.port().equals(island) && data.site().equals(treasure), "bound to his island's treasure: " + data);

        CaptainEntry entry = CaptainRegistry.get(server).get(island).orElseThrow();
        h.assertFalse(entry.alive(), "the registry marks him lost");
        h.assertValueEqual(entry.diedDay(), IslandCaptains.today(server), "lost today");
        h.assertTrue(LawService.hasBounty(server, c.getUUID()), "the bounty waits for the proof");
        h.assertTrue(LawService.claimWithProof(killer, proof).success(), "the proof claims it");
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    // ------------------------------------------------------------------ capture and turn-in

    /** Captured and handed to a navy officer, the captain pays the captain's tier plus his bounty alive. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "turn_in")
    public static void capturedCaptainPaysTheCaptainsTier(GameTestHelper h) {
        floor(h, 9);
        MinecraftServer server = h.getLevel().getServer();
        ResourceLocation island = island();
        NavyOfficer officer = h.spawnWithNoFreeWill(MobContent.NAVY_OFFICER.get(), 1, 1, 1);
        PirateCaptain c = spawnCaptain(h, island, 2, 2);
        c.setNoAi(true);
        h.assertValueEqual(NavyOfficer.pirateTier(c), PirateTier.CAPTAIN, "the navy's rank of a captain");
        Player captor = MobTestSupport.playerInLevel(h, new Vec3(2.5, 1, 1.5), 0f);
        c.setHealth(c.getMaxHealth() * 0.1f);
        h.assertValueEqual(BrigService.capture(captor, c), CaptureRules.Result.OK, "captain captured");
        long bounty = LawService.bountyTotal(server, c.getUUID());
        long expected = LawConfig.bountyRules().turnInReward(PirateTier.CAPTAIN) + Math.round(bounty * LawConfig.bountyRules().aliveFactor());
        captor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(officer.interact(captor, InteractionHand.MAIN_HAND).consumesAction(), "handed over");
        h.assertValueEqual(Wallet.count(captor), expected, "captain's tier plus his bounty alive");
        h.assertTrue(c.isRemoved(), "the navy leads him away");
        h.assertFalse(CaptainRegistry.get(server).get(island).orElseThrow().alive(), "the island lost its captain");
        h.assertFalse(LawService.hasBounty(server, c.getUUID()), "the bounty is claimed");
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }

    // ------------------------------------------------------------------ succession

    /** A captain lost respawn_days ago gets a successor at his post with a new name and a new bounty; a recent loss does not. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "successor")
    public static void successorTakesThePostAfterRespawnDays(GameTestHelper h) {
        floor(h, 9);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        long today = IslandCaptains.today(server);
        int days = CaptainConfig.RESPAWN_DAYS.get();

        ResourceLocation recent = island();
        BlockPos recentPost = h.absolutePos(new BlockPos(2, 1, 6));
        CaptainRegistry.get(server).put(recent, new CaptainEntry(UUID.randomUUID(), "Recent", 0, false, today, level.dimension(),
                recentPost, Direction.SOUTH));
        ResourceLocation island = island();
        BlockPos post = h.absolutePos(new BlockPos(4, 1, 4));
        UUID old = UUID.randomUUID();
        String oldName = CaptainNames.name(island.toString(), 0);
        CaptainRegistry.get(server).put(island, new CaptainEntry(old, oldName, 0, false, today - days, level.dimension(),
                post, Direction.EAST));
        if (days > 0) h.assertFalse(CaptainRegistry.get(server).get(recent).orElseThrow().successorDue(today, days), "a recent loss waits");
        IslandCaptains.succeed(server);

        CaptainEntry next = CaptainRegistry.get(server).get(island).orElseThrow();
        h.assertTrue(next.alive() && next.generation() == 1 && !next.id().equals(old), "a successor: " + next);
        h.assertFalse(next.name().equals(oldName), "with a new name");
        h.assertValueEqual(next.name(), CaptainNames.name(island.toString(), 1), "the next name of the island");
        PirateCaptain successor = level.getEntity(next.id()) instanceof PirateCaptain p ? p : null;
        h.assertTrue(successor != null, "the successor stands in the world");
        h.assertValueEqual(successor.blockPosition(), post, "at the old post");
        h.assertValueEqual(successor.postFacing(), Direction.EAST, "facing the old way");
        h.assertTrue(LawService.hasBounty(server, next.id()), "a new bounty");
        if (days > 0) {
            h.assertFalse(CaptainRegistry.get(server).get(recent).orElseThrow().alive(), "no successor for a recent loss");
        }
        successor.discard();
        cleanUp(h, island, next.id());
        cleanUp(h, recent);
        h.succeed();
    }

    // ------------------------------------------------------------------ config

    /** With {@code mobs.captain.enabled} off nothing spawns and no successor appears. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "enabled")
    public static void disabledCaptainsSpawnNothing(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.enabled(MobKind.PIRATE_CAPTAIN), false);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ResourceLocation island = island();
        h.assertTrue(IslandCaptains.spawn(level, island, h.absolutePos(new BlockPos(4, 1, 4)), Direction.NORTH, 0) == null,
                "no captain while disabled");
        h.assertTrue(CaptainRegistry.get(server).get(island).isEmpty(), "no registry entry");
        CaptainRegistry.get(server).put(island, new CaptainEntry(UUID.randomUUID(), "Gone", 0, false, -1000L, level.dimension(),
                h.absolutePos(new BlockPos(4, 1, 4)), Direction.NORTH));
        h.assertValueEqual(IslandCaptains.succeed(server), 0, "no successor while disabled");
        h.assertFalse(CaptainRegistry.get(server).get(island).orElseThrow().alive(), "still lost");
        cleanUp(h, island);
        h.succeed();
    }

    /** With {@code mobs.captain.duel_enabled} off the challenge is refused. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "duel")
    public static void duelCanBeSwitchedOff(GameTestHelper h) {
        ConfigOverrides.during(h, CaptainConfig.DUEL_ENABLED, false);
        floor(h, 9);
        ResourceLocation island = island();
        PirateCaptain c = spawnCaptain(h, island, 2, 2);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.CUTLASS.get()));
        player.setShiftKeyDown(true);
        h.assertValueEqual(DuelChallenge.challenge(c, player), DuelRules.Refusal.DISABLED, "duels are off");
        h.assertFalse(c.inDuel(), "no duel");
        c.discard();
        cleanUp(h, island, c.getUUID());
        h.succeed();
    }
}

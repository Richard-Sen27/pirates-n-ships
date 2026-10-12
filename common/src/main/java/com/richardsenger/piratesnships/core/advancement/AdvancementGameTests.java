package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.AlmanacReading;
import com.richardsenger.piratesnships.almanac.data.AlmanacBook;
import com.richardsenger.piratesnships.almanac.data.AlmanacProgress;
import com.richardsenger.piratesnships.almanac.data.Chapter;
import com.richardsenger.piratesnships.almanac.sync.AlmanacPagePayload;
import com.richardsenger.piratesnships.almanac.sync.AlmanacSync;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.net.ChartMarkerPayload;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.brig.CaptureRules;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.rpg.quest.Quest;
import com.richardsenger.piratesnships.rpg.quest.QuestRewards;
import com.richardsenger.piratesnships.rpg.quest.QuestRules;
import com.richardsenger.piratesnships.rpg.quest.QuestState;
import com.richardsenger.piratesnships.rpg.quest.QuestTarget;
import com.richardsenger.piratesnships.rpg.quest.QuestType;
import com.richardsenger.piratesnships.rpg.quest.Quests;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The advancement tab in a real server (ADV1, {@code docs/advancements.md}): the tree loads with every parent, each
 * family of the mod's criteria is awarded from its event site (or, where the event needs a world a test player cannot
 * join, from its {@link ModAdvancements} line), the vanilla criteria (the helm, the rope railing, the wreck chest, the
 * shark) fire, experience is paid once and scaled, and {@code advancements.enabled} off fires nothing. Tests that change
 * config or global state (wind, sea state) run in batches of their own.
 */
public final class AdvancementGameTests {

    private static final String BATCH = "pirates_n_ships_advancements";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_advancements_";

    private static int menuIds = 9000;

    private AdvancementGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AdvancementGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A real server player with a mock connection, not added to the level (the career tests' pattern). */
    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "adv_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(at)));
        p.getInventory().clearContent();
        return p;
    }

    private static ServerPlayer player(GameTestHelper h) {
        return player(h, new BlockPos(1, 1, 1));
    }

    private static AdvancementHolder holder(GameTestHelper h, String path) {
        AdvancementHolder a = h.getLevel().getServer().getAdvancements().get(Constants.id(path));
        if (a == null) throw new GameTestAssertException("advancement " + path + " is not loaded");
        return a;
    }

    private static boolean done(GameTestHelper h, ServerPlayer p, String path) {
        return p.getAdvancements().getOrStartProgress(holder(h, path)).isDone();
    }

    private static void assertDone(GameTestHelper h, ServerPlayer p, String... paths) {
        for (String path : paths) h.assertTrue(done(h, p, path), path + " was not awarded");
    }

    private static void assertNotDone(GameTestHelper h, ServerPlayer p, String... paths) {
        for (String path : paths) h.assertFalse(done(h, p, path), path + " was awarded too early");
    }

    /** A port of {@code kind} over the 9×9 test area. */
    private static Port port(GameTestHelper h, PortKind kind, List<TreasureSite> treasures) {
        Port port = new Port(Constants.id("gametest/adv_" + kind.getSerializedName() + "_" + UUID.randomUUID().toString().substring(0, 8)),
                kind, h.getLevel().dimension(), h.absolutePos(new BlockPos(4, 1, 4)),
                BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 5, 8))), Climate.TEMPERATE,
                List.of(), treasures);
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static void removePort(GameTestHelper h, Port port) {
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
    }

    // ------------------------------------------------------------------ the tree

    /** Every node of the tree is loaded with its parent, frame, icon and visibility; the root carries the tab. */
    @ModGameTest(batch = BATCH)
    public static void treeLoadsWithEveryParent(GameTestHelper h) {
        ServerAdvancementManager manager = h.getLevel().getServer().getAdvancements();
        for (AdvancementTree.Node node : AdvancementTree.nodes()) {
            AdvancementHolder a = holder(h, node.path());
            var display = a.value().display().orElseThrow(() -> new GameTestAssertException(node.path() + " has no display"));
            h.assertValueEqual(display.isHidden(), node.hidden(), node.path() + " hidden");
            h.assertValueEqual(display.getType().getSerializedName(), node.frame().id(), node.path() + " frame");
            if (node.parent() == null) {
                h.assertTrue(a.value().isRoot(), node.path() + " should be the root");
                h.assertTrue(display.getBackground().isPresent(), "the root has no background");
                h.assertTrue(manager.tree().get(a) != null && manager.tree().get(a).parent() == null, "root not in the tree");
            } else {
                h.assertValueEqual(a.value().parent().map(ResourceLocation::toString).orElse(""),
                        Constants.MOD_ID + ":" + node.parent(), node.path() + " parent");
                var treeNode = manager.tree().get(a);
                h.assertTrue(treeNode != null && treeNode.parent() != null, node.path() + " hangs in no tree");
                h.assertValueEqual(treeNode.root().holder().id(), Constants.id("root"), node.path() + " tab");
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ ships and sailing

    /**
     * A ship assembled by the player: Launched!; the winch sets her sail: Canvas Aloft; with the wind astern she makes
     * way with the player aboard: Following Wind.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "_sailing")
    public static void assembleSetSailAndMakeWay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.HALF);
        SailingGameTestsShips.ballast(h, 17, 3);
        h.setBlock(new BlockPos(18, 9, 4), SailingBlocks.SAIL_WINCH.get());
        ServerPlayer p = player(h, new BlockPos(19, 10, 5));
        assertNotDone(h, p, "shipwright/launch_ship");

        AssemblyResult r = ShipAssembler.assemble(level, h.absolutePos(helm), p);
        h.assertTrue(r.shipId() != null, "assembly failed: " + r);
        ShipTestCleanup.track(h, r.shipId());
        assertDone(h, p, "shipwright/launch_ship");
        assertNotDone(h, p, "shipwright/big_ship");

        ShipBody ship = SableShips.byId(level, r.shipId());
        h.assertTrue(ship != null, "no ship");
        BlockPos winch = ship.plotBlocks().stream().filter(b -> level.getBlockState(b).is(SailingBlocks.SAIL_WINCH.get()))
                .findFirst().orElseThrow();
        SailWinchBlock.use(level, winch, p); // HALF -> FULL
        assertDone(h, p, "shipwright/set_sail");

        String dim = level.dimension().location().toString();
        WindOverride.set(dim, 0.0, 6.0, level.getGameTime() + 380);
        h.succeedWhen(() -> {
            AdvancementWatch.checkShip(p, ship);
            if (!done(h, p, "shipwright/sail_with_wind")) throw new GameTestAssertException("not under way yet");
            WindOverride.clear(dim);
        });
    }

    /** A minute aboard in a storm: Weathered the Storm (and not before); off the ship the count starts over. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "_storm")
    public static void aMinuteOfStormAboard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED);
        SailingGameTestsShips.ballast(h, 17, 3);
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        ShipBody ship = SableShips.byId(level, r.shipId());
        h.assertTrue(ship != null, "no ship");
        ServerPlayer p = player(h, new BlockPos(19, 10, 5));
        SeaStates.set(level, SeaState.STORM, null, level.getGameTime() + 190);
        try {
            for (int s = 0; s < 59; s++) AdvancementWatch.checkShip(p, ship);
            AdvancementWatch.checkShip(p, null); // stepped ashore: starts over
            for (int s = 0; s < 59; s++) AdvancementWatch.checkShip(p, ship);
            assertNotDone(h, p, "world/storm");
            AdvancementWatch.checkShip(p, ship);
            assertDone(h, p, "world/storm");
        } finally {
            SeaStates.clear(level);
            AdvancementWatch.forget(p.getUUID());
        }
        h.succeed();
    }

    /** The helm in the inventory (vanilla's inventory_changed) and a rope railing placed (placed_block). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void helmInHandAndRopeRailingPlaced(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack spruceHelm = new ItemStack(com.richardsenger.piratesnships.ship.assembly.AssemblyContent.HELMS.get(ShipWood.SPRUCE));
        p.getInventory().add(spruceHelm.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), spruceHelm);
        assertDone(h, p, "shipwright/craft_helm", "shipwright/helm_wood");

        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos at = h.absolutePos(new BlockPos(4, 1, 4));
        ItemStack railing = new ItemStack(RailingContent.ROPE_RAILINGS.get(ShipWood.BIRCH));
        p.setItemInHand(InteractionHand.MAIN_HAND, railing);
        var placed = railing.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at.below(), false)));
        h.assertTrue(placed.consumesAction(), "the rope railing was not placed: " + placed);
        assertDone(h, p, "shipwright/rope_railing");
        h.succeed();
    }

    // ------------------------------------------------------------------ combat

    /** A loaded cannon fired by the player: Fire in the Hole; a hit on a ship by the player's own ball: A Palpable Hit. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void cannonFiredAndShipHit(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(1, 1, 4);
        var master = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.EAST);
        h.setBlock(rel, master);
        h.setBlock(CannonRules.rearOf(rel, Direction.EAST), CannonContent.CANNON.get().rearState(master));
        BlockPos pos = h.absolutePos(rel);
        ServerPlayer p = player(h);
        p.getAbilities().instabuild = true;
        h.assertTrue(CannonService.load(level, pos, p, new ItemStack(Items.GUNPOWDER)).outcome() == CannonService.Outcome.POWDER_IN, "powder");
        h.assertTrue(CannonService.load(level, pos, p, new ItemStack(CombatContent.CANNONBALL.get())).outcome() == CannonService.Outcome.BALL_IN, "ball");
        CannonService.Use use = CannonService.fire(level, pos, p);
        h.assertTrue(use.outcome() == CannonService.Outcome.FIRED, "no shot: " + use.outcome());
        if (use.ball() != null) use.ball().discard();
        assertDone(h, p, "combat/fire_cannon");

        // the hit listener's shooter: the firing player, else the online owner of the firing ship
        h.assertTrue(AdvancementModule.shooter(level.getServer(), p, null) == p, "the shooter is not the player");
        h.assertTrue(AdvancementModule.shooter(level.getServer(), null, UUID.randomUUID()) == null, "an unknown ship has a shooter");
        ModAdvancements.shipHit(AdvancementModule.shooter(level.getServer(), p, null));
        assertDone(h, p, "combat/hit_ship");
        h.succeed();
    }

    /** A shark killed by the player: vanilla's player_killed_entity (Shark Bait). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void sharkKilled(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob shark = h.spawnWithNoFreeWill(MobContent.SHARK.get(), new BlockPos(4, 2, 4));
        shark.hurt(h.getLevel().damageSources().playerAttack(p), 10_000f);
        h.assertTrue(shark.isDeadOrDying(), "the shark lives");
        assertDone(h, p, "combat/shark");
        assertNotDone(h, p, "combat/kraken");
        h.succeed();
    }

    /** The criminal score paid off at a fine: Square with the Crown; a weak pillager put in shackles: In Irons. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void fineAndShackles(GameTestHelper h) {
        ServerPlayer p = player(h);
        LawService.setScore(p, 60);
        LawService.payFine(p, 10_000);
        assertDone(h, p, "law/bounty_paid");

        Mob pillager = h.spawnWithNoFreeWill(EntityType.PILLAGER, new BlockPos(4, 1, 4));
        pillager.setHealth(4.0f);
        h.assertValueEqual(BrigService.capture(p, pillager), CaptureRules.Result.OK, "capture");
        assertDone(h, p, "law/shackled");
        assertNotDone(h, p, "law/brigged");
        pillager.discard();
        h.succeed();
    }

    // ------------------------------------------------------------------ the world

    /** Standing in each kind of port: Landfall at the first, Ports of Call once all three were seen. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "_ports")
    public static void everyPortKindVisited(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(4, 1, 4));
        List<PortKind> kinds = List.of(PortKind.SEAFARER_VILLAGE, PortKind.NAVY_OUTPOST, PortKind.PIRATE_ISLAND);
        for (int i = 0; i < kinds.size(); i++) {
            Port port = port(h, kinds.get(i), List.of());
            try {
                AdvancementWatch.check(p);
            } finally {
                removePort(h, port);
            }
            assertDone(h, p, "world/landfall");
            if (i < kinds.size() - 1) assertNotDone(h, p, "world/all_ports");
        }
        assertDone(h, p, "world/all_ports");
        h.succeed();
    }

    /** Opening the chest at a treasure site: X Marks the Spot; a chest with the wreck loot table: Davy Jones' Locker. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "_ports")
    public static void treasureDugAndWreckOpened(GameTestHelper h) {
        BlockPos site = new BlockPos(2, 1, 2);
        h.setBlock(site, Blocks.CHEST);
        Port port = port(h, PortKind.PIRATE_ISLAND, List.of(new TreasureSite(h.absolutePos(site), false)));
        ServerPlayer p = player(h, new BlockPos(2, 1, 3));
        try {
            ChestBlockEntity chest = h.getBlockEntity(site);
            AbstractContainerMenu menu = chest.createMenu(++menuIds, p.getInventory(), p);
            h.assertTrue(menu != null, "no chest menu");
            CommonEvents.CONTAINER_OPEN.invoker().on(p, menu);
            menu.removed(p);
        } finally {
            removePort(h, port);
        }
        assertDone(h, p, "world/treasure");

        BlockPos wreckChest = new BlockPos(6, 1, 6);
        h.setBlock(wreckChest, Blocks.CHEST);
        ChestBlockEntity chest = h.getBlockEntity(wreckChest);
        chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/wreck")), 1L);
        chest.unpackLootTable(p);
        assertDone(h, p, "world/wreck");
        h.succeed();
    }

    /** Ten markers on the chart: Cartographer (not at nine). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void tenChartMarkers(GameTestHelper h) {
        ServerPlayer p = player(h);
        ChartBackend.record(p.getUUID());
        try {
            for (int i = 0; i < 10; i++) {
                if (i == 9) assertNotDone(h, p, "world/chart_markers");
                h.assertTrue(ChartBackend.handleMarker(p, ChartMarkerPayload.add(100 * i, -200, MarkerIcon.X, "m" + i)).ok(), "marker " + i);
            }
        } finally {
            ChartBackend.stopRecording(p.getUUID());
        }
        assertDone(h, p, "world/chart_markers");
        h.succeed();
    }

    // ------------------------------------------------------------------ trade

    /**
     * Doubloons paid out add up: Pieces of Eight at the first, A Fat Purse at 1,000, A King's Ransom at 10,000; the
     * experience is paid once per advancement.
     */
    @ModGameTest(batch = BATCH)
    public static void doubloonsAddUpAndPayExperienceOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        int xp = p.totalExperience;
        Wallet.give(p, 1);
        assertDone(h, p, "trade/first_doubloon");
        h.assertValueEqual(p.totalExperience - xp, AdvancementTree.XP_TASK, "experience of a task");
        Wallet.give(p, 998);
        assertNotDone(h, p, "trade/thousand");
        Wallet.give(p, 1);
        assertDone(h, p, "trade/thousand");
        assertNotDone(h, p, "trade/ten_thousand");
        Wallet.give(p, 9_000);
        assertDone(h, p, "trade/ten_thousand");
        h.assertValueEqual(p.totalExperience - xp, AdvancementTree.XP_TASK + AdvancementTree.XP_GOAL + AdvancementTree.XP_CHALLENGE,
                "experience after three advancements");
        AdvancementXp.settle(p);
        h.assertValueEqual(p.totalExperience - xp, AdvancementTree.XP_TASK + AdvancementTree.XP_GOAL + AdvancementTree.XP_CHALLENGE,
                "experience paid twice");
        h.assertValueEqual(Services.ATTACHMENTS.get(p, AdvancementAttachments.RECORD).doubloons(), 10_000L, "doubloons counted");
        h.succeed();
    }

    /** An escort quest's reward: Harbour Business and Safe Passage. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "_ports")
    public static void escortQuestCompleted(GameTestHelper h) {
        Port village = port(h, PortKind.SEAFARER_VILLAGE, List.of());
        ServerPlayer p = player(h);
        try {
            long day = Quests.day(h.getLevel().getServer());
            Quest q = new Quest(UUID.randomUUID(), village.id(), village.kind(), QuestType.ESCORT, QuestTarget.None.INSTANCE, 1, 1, 50,
                    QuestRules.rewardDeed(village.kind()), day, day + 1, day + 5, QuestState.DONE);
            QuestRewards.grant(p, q);
        } finally {
            removePort(h, village);
        }
        assertDone(h, p, "trade/quest", "trade/convoy", "trade/first_doubloon");
        h.succeed();
    }

    // ------------------------------------------------------------------ careers

    /** Straight to admiral and pirate lord through the career API: every rank on the way; a letter of marque. */
    @ModGameTest(batch = BATCH)
    public static void ranksThroughTheCareerApi(GameTestHelper h) {
        ServerPlayer navy = player(h);
        Careers.setNavy(navy, NavyRank.LIEUTENANT);
        assertDone(h, navy, "career/midshipman", "career/lieutenant");
        assertNotDone(h, navy, "career/captain");
        Careers.setNavy(navy, NavyRank.ADMIRAL);
        assertDone(h, navy, "career/captain", "career/commodore", "career/admiral");
        Careers.forceLetter(navy);
        assertDone(h, navy, "law/letter");

        ServerPlayer pirate = player(h);
        Careers.setInfamy(pirate, InfamyRank.PIRATE_LORD);
        assertDone(h, pirate, "career/buccaneer", "career/dread_captain", "career/pirate_lord");
        assertNotDone(h, pirate, "career/midshipman");
        h.succeed();
    }

    // ------------------------------------------------------------------ the Almanac

    /** The Almanac in hand: Required Reading; a page of every chapter: Cover to Cover (not before the last). */
    @ModGameTest(batch = BATCH)
    public static void almanacOpenedAndEveryChapterRead(GameTestHelper h) {
        ServerPlayer p = player(h);
        AlmanacReading.record(p.getUUID());
        try {
            h.assertTrue(AlmanacReading.readInHand(p), "the almanac did not open");
        } finally {
            AlmanacReading.stopRecording(p.getUUID());
        }
        assertDone(h, p, "almanac/open");

        List<Chapter> chapters = AlmanacBook.server().chapters();
        h.assertTrue(chapters.size() > 1, "no chapters loaded");
        for (int i = 0; i < chapters.size(); i++) {
            if (i == chapters.size() - 1) assertNotDone(h, p, "almanac/all_chapters");
            AlmanacSync.onReport(new AlmanacPagePayload.Report(AlmanacProgress.at(chapters.get(i).id(), 0)), p);
        }
        assertDone(h, p, "almanac/all_chapters");

        SceneOpenedPayload.handle(new SceneOpenedPayload(Constants.id("cannon_loading")), p);
        assertDone(h, p, "almanac/scene");
        h.succeed();
    }

    // ------------------------------------------------------------------ the remaining families

    /**
     * Every remaining family through its {@link ModAdvancements} line (their events need a ship in a port, a crew or a
     * player Sable tracks aboard, which a test player cannot be): each awards its advancement, and a value below the
     * bound does not.
     */
    @ModGameTest(batch = BATCH)
    public static void everyOtherFamilyAwardsItsAdvancement(GameTestHelper h) {
        ServerPlayer p = player(h);
        ModAdvancements.anchorDropped(p);
        ModAdvancements.leakFixed(p);
        ModAdvancements.shipOrdered(p, Constants.id("starter_sloop"));
        assertDone(h, p, "shipwright/drop_anchor", "shipwright/fix_leak", "shipwright/order_ship");
        ModAdvancements.shipLaunched(p, Constants.id("starter_sloop"), 300);
        assertNotDone(h, p, "shipwright/launch_brig");
        ModAdvancements.shipLaunched(p, Constants.id("brig"), 900);
        assertDone(h, p, "shipwright/launch_brig");
        ModAdvancements.shipAssembled(p, 499);
        assertNotDone(h, p, "shipwright/big_ship");
        ModAdvancements.shipAssembled(p, 500);
        assertDone(h, p, "shipwright/big_ship");

        ModAdvancements.crewHired(p, 7);
        assertDone(h, p, "crew/hire_hand");
        assertNotDone(h, p, "crew/full_crew");
        ModAdvancements.crewHired(p, 8);
        ModAdvancements.orderGiven(p, "hoist");
        ModAdvancements.hammockNight(p);
        assertDone(h, p, "crew/full_crew", "crew/give_order", "crew/hammock_night");
        UUID ship = UUID.randomUUID();
        for (int day = 1; day <= 9; day++) ModAdvancements.crewDay(p, ship, 4, 0, false);
        assertDone(h, p, "crew/wages_paid");
        ModAdvancements.crewDay(p, ship, 4, 1, true); // a deserter: the count starts over
        assertDone(h, p, "crew/deserter_returned");
        for (int day = 1; day <= 9; day++) ModAdvancements.crewDay(p, ship, 0, 0, false);
        assertNotDone(h, p, "crew/loyal_crew");
        ModAdvancements.crewDay(p, ship, 0, 0, false);
        assertDone(h, p, "crew/loyal_crew");

        ModAdvancements.contractDelivered(p);
        ModAdvancements.grappleLatched(p);
        ModAdvancements.shipBoarded(p, "plank");
        ModAdvancements.shipCaptured(p, "navy");
        assertDone(h, p, "trade/contract", "combat/grapple", "combat/board", "combat/capture");

        ModAdvancements.flagHoisted(p, "navy");
        assertDone(h, p, "world/fly_flag");
        assertNotDone(h, p, "law/black_flag");
        ModAdvancements.flagHoisted(p, "jolly_roger");
        ModAdvancements.prisonerTaken(p, "brigged");
        assertDone(h, p, "law/black_flag", "law/brigged");
        h.succeed();
    }

    /** {@code advancements.enabled} off: no criterion of the mod fires and no experience is paid. */
    @ModGameTest(batch = CONFIG_BATCH + "enabled")
    public static void disabledFiresNothing(GameTestHelper h) {
        ConfigOverrides.during(h, AdvancementConfig.ENABLED, false);
        ServerPlayer p = player(h);
        int xp = p.totalExperience;
        Wallet.give(p, 5);
        ModAdvancements.anchorDropped(p);
        AlmanacReading.record(p.getUUID());
        try {
            AlmanacReading.readInHand(p);
        } finally {
            AlmanacReading.stopRecording(p.getUUID());
        }
        assertNotDone(h, p, "trade/first_doubloon", "shipwright/drop_anchor", "almanac/open");
        h.assertValueEqual(p.totalExperience, xp, "experience while disabled");
        h.succeed();
    }

    /** {@code advancements.experience_scale} 2: a task pays twice its experience. */
    @ModGameTest(batch = CONFIG_BATCH + "scale")
    public static void experienceIsScaled(GameTestHelper h) {
        ConfigOverrides.during(h, AdvancementConfig.EXPERIENCE_SCALE, 2.0);
        ServerPlayer p = player(h);
        int xp = p.totalExperience;
        ModAdvancements.anchorDropped(p);
        assertDone(h, p, "shipwright/drop_anchor");
        h.assertValueEqual(p.totalExperience - xp, 2 * AdvancementTree.XP_TASK, "scaled experience");
        h.succeed();
    }
}

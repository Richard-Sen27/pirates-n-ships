package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.block.PartBlockEntity;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.content.MarketBoardPart;
import com.richardsenger.piratesnships.law.content.MarketNoticeBoardBlock;
import com.richardsenger.piratesnships.law.content.MarketNoticeBoardBlockEntity;
import com.richardsenger.piratesnships.law.content.NoticeBoardLoad;
import com.richardsenger.piratesnships.law.net.NoticeBoardBackend;
import com.richardsenger.piratesnships.law.net.NoticeBoardPayloads;
import com.richardsenger.piratesnships.law.net.NoticeBoardPostings;
import com.richardsenger.piratesnships.rpg.quest.Quest;
import com.richardsenger.piratesnships.rpg.quest.QuestData;
import com.richardsenger.piratesnships.rpg.quest.QuestRules;
import com.richardsenger.piratesnships.rpg.quest.QuestState;
import com.richardsenger.piratesnships.rpg.quest.QuestTarget;
import com.richardsenger.piratesnships.rpg.quest.QuestType;
import com.richardsenger.piratesnships.rpg.quest.Quests;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The market notice board (NB2) on real blocks: placing needs a free 2×2 and refuses otherwise, breaking any part takes
 * the whole board and drops it once (nothing in creative), using any part opens the board's screen at its anchor, the
 * {@code load} of all four parts follows the postings (bounties placed through the backend, a port's quest offers),
 * wood variants place, and the village's market square carries the board on its posts.
 */
public final class MarketNoticeBoardGameTests {

    /** The load tests clear the global bounty board for their run, so they run alone in their own batch. */
    private static final String LOAD_BATCH = "pirates_n_ships_market_notice_board_load";
    private static final BlockPos ANCHOR = new BlockPos(3, 1, 3);

    private MarketNoticeBoardGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MarketNoticeBoardGameTests.class);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    /** A survival player looking south (so the board's notices face north and it runs west and up). */
    private static Player placer(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setYRot(0f);
        p.setXRot(60f);
        p.yHeadRot = 0f;
        return p;
    }

    /** Uses a board item of {@code wood} on the top of the block below {@code rel}. */
    private static InteractionResult place(GameTestHelper h, Player player, ShipWood wood, BlockPos rel) {
        BlockPos below = h.absolutePos(rel.below());
        ItemStack stack = new ItemStack(LawContent.MARKET_NOTICE_BOARDS.get(wood));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** A board of {@code wood} set directly, notices north, anchor at {@code rel}; the absolute anchor. */
    private static BlockPos board(GameTestHelper h, ShipWood wood, BlockPos rel) {
        BlockState anchor = LawContent.MARKET_NOTICE_BOARDS.get(wood).defaultBlockState().setValue(MarketNoticeBoardBlock.FACING, Direction.NORTH);
        h.setBlock(rel, anchor);
        BlockPos abs = h.absolutePos(rel);
        MarketNoticeBoardBlock.placeOthers(h.getLevel(), abs, anchor);
        return abs;
    }

    /** The four parts of the board anchored at {@code anchor} (absolute) facing {@code facing}, in part order. */
    private static List<BlockState> parts(GameTestHelper h, BlockPos anchor, Direction facing) {
        List<BlockState> out = new ArrayList<>();
        for (BlockPos c : MarketBoardPart.cells(anchor, facing)) out.add(h.getLevel().getBlockState(c));
        return out;
    }

    private static void assertWhole(GameTestHelper h, BlockPos anchor, Direction facing, Block block) {
        List<BlockState> parts = parts(h, anchor, facing);
        for (MarketBoardPart p : MarketBoardPart.values()) {
            BlockState s = parts.get(p.ordinal());
            h.assertTrue(s.is(block), p + ": expected " + block + ", found " + s);
            h.assertValueEqual(s.getValue(MarketNoticeBoardBlock.PART), p, "the part at " + p + "'s cell");
            h.assertValueEqual(s.getValue(MarketNoticeBoardBlock.FACING), facing, p + " facing");
        }
        BlockEntity anchorEntity = h.getLevel().getBlockEntity(anchor);
        h.assertTrue(anchorEntity instanceof MarketNoticeBoardBlockEntity, "the anchor's block entity: " + anchorEntity);
        for (int i = 1; i < 4; i++) {
            BlockEntity be = h.getLevel().getBlockEntity(MarketBoardPart.cells(anchor, facing).get(i));
            h.assertTrue(be instanceof PartBlockEntity && !(be instanceof MarketNoticeBoardBlockEntity), "an empty part entity at part " + i + ": " + be);
        }
    }

    private static void assertGone(GameTestHelper h, BlockPos anchor, Direction facing, String why) {
        for (BlockPos c : MarketBoardPart.cells(anchor, facing)) {
            h.assertTrue(h.getLevel().getBlockState(c).isAir(), why + ": " + h.getLevel().getBlockState(c) + " left at " + c);
        }
    }

    private static void assertLoad(GameTestHelper h, BlockPos anchor, NoticeBoardLoad load, String why) {
        for (BlockState s : parts(h, anchor, Direction.NORTH)) {
            h.assertTrue(s.getBlock() instanceof MarketNoticeBoardBlock, why + ": not a board part: " + s);
            h.assertValueEqual(s.getValue(MarketNoticeBoardBlock.LOAD), load, why + " (" + s.getValue(MarketNoticeBoardBlock.PART) + ")");
        }
    }

    /** A server player with a recorded connection, as in {@link NoticeBoardGameTests}. */
    private static ServerPlayer player(GameTestHelper h, String name, long coins, BlockPos rel) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(rel)));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        return p;
    }

    // --- placing and breaking -----------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placingNeedsAFreeTwoByTwo(GameTestHelper h) {
        Player player = placer(h);
        Block board = LawContent.MARKET_NOTICE_BOARD.get();
        // blocked: something stands where the top-right part would go (west of the anchor, one up)
        BlockPos blocked = new BlockPos(6, 1, 3);
        h.setBlock(blocked.west().above(), Blocks.STONE);
        h.assertFalse(place(h, player, ShipWood.DARK_OAK, blocked).consumesAction(), "placed although the 2x2 is not free");
        h.assertTrue(h.getBlockState(blocked).isAir(), "nothing placed at the anchor: " + h.getBlockState(blocked));
        h.assertTrue(h.getBlockState(blocked.west()).isAir(), "nothing placed beside it");
        // free: the clicked cell is the bottom left, the board runs to the placer's right (west) and up
        InteractionResult result = place(h, player, ShipWood.DARK_OAK, ANCHOR);
        h.assertTrue(result.consumesAction(), "the board was not placed: " + result);
        assertWhole(h, h.absolutePos(ANCHOR), Direction.NORTH, board);
        h.assertValueEqual(h.getBlockState(ANCHOR.west()).getValue(MarketNoticeBoardBlock.PART), MarketBoardPart.BOTTOM_RIGHT, "west is the right");
        h.assertValueEqual(h.getBlockState(ANCHOR.above()).getValue(MarketNoticeBoardBlock.PART), MarketBoardPart.TOP_LEFT, "up is the top");
        player.discard();
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingAnyPartTakesTheWholeBoardAndDropsItOnce(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int i = 0;
        for (MarketBoardPart broken : MarketBoardPart.values()) {
            BlockPos rel = new BlockPos(2 + 2 * (i % 3), 1, 1 + 4 * (i / 3));
            BlockPos anchor = board(h, ShipWood.DARK_OAK, rel);
            level.destroyBlock(MarketBoardPart.cells(anchor, Direction.NORTH).get(broken.ordinal()), true);
            assertGone(h, anchor, Direction.NORTH, "broke the " + broken);
            i++;
        }
        // one board per break, never one per part
        h.assertItemEntityCountIs(LawContent.MARKET_NOTICE_BOARD.get().asItem(), new BlockPos(4, 1, 4), 9.0, 4);
        // in creative, breaking a part drops nothing at all
        BlockPos anchor = board(h, ShipWood.DARK_OAK, new BlockPos(6, 1, 5));
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos topRight = MarketBoardPart.cells(anchor, Direction.NORTH).get(MarketBoardPart.TOP_RIGHT.ordinal());
        BlockState s = level.getBlockState(topRight);
        s.getBlock().playerWillDestroy(level, topRight, s, creative);
        level.removeBlock(topRight, false);
        assertGone(h, anchor, Direction.NORTH, "broke the top right in creative");
        h.assertItemEntityCountIs(LawContent.MARKET_NOTICE_BOARD.get().asItem(), new BlockPos(4, 1, 4), 9.0, 4);
        creative.discard();
        h.succeed();
    }

    /** WOOD3 pattern: every vanilla wood's board places whole, with its block entities, and drops itself. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void everyVanillaWoodPlaces(GameTestHelper h) {
        Player player = placer(h);
        int i = 0;
        for (ShipWood wood : ShipWood.all()) {
            if (!wood.isVanilla() || !LawContent.MARKET_NOTICE_BOARDS.woods().contains(wood)) continue;
            BlockPos rel = new BlockPos(2 + 3 * (i % 7), 1, 2 + 5 * (i / 7));
            h.assertTrue(place(h, player, wood, rel).consumesAction(), wood.id() + ": not placed");
            Block block = LawContent.MARKET_NOTICE_BOARDS.get(wood);
            assertWhole(h, h.absolutePos(rel), Direction.NORTH, block);
            h.getLevel().destroyBlock(h.absolutePos(rel.above()), true);
            assertGone(h, h.absolutePos(rel), Direction.NORTH, wood.id() + " broken");
            h.assertItemEntityPresent(block.asItem(), rel, 3.0);
            i++;
        }
        h.assertTrue(i >= 10, "only " + i + " vanilla woods");
        player.discard();
        h.succeed();
    }

    // --- use ----------------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void usingAnyPartOpensTheBoard(GameTestHelper h) {
        BlockPos anchor = board(h, ShipWood.MANGROVE, ANCHOR);
        ServerPlayer p = player(h, "mnb_reader", 0, ANCHOR.north());
        List<CustomPacketPayload> sent = NoticeBoardBackend.record(p.getUUID());
        for (BlockPos c : MarketBoardPart.cells(anchor, Direction.NORTH)) {
            NoticeBoardBackend.close(p);
            BlockState s = h.getLevel().getBlockState(c);
            InteractionResult r = s.useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(c), Direction.NORTH, c, false));
            h.assertTrue(r.consumesAction(), s.getValue(MarketNoticeBoardBlock.PART) + " does not open: " + r);
            h.assertTrue(NoticeBoardBackend.isOpen(p.getUUID(), anchor), "the session is the anchor's after using " + s.getValue(MarketNoticeBoardBlock.PART));
        }
        long opens;
        synchronized (sent) {
            opens = sent.stream().filter(NoticeBoardPayloads.Open.class::isInstance)
                    .map(NoticeBoardPayloads.Open.class::cast).filter(o -> o.board().equals(anchor)).count();
        }
        h.assertValueEqual(opens, 4L, "four screens opened for the anchor");
        // the backend keeps the session while the board stands, and the anchor is a board for it
        h.assertTrue(NoticeBoardBackend.isBoard(h.getLevel().getBlockState(anchor)), "the anchor counts as a board");
        h.assertFalse(NoticeBoardBackend.isBoard(h.getLevel().getBlockState(anchor.above())), "another part does not");
        NoticeBoardBackend.stopRecording(p.getUUID());
        h.succeed();
    }

    // --- load ---------------------------------------------------------------------------------------------------

    /**
     * Bounties posted through the backend turn the board from empty to some and, at five, to busy on all four parts
     * (the global bounty board is cleared for the run and put back after).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LOAD_BATCH, timeoutTicks = 200)
    public static void theLoadFollowsTheBountiesPostedThroughTheBackend(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BountyBoardData data = BountyBoardData.get(server);
        BountyBoard saved = data.board();
        data.setBoard(BountyBoard.EMPTY);
        ConfigOverrides.during(h, LawConfig.NOTICE_BOARD_BUSY_THRESHOLD, 5);
        BlockPos anchor = board(h, ShipWood.DARK_OAK, ANCHOR);
        int quests = NoticeBoardPostings.questOffers(h.getLevel(), anchor);
        ServerPlayer payer = player(h, "mnb_payer", 500, ANCHOR.north());
        BountyTarget target = BountyTarget.npc(UUID.randomUUID(), "mnb_" + UUID.randomUUID().toString().substring(0, 8));
        int[] step = {-2}; // two ticks for the board's first count
        h.onEachTick(() -> {
            switch (step[0]) {
                case 0 -> {
                    // the first tick after placing counts: nothing posted
                    assertLoad(h, anchor, NoticeBoardLoad.of(quests, 5), "before any posting");
                    // a target on the board, then one bounty through the backend: the board recounts at once
                    LawService.placeBounty(server, UUID.randomUUID(), "Elsewhere", target, 20);
                    var r = NoticeBoardBackend.place(payer, target.name(), 20);
                    h.assertTrue(r.ok(), "posted: " + r.key());
                    step[0] = 1;
                }
                case 1 -> {
                    assertLoad(h, anchor, NoticeBoardLoad.of(quests + 2, 5), "two bounties posted");
                    for (int i = 0; i < 3; i++) h.assertTrue(NoticeBoardBackend.place(payer, target.name(), 20).ok(), "posted " + i);
                    step[0] = 2;
                }
                case 2 -> {
                    assertLoad(h, anchor, NoticeBoardLoad.BUSY, "five bounties posted");
                    h.assertValueEqual(NoticeBoardPostings.count(h.getLevel(), anchor), quests + 5, "postings counted");
                    data.setBoard(saved);
                    NoticeBoardBackend.stopRecording(payer.getUUID());
                    step[0] = 3;
                    h.succeed();
                }
                default -> {
                    if (step[0] < 0) step[0]++;
                }
            }
        });
        if (quests == 0) {
            h.assertValueEqual(NoticeBoardLoad.of(quests, 5), NoticeBoardLoad.EMPTY, "no quests here, an empty board to start");
        }
    }

    /**
     * A port's open quest offers count too, only for a board inside the port, and expired ones do not (the bounties on
     * the global board are counted in the same tick, so other tests' bounties change nothing).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100)
    public static void aPortsQuestOffersArePostedOnItsBoard(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos anchor = board(h, ShipWood.DARK_OAK, ANCHOR);
        BlockPos min = h.absolutePos(BlockPos.ZERO), max = h.absolutePos(new BlockPos(8, 5, 8));
        Port port = new Port(Constants.id("gametest/market_board_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.VILLAGE,
                h.getLevel().dimension(), h.absolutePos(new BlockPos(4, 1, 4)), BoundingBox.fromCorners(min, max), Climate.TEMPERATE, List.of());
        PortService.register(server, port);
        Quests.refresh(server, port.id()); // today's generated offers first, so they don't replace ours
        int before = NoticeBoardPostings.questOffers(h.getLevel(), anchor);
        long day = Quests.day(server);
        QuestData.get(server).addOffer(new Quest(UUID.randomUUID(), port.id(), port.kind(), QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE,
                3, 0, 50, QuestRules.rewardDeed(port.kind()), day, day + 1, 0L, QuestState.OFFERED));
        QuestData.get(server).addOffer(new Quest(UUID.randomUUID(), port.id(), port.kind(), QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE,
                3, 0, 50, QuestRules.rewardDeed(port.kind()), day - 5, day - 4, 0L, QuestState.OFFERED));
        int after = NoticeBoardPostings.questOffers(h.getLevel(), anchor);
        int outside = NoticeBoardPostings.questOffers(h.getLevel(), h.absolutePos(new BlockPos(30, 1, 30)));
        int bounties = NoticeBoardPostings.bounties(server);
        NoticeBoardLoad load = MarketNoticeBoardBlockEntity.refresh(h.getLevel(), anchor, h.getLevel().getBlockState(anchor));
        PortRegistry.get(server).remove(port.id());
        QuestData.get(server).forget(port.id());
        if (!Quests.enabled()) {
            h.assertValueEqual(after, 0, "quests off: none posted");
            h.succeed();
            return;
        }
        h.assertValueEqual(after, before + 1, "the open offer is posted, the expired one is not");
        h.assertTrue(outside == 0 || outside != after, "a board outside the port does not show its offers");
        h.assertValueEqual(load, NoticeBoardLoad.of(bounties + after, LawConfig.NOTICE_BOARD_BUSY_THRESHOLD.get()), "the board counts the offers");
        h.assertTrue(load != NoticeBoardLoad.EMPTY, "a port with an offer has something on its board");
        assertLoad(h, anchor, load, "all four parts");
        h.succeed();
    }

    // --- in the village -----------------------------------------------------------------------------------------

    /** The market square piece stands the board at its centre-back, whole, on its posts, the notices to the square. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60)
    public static void theMarketSquareCarriesTheBoard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ResourceLocation id = Constants.id("village/street_square_market");
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        h.assertTrue(template != null, id + " does not load");
        BlockPos origin = h.absolutePos(new BlockPos(1, 1, 1));
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        List<BlockPos> anchors = new ArrayList<>();
        BoundingBox box = template.getBoundingBox(new StructurePlaceSettings(), origin);
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MarketNoticeBoardBlock && s.getValue(MarketNoticeBoardBlock.PART).isAnchor()) anchors.add(p.immutable());
        }
        h.assertValueEqual(anchors.size(), 1, "market boards in the square");
        BlockPos anchor = anchors.get(0);
        BlockState s = level.getBlockState(anchor);
        h.assertValueEqual(s.getValue(MarketNoticeBoardBlock.FACING), Direction.NORTH, "the notices face the street and the stall");
        assertWhole(h, anchor, Direction.NORTH, s.getBlock());
        // centre-back: the square's middle columns, in the back rows (the street comes in from the north at z 0)
        int x = anchor.getX() - origin.getX(), z = anchor.getZ() - origin.getZ();
        h.assertTrue(x >= 6 && x <= 8 && z >= 11, "the board stands at the centre-back, found at " + x + ", " + z);
        // standing on the paving, the top part under the open sky
        h.assertFalse(level.getBlockState(anchor.below()).isAir(), "the board's posts stand on the ground");
        h.succeed();
    }
}

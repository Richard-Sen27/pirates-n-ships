package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.net.NoticeBoardBackend;
import com.richardsenger.piratesnships.law.net.NoticeBoardPayloads;
import com.richardsenger.piratesnships.law.net.NoticeBoardView;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The notice board (docs/design.md §13.2) on a real block with a server player whose payloads are recorded: opening
 * it lists the bounties, the place form pays and lists the new bounty, refusals keep the doubloons, the list
 * refreshes when the board changes, and the config switch.
 */
public final class NoticeBoardGameTests {

    private static final BlockPos BOARD = new BlockPos(1, 1, 1);

    private NoticeBoardGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NoticeBoardGameTests.class);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static BlockPos board(GameTestHelper helper) {
        helper.setBlock(BOARD, LawContent.NOTICE_BOARD.get().defaultBlockState());
        return helper.absolutePos(BOARD);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, long coins) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(BOARD.north())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        return p;
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(BOARD);
        return helper.getBlockState(BOARD).useWithoutItem(helper.getLevel(), p,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
    }

    /** A target on the board under a name no other test uses. */
    private static BountyTarget target(GameTestHelper helper, int amount) {
        BountyTarget t = BountyTarget.npc(UUID.randomUUID(), "nb_" + UUID.randomUUID().toString().substring(0, 8));
        var placed = LawService.placeBounty(helper.getLevel().getServer(), UUID.randomUUID(), "Elsewhere", t, amount);
        helper.assertTrue(placed.placed(), "bounty placed: " + placed.outcome());
        return t;
    }

    private static <T> List<T> of(List<CustomPacketPayload> sent, Class<T> type) {
        synchronized (sent) {
            return sent.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static NoticeBoardPayloads.State lastState(GameTestHelper helper, List<CustomPacketPayload> sent) {
        List<NoticeBoardPayloads.State> states = of(sent, NoticeBoardPayloads.State.class);
        helper.assertFalse(states.isEmpty(), "a board state was sent");
        return states.get(states.size() - 1);
    }

    private static List<NoticeBoardListing.Line> linesFor(NoticeBoardView view, UUID target) {
        return view.lines().stream().filter(l -> l.target().equals(target)).toList();
    }

    private static void finish(GameTestHelper helper, List<UUID> targets, ServerPlayer... players) {
        for (ServerPlayer p : players) NoticeBoardBackend.stopRecording(p.getUUID());
        BountyBoardData data = BountyBoardData.get(helper.getLevel().getServer());
        for (UUID t : targets) data.setBoard(data.board().clearTarget(t));
        helper.succeed();
    }

    // --- tests --------------------------------------------------------------------------------------------------

    @ModGameTest
    public static void openingWithoutDoubloonsListsTheBounties(GameTestHelper helper) {
        BountyTarget t = target(helper, 25);
        BlockPos abs = board(helper);
        ServerPlayer p = player(helper, "nb_reader", 0);
        List<CustomPacketPayload> sent = NoticeBoardBackend.record(p.getUUID());
        helper.assertTrue(use(helper, p).consumesAction(), "the board opens");
        List<NoticeBoardPayloads.Open> opens = of(sent, NoticeBoardPayloads.Open.class);
        helper.assertValueEqual(opens.size(), 1, "one open payload");
        helper.assertValueEqual(opens.get(0).board(), abs, "for this board");
        NoticeBoardView view = lastState(helper, sent).view().orElseThrow();
        List<NoticeBoardListing.Line> lines = linesFor(view, t.id());
        helper.assertValueEqual(lines.size(), 1, "the bounty is listed");
        helper.assertValueEqual(lines.get(0).amount(), 25, "with its amount");
        helper.assertValueEqual(lines.get(0).placedBy(), "Elsewhere", "and who placed it");
        helper.assertValueEqual(view.coins(), 0L, "no doubloons");
        helper.assertValueEqual(view.ownTotal(), 0L, "no bounty on the reader");
        helper.assertTrue(view.names().contains(t.name()), "the target's name is offered");
        helper.assertTrue(NoticeBoardBackend.isOpen(p.getUUID(), abs), "session open");
        finish(helper, List.of(t.id()), p);
    }

    @ModGameTest
    public static void placingThroughTheBoardPaysAndLists(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        BountyTarget t = target(helper, 10);
        BlockPos abs = board(helper);
        ServerPlayer p = player(helper, "nb_payer", 100);
        List<CustomPacketPayload> sent = NoticeBoardBackend.record(p.getUUID());
        use(helper, p);

        NoticeBoardBackend.handlePlace(p, new NoticeBoardPayloads.Place(abs, t.name().toUpperCase(java.util.Locale.ROOT), 30));
        NoticeBoardPayloads.State state = lastState(helper, sent);
        helper.assertTrue(state.result().orElseThrow().ok(), "placed: " + state.result());
        helper.assertValueEqual(Wallet.count(p), 70L, "30 doubloons paid");
        helper.assertValueEqual(LawService.bountyTotal(server, t.id()), 40L, "added to the target's bounties");
        List<NoticeBoardListing.Line> lines = linesFor(state.view().orElseThrow(), t.id());
        helper.assertValueEqual(lines.size(), 2, "both bounties listed");
        helper.assertValueEqual(lines.get(0).amount(), 30, "larger bounty first");
        helper.assertValueEqual(lines.get(0).placedBy(), "nb_payer", "placed by the reader");
        helper.assertValueEqual(state.view().orElseThrow().coins(), 70L, "the view shows the doubloons left");

        // Refusals keep the doubloons
        NoticeBoardBackend.handlePlace(p, new NoticeBoardPayloads.Place(abs, t.name(), 5));
        helper.assertFalse(lastState(helper, sent).result().orElseThrow().ok(), "below the minimum");
        NoticeBoardBackend.handlePlace(p, new NoticeBoardPayloads.Place(abs, t.name(), 500));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().key(),
                NoticeBoardBackend.MSG + "refused.not_enough", "not enough doubloons");
        NoticeBoardBackend.handlePlace(p, new NoticeBoardPayloads.Place(abs, "nobody_" + UUID.randomUUID(), 20));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().key(),
                NoticeBoardBackend.MSG + "refused.unknown_target", "unknown name");
        NoticeBoardBackend.handlePlace(p, new NoticeBoardPayloads.Place(abs.east(3), t.name(), 20));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().key(),
                NoticeBoardBackend.MSG + "refused.closed", "another board than the open one");
        helper.assertValueEqual(Wallet.count(p), 70L, "refusals cost nothing");
        helper.assertValueEqual(LawService.bountyTotal(server, t.id()), 40L, "and place nothing");
        finish(helper, List.of(t.id()), p);
    }

    @ModGameTest(timeoutTicks = 120)
    public static void theListRefreshesWhenTheBoardChanges(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        board(helper);
        ServerPlayer p = player(helper, "nb_watcher", 0);
        List<CustomPacketPayload> sent = NoticeBoardBackend.record(p.getUUID());
        use(helper, p);
        BountyTarget t = BountyTarget.npc(UUID.randomUUID(), "nb_" + UUID.randomUUID().toString().substring(0, 8));
        helper.assertTrue(linesFor(lastState(helper, sent).view().orElseThrow(), t.id()).isEmpty(), "not listed yet");
        LawService.placeBounty(server, UUID.randomUUID(), "Elsewhere", t, 15);
        helper.succeedWhen(() -> {
            NoticeBoardView view = lastState(helper, sent).view().orElseThrow();
            helper.assertValueEqual(linesFor(view, t.id()).size(), 1, "the new bounty is pushed to the open screen");
            NoticeBoardBackend.stopRecording(p.getUUID());
            BountyBoardData data = BountyBoardData.get(server);
            data.setBoard(data.board().clearTarget(t.id()));
        });
    }

    @ModGameTest(batch = "pirates_n_ships_config_law_notice_boards")
    public static void boardsCanBeSwitchedOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.NOTICE_BOARDS, false);
        BlockPos abs = board(helper);
        ServerPlayer p = player(helper, "nb_off", 50);
        List<CustomPacketPayload> sent = NoticeBoardBackend.record(p.getUUID());
        helper.assertValueEqual(use(helper, p), InteractionResult.PASS, "a switched-off board is decoration");
        helper.assertTrue(sent.isEmpty(), "nothing sent");
        helper.assertFalse(NoticeBoardBackend.isOpen(p.getUUID(), abs), "no session");
        finish(helper, List.of(), p);
    }
}

package com.richardsenger.piratesnships.law.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.client.ClientNoticeBoard;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the notice board (docs/design.md §13.2). A player who uses a board gets a session for it; once a
 * second every session's view is rebuilt and sent if anything changed (a bounty placed, claimed or withdrawn anywhere,
 * the viewer's doubloons), so the screen refreshes when the board changes. Sessions end when the screen is closed,
 * the player leaves the board's reach, the board is gone or the player logs out.
 *
 * <p>Placing a bounty goes through the same service call as {@code /pirates law bounty place}
 * ({@link LawService#placeBounty}), and the payer's doubloons are taken only when it was placed. The target is named:
 * an online player, or a target already on the board (player or NPC, also offline or unloaded).
 */
public final class NoticeBoardBackend {

    public static final String MSG = "message." + Constants.MOD_ID + ".notice_board.";
    private static final int REFRESH_INTERVAL = 20;

    private record Session(ServerPlayer player, BlockPos board, ResourceKey<Level> dimension, Optional<NoticeBoardView> lastSent) {
        Session withSent(NoticeBoardView view) {
            return new Session(player, board, dimension, Optional.of(view));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();

    private NoticeBoardBackend() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(NoticeBoardPayloads.Place.TYPE, NoticeBoardPayloads.Place.CODEC,
                (p, player) -> handlePlace((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(NoticeBoardPayloads.Close.TYPE, NoticeBoardPayloads.Close.CODEC,
                (p, player) -> close((ServerPlayer) player));
        Services.NETWORK.registerToClient(NoticeBoardPayloads.Open.TYPE, NoticeBoardPayloads.Open.CODEC,
                (p, player) -> ClientNoticeBoard.open(p));
        Services.NETWORK.registerToClient(NoticeBoardPayloads.State.TYPE, NoticeBoardPayloads.State.CODEC,
                (p, player) -> ClientNoticeBoard.accept(p));
    }

    // --- Sessions -----------------------------------------------------------------------------------------------

    /** A player used the board at {@code board}: open the screen and send what it shows. */
    public static void open(ServerPlayer player, BlockPos board) {
        SESSIONS.put(player.getUUID(), new Session(player, board.immutable(), player.level().dimension(), Optional.empty()));
        deliver(player, new NoticeBoardPayloads.Open(board.immutable(), LawConfig.NOTICE_BOARD_REACH.get()));
        sendState(player, Optional.empty());
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Whether {@code player} has the board at {@code board} open (tests, debug). */
    public static boolean isOpen(UUID player, BlockPos board) {
        Session s = SESSIONS.get(player);
        return s != null && s.board().equals(board);
    }

    public static void onServerStopped(MinecraftServer server) {
        SESSIONS.clear();
        RECORDINGS.clear();
    }

    /** Once a second: end invalid sessions, resend views that changed. */
    public static void onServerTick(MinecraftServer server) {
        if (SESSIONS.isEmpty() || server.getTickCount() % REFRESH_INTERVAL != 0) return;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s == null) continue;
            ServerPlayer player = s.player();
            if (player.isRemoved() || player.hasDisconnected()) {
                // logged out, or died (a respawn is a new player object)
                SESSIONS.remove(id);
                continue;
            }
            if (!valid(player, s, s.board())) {
                SESSIONS.remove(id);
                deliver(player, new NoticeBoardPayloads.State(Optional.empty(), Optional.empty()));
                continue;
            }
            NoticeBoardView view = view(player);
            if (s.lastSent().isEmpty() || !view.sameContent(s.lastSent().get())) {
                SESSIONS.put(id, s.withSent(view));
                deliver(player, new NoticeBoardPayloads.State(Optional.of(view), Optional.empty()));
            }
        }
    }

    // --- Requests -----------------------------------------------------------------------------------------------

    public static void handlePlace(ServerPlayer player, NoticeBoardPayloads.Place request) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !valid(player, s, request.board())) {
            SESSIONS.remove(player.getUUID());
            deliver(player, new NoticeBoardPayloads.State(Optional.empty(), Optional.of(fail("closed"))));
            return;
        }
        sendState(player, Optional.of(place(player, request.target(), request.amount())));
    }

    /** Places a bounty for {@code payer} on the target named {@code name}, paying {@code amount} doubloons. */
    public static NoticeBoardPayloads.Result place(ServerPlayer payer, String name, int amount) {
        MinecraftServer server = payer.server;
        if (!LawConfig.PLAYER_BOUNTIES.get()) return fail("disabled");
        int minimum = LawConfig.PLAYER_BOUNTY_MINIMUM.get();
        if (amount < minimum) return fail("below_minimum", Integer.toString(minimum));
        Optional<BountyTarget> target = resolve(server, name);
        if (target.isEmpty()) return fail("unknown_target", name.trim());
        if (target.get().id().equals(payer.getUUID())) return fail("self");
        if (!Wallet.has(payer, amount)) return fail("not_enough", Integer.toString(amount), Long.toString(Wallet.count(payer)));
        BountyBoard.PlaceResult result = LawService.placeBounty(server, payer.getUUID(), payer.getName().getString(), target.get(), amount);
        if (!result.placed()) return fail(result.outcome().name().toLowerCase(Locale.ROOT));
        Wallet.take(payer, amount);
        payer.level().playSound(null, payer.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        long total = LawService.bountyTotal(server, target.get().id());
        return new NoticeBoardPayloads.Result(true, MSG + "placed",
                List.of(Integer.toString(amount), target.get().name(), Long.toString(total)));
    }

    /** An online player by name (ignoring case), else a target already on the board. */
    public static Optional<BountyTarget> resolve(MinecraftServer server, String name) {
        String wanted = name.trim();
        if (wanted.isEmpty()) return Optional.empty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(wanted)) {
                return Optional.of(BountyTarget.player(p.getUUID(), p.getGameProfile().getName()));
            }
        }
        return NoticeBoardListing.knownTarget(LawService.board(server), wanted, LawService.now(server));
    }

    /** What the board shows to {@code viewer} now. */
    public static NoticeBoardView view(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        long now = LawService.now(server);
        BountyBoard board = LawService.board(server);
        List<String> online = server.getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()).toList();
        return new NoticeBoardView(NoticeBoardListing.lines(board, now), board.total(viewer.getUUID(), now),
                Wallet.count(viewer), LawConfig.PLAYER_BOUNTY_MINIMUM.get(), LawConfig.PLAYER_BOUNTIES.get(),
                NoticeBoardListing.names(online, board, now, viewer.getGameProfile().getName()), now);
    }

    private static boolean valid(ServerPlayer player, Session s, BlockPos board) {
        if (!LawConfig.NOTICE_BOARDS.get() || !s.board().equals(board)) return false;
        if (!player.isAlive() || player.level().dimension() != s.dimension()) return false;
        if (!player.level().getBlockState(board).is(LawContent.NOTICE_BOARD.get())) return false;
        double reach = LawConfig.NOTICE_BOARD_REACH.get();
        return player.position().distanceToSqr(Vec3.atCenterOf(board)) <= reach * reach;
    }

    private static void sendState(ServerPlayer player, Optional<NoticeBoardPayloads.Result> result) {
        NoticeBoardView view = view(player);
        SESSIONS.computeIfPresent(player.getUUID(), (k, s) -> s.withSent(view));
        deliver(player, new NoticeBoardPayloads.State(Optional.of(view), result));
    }

    private static NoticeBoardPayloads.Result fail(String what, String... args) {
        return new NoticeBoardPayloads.Result(false, MSG + "refused." + what, List.of(args));
    }

    private static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            // A GameTest player has a mock connection without negotiated channels: record instead of sending
            rec.add(payload);
            return;
        }
        Services.NETWORK.sendToPlayer(player, payload);
    }

    // --- Test support -------------------------------------------------------------------------------------------

    /** GameTests: every notice board payload for {@code player} goes into the returned list instead of the network. */
    public static List<CustomPacketPayload> record(UUID player) {
        List<CustomPacketPayload> list = Collections.synchronizedList(new ArrayList<>());
        RECORDINGS.put(player, list);
        return list;
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
        SESSIONS.remove(player);
    }
}

package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.hiring.Hiring;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.career.CareerShipTitles;
import com.richardsenger.piratesnships.sailing.helm.SessionRules;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the ship screen at the helm (HGUI1, docs/design.md §7.2). Sneak-use with an empty hand on the steering
 * helm of an assembled ship ({@link HelmBlock.ManageHandler}) opens it for the ship's owner, or for anyone when the
 * ship has no owner; anyone else gets an action-bar refusal. While {@code ship_screen.enabled} is off the helm
 * disassembles at once, as before.
 *
 * <p>A session per player remembers the ship and helm. Every action ({@link ShipScreenPayloads.Action}) is checked
 * against the session: the feature on, the ship still there, the helm still a helm, the player within
 * {@code ship_screen.reach} of it (measured in the ship's frame, like the wheel's session), the player still allowed to
 * manage the ship; then the action's own rules, the same as the whistle's (its order path {@link WhistleOrders#issue},
 * its dismissal {@link Hiring#dismiss}, its assignment {@link CrewStations#assign}). Each answer carries the new view
 * and a message. Every {@code refresh_ticks} an open screen gets a new view when something changed; a session that is
 * no longer valid ends and its screen closes.
 */
public final class ShipScreens {

    /** One open screen. {@code lastSent}: the view last sent, to refresh only on change. */
    record Session(ServerPlayer player, ServerLevel level, UUID ship, BlockPos helm, Optional<ShipScreenView> lastSent) {
        Session sent(ShipScreenView view) {
            return new Session(player, level, ship, helm, Optional.of(view));
        }
    }

    /** What happened on a sneak-use of the helm. */
    public enum OpenResult { OPENED, REFUSED, DISABLED }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();

    private ShipScreens() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(ShipScreenPayloads.Action.TYPE, ShipScreenPayloads.Action.CODEC,
                (p, player) -> handle((ServerPlayer) player, p));
        Services.NETWORK.registerToClient(ShipScreenPayloads.State.TYPE, ShipScreenPayloads.State.CODEC,
                (p, player) -> com.richardsenger.piratesnships.ship.screen.client.ClientShipScreenState.accept(p));
    }

    // ------------------------------------------------------------------ opening

    /**
     * {@link HelmBlock.ManageHandler}: takes the sneak-use while the screen is on. A player that is no server player
     * (no connection, so no screen to show) keeps the old sneak-use, which disassembles.
     */
    public static boolean onSneakUse(ServerLevel level, ShipBody ship, BlockPos helm, Player player) {
        if (!ShipScreenConfig.ENABLED.get() || !(player instanceof ServerPlayer sp)) return false;
        open(sp, level, ship, helm);
        return true;
    }

    /** Opens the screen of {@code ship} at the helm {@code helm} (plot) for {@code player}, or refuses a stranger. */
    public static OpenResult open(ServerPlayer player, ServerLevel level, ShipBody ship, BlockPos helm) {
        if (!ShipScreenConfig.ENABLED.get()) return OpenResult.DISABLED;
        if (!ShipScreenRules.mayManage(player.getUUID(), owner(level, ship))) {
            player.displayClientMessage(Component.translatable(ShipScreenText.NOT_YOURS), true);
            return OpenResult.REFUSED;
        }
        ShipScreenView view = ShipScreenViews.build(level, ship, helm, player);
        SESSIONS.put(player.getUUID(), new Session(player, level, ship.id(), helm.immutable(), Optional.of(view)));
        deliver(player, new ShipScreenPayloads.State(true, Optional.of(view), Optional.empty(), true));
        return OpenResult.OPENED;
    }

    /** Whether {@code player} has an open ship screen (tests). */
    public static boolean isOpen(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Ends {@code player}'s session without a message. */
    public static void close(UUID player) {
        SESSIONS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
        RECORDINGS.clear();
    }

    // ------------------------------------------------------------------ actions

    /** A message for the player, done or refused. */
    record Answer(Component message, boolean ok) {
        static Answer ok(Component m) {
            return new Answer(m, true);
        }

        static Answer no(Component m) {
            return new Answer(m, false);
        }

        static Answer no(String key, Object... args) {
            return new Answer(Component.translatable(key, args), false);
        }
    }

    /** Handles one action of {@code player}'s screen and answers with the new view (or closes it). */
    public static void handle(ServerPlayer player, ShipScreenPayloads.Action action) {
        if (action.kind() == ShipScreenPayloads.Kind.CLOSE) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            deliver(player, new ShipScreenPayloads.State(false, Optional.empty(),
                    Optional.of(Component.translatable(ShipScreenText.TOO_FAR)), false));
            return;
        }
        if (!s.ship().equals(action.ship())) {
            answer(s, Answer.no(ShipScreenText.STALE));
            return;
        }
        String invalid = invalid(s);
        if (invalid != null) {
            end(s, Component.translatable(invalid));
            return;
        }
        ShipBody ship = SableShips.byId(s.level(), s.ship());
        Answer a = act(s, ship, action);
        if (action.kind() == ShipScreenPayloads.Kind.DISASSEMBLE && a.ok()) {
            // the ship is blocks again: the screen closes with the helm's own message (also in chat, as before)
            player.displayClientMessage(a.message(), false);
            SESSIONS.remove(player.getUUID());
            deliver(player, new ShipScreenPayloads.State(false, Optional.empty(), Optional.of(a.message()), true));
            return;
        }
        answer(s, a);
    }

    private static Answer act(Session s, ShipBody ship, ShipScreenPayloads.Action action) {
        ServerPlayer player = s.player();
        ServerLevel level = s.level();
        Optional<UUID> owner = owner(level, ship);
        boolean manage = ShipScreenRules.mayManage(player.getUUID(), owner);
        switch (action.kind()) {
            case RENAME -> {
                if (!manage) return Answer.no(ShipScreenText.NOT_YOURS);
                Optional<String> typed = ShipScreenRules.cleanName(action.text());
                if (typed.isEmpty()) return Answer.no(ShipScreenText.BAD_NAME);
                // HON1: the same naming path as a name tag on the helm (title in front, then the record)
                String name = CareerShipTitles.forNaming(player, ship, typed.get());
                AssemblyResult r = ShipAssembler.name(ship, name);
                return r.success() ? Answer.ok(Component.translatable(r.outcome().key(), name)) : Answer.no(r.message());
            }
            case ORDER -> {
                if (!manage) return Answer.no(ShipScreenText.NOT_YOURS);
                Optional<WhistleOrder> order = WhistleOrder.byId(action.text());
                if (order.isEmpty()) return Answer.no(ShipScreenText.UNKNOWN_ORDER);
                WhistleOrders.Issued issued = WhistleOrders.issue(level, ship, order.get(), WhistleOrders.heldWhistle(player));
                return new Answer(issued.message(), issued.result().outcome() == WhistleOrders.Outcome.ISSUED);
            }
            case RELEASE -> {
                CrewMember c = crew(level, ship, action.crew());
                if (c == null) return Answer.no(ShipScreenText.NO_CREW);
                if (!ShipScreenRules.mayCommand(player.getUUID(), c.hiredBy(), owner)) {
                    return Answer.no(ShipScreenText.NOT_YOUR_HAND, c.getDisplayName());
                }
                if (!StationConfig.ENABLED.get()) return Answer.no(CrewStations.KEY_DISABLED);
                if (c.assignment() == null) return Answer.no(ShipScreenText.NOT_AT_STATION, c.getDisplayName());
                CrewStations.release(level, c);
                return Answer.ok(Component.translatable(CrewStations.KEY_RELEASED, c.getDisplayName()));
            }
            case DISMISS -> {
                CrewMember c = crew(level, ship, action.crew());
                if (c == null) return Answer.no(ShipScreenText.NO_CREW);
                Hiring.Result r = Hiring.dismiss(player, c); // the whistle's sneak-use: hirer, owner or ownerless
                return new Answer(r.message(), r.done());
            }
            case ASSIGN -> {
                CrewMember c = crew(level, ship, action.crew());
                if (c == null) return Answer.no(ShipScreenText.NO_CREW);
                if (!ShipScreenRules.mayCommand(player.getUUID(), c.hiredBy(), owner)) {
                    return Answer.no(ShipScreenText.NOT_YOUR_HAND, c.getDisplayName());
                }
                BlockPos pos = action.pos().orElse(null);
                StationRef ref = pos == null || !ship.plotContains(pos) ? null : Stations.at(level, pos);
                if (ref == null || !ref.ship().equals(ship.id())) return Answer.no(ShipScreenText.NOT_ON_THIS_SHIP);
                CrewStations.AssignResult r = CrewStations.assign(level, c, ref.pos());
                return new Answer(CaptainsWhistleItem.assignMessage(r, c), r == CrewStations.AssignResult.ASSIGNED);
            }
            case DISASSEMBLE -> {
                if (!manage) return Answer.no(ShipScreenText.NOT_YOURS);
                AssemblyResult r = ShipAssembler.disassemble(ship, s.helm(), player);
                return new Answer(r.message(), r.success());
            }
            default -> {
                return Answer.no(ShipScreenText.UNKNOWN_ORDER);
            }
        }
    }

    /** The crew member {@code id} if it belongs to {@code ship} now (aboard, at its stations or hammocks). */
    private static @Nullable CrewMember crew(ServerLevel level, ShipBody ship, Optional<UUID> id) {
        if (id.isEmpty()) return null;
        for (CrewMember c : ShipBunks.crewOf(level, ship)) {
            if (c.getUUID().equals(id.get())) return c;
        }
        return null;
    }

    private static void answer(Session s, Answer a) {
        ShipBody ship = SableShips.byId(s.level(), s.ship());
        if (ship == null || ship.isRemoved()) {
            end(s, Component.translatable(ShipScreenText.GONE));
            return;
        }
        ShipScreenView view = ShipScreenViews.build(s.level(), ship, s.helm(), s.player());
        SESSIONS.computeIfPresent(s.player().getUUID(), (k, cur) -> cur.ship().equals(s.ship()) ? cur.sent(view) : cur);
        deliver(s.player(), new ShipScreenPayloads.State(false, Optional.of(view), Optional.of(a.message()), a.ok()));
    }

    // ------------------------------------------------------------------ validity, refresh

    /** Why the session is no longer valid (a message key), or null when it is. */
    static @Nullable String invalid(Session s) {
        if (!ShipScreenConfig.ENABLED.get()) return ShipScreenText.DISABLED;
        ServerPlayer p = s.player();
        if (p.isRemoved() || p.hasDisconnected() || !p.isAlive() || p.level() != s.level()) return ShipScreenText.TOO_FAR;
        ShipBody ship = SableShips.byId(s.level(), s.ship());
        if (ship == null || ship.isRemoved() || !(s.level().getBlockState(s.helm()).getBlock() instanceof HelmBlock)) {
            return ShipScreenText.GONE;
        }
        Vec3 at = ship.toPlot(p.position());
        double d = SessionRules.distanceToBlock(at.x, at.y, at.z, s.helm().getX(), s.helm().getY(), s.helm().getZ());
        if (!ShipScreenRules.inReach(d, ShipScreenConfig.REACH.get())) return ShipScreenText.TOO_FAR;
        if (!ShipScreenRules.mayManage(p.getUUID(), owner(s.level(), ship))) return ShipScreenText.NOT_YOURS;
        return null;
    }

    private static void end(Session s, Component why) {
        SESSIONS.remove(s.player().getUUID(), s);
        deliver(s.player(), new ShipScreenPayloads.State(false, Optional.empty(), Optional.of(why), false));
    }

    /**
     * Every server tick: ends sessions that are no longer valid (their screens close), and every
     * {@code ship_screen.refresh_ticks} sends a new view to each open screen whose view changed.
     */
    public static void onServerTick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        boolean refresh = server.getTickCount() % ShipScreenConfig.REFRESH_TICKS.get() == 0;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (s.player().hasDisconnected() || s.player().isRemoved()) {
                SESSIONS.remove(s.player().getUUID(), s);
                continue;
            }
            String invalid = invalid(s);
            if (invalid != null) {
                end(s, Component.translatable(invalid));
                continue;
            }
            if (refresh) refresh(s);
        }
    }

    /** Sends {@code s} a new view if it changed since the last one. */
    static void refresh(Session s) {
        ShipBody ship = SableShips.byId(s.level(), s.ship());
        if (ship == null) return;
        ShipScreenView view = ShipScreenViews.build(s.level(), ship, s.helm(), s.player());
        if (s.lastSent().isPresent() && s.lastSent().get().equals(view)) return;
        SESSIONS.computeIfPresent(s.player().getUUID(), (k, cur) -> cur.ship().equals(s.ship()) ? cur.sent(view) : cur);
        deliver(s.player(), new ShipScreenPayloads.State(false, Optional.of(view), Optional.empty(), true));
    }

    /** Runs the session checks and the refresh for {@code player} now (GameTests, which cannot wait for the tick). */
    public static void tick(ServerPlayer player, boolean forceRefresh) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        String invalid = invalid(s);
        if (invalid != null) {
            end(s, Component.translatable(invalid));
        } else if (forceRefresh) {
            refresh(s);
        }
    }

    public static void onLogout(Player player) {
        SESSIONS.remove(player.getUUID());
    }

    private static Optional<UUID> owner(ServerLevel level, ShipBody ship) {
        return ShipRegistry.get(level.getServer()).find(ship.id()).flatMap(ShipData::owner);
    }

    // ------------------------------------------------------------------ sending, test support

    /** Sends a payload to the player, or records it for a GameTest ({@link #record}). */
    static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            rec.add(payload); // a mock player's connection has no negotiated channels
            return;
        }
        Services.NETWORK.sendToPlayer(player, payload);
    }

    /** GameTests: every ship screen payload for {@code player} goes into the returned list from now on. */
    public static List<CustomPacketPayload> record(UUID player) {
        return RECORDINGS.computeIfAbsent(player, id -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
    }
}

package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ship screen at the helm (HGUI1) on the server: sneak-use by the owner opens it with every hand aboard, release,
 * dismissal, assignment and renaming act, an order posts jobs like the whistle's, a stranger is refused, walking away
 * closes it, the Disassemble button disassembles, and {@code ship_screen.enabled} off brings back the old sneak-use.
 * The ship is the 5×4×5 hull of {@link StationGameTests} (winch, sail, helm) resting on land, deck top at relative
 * y = 9; the player is a mock server player not added to the level (the market tests' pattern) whose payloads are
 * recorded.
 */
public final class ShipScreenGameTests {

    private static final String BATCH = "pirates_n_ships_ship_screen";
    /** Ticks for Sable to fill the new ship's world bounds. */
    private static final int SETTLE = 3;

    private ShipScreenGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipScreenGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A ship, its owner (or none), two crew members on deck: the first at the winch, the second free. */
    private record Scene(StationGameTests.Fixture f, ServerPlayer player, CrewMember atWinch, CrewMember free) {
        ShipBody ship() {
            return f.ship();
        }
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.getInventory().clearContent();
        ShipScreens.record(p.getUUID());
        return p;
    }

    /** Puts {@code p} on the deck beside the helm (one block to the side, in the ship's frame). */
    private static void atHelm(StationGameTests.Fixture f, ServerPlayer p) {
        Vec3 w = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm().east()));
        p.setPos(w.x, w.y, w.z);
    }

    /** The test ship owned by {@code owner} (empty: no owner); the player stands at the helm. */
    private static StationGameTests.Fixture ship(GameTestHelper h, Optional<UUID> owner) {
        StationGameTests.Fixture f = StationGameTests.ship(h, false, x -> { });
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.put(registry.find(f.ship().id()).orElseThrow().withOwner(owner));
        return f;
    }

    /** Two crew members on the deck; the first is sent to the winch. Call after {@link #SETTLE}. */
    private static Scene crew(GameTestHelper h, StationGameTests.Fixture f, ServerPlayer p) {
        CrewMember a = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(20, 9, 20));
        CrewMember b = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(20, 9, 18));
        a.setCustomName(Component.literal("Anne"));
        b.setCustomName(Component.literal("Bart"));
        h.assertTrue(CrewStations.assign(h.getLevel(), a, f.winch()) == CrewStations.AssignResult.ASSIGNED, "Anne mans the winch");
        return new Scene(f, p, a, b);
    }

    private static void sneakUseHelm(GameTestHelper h, StationGameTests.Fixture f, ServerPlayer p) {
        ServerLevel level = h.getLevel();
        p.setShiftKeyDown(true);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(f.helm()), Direction.UP, f.helm(), false);
        level.getBlockState(f.helm()).useWithoutItem(level, p, hit);
        p.setShiftKeyDown(false);
    }

    private static List<ShipScreenPayloads.State> sent(ServerPlayer p) {
        List<CustomPacketPayload> rec = ShipScreens.record(p.getUUID());
        synchronized (rec) {
            return rec.stream().filter(ShipScreenPayloads.State.class::isInstance).map(ShipScreenPayloads.State.class::cast).toList();
        }
    }

    private static ShipScreenPayloads.State last(GameTestHelper h, ServerPlayer p) {
        List<ShipScreenPayloads.State> all = sent(p);
        h.assertFalse(all.isEmpty(), "a ship screen state was sent");
        return all.get(all.size() - 1);
    }

    private static ShipScreenView lastView(GameTestHelper h, ServerPlayer p) {
        return last(h, p).view().orElseThrow(() -> new AssertionError("the screen was closed: " + last(h, p).message()));
    }

    private static String key(Optional<Component> message) {
        return message.map(m -> m.getContents() instanceof TranslatableContents t ? t.getKey() : m.getString()).orElse("");
    }

    private static Optional<ShipScreenView.CrewLine> line(ShipScreenView v, CrewMember c) {
        return v.crew().stream().filter(l -> l.id().equals(c.getUUID())).findFirst();
    }

    private static void done(GameTestHelper h, ServerPlayer p, CrewMember... crew) {
        ShipScreens.close(p.getUUID());
        ShipScreens.stopRecording(p.getUUID());
        for (CrewMember c : crew) {
            if (!c.isRemoved()) c.discard();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ tests

    /**
     * The owner's sneak-use on the helm opens the screen: an open state whose view names the ship, the owner, both hands
     * (Anne at the winch, Bart free, both hired by nobody and commandable by the owner), the winch manned by Anne and
     * the helm as a free station; the ship is not disassembled.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void sneakUseOpensTheScreenWithEveryHand(GameTestHelper h) {
        ServerPlayer p = player(h, "screen_captain");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE, () -> {
            Scene s = crew(h, f, p);
            sneakUseHelm(h, f, p);
            h.assertTrue(SableShips.byId(h.getLevel(), f.ship().id()) != null, "the sneak-use disassembled the ship");
            h.assertTrue(ShipScreens.isOpen(p.getUUID()), "no session");
            ShipScreenPayloads.State st = last(h, p);
            h.assertTrue(st.open(), "the first state opens the screen");
            ShipScreenView v = st.view().orElseThrow();
            h.assertValueEqual(v.ship(), f.ship().id(), "ship id");
            h.assertValueEqual(v.helm(), f.helm(), "helm");
            h.assertValueEqual(v.header().owner(), Optional.of("screen_captain"), "owner's name");
            h.assertTrue(v.toggles().mayManage(), "the owner may manage");
            h.assertValueEqual(v.crew().size(), 2, "both hands aboard: " + v.crew());
            ShipScreenView.CrewLine anne = line(v, s.atWinch()).orElseThrow(() -> new AssertionError("Anne missing"));
            ShipScreenView.CrewLine bart = line(v, s.free()).orElseThrow(() -> new AssertionError("Bart missing"));
            h.assertValueEqual(anne.name(), "Anne", "name");
            h.assertValueEqual(anne.state(), ShipScreenRules.CrewState.STATION, "Anne's state");
            h.assertValueEqual(anne.station(), Optional.of(f.winch()), "Anne's station");
            h.assertFalse(anne.stationKey().isEmpty(), "the station's name key");
            h.assertValueEqual(bart.state(), ShipScreenRules.CrewState.FREE, "Bart's state");
            h.assertTrue(anne.mayCommand() && bart.mayCommand(), "the owner commands both");
            h.assertValueEqual(v.upkeep().crew(), 2, "crew count");
            ShipScreenView.StationLine winch = v.stations().stream().filter(l -> l.pos().equals(f.winch())).findFirst()
                    .orElseThrow(() -> new AssertionError("the winch is not listed: " + v.stations()));
            h.assertValueEqual(winch.occupant(), Optional.of(s.atWinch().getUUID()), "the winch's occupant");
            h.assertValueEqual(winch.occupantName(), "Anne", "the occupant's name");
            h.assertTrue(v.stations().stream().anyMatch(l -> l.pos().equals(f.helm()) && l.occupant().isEmpty()), "the helm is a free station");
            h.assertValueEqual(v.status().sails(), 1, "one sail");
            h.assertValueEqual(ShipScreenRules.freeCrew(v).size(), 1, "one free hand");
            done(h, p, s.atWinch(), s.free());
        });
    }

    /**
     * The buttons act through the whistle's rules: Release frees Anne, Send puts Bart at the winch, Dismiss turns Anne
     * into a sailor, Rename names the ship; each answer carries the new view.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void releaseAssignDismissAndRenameAct(GameTestHelper h) {
        ServerPlayer p = player(h, "screen_actor");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE, () -> {
            Scene s = crew(h, f, p);
            sneakUseHelm(h, f, p);
            UUID ship = f.ship().id();
            CrewMember anne = s.atWinch();
            CrewMember bart = s.free();

            ShipScreens.handle(p, ShipScreenPayloads.Action.crew(ship, ShipScreenPayloads.Kind.RELEASE, anne.getUUID()));
            h.assertTrue(last(h, p).ok(), "release refused: " + last(h, p).message());
            h.assertTrue(anne.assignment() == null && !anne.isPassenger(), "Anne still at the winch");
            h.assertValueEqual(key(last(h, p).message()), CrewStations.KEY_RELEASED, "release message");
            h.assertValueEqual(line(lastView(h, p), anne).orElseThrow().state(), ShipScreenRules.CrewState.FREE, "Anne free in the view");

            ShipScreens.handle(p, ShipScreenPayloads.Action.crew(ship, ShipScreenPayloads.Kind.RELEASE, bart.getUUID()));
            h.assertFalse(last(h, p).ok(), "releasing a free hand is refused");
            h.assertValueEqual(key(last(h, p).message()), ShipScreenText.NOT_AT_STATION, "refusal");

            ShipScreens.handle(p, ShipScreenPayloads.Action.assign(ship, bart.getUUID(), f.winch()));
            h.assertTrue(last(h, p).ok(), "assign refused: " + last(h, p).message());
            h.assertTrue(bart.assignment() != null && bart.assignment().pos().equals(f.winch()), "Bart not at the winch");
            h.assertTrue(bart.isPinned(), "an assignment by hand pins, like the whistle's");

            ShipScreens.handle(p, ShipScreenPayloads.Action.assign(ship, anne.getUUID(), f.winch().above(5)));
            h.assertFalse(last(h, p).ok(), "a station off the ship is refused");

            ShipScreens.handle(p, ShipScreenPayloads.Action.crew(ship, ShipScreenPayloads.Kind.DISMISS, anne.getUUID()));
            h.assertTrue(last(h, p).ok(), "dismiss refused: " + last(h, p).message());
            h.assertTrue(anne.isRemoved(), "Anne is still a crew member");
            h.assertValueEqual(lastView(h, p).crew().size(), 1, "one hand left");

            ShipScreens.handle(p, ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.RENAME, "  Sea Wolf "));
            h.assertTrue(last(h, p).ok(), "rename refused: " + last(h, p).message());
            h.assertValueEqual(ShipRegistry.get(h.getLevel().getServer()).find(ship).map(ShipData::name), Optional.of("Sea Wolf"), "name");
            h.assertValueEqual(lastView(h, p).header().name(), "Sea Wolf", "name in the view");
            ShipScreens.handle(p, ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.RENAME, "   "));
            h.assertValueEqual(key(last(h, p).message()), ShipScreenText.BAD_NAME, "an empty name is refused");

            ShipScreens.handle(p, ShipScreenPayloads.Action.text(UUID.randomUUID(), ShipScreenPayloads.Kind.RENAME, "Other"));
            h.assertValueEqual(key(last(h, p).message()), ShipScreenText.STALE, "an action for another ship is refused");
            done(h, p, anne, bart);
        });
    }

    /**
     * "Hoist" from the screen goes the whistle's way ({@code WhistleOrders.issue}): nobody mans the winch, so the winch
     * becomes an open hoist job on the ship's board for the free hands; "Release crew" clears the board.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void anOrderPostsJobsLikeTheWhistle(GameTestHelper h) {
        ServerPlayer p = player(h, "screen_orders");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE, () -> {
            Scene s = crew(h, f, p);
            CrewStations.release(h.getLevel(), s.atWinch()); // both hands free
            sneakUseHelm(h, f, p);
            UUID ship = f.ship().id();
            ShipScreens.handle(p, ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.ORDER, WhistleOrder.HOIST.id()));
            h.assertTrue(last(h, p).ok(), "order refused: " + last(h, p).message());
            h.assertValueEqual(key(last(h, p).message()), JobBoard.KEY_ORDER_POSTED, "the whistle's 'order posted' line");
            JobBoard.Job job = JobBoard.jobs(ship).get(f.winch());
            h.assertTrue(job != null && job.order() == SailOrder.HOIST, "no hoist job at the winch: " + JobBoard.jobs(ship));
            h.assertTrue(lastView(h, p).stations().stream().anyMatch(l -> l.pos().equals(f.winch()) && l.jobKey().equals(SailOrder.HOIST.nameKey())),
                    "the view shows the open job");

            ShipScreens.handle(p, ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.ORDER, "no_such_order"));
            h.assertValueEqual(key(last(h, p).message()), ShipScreenText.UNKNOWN_ORDER, "unknown order");

            ShipScreens.handle(p, ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.ORDER, WhistleOrder.RELEASE.id()));
            h.assertTrue(JobBoard.jobs(ship).isEmpty(), "release crew clears the board");
            done(h, p, s.atWinch(), s.free());
        });
    }

    /**
     * A stranger's sneak-use opens nothing and leaves the ship assembled; their actions without a session are refused
     * and touch no crew. On an ownerless ship anyone may open it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void aStrangerIsRefused(GameTestHelper h) {
        ServerPlayer owner = player(h, "screen_owner");
        ServerPlayer stranger = player(h, "screen_stranger");
        StationGameTests.Fixture f = ship(h, Optional.of(owner.getUUID()));
        atHelm(f, stranger);
        h.runAfterDelay(SETTLE, () -> {
            Scene s = crew(h, f, owner);
            sneakUseHelm(h, f, stranger);
            h.assertFalse(ShipScreens.isOpen(stranger.getUUID()), "the stranger has a session");
            h.assertTrue(sent(stranger).isEmpty(), "the stranger got a screen");
            h.assertTrue(SableShips.byId(h.getLevel(), f.ship().id()) != null, "the stranger's sneak-use disassembled the ship");
            h.assertValueEqual(ShipScreens.open(stranger, h.getLevel(), f.ship(), f.helm()), ShipScreens.OpenResult.REFUSED, "open");

            ShipScreens.handle(stranger, ShipScreenPayloads.Action.crew(f.ship().id(), ShipScreenPayloads.Kind.DISMISS, s.atWinch().getUUID()));
            ShipScreenPayloads.State st = last(h, stranger);
            h.assertTrue(st.view().isEmpty() && !st.ok(), "a request without a session is refused and closes");
            h.assertTrue(!s.atWinch().isRemoved() && s.atWinch().assignment() != null, "the stranger's request acted");

            // the owner gives the ship away: now anyone may open it
            ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
            registry.put(registry.find(f.ship().id()).orElseThrow().withOwner(Optional.empty()));
            h.assertValueEqual(ShipScreens.open(stranger, h.getLevel(), f.ship(), f.helm()), ShipScreens.OpenResult.OPENED, "ownerless");
            h.assertTrue(lastView(h, stranger).header().ownerless(), "the view says ownerless");
            ShipScreens.close(owner.getUUID());
            ShipScreens.stopRecording(owner.getUUID());
            done(h, stranger, s.atWinch(), s.free());
        });
    }

    /** Walking more than {@code ship_screen.reach} from the helm ends the session: a closing state "you stepped away". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH)
    public static void walkingAwayClosesTheScreen(GameTestHelper h) {
        ServerPlayer p = player(h, "screen_walker");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE, () -> {
            sneakUseHelm(h, f, p);
            ShipScreens.tick(p, false);
            h.assertTrue(ShipScreens.isOpen(p.getUUID()), "closed while at the helm");
            Vec3 far = f.ship().toWorld(Vec3.atCenterOf(f.helm())).add(ShipScreenConfig.REACH.get() + 4, 0, 0);
            p.setPos(far.x, far.y, far.z);
            ShipScreens.tick(p, false);
            h.assertFalse(ShipScreens.isOpen(p.getUUID()), "still open far from the helm");
            ShipScreenPayloads.State st = last(h, p);
            h.assertTrue(st.view().isEmpty(), "no closing state");
            h.assertValueEqual(key(st.message()), ShipScreenText.TOO_FAR, "why");
            done(h, p);
        });
    }

    /** The Ship tab's Disassemble button disassembles at the helm, as sneak-use did before, and closes the screen. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "_disassemble")
    public static void theDisassembleButtonDisassembles(GameTestHelper h) {
        ServerPlayer p = player(h, "screen_breaker");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE + 10, () -> {
            sneakUseHelm(h, f, p);
            UUID ship = f.ship().id();
            ShipScreens.handle(p, ShipScreenPayloads.Action.of(ship, ShipScreenPayloads.Kind.DISASSEMBLE));
            ShipScreenPayloads.State st = last(h, p);
            h.assertTrue(st.ok() && st.view().isEmpty(), "disassembly refused or the screen stayed: " + st.message());
            h.assertTrue(ShipRegistry.get(h.getLevel().getServer()).find(ship).isEmpty() || SableShips.byId(h.getLevel(), ship) == null,
                    "the ship is still assembled");
            h.assertFalse(ShipScreens.isOpen(p.getUUID()), "session left over");
            done(h, p);
        });
    }

    /** {@code ship_screen.enabled} off: sneak-use disassembles at once, as before HGUI1, and opens no screen. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_ship_screen_enabled")
    public static void switchedOffSneakUseDisassemblesAsBefore(GameTestHelper h) {
        ConfigOverrides.during(h, ShipScreenConfig.ENABLED, false);
        ServerPlayer p = player(h, "screen_off");
        StationGameTests.Fixture f = ship(h, Optional.of(p.getUUID()));
        atHelm(f, p);
        h.runAfterDelay(SETTLE + 10, () -> {
            UUID ship = f.ship().id();
            sneakUseHelm(h, f, p);
            h.assertTrue(sent(p).isEmpty(), "a screen opened while switched off");
            h.assertFalse(ShipScreens.isOpen(p.getUUID()), "a session while switched off");
            h.assertTrue(SableShips.byId(h.getLevel(), ship) == null, "sneak-use did not disassemble the ship");
            done(h, p);
        });
    }
}

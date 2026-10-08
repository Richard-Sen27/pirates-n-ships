package com.richardsenger.piratesnships.trade.fees;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Harbor dues in game (PRT1b): a navy port registered around the GameTest's basin, the small sailing test hull owned by
 * an offline test player, and {@link DockingFees#check} called with a lookup that knows only that player (the level
 * tick's own check never finds an offline player, so it cannot interfere).
 */
public final class FeesGameTests {

    static final String DISABLED_BATCH = "pirates_n_ships_config_trade_fees_disabled";

    /** The hull spans x 17..21, z 3..7 (test-relative); the capstan stands forward of the mast. */
    private static final int X0 = 17, Z0 = 3;
    private static final BlockPos CAPSTAN = new BlockPos(19, 9, 6);
    /** A berth at sea level right beside the hull's west side. */
    private static final BlockPos BERTH_NEAR = new BlockPos(15, 7, 5);
    /** A berth across the basin, far beyond {@code berth_radius}. */
    private static final BlockPos BERTH_FAR = new BlockPos(3, 7, 35);
    /** The desk on the basin's corner wall. */
    private static final BlockPos DESK = new BlockPos(0, 9, 0);
    /** Ticks the assembled hull settles before it counts as lying at a berth. */
    private static final int SETTLE = 40;

    private FeesGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FeesGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static Port navyPort(GameTestHelper h, BlockPos... berths) {
        BoundingBox box = BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(39, 30, 39)));
        List<Berth> list = java.util.Arrays.stream(berths).map(b -> new Berth(h.absolutePos(b), Direction.NORTH)).toList();
        Port port = new Port(Constants.id("gametest/fees_navy_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.NAVY_OUTPOST,
                h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 8, 20)), box, Climate.TEMPERATE, list);
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static ServerPlayer player(GameTestHelper h, long coins) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "fees_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(DESK.south())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        return p;
    }

    /** The small test hull in a water basin, assembled and owned by {@code owner}; {@code extra} builds into the hull first. */
    private static Fixture hull(GameTestHelper h, ServerPlayer owner, boolean capstan, java.util.function.Consumer<GameTestHelper> extra) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, X0, Z0, SailTrim.FURLED);
        if (capstan) h.setBlock(CAPSTAN, SailingBlocks.CAPSTAN.get());
        extra.accept(h);
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        ShipRegistry ships = ShipRegistry.get(h.getLevel().getServer());
        ships.put(ships.find(f.ship().id()).orElseThrow().withOwner(Optional.of(owner.getUUID())));
        return f;
    }

    private static Function<UUID, ServerPlayer> only(ServerPlayer p) {
        return id -> id.equals(p.getUUID()) ? p : null;
    }

    /** The charges of one check for {@code ship} only (other tests' ships are never charged: their owners are unknown). */
    private static List<DockingFees.Charge> check(GameTestHelper h, ServerPlayer owner, ShipBody ship) {
        return DockingFees.check(h.getLevel(), only(owner)).stream().filter(c -> c.ship().equals(ship.id())).toList();
    }

    private static BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) return p;
        }
        throw new AssertionError("no " + block + " on the ship");
    }

    private static void finish(GameTestHelper h, Port port, ShipBody ship, ServerPlayer... players) {
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        PortVisitData.get(h.getLevel().getServer()).forget(ship.id());
        for (ServerPlayer p : players) {
            MarketBackend.close(p);
            MarketBackend.stopRecording(p.getUUID());
        }
        h.succeed();
    }

    /**
     * Polls one check a tick from {@link #SETTLE} on (the hull bobs after assembly) until it charges the ship, then
     * hands the charges to {@code then}.
     */
    private static void onFirstCharge(GameTestHelper h, ServerPlayer owner, Fixture f, java.util.function.Consumer<List<DockingFees.Charge>> then) {
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < SETTLE || f.ship().isRemoved()) return;
            List<DockingFees.Charge> c = check(h, owner, f.ship());
            if (!c.isEmpty()) {
                done[0] = true;
                then.accept(c);
            }
        });
    }

    // ------------------------------------------------------------------ tests

    /**
     * An owned hull anchors inside a navy outpost's box (the capstan through {@link ShipControls#useCapstan}): one check
     * takes the fee from the owner's wallet, the next takes nothing; before it anchored (no berth near) nothing was taken.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 900)
    public static void anchoredShipPaysOncePerPeriod(GameTestHelper h) {
        ServerPlayer owner = player(h, 20);
        Fixture f = hull(h, owner, true, x -> { });
        Port port = navyPort(h);
        int fee = TradeConfig.NAVY_DOCKING_FEE.get();
        boolean[] dropped = {false};
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || f.ship().isRemoved()) return;
            if (!f.runtime().isAnchored()) {
                h.assertTrue(check(h, owner, f.ship()).isEmpty(), "charged before the anchor held");
                if (h.getTick() >= 3 && !dropped[0]) {
                    ShipControls.useCapstan(h.getLevel(), find(f.ship(), SailingBlocks.CAPSTAN.get()));
                    dropped[0] = true;
                }
                return;
            }
            done[0] = true;
            List<DockingFees.Charge> first = check(h, owner, f.ship());
            h.assertValueEqual(first.size(), 1, "charges at the first check");
            h.assertValueEqual(first.get(0).payer(), DockingRules.Payer.WALLET, "payer");
            h.assertValueEqual(first.get(0).fee(), fee, "fee");
            h.assertValueEqual(Wallet.count(owner), 20L - fee, "coins after the fee");
            h.assertTrue(check(h, owner, f.ship()).isEmpty(), "charged twice in one period");
            h.assertValueEqual(Wallet.count(owner), 20L - fee, "coins after the second check");
            PortVisitData.Visit v = PortVisitData.get(h.getLevel().getServer()).get(f.ship().id(), port.id()).orElseThrow();
            h.assertValueEqual(v.paid(), fee, "visit paid");
            h.assertValueEqual(v.owed(), 0, "visit owed");
            finish(h, port, f.ship(), owner);
        });
    }

    /** A captain the navy trusts (reputation above the waiver) is told the dues are waived and pays nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400)
    public static void trustedCaptainAtABerthIsWaived(GameTestHelper h) {
        ServerPlayer owner = player(h, 20);
        Reputation.set(owner, Faction.NAVY, TradeConfig.FEE_WAIVER_STANDING.get() + 10, "gametest");
        Fixture f = hull(h, owner, false, x -> { });
        Port port = navyPort(h, BERTH_NEAR);
        onFirstCharge(h, owner, f, charges -> {
            h.assertValueEqual(charges.get(0).payer(), DockingRules.Payer.WAIVED, "payer");
            h.assertValueEqual(charges.get(0).fee(), 0, "fee");
            h.assertValueEqual(Wallet.count(owner), 20L, "coins");
            h.assertTrue(check(h, owner, f.ship()).isEmpty(), "waived twice in one period");
            finish(h, port, f.ship(), owner);
        });
    }

    /** An empty wallet: the coins in a chest aboard pay. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400)
    public static void shipChestPaysWhenTheWalletIsEmpty(GameTestHelper h) {
        ServerPlayer owner = player(h, 0);
        BlockPos chest = new BlockPos(X0 + 1, 6, Z0 + 3);
        Fixture f = hull(h, owner, false, x -> {
            x.setBlock(chest, Blocks.CHEST);
            ((Container) x.getBlockEntity(chest)).setItem(0, new ItemStack(TradeContent.DOUBLOON.get(), 12));
        });
        Port port = navyPort(h, BERTH_NEAR);
        int fee = TradeConfig.NAVY_DOCKING_FEE.get();
        long before = com.richardsenger.piratesnships.crew.upkeep.ShipCoins.total(h.getLevel(), f.ship());
        h.assertValueEqual(before, 12L, "coins aboard after assembly");
        onFirstCharge(h, owner, f, charges -> {
            h.assertValueEqual(charges.get(0).payer(), DockingRules.Payer.SHIP, "payer");
            h.assertValueEqual(com.richardsenger.piratesnships.crew.upkeep.ShipCoins.total(h.getLevel(), f.ship()), 12L - fee, "coins aboard");
            h.assertValueEqual(PortVisitData.get(h.getLevel().getServer()).owed(owner.getUUID(), port.id()), 0, "owed");
            finish(h, port, f.ship(), owner);
        });
    }

    /**
     * No coins anywhere: the dues are owed, the port's desk refuses the owner (also with coins in the wallet but not in
     * hand), and using it with doubloons in hand pays, clears the debt and opens the market.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400)
    public static void owedDuesBlockTheDeskUntilPaid(GameTestHelper h) {
        ServerPlayer owner = player(h, 0);
        Fixture f = hull(h, owner, false, x -> { });
        Port port = navyPort(h, BERTH_NEAR);
        h.setBlock(DESK, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(DESK)).setPort(Optional.of(port.id()));
        MarketBackend.record(owner.getUUID());
        int fee = TradeConfig.NAVY_DOCKING_FEE.get();
        onFirstCharge(h, owner, f, charges -> {
            h.assertValueEqual(charges.get(0).payer(), DockingRules.Payer.OWED, "payer");
            PortVisitData visits = PortVisitData.get(h.getLevel().getServer());
            h.assertValueEqual(visits.owed(owner.getUUID(), port.id()), fee, "owed");
            BlockPos desk = h.absolutePos(DESK);
            h.assertValueEqual(HarborDeskService.use(owner, desk), HarborDeskService.Use.DUES_OWED, "desk while owed");
            h.assertFalse(MarketBackend.isOpen(owner.getUUID(), port.id()), "market opened while owed");
            owner.getInventory().selected = 8;
            Wallet.give(owner, fee + 10); // into the first free slot (0), not the hand
            h.assertFalse(Wallet.isCoin(owner.getMainHandItem()), "coins in hand too early");
            h.assertValueEqual(HarborDeskService.use(owner, desk), HarborDeskService.Use.DUES_OWED, "desk with coins not in hand");
            owner.getInventory().selected = 0;
            h.assertTrue(Wallet.isCoin(owner.getMainHandItem()), "no coins in hand");
            h.assertValueEqual(HarborDeskService.use(owner, desk), HarborDeskService.Use.OPENED, "desk with coins in hand");
            h.assertValueEqual(visits.owed(owner.getUUID(), port.id()), 0, "owed after paying");
            h.assertValueEqual(Wallet.count(owner), 10L, "coins after paying");
            finish(h, port, f.ship(), owner);
        });
    }

    /** A hull afloat in the outpost's box, neither anchored nor at a berth, pays nothing however long it lies there. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300)
    public static void shipNotDockedPaysNothing(GameTestHelper h) {
        ServerPlayer owner = player(h, 20);
        Fixture f = hull(h, owner, false, x -> { });
        Port port = navyPort(h, BERTH_FAR);
        h.onEachTick(() -> {
            if (f.ship().isRemoved()) return;
            h.assertTrue(check(h, owner, f.ship()).isEmpty(), "charged a ship that is not docked");
        });
        h.runAtTickTime(200, () -> {
            h.assertValueEqual(Wallet.count(owner), 20L, "coins");
            h.assertTrue(PortVisitData.get(h.getLevel().getServer()).get(f.ship().id(), port.id()).isEmpty(), "a visit was recorded");
            finish(h, port, f.ship(), owner);
        });
    }

    /** {@code port_fees.enabled = false}: a ship lying at a berth is never charged. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = DISABLED_BATCH)
    public static void disabledFeesChargeNothing(GameTestHelper h) {
        ConfigOverrides.during(h, TradeConfig.PORT_FEES, false);
        ServerPlayer owner = player(h, 20);
        Fixture f = hull(h, owner, false, x -> { });
        Port port = navyPort(h, BERTH_NEAR);
        boolean[] docked = {false};
        h.onEachTick(() -> {
            if (f.ship().isRemoved()) return;
            h.assertTrue(check(h, owner, f.ship()).isEmpty(), "charged with port fees off");
            if (h.getTick() > SETTLE) {
                docked[0] |= DockingFees.docked(f.ship(), port, f.ship().worldBounds(), TradeConfig.dockingParams());
            }
        });
        h.runAtTickTime(200, () -> {
            h.assertTrue(docked[0], "the hull never lay at the berth, so the test proves nothing");
            h.assertValueEqual(Wallet.count(owner), 20L, "coins");
            finish(h, port, f.ship(), owner);
        });
    }
}

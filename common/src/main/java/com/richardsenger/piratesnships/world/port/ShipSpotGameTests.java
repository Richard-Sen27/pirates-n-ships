package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * STR7's delivery spot in a level ({@link ShipSpots}): a 48 by 48 basin (stone rim, sea surface y 7) whose southern
 * rows are sand shallows (two blocks of water) with the port's berth in them, deep water (six blocks) to the north.
 *
 * <ul>
 *     <li>{@link #largestShippedCoversEveryTemplate}: the hull the berths are carved for covers every loaded template.</li>
 *     <li>{@link #shallowBerthDeliversFartherOut}: an order's delivery ({@link ShipOrders#deliver}) puts the ship in the
 *     deep water, every footprint column deep and no sand within the clearance, assembled; {@code /pirates port spot}
 *     names the same place.</li>
 *     <li>{@link #noDeepWaterRefuses}: all shallows: no ship, the refusal is the action-bar "No deep water near the pier
 *     for her".</li>
 * </ul>
 */
public final class ShipSpotGameTests {

    private static final String BATCH = "pirates_n_ships_ship_spot_";
    private static final int SURFACE = 7;
    private static final BlockPos BERTH = new BlockPos(24, SURFACE, 42);

    private ShipSpotGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipSpotGameTests.class);
    }

    /** Stone floor y 1, rim, water to y 7; sand shallows (water y 6..7) from z {@code shallowFrom}; the ceiling cleared. */
    private static void basin(GameTestHelper h, int shallowFrom) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                boolean shallow = z >= shallowFrom;
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE
                            : y > SURFACE ? Blocks.AIR
                            : shallow && y <= 4 ? Blocks.STONE
                            : shallow && y == 5 ? Blocks.SAND : Blocks.WATER);
                }
                for (int y = 9; y <= 30; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    private static Port port(GameTestHelper h) {
        BoundingBox box = BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(47, 30, 47)));
        Port port = new Port(Constants.id("gametest/str7_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.SEAFARER_VILLAGE,
                h.getLevel().dimension(), h.absolutePos(new BlockPos(24, 8, 44)), box, Climate.TEMPERATE,
                List.of(new Berth(h.absolutePos(BERTH), Direction.NORTH)));
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static ServerPlayer player(GameTestHelper h) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "spot_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(24, 9, 45))));
        return p;
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = BATCH + "templates")
    public static void largestShippedCoversEveryTemplate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (ResourceLocation id : ShipTemplates.TYPE.server().ids()) {
            ShipSpots.Shape shape = ShipSpots.shape(level, id).orElseThrow(() -> new IllegalStateException("no shape for " + id));
            h.assertTrue(shape.hull().beam() <= ShipSpots.LARGEST_SHIPPED.beam() && shape.hull().length() <= ShipSpots.LARGEST_SHIPPED.length(),
                    id + ": hull " + shape.hull() + " within " + ShipSpots.LARGEST_SHIPPED);
            h.assertTrue(shape.draught() <= ShipSpots.LARGEST_DRAUGHT, id + ": draught " + shape.draught());
        }
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = BATCH + "far", timeoutTicks = 200)
    public static void shallowBerthDeliversFartherOut(GameTestHelper h) {
        basin(h, 38);
        Port port = port(h);
        ServerLevel level = h.getLevel();
        int surface = h.absolutePos(BERTH).getY();
        ShipSpots.Shape shape = ShipSpots.shape(level, ShipTemplates.STARTER_SLOOP_ID).orElseThrow();
        ShipOrders.Delivery d = ShipOrders.deliver(level, port, ShipTemplates.STARTER_SLOOP_ID, player(h), "STR7 test");
        d.berthed().map(b -> b.placed().assembly()).filter(a -> a != null && a.shipId() != null)
                .ifPresent(a -> ShipTestCleanup.track(h, a.shipId()));
        h.assertTrue(d.berthed().isPresent(), "delivered: " + d.failure());
        h.assertValueEqual(d.berthed().get().berth(), 1, "found from berth 1");
        UUID ship = d.berthed().get().placed().assembly().shipId();
        h.assertTrue(ship != null && SableShips.byId(level, ship) != null, "assembled");
        // the footprint the template went to: every column was deep, nothing of the shallows within the clearance
        BlockPos origin = d.berthed().get().placed().origin();
        BoundingBox box = com.richardsenger.piratesnships.ship.template.TemplatePlacement.worldBox(
                shape.size(), d.berthed().get().placed().rotation(), origin);
        int clearance = WorldConfig.PORTS_SHIP_CLEARANCE.get();
        int shallowZ = h.absolutePos(new BlockPos(0, 0, 38)).getZ();
        h.assertTrue(box.maxZ() + clearance < shallowZ, "clear of the shallows: " + box + ", shallows from z " + shallowZ);
        h.assertValueEqual(origin.getY(), surface - shape.waterline(), "waterline on the surface");
        PortRegistry.get(level.getServer()).remove(port.id());
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = BATCH + "command", timeoutTicks = 100)
    public static void spotCommandNamesTheSpot(GameTestHelper h) {
        basin(h, 38);
        Port port = port(h);
        ServerLevel level = h.getLevel();
        ShipSpots.Found found = ShipSpots.atPort(level, port, ShipTemplates.STARTER_SLOOP_ID).orElseThrow();
        h.assertTrue(found.spot().isPresent(), "a spot: " + found.search().failure());
        ShipSpot.Spot spot = found.spot().get();
        h.assertTrue(spot.distance() > 0, "farther out than the shallow berth");
        int surface = h.absolutePos(BERTH).getY();
        ShipSpot.Rect r = spot.footprint();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                int f = ShipSpots.flags(level, x, z, surface, found.shape().draught(), WorldConfig.PORTS_BERTH_DEPTH.get(), found.shape().above());
                h.assertTrue((f & ShipSpot.DEEP) != 0, "deep at " + x + " " + z);
            }
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(h.absolutePos(BERTH))).withPermission(4).withSuppressedOutput();
        int result;
        try {
            result = level.getServer().getCommands().getDispatcher().execute("pirates port spot pirates_n_ships:starter_sloop", source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("the command did not parse", e);
        }
        h.assertValueEqual(result, spot.distance(), "the command reports the same spot");
        PortRegistry.get(level.getServer()).remove(port.id());
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = BATCH + "none", timeoutTicks = 100)
    public static void noDeepWaterRefuses(GameTestHelper h) {
        basin(h, 1);
        Port port = port(h);
        ServerLevel level = h.getLevel();
        ShipOrders.Delivery d = ShipOrders.deliver(level, port, ShipTemplates.STARTER_SLOOP_ID, player(h), "STR7 test");
        h.assertTrue(d.berthed().isEmpty(), "no ship in the shallows");
        h.assertValueEqual(d.failure(), ShipSpot.Failure.NO_WATER, "no deep water");
        h.assertTrue(ShipOrders.isNoDeepWater(d.refusal()), "the refusal: " + d.refusal().getString());
        h.assertTrue(SableShips.all(level).stream().noneMatch(b -> port.box().isInside(BlockPos.containing(b.worldBounds().getCenter()))),
                "no ship placed");
        PortRegistry.get(level.getServer()).remove(port.id());
        h.succeed();
    }
}

package com.richardsenger.piratesnships.world.port;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.ship.template.ShipOrder;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SW1: a port's shipwright orders survive the saved-data codec, older records without them load, and updates replace. */
class PortOrdersCodecTest {

    private static final ShipOrder ORDER = new ShipOrder(UUID.fromString("11111111-2222-3333-4444-555555555555"),
            ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "starter_sloop_basic"), UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            1.5, 2.86);

    private static final Port VILLAGE = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_10_20"),
            PortKind.SEAFARER_VILLAGE, Level.OVERWORLD, new BlockPos(10, 64, 20), new BoundingBox(-20, 50, -10, 40, 80, 50),
            Climate.TEMPERATE, List.of(new Berth(new BlockPos(7, 62, 0), Direction.NORTH), new Berth(new BlockPos(13, 62, 0), Direction.NORTH)),
            List.of(), List.of(ORDER));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ordersRoundTrip() {
        Tag nbt = Port.CODEC.encodeStart(NbtOps.INSTANCE, VILLAGE).getOrThrow();
        assertEquals(VILLAGE, Port.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        JsonObject json = Port.CODEC.encodeStart(JsonOps.INSTANCE, VILLAGE).getOrThrow().getAsJsonObject();
        assertEquals(1, json.getAsJsonArray("orders").size());
        assertEquals(VILLAGE, Port.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void recordsWithoutOrdersStillLoad() {
        JsonObject json = Port.CODEC.encodeStart(JsonOps.INSTANCE, VILLAGE.withOrders(List.of())).getOrThrow().getAsJsonObject();
        json.remove("orders");
        Port back = Port.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(back.orders().isEmpty());
        assertEquals(VILLAGE.withOrders(List.of()), back);
        // the older constructors leave the orders empty
        assertTrue(new Port(VILLAGE.id(), VILLAGE.kind(), VILLAGE.dimension(), VILLAGE.centre(), VILLAGE.box(), VILLAGE.climate(),
                VILLAGE.berths()).orders().isEmpty());
    }

    @Test
    void indexReplacesAKnownPortOnly() {
        PortIndex index = PortIndex.EMPTY.with(VILLAGE.withOrders(List.of()));
        PortIndex next = index.replace(VILLAGE);
        assertEquals(List.of(ORDER), next.byId(VILLAGE.id()).orElseThrow().orders());
        Port other = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_0_0"), PortKind.SEAFARER_VILLAGE,
                Level.OVERWORLD, BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Climate.COLD, List.of());
        assertSame(index, index.replace(other));
        // the index codec keeps the orders
        Tag nbt = PortIndex.CODEC.encodeStart(NbtOps.INSTANCE, next).getOrThrow();
        assertEquals(List.of(ORDER), PortIndex.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().byId(VILLAGE.id()).orElseThrow().orders());
    }
}

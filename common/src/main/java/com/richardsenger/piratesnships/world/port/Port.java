package com.richardsenger.piratesnships.world.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.ship.template.ShipOrder;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * One generated port (design.md §10.4 "Port registry"). {@code id} is also the port's market id in
 * {@code trade.TradeData}; {@code box} is the whole structure's bounding box (desks inside it belong to the port);
 * {@code treasures} are the buried chests of a pirate island (WG2), empty for other ports; {@code orders} are the
 * shipwright orders waiting for pickup (SW1, seafarer villages; optional in the codec so older saves load).
 */
public record Port(ResourceLocation id, PortKind kind, ResourceKey<Level> dimension, BlockPos centre, BoundingBox box,
                   Climate climate, List<Berth> berths, List<TreasureSite> treasures, List<ShipOrder> orders) {

    public static final Codec<Port> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Port::id),
            PortKind.CODEC.fieldOf("kind").forGetter(Port::kind),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Port::dimension),
            BlockPos.CODEC.fieldOf("centre").forGetter(Port::centre),
            BoundingBox.CODEC.fieldOf("box").forGetter(Port::box),
            Climate.CODEC.fieldOf("climate").forGetter(Port::climate),
            Berth.CODEC.listOf().optionalFieldOf("berths", List.of()).forGetter(Port::berths),
            TreasureSite.CODEC.listOf().optionalFieldOf("treasures", List.of()).forGetter(Port::treasures),
            ShipOrder.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(Port::orders)
    ).apply(i, Port::new));

    public Port {
        berths = List.copyOf(berths);
        treasures = List.copyOf(treasures);
        orders = List.copyOf(orders);
    }

    /** A port without shipwright orders. */
    public Port(ResourceLocation id, PortKind kind, ResourceKey<Level> dimension, BlockPos centre, BoundingBox box,
                Climate climate, List<Berth> berths, List<TreasureSite> treasures) {
        this(id, kind, dimension, centre, box, climate, berths, treasures, List.of());
    }

    /** A port without treasure sites (villages, outposts). */
    public Port(ResourceLocation id, PortKind kind, ResourceKey<Level> dimension, BlockPos centre, BoundingBox box,
                Climate climate, List<Berth> berths) {
        this(id, kind, dimension, centre, box, climate, berths, List.of(), List.of());
    }

    /** A copy with {@code orders} instead of this port's orders. */
    public Port withOrders(List<ShipOrder> orders) {
        return new Port(id, kind, dimension, centre, box, climate, berths, treasures, orders);
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return dimension.equals(dim) && box.isInside(pos);
    }
}

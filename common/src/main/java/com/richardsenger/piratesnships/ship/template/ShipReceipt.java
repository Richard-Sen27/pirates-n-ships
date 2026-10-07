package com.richardsenger.piratesnships.ship.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The data of a ship receipt ({@link ShipReceiptItem}, SW1): the port that builds the ship, the order on that port,
 * the template and its name key (the tooltip's name; definitions are server only) and the finish day (world days, for the tooltip's progress). Saved and synced.
 */
public record ShipReceipt(ResourceLocation port, UUID order, ResourceLocation template, String name, double finishDay) {

    public static final Codec<ShipReceipt> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("port").forGetter(ShipReceipt::port),
            UUIDUtil.CODEC.fieldOf("order").forGetter(ShipReceipt::order),
            ResourceLocation.CODEC.fieldOf("template").forGetter(ShipReceipt::template),
            Codec.STRING.fieldOf("name").forGetter(ShipReceipt::name),
            Codec.DOUBLE.fieldOf("finish_day").forGetter(ShipReceipt::finishDay)
    ).apply(i, ShipReceipt::new));

    public static final StreamCodec<ByteBuf, ShipReceipt> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ShipReceipt::port,
            UUIDUtil.STREAM_CODEC, ShipReceipt::order,
            ResourceLocation.STREAM_CODEC, ShipReceipt::template,
            ByteBufCodecs.STRING_UTF8, ShipReceipt::name,
            ByteBufCodecs.DOUBLE, ShipReceipt::finishDay,
            ShipReceipt::new);

    public static ShipReceipt of(ResourceLocation port, ShipOrder order, String name) {
        return new ShipReceipt(port, order.id(), order.template(), name, order.finishDay());
    }

    public ShipReceipt withFinishDay(double day) {
        return new ShipReceipt(port, order, template, name, day);
    }
}

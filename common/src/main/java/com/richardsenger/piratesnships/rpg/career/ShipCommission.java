package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * The data of a ship commission ({@link ShipCommissionItem}, SHP1): the ladder that granted it, the ship template
 * (fixed when it was handed out), the template's name key for the tooltip (definitions are server only), and the
 * player it names. Saved and synced.
 */
public record ShipCommission(ShipGrantRules.Ladder ladder, ResourceLocation template, String name, UUID owner, String ownerName) {

    public static final Codec<ShipCommission> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShipGrantRules.Ladder.CODEC.fieldOf("ladder").forGetter(ShipCommission::ladder),
            ResourceLocation.CODEC.fieldOf("template").forGetter(ShipCommission::template),
            Codec.STRING.fieldOf("name").forGetter(ShipCommission::name),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(ShipCommission::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(ShipCommission::ownerName)
    ).apply(i, ShipCommission::new));

    public static final StreamCodec<ByteBuf, ShipGrantRules.Ladder> LADDER_STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> ShipGrantRules.Ladder.values()[i], ShipGrantRules.Ladder::ordinal);

    public static final StreamCodec<ByteBuf, ShipCommission> STREAM_CODEC = StreamCodec.composite(
            LADDER_STREAM_CODEC, ShipCommission::ladder,
            ResourceLocation.STREAM_CODEC, ShipCommission::template,
            ByteBufCodecs.STRING_UTF8, ShipCommission::name,
            UUIDUtil.STREAM_CODEC, ShipCommission::owner,
            ByteBufCodecs.STRING_UTF8, ShipCommission::ownerName,
            ShipCommission::new);
}

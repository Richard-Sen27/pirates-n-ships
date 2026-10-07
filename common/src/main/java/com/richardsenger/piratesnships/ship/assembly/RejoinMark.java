package com.richardsenger.piratesnships.ship.assembly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The piece a Shipwright's Toolkit is marked on (RS2): the keeper of the next rejoin. Stored on the toolkit stack as a
 * data component ({@link AssemblyContent#REJOIN_MARK}).
 *
 * @param ship  the marked ship's id
 * @param label what the tooltip shows (the ship's name, or "Wreck of …", or empty)
 */
public record RejoinMark(UUID ship, String label) {

    public static final Codec<RejoinMark> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("ship").forGetter(RejoinMark::ship),
            Codec.STRING.optionalFieldOf("label", "").forGetter(RejoinMark::label)
    ).apply(i, RejoinMark::new));

    public static final StreamCodec<ByteBuf, RejoinMark> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RejoinMark::ship,
            ByteBufCodecs.STRING_UTF8, RejoinMark::label,
            RejoinMark::new);
}

package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client (HELM1): the player took the wheel of the helm at {@code pos} ({@code active}, with the wheel's
 * current angle to predict from) or the server ended the session (released, too far, ship gone, steering off).
 */
public record HelmSessionPayload(BlockPos pos, boolean active, float wheel) implements CustomPacketPayload {

    public static final Type<HelmSessionPayload> TYPE = new Type<>(Constants.id("helm_session"));
    public static final StreamCodec<ByteBuf, HelmSessionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HelmSessionPayload::pos,
            ByteBufCodecs.BOOL, HelmSessionPayload::active,
            ByteBufCodecs.FLOAT, HelmSessionPayload::wheel,
            HelmSessionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

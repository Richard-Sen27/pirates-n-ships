package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server (HELM1): the helmsman turned the wheel of the helm at {@code pos} (a plot position on a ship) by
 * {@code delta} degrees this tick (positive = clockwise = starboard). Sent once per tick while steering, only when the
 * delta is not zero. The server applies it only to the player's own session and clamps it
 * ({@link HelmService#turn}).
 */
public record HelmWheelPayload(BlockPos pos, float delta) implements CustomPacketPayload {

    public static final Type<HelmWheelPayload> TYPE = new Type<>(Constants.id("helm_wheel"));
    public static final StreamCodec<ByteBuf, HelmWheelPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HelmWheelPayload::pos,
            ByteBufCodecs.FLOAT, HelmWheelPayload::delta,
            HelmWheelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

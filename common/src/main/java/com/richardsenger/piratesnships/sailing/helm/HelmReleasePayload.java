package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server (HELM1): the helmsman let go of the use button while holding the wheel of the helm at {@code pos}.
 * Vanilla sends nothing when a held block use ends, so {@code client/HelmSteeringClient} does. Ends the session
 * ({@link HelmService#release}); the wheel stays where it was turned.
 */
public record HelmReleasePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<HelmReleasePayload> TYPE = new Type<>(Constants.id("helm_release"));
    public static final StreamCodec<ByteBuf, HelmReleasePayload> CODEC =
            BlockPos.STREAM_CODEC.map(HelmReleasePayload::new, HelmReleasePayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

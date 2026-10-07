package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Client → server: the player let go of the use button while aiming the swivel gun at {@code pos} (a plot position on
 * a ship). Vanilla sends nothing when a held block use ends, so {@code client/SwivelAimClient} does. The server fires
 * the gun if this player is aiming it and it is loaded ({@link SwivelService#release}); anything else is ignored.
 */
public record SwivelReleasePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<SwivelReleasePayload> TYPE = new Type<>(Constants.id("swivel_release"));
    public static final StreamCodec<ByteBuf, SwivelReleasePayload> CODEC =
            BlockPos.STREAM_CODEC.map(SwivelReleasePayload::new, SwivelReleasePayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler (main thread). */
    public static void handle(SwivelReleasePayload payload, Player player) {
        if (player != null && player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())) {
            SwivelService.Use use = SwivelService.release(level, payload.pos(), player);
            if (use != null) {
                player.displayClientMessage(use.message(), true);
            }
        }
    }
}

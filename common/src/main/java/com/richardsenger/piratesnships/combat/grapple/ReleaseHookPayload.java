package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Client → server: the player sneak-used with an empty hand while a hook of theirs was out (vanilla sends nothing for
 * a use with an empty hand into the air). The server checks sneaking and the empty hand again and releases the hook.
 */
public record ReleaseHookPayload() implements CustomPacketPayload {

    public static final ReleaseHookPayload INSTANCE = new ReleaseHookPayload();
    public static final Type<ReleaseHookPayload> TYPE = new Type<>(Constants.id("grapple_release"));
    public static final StreamCodec<ByteBuf, ReleaseHookPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler. */
    public static void handle(ReleaseHookPayload payload, Player player) {
        if (player != null) {
            GrappleService.onReleaseRequest(player);
        }
    }
}

package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Client → server: the player used the rope of the hook with network id {@code hookId} while looking at it (GR2; the
 * client's pick, {@code client.GrappleClient}). The server picks again ({@link RopeSlideService#tryBoard}) before the
 * player grabs the rope.
 */
public record BoardRopePayload(int hookId) implements CustomPacketPayload {

    public static final Type<BoardRopePayload> TYPE = new Type<>(Constants.id("grapple_board_rope"));
    public static final StreamCodec<ByteBuf, BoardRopePayload> CODEC = ByteBufCodecs.VAR_INT.map(BoardRopePayload::new, BoardRopePayload::hookId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler. */
    public static void handle(BoardRopePayload payload, Player player) {
        if (player != null) {
            RopeSlideService.onBoardRequest(player, payload.hookId());
        }
    }
}

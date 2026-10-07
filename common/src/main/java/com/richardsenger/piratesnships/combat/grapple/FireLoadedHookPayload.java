package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Client → server: the player pressed use on a hook-loaded crossbow (GR3). The client cancels vanilla's use, which would
 * shoot the charged crossbow as an arrow; the server checks the crossbow again and shoots the hook
 * ({@link CrossbowHookLaunch#fire}).
 */
public record FireLoadedHookPayload(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<FireLoadedHookPayload> TYPE = new Type<>(Constants.id("grapple_fire_loaded"));
    public static final StreamCodec<ByteBuf, FireLoadedHookPayload> CODEC = ByteBufCodecs.BOOL.map(
            main -> new FireLoadedHookPayload(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND),
            p -> p.hand() == InteractionHand.MAIN_HAND);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server handler. */
    public static void handle(FireLoadedHookPayload payload, Player player) {
        if (player != null && !player.isSpectator() && player.level() instanceof ServerLevel level) {
            CrossbowHookLaunch.fire(level, player, payload.hand());
        }
    }
}

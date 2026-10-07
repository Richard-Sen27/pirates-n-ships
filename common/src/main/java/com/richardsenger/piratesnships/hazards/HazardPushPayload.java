package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Server → client: one tick of a hazard's field on the receiving player, or on the vehicle they steer. The client owns
 * its player's (and its boat's) movement, so the server's acceleration is added there, the upward part capped as on the
 * server ({@link HazardField#applyLift}).
 *
 * @param x       acceleration, blocks/tick²
 * @param maxLift upward speed above which lifting stops, blocks/tick
 * @param vehicle true to push the player's vehicle instead of the player
 */
public record HazardPushPayload(float x, float y, float z, float maxLift, boolean vehicle) implements CustomPacketPayload {

    public static final Type<HazardPushPayload> TYPE = new Type<>(Constants.id("hazard_push"));

    public static final StreamCodec<ByteBuf, HazardPushPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, HazardPushPayload::x,
            ByteBufCodecs.FLOAT, HazardPushPayload::y,
            ByteBufCodecs.FLOAT, HazardPushPayload::z,
            ByteBufCodecs.FLOAT, HazardPushPayload::maxLift,
            ByteBufCodecs.BOOL, HazardPushPayload::vehicle,
            HazardPushPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler: adds the push to the local player or its vehicle. */
    public static void apply(HazardPushPayload payload, Player player) {
        if (player == null) {
            return;
        }
        Entity target = payload.vehicle() ? player.getVehicle() : player;
        if (target == null) {
            return;
        }
        HazardForces.addVelocity(target, payload.x(), payload.y(), payload.z(), payload.maxLift());
    }
}

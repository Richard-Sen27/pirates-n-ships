package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the local player pressed the attack key with a gun to fire (FA1). It carries nothing: the server
 * picks the gun from its own view of the player ({@link FirearmTrigger#gunHand}) and validates everything
 * ({@link FirearmTrigger#pull}).
 */
public record FirearmFirePayload() implements CustomPacketPayload {

    public static final FirearmFirePayload INSTANCE = new FirearmFirePayload();
    public static final Type<FirearmFirePayload> TYPE = new Type<>(Constants.id("firearm_fire"));
    public static final StreamCodec<ByteBuf, FirearmFirePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<FirearmFirePayload> type() {
        return TYPE;
    }
}

package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the local player pressed a melee action while holding a skill-based sword. The action travels as
 * its ordinal so an unknown value (newer or modified client) decodes and is ignored by {@link MeleeActions#handle}
 * instead of breaking the connection.
 *
 * @param action     {@link MeleeAction} ordinal
 * @param clientTick the client's game time when the input was classified (the client timestamp of §8.5). The server
 *                   resolves with its own tick plus the latency allowance and only reports this value back for
 *                   diagnosis; it is never trusted for timing.
 */
public record MeleeActionPayload(int action, int clientTick) implements CustomPacketPayload {

    public static final Type<MeleeActionPayload> TYPE = new Type<>(Constants.id("melee_action"));
    public static final StreamCodec<ByteBuf, MeleeActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeleeActionPayload::action,
            ByteBufCodecs.VAR_INT, MeleeActionPayload::clientTick,
            MeleeActionPayload::new);

    public MeleeActionPayload(MeleeAction action, int clientTick) {
        this(action.ordinal(), clientTick);
    }

    /** The decoded action, or {@code null} when unknown. */
    public @org.jetbrains.annotations.Nullable MeleeAction decodedAction() {
        return MeleeAction.byOrdinal(action);
    }

    @Override
    public Type<MeleeActionPayload> type() {
        return TYPE;
    }
}

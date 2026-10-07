package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the sea of the player's level (WV1), like the wind sync. The wave trains are fixed
 * ({@link WaveField#COMPONENTS}), so the amplitude and the direction are enough for the client to rebuild the field;
 * it blends toward each sample over {@code intervalTicks}.
 *
 * @param state         ordinal of the current {@link SeaState}
 * @param amplitude     crest height [blocks] including {@code waves.amplitude}; 0 when waves are off
 * @param directionDeg  compass bearing the waves run toward
 * @param intervalTicks ticks until the next sync
 */
public record WaveSyncPayload(int state, float amplitude, float directionDeg, int intervalTicks) implements CustomPacketPayload {

    public static final Type<WaveSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wave_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WaveSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WaveSyncPayload::state,
            ByteBufCodecs.FLOAT, WaveSyncPayload::amplitude,
            ByteBufCodecs.FLOAT, WaveSyncPayload::directionDeg,
            ByteBufCodecs.VAR_INT, WaveSyncPayload::intervalTicks,
            WaveSyncPayload::new);

    public static WaveSyncPayload of(SeaState state, WaveField field, int intervalTicks) {
        return new WaveSyncPayload(state.ordinal(), (float) field.amplitude(), (float) field.directionDegrees(), intervalTicks);
    }

    public SeaState seaState() {
        return SeaState.byOrdinal(state);
    }

    public WaveField field() {
        return new WaveField(amplitude, directionDeg);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

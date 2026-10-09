package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the sea of the player's level (WV1, WAV2), like the wind sync. The trains come from a fixed seed
 * ({@link WaveSpectrum}), so the amplitude, the direction, the train count, the peak wavelength and the groups are
 * enough for the client to rebuild the same field; it blends toward each sample over {@code intervalTicks}.
 *
 * @param state            ordinal of the current {@link SeaState}
 * @param amplitude        crest height [blocks] including {@code waves.amplitude}; 0 when waves are off
 * @param directionDeg     compass bearing the waves run toward
 * @param intervalTicks    ticks until the next sync
 * @param components       number of wave trains ({@code waves.components})
 * @param peakWavelength   the sea state's peak wavelength [blocks]
 * @param groupDepth       {@code waves.group_depth}
 * @param groupPeriodTicks {@code waves.group_period_seconds} × 20
 */
public record WaveSyncPayload(int state, float amplitude, float directionDeg, int intervalTicks, int components,
                              float peakWavelength, float groupDepth, float groupPeriodTicks) implements CustomPacketPayload {

    public static final Type<WaveSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wave_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WaveSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.state);
                buf.writeFloat(p.amplitude);
                buf.writeFloat(p.directionDeg);
                buf.writeVarInt(p.intervalTicks);
                buf.writeVarInt(p.components);
                buf.writeFloat(p.peakWavelength);
                buf.writeFloat(p.groupDepth);
                buf.writeFloat(p.groupPeriodTicks);
            },
            buf -> new WaveSyncPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** The payload for {@code field}, a sea of {@code peakWavelength} built by {@link SeaStates}. */
    public static WaveSyncPayload of(SeaState state, WaveField field, double peakWavelength, int intervalTicks) {
        return new WaveSyncPayload(state.ordinal(), (float) field.amplitude(), (float) field.directionDegrees(), intervalTicks,
                Math.max(WaveSpectrum.MIN_COMPONENTS, field.components().size()), (float) peakWavelength,
                (float) field.groups().depth(), (float) field.groups().periodTicks());
    }

    public SeaState seaState() {
        return SeaState.byOrdinal(state);
    }

    public WaveField.Groups groups() {
        return new WaveField.Groups(groupDepth, groupPeriodTicks);
    }

    /** The field this payload describes, unpinned ({@link WaveField.Origin#NONE}). */
    public WaveField field() {
        return new WaveField(amplitude, directionDeg, WaveSpectrum.components(components, peakWavelength), WaveField.Origin.NONE,
                groups());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

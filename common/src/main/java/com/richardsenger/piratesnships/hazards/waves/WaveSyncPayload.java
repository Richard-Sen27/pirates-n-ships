package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the sea of the player's level (WV1, WAV2, WAV7), like the wind sync. The trains come from a fixed
 * seed ({@link WaveSpectrum}), so the amplitude, the heading (direction, previous direction and crossfade), the train
 * count, the peak wavelength, the groups and the origin are enough for the client to rebuild exactly the server's
 * field. Every number is a double: the phase at a far coordinate multiplies the direction by up to {@code 10⁷}, so a
 * float direction would put the client's sea a visible fraction of a wave off the server's. The client blends the
 * amplitude, the peak wavelength and the group depth toward each sample over {@code intervalTicks}; the heading and the
 * origin it takes as they are.
 *
 * @param state          ordinal of the current {@link SeaState}
 * @param amplitude      crest height [blocks] including {@code waves.amplitude}; 0 when waves are off
 * @param heading        the direction the waves run toward and the crossfade from the previous one
 * @param intervalTicks  ticks until the next sync
 * @param components     number of wave trains ({@code waves.components})
 * @param peakWavelength the sea state's peak wavelength [blocks]
 * @param groups         {@code waves.group_depth} (scaled by the state) and {@code waves.group_period_seconds} × 20
 * @param origin         the field's origin ({@link WaveField.Origin#NONE} in gameplay)
 */
public record WaveSyncPayload(int state, double amplitude, WaveField.Heading heading, int intervalTicks, int components,
                              double peakWavelength, WaveField.Groups groups, WaveField.Origin origin) implements CustomPacketPayload {

    public static final Type<WaveSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wave_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WaveSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.state);
                buf.writeDouble(p.amplitude);
                buf.writeDouble(p.heading.current());
                buf.writeDouble(p.heading.previous());
                buf.writeDouble(p.heading.fadeStart());
                buf.writeDouble(p.heading.fadeTicks());
                buf.writeVarInt(p.intervalTicks);
                buf.writeVarInt(p.components);
                buf.writeDouble(p.peakWavelength);
                buf.writeDouble(p.groups.depth());
                buf.writeDouble(p.groups.periodTicks());
                buf.writeDouble(p.origin.ticks());
                buf.writeDouble(p.origin.x());
                buf.writeDouble(p.origin.z());
            },
            buf -> new WaveSyncPayload(buf.readVarInt(), buf.readDouble(),
                    new WaveField.Heading(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    new WaveField.Groups(buf.readDouble(), buf.readDouble()),
                    new WaveField.Origin(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    /** The payload for {@code field}, a sea of {@code peakWavelength} built by {@link SeaStates}. */
    public static WaveSyncPayload of(SeaState state, WaveField field, double peakWavelength, int intervalTicks) {
        return new WaveSyncPayload(state.ordinal(), field.amplitude(), field.heading(), intervalTicks,
                Math.max(WaveSpectrum.MIN_COMPONENTS, field.components().size()), peakWavelength, field.groups(), field.origin());
    }

    public SeaState seaState() {
        return SeaState.byOrdinal(state);
    }

    /** Compass bearing the waves run toward once any fade is over [degrees]. */
    public double directionDeg() {
        return heading.current();
    }

    /** The field this payload describes: the server's field, number for number. */
    public WaveField field() {
        return new WaveField(amplitude, heading, WaveSpectrum.components(components, peakWavelength), origin, groups);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

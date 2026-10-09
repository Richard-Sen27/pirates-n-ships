package com.richardsenger.piratesnships.hazards.waves;

/**
 * Client-side holder of the last synced sea (WV1), like {@code sailing.wind.ClientWind}: the amplitude and direction
 * blend linearly from the previous sample to the new one over the sync interval. Contains no client-only classes, so it
 * is safe to load on a dedicated server. Updated on the client main thread by the {@link WaveSyncPayload} handler.
 */
public final class ClientWaves {

    /**
     * A blend between two seas from {@code start} over {@code duration} ticks; the peak wavelength and the group depth
     * blend with the amplitude, the train count and the group period are the latest sample's (WAV2). Pure.
     */
    public record Blend(double fromAmplitude, double fromDirection, double toAmplitude, double toDirection,
                       SeaState state, double start, double duration, double fromPeak, double toPeak, int components,
                       double fromGroupDepth, WaveField.Groups groups) {

        public static final Blend FLAT = new Blend(0.0, 0.0, 0.0, 0.0, SeaState.CALM, 0.0, 1.0,
                WaveSpectrum.REFERENCE_WAVELENGTH, WaveSpectrum.REFERENCE_WAVELENGTH, WaveSpectrum.DEFAULT_COMPONENTS,
                0.0, WaveField.Groups.NONE);

        /** A steady sea from one sample. */
        public static Blend of(WaveSyncPayload p, double time) {
            return new Blend(p.amplitude(), p.directionDeg(), p.amplitude(), p.directionDeg(), p.seaState(), time, 1.0,
                    p.peakWavelength(), p.peakWavelength(), p.components(), p.groupDepth(), p.groups());
        }

        public double fraction(double time) {
            return duration <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, (time - start) / duration));
        }

        public double amplitude(double time) {
            return fromAmplitude + (toAmplitude - fromAmplitude) * fraction(time);
        }

        /** Direction along the shorter arc. */
        public double direction(double time) {
            double d = ((toDirection - fromDirection) % 360.0 + 540.0) % 360.0 - 180.0;
            return fromDirection + d * fraction(time);
        }

        public double peakWavelength(double time) {
            return fromPeak + (toPeak - fromPeak) * fraction(time);
        }

        public double groupDepth(double time) {
            return fromGroupDepth + (groups.depth() - fromGroupDepth) * fraction(time);
        }

        /** The sea at {@code time}: the same field the server builds, unpinned. */
        public WaveField field(double time) {
            return new WaveField(amplitude(time), direction(time), WaveSpectrum.components(components, peakWavelength(time)),
                    WaveField.Origin.NONE, new WaveField.Groups(groupDepth(time), groups.periodTicks()));
        }

        /** The next blend: from where this one is at {@code time} toward {@code p}. */
        public Blend next(WaveSyncPayload p, double time) {
            return new Blend(amplitude(time), direction(time), p.amplitude(), p.directionDeg(), p.seaState(), time,
                    Math.max(1, p.intervalTicks()), peakWavelength(time), p.peakWavelength(), p.components(), groupDepth(time),
                    p.groups());
        }
    }

    private static volatile Blend blend = Blend.FLAT;
    private static volatile boolean received;

    private ClientWaves() {
    }

    /** The sea at client game time {@code time}. */
    public static WaveField field(double time) {
        return blend.field(time);
    }

    /** The last synced sea state. */
    public static SeaState state() {
        return blend.state();
    }

    public static boolean hasData() {
        return received;
    }

    /** Called by the payload handler. The first sample after a reset is taken at once. */
    public static void accept(WaveSyncPayload payload, double clientTime) {
        blend = received ? blend.next(payload, clientTime) : Blend.of(payload, clientTime);
        received = true;
    }

    public static void reset() {
        blend = Blend.FLAT;
        received = false;
    }
}

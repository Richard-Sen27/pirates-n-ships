package com.richardsenger.piratesnships.hazards.waves;

/**
 * Client-side holder of the last synced sea (WV1, WAV7), like {@code sailing.wind.ClientWind}. Contains no client-only
 * classes, so it is safe to load on a dedicated server. Updated on the client main thread by the
 * {@link WaveSyncPayload} handler.
 *
 * <p>The field the client draws is the server's field (WAV7): the heading (direction, previous direction, crossfade),
 * the origin, the train count and the group period are the latest sample's exactly, so the crests stand where the
 * server's ships feel them. Only the size of the sea blends: the amplitude, the peak wavelength (which only reweights
 * the trains) and the group depth move linearly from where they were to the new sample over the sync interval, as the
 * server's sea eases between states. In a steady sea the client's field equals the server's.
 */
public final class ClientWaves {

    /**
     * A blend of the sea's size from {@code from…} at {@code start} over {@code duration} ticks toward the sample
     * {@code to}, whose heading, origin and trains apply at once. Pure.
     */
    public record Blend(double fromAmplitude, double fromPeak, double fromGroupDepth, double start, double duration,
                        WaveSyncPayload to) {

        private static final WaveSyncPayload FLAT_SAMPLE = WaveSyncPayload.of(SeaState.CALM, WaveField.FLAT,
                WaveSpectrum.REFERENCE_WAVELENGTH, 1);
        public static final Blend FLAT = of(FLAT_SAMPLE, 0.0);

        /** A steady sea from one sample. */
        public static Blend of(WaveSyncPayload p, double time) {
            return new Blend(p.amplitude(), p.peakWavelength(), p.groups().depth(), time, 1.0, p);
        }

        public SeaState state() {
            return to.seaState();
        }

        public double fraction(double time) {
            return duration <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, (time - start) / duration));
        }

        public double amplitude(double time) {
            return fromAmplitude + (to.amplitude() - fromAmplitude) * fraction(time);
        }

        public double peakWavelength(double time) {
            return fromPeak + (to.peakWavelength() - fromPeak) * fraction(time);
        }

        public double groupDepth(double time) {
            return fromGroupDepth + (to.groups().depth() - fromGroupDepth) * fraction(time);
        }

        /** The sea at {@code time}: once the blend is done, exactly the field the server built. */
        public WaveField field(double time) {
            if (fraction(time) >= 1.0) {
                return to.field();
            }
            return new WaveField(amplitude(time), to.heading(), WaveSpectrum.components(to.components(), peakWavelength(time)),
                    to.origin(), new WaveField.Groups(groupDepth(time), to.groups().periodTicks()));
        }

        /** The next blend: from where this one is at {@code time} toward {@code p}. */
        public Blend next(WaveSyncPayload p, double time) {
            return new Blend(amplitude(time), peakWavelength(time), groupDepth(time), time, Math.max(1, p.intervalTicks()), p);
        }
    }

    private static volatile Blend blend = Blend.FLAT;
    private static volatile boolean received;
    /** The finished blend's field (rebuilding it costs a list and arrays), with the blend it belongs to. */
    private record Cached(Blend blend, WaveField field) {
    }

    private static volatile Cached cached = new Cached(Blend.FLAT, WaveField.FLAT);

    private ClientWaves() {
    }

    /** The sea at client game time {@code time}. */
    public static WaveField field(double time) {
        Blend b = blend;
        if (b.fraction(time) < 1.0) {
            return b.field(time);
        }
        Cached c = cached;
        if (c.blend() != b) {
            c = new Cached(b, b.field(time));
            cached = c;
        }
        return c.field();
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

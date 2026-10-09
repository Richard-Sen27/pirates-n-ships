package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceWaves;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * WAV7: physics and drawing share one sea. The server's hull sample ({@code WaveForces}: {@link WaveField#heightAt} of
 * the level's field) and the client's drawn height ({@link SurfaceWaves} over the field rebuilt from the
 * {@link WaveSyncPayload} by {@link ClientWaves}) agree at the same point and time, anywhere in the world and during a
 * crossfade of the direction.
 *
 * <p>Measured before (WV1's anchored field, a storm at (3000, −2000) with the waves 20° off the trains' reference
 * bearing, the bow 8 blocks ahead of the hull centre, over 3600 ticks): with the drawn sea anchored at the ship's
 * position 2.5 blocks from the hull centre, the drawn height at the bow differed from the physics' by up to 0.16 blocks
 * (rms 0.05 on an rms height of 0.60; phase offsets up to 10° per train); with a free camera anchor 40 blocks away by
 * up to 1.22 blocks (rms 0.48, i.e. uncorrelated; phase offsets 25–34° on the main trains and 174° on the shortest).
 */
class OneSeaTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WaveField serverField(WaveField.Heading heading, WaveField.Origin origin) {
        double state = SeaState.STORM.amplitude();
        return new WaveField(state * 1.0, heading, WaveSpectrum.components(6, WaveSpectrum.peakWavelength(state)), origin,
                new WaveField.Groups(0.35 * WaveSpectrum.groupShare(state), 1200.0));
    }

    private static WaveSyncPayload wire(WaveSyncPayload p) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            WaveSyncPayload.CODEC.encode(buf, p);
            WaveSyncPayload back = WaveSyncPayload.CODEC.decode(buf);
            assertEquals(0, buf.readableBytes());
            return back;
        } finally {
            buf.release();
        }
    }

    @Test
    void thePayloadRoundTripsEveryNumber() {
        WaveField f = serverField(new WaveField.Heading(111.123456789, 97.5, 123456.0, 300.0), new WaveField.Origin(77, 3000.25, -2000.5));
        WaveSyncPayload p = WaveSyncPayload.of(SeaState.STORM, f, WaveSpectrum.peakWavelength(2.0), 60);
        assertEquals(p, wire(p));
        WaveSyncPayload steady = WaveSyncPayload.of(SeaState.CALM, WaveField.FLAT, 56.0, 60);
        assertEquals(steady, wire(steady));
    }

    /** The hull sample and the drawn surface agree to 1e-9 anywhere in the world, steady and while crossfading. */
    @Test
    void theHullSampleAndTheDrawnSeaAgreeEverywhere() {
        Random r = new Random(7);
        WaveField.Heading[] headings = {
                WaveField.Heading.steady(110.0),
                new WaveField.Heading(123.0, 110.0, 50_000.0, 300.0),
                new WaveField.Heading(-170.0, 175.0, 2_000_000.0, 300.0)};
        WaveField.Origin[] origins = {WaveField.Origin.NONE, new WaveField.Origin(49_900, 3011.5, -1990.5)};
        double[][] places = {{3000, -2000}, {0, 0}, {10_000, 10_000}, {-2.9e7, 2.9e7}, {2.9999e7, -1.5e7}};
        for (WaveField.Heading heading : headings) {
            for (WaveField.Origin origin : origins) {
                WaveField server = serverField(heading, origin);
                WaveSyncPayload sent = WaveSyncPayload.of(SeaState.STORM, server, WaveSpectrum.peakWavelength(2.0), 60);
                double start = Double.isFinite(heading.fadeStart()) ? heading.fadeStart() : 98_765.4;
                WaveField client = ClientWaves.Blend.of(wire(sent), start - 100).field(start);
                for (double[] place : places) {
                    for (double t : new double[] {start - 1, start + 75.25, start + 150, start + 299.9, start + 1000, 12_345.678}) {
                        SurfaceWaves drawn = new SurfaceWaves().prepare(client, t);
                        for (int i = 0; i < 20; i++) {
                            double x = place[0] + (r.nextDouble() - 0.5) * 200, z = place[1] + (r.nextDouble() - 0.5) * 200;
                            double hull = server.heightAt(x, z, t);
                            assertEquals(hull, drawn.height(x, z), 1e-9, "at " + x + ", " + z + ", t=" + t + ", " + heading);
                            assertEquals(hull, client.heightAt(x, z, t), 1e-9, "the client's point sample");
                        }
                        // the mesh's grid from integer block corners, as the near ring builds it: its recurrence steps the
                        // phase instead of rounding it afresh at every vertex, which at 10⁷ rad differs from the point
                        // formula by the formula's own rounding (about 1e-9 rad); the vertices themselves are floats
                        // relative to the camera (about 1e-6 blocks), so 1e-8 there is exact for drawing
                        double tol = Math.abs(place[0]) + Math.abs(place[1]) > 1.0e6 ? 1.0e-8 : 1.0e-9;
                        int n = 33;
                        double x0 = Math.floor(place[0]) - 16, z0 = Math.floor(place[1]) - 16;
                        double[] grid = new double[n * n];
                        drawn.fillGrid(x0, z0, 1, n, grid);
                        for (int iz = 0; iz < n; iz += 4) {
                            for (int ix = 0; ix < n; ix += 4) {
                                assertEquals(server.heightAt(x0 + ix, z0 + iz, t), grid[iz * n + ix], tol, "grid at " + (x0 + ix) + ", " + (z0 + iz));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The whole path over time: the server's tracker steps the direction as the wind turns, every heading change and
     * every sync interval goes over the wire, and at every tick the client's field gives the server's heights at the
     * bow of a ship 3000 blocks out and beside a camera far away.
     */
    @Test
    void theClientFollowsTheServersSeaThroughDirectionSteps() {
        SeaStateModel.Tracker tracker = new SeaStateModel.Tracker();
        ClientWaves.reset();
        int steps = 0;
        WaveField.Heading last = null;
        try {
            for (long now = 0; now < 6000; now++) {
                tracker.tick(SeaState.STORM, 1.0, now);
                double wind = 40.0 + 70.0 * Math.sin(now / 900.0); // the wind swings by ±70°
                WaveField.Heading heading = tracker.tickDirection(wind, now, 10.0, 300.0);
                WaveField server = serverField(heading, WaveField.Origin.NONE);
                if (now % 60 == 0 || !heading.equals(last)) {
                    if (last != null && !heading.equals(last)) {
                        steps++;
                    }
                    ClientWaves.accept(wire(WaveSyncPayload.of(SeaState.STORM, server, WaveSpectrum.peakWavelength(2.0), 60)), now);
                    last = heading;
                }
                for (double frac : new double[] {0.0, 0.5}) {
                    double t = now + frac;
                    WaveField client = ClientWaves.field(t);
                    SurfaceWaves drawn = new SurfaceWaves().prepare(client, t);
                    for (double[] p : new double[][] {{3000, -2008}, {3002, -1998.5}, {-25_000.5, 18_000.25}}) {
                        assertEquals(server.heightAt(p[0], p[1], t), drawn.height(p[0], p[1]), 1e-9, "tick " + now + " at " + p[0]);
                    }
                }
            }
        } finally {
            ClientWaves.reset();
        }
        assertTrue(steps >= 5, "the swinging wind should have turned the sea several times: " + steps);
    }

    /** The direction steps only past the step, never while a fade is pending or running, and takes the short arc. */
    @Test
    void theDirectionStepsAndFades() {
        WaveField.Heading h = SeaStateModel.stepHeading(null, 100.0, 0, 10.0, 300.0);
        assertEquals(WaveField.Heading.steady(100.0), h);
        assertEquals(h, SeaStateModel.stepHeading(h, 109.9, 10, 10.0, 300.0), "within the step");
        WaveField.Heading s = SeaStateModel.stepHeading(h, 110.5, 20, 10.0, 300.0);
        assertEquals(new WaveField.Heading(110.5, 100.0, 20 + SeaStateModel.DIRECTION_LEAD_TICKS, 300.0), s);
        assertEquals(0.0, s.fade(25), 0.0, "the lead: the clients hear of the step before it shows");
        assertEquals(s, SeaStateModel.stepHeading(s, 150.0, 200, 10.0, 300.0), "no new step while fading");
        WaveField.Heading next = SeaStateModel.stepHeading(s, 150.0, 20 + SeaStateModel.DIRECTION_LEAD_TICKS + 300, 10.0, 300.0);
        assertEquals(150.0, next.current(), 1e-12);
        assertEquals(110.5, next.previous(), 1e-12);
        // the short arc across north
        WaveField.Heading north = SeaStateModel.stepHeading(WaveField.Heading.steady(355.0), 20.0, 0, 10.0, 300.0);
        assertEquals(380.0, north.current(), 1e-12);
        assertEquals(25.0, SeaStateModel.turnDegrees(355.0, 20.0), 1e-12);
        // a held direction applies at once
        SeaStateModel.Tracker t = new SeaStateModel.Tracker();
        t.tickDirection(30.0, 0, 10.0, 300.0);
        t.setOverride(SeaState.STORM, 90.0, SeaStateModel.Tracker.FOREVER);
        assertEquals(WaveField.Heading.steady(90.0), t.heading());
        assertEquals(WaveField.Heading.steady(90.0), t.tickDirection(30.0, 1, 10.0, 300.0), "the hold's direction wins");
        t.clearOverride();
        WaveField.Heading back = t.tickDirection(30.0, 2, 10.0, 300.0);
        assertEquals(30.0, back.current(), 1e-12);
        assertEquals(90.0, back.previous(), 1e-12);
    }

    @Test
    void theClientBlendsOnlyTheSizeOfTheSea() {
        WaveField calm = new WaveField(0.15, WaveField.Heading.steady(350.0), WaveSpectrum.components(6, 56.0), WaveField.Origin.NONE,
                WaveField.Groups.NONE);
        WaveField storm = new WaveField(2.0, new WaveField.Heading(10.0, 350.0, 120.0, 300.0), WaveSpectrum.components(6, 42.0),
                WaveField.Origin.NONE, new WaveField.Groups(0.35, 1200.0));
        ClientWaves.Blend b = ClientWaves.Blend.of(WaveSyncPayload.of(SeaState.CALM, calm, 56.0, 60), 0)
                .next(WaveSyncPayload.of(SeaState.STORM, storm, 42.0, 60), 100);
        assertEquals(0.15, b.amplitude(100), 1e-12);
        assertEquals(1.075, b.amplitude(130), 1e-12);
        assertEquals(2.0, b.amplitude(500), 1e-12);
        assertEquals(49.0, b.peakWavelength(130), 1e-12);
        assertEquals(0.175, b.groupDepth(130), 1e-12);
        assertEquals(SeaState.STORM, b.state());
        // the heading is the new sample's at once
        assertEquals(storm.heading(), b.field(130).heading());
        // once blended, the server's field number for number
        for (double x : new double[] {0, 3000, -2.9e7}) {
            assertEquals(storm.heightAt(x, 77, 500), b.field(500).heightAt(x, 77, 500), 0.0);
        }
        List<WaveField.Component> c = b.field(500).components();
        assertEquals(storm.components(), c);
    }
}

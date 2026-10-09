package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.richardsenger.piratesnships.hazards.waves.client.CameraSway;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodTickInput;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class WaveSpillAndSyncTest {

    @Test
    void spillIsTheHighestCrestAndNeverBelowZero() {
        assertEquals(0.8, WaveSpill.height(new double[] {0.1, -0.4, 0.8, 0.3}, true));
        assertEquals(0.0, WaveSpill.height(new double[] {-0.1, -0.4}, true));
        assertEquals(0.0, WaveSpill.height(new double[] {0.9}, false));
        assertEquals(0.0, WaveSpill.height(new double[0], true));
        assertEquals(0.2, WaveSpill.height(new double[] {Double.NaN, 0.2}, true));
    }

    @Test
    void spillRaisesTheOutsideLevelOfTheFloodInput() {
        // the hull runtime feeds the spill height as FloodTickInput.waveHeight
        FloodTickInput in = FloodTickInput.calm(3.0).withWaves(WaveSpill.height(new double[] {0.6, 0.2}, true));
        assertEquals(3.6, in.outsideLevel(null), 1e-12);
    }

    @Test
    void clientBlendGoesTheShortWayAndReachesTheSample() {
        ClientWaves.Blend b = ClientWaves.Blend.of(new WaveSyncPayload(SeaState.CALM.ordinal(), 0.1f, 350.0f, 60, 6, 20.0f, 0.35f, 1200f), 0)
                .next(new WaveSyncPayload(SeaState.STORM.ordinal(), 1.2f, 10.0f, 60, 6, 34.0f, 0.35f, 1200f), 100);
        assertEquals(0.1, b.amplitude(100), 1e-6);
        assertEquals(0.65, b.amplitude(130), 1e-6);
        assertEquals(1.2, b.amplitude(500), 1e-6);
        assertEquals(360.0, b.direction(130), 1e-4);
        assertEquals(370.0, b.direction(160), 1e-4);
        assertEquals(SeaState.STORM, b.state());
        assertEquals(27.0, b.peakWavelength(130), 1e-4);
        assertEquals(34.0, b.peakWavelength(500), 1e-4);
        assertEquals(0.35, b.groupDepth(500), 1e-6);
    }

    @Test
    void theClientRebuildsTheServersSea() {
        // the server's field (SeaStates.build) and the client's (from the payload) give the same heights
        WaveField server = new WaveField(1.2, 77.0, WaveSpectrum.components(6, WaveSpectrum.peakWavelength(1.2)),
                WaveField.Origin.NONE, new WaveField.Groups(0.35, 1200.0));
        WaveSyncPayload p = WaveSyncPayload.of(SeaState.STORM, server, WaveSpectrum.peakWavelength(1.2), 60);
        WaveField client = ClientWaves.Blend.of(p, 500).field(510);
        for (int i = 0; i < 40; i++) {
            double x = 3000 + i * 3.1, z = -700 + i * 1.3, t = 12345 + i * 17;
            assertEquals(server.heightAround(x, z, x + 2, z - 4, t), client.heightAround(x, z, x + 2, z - 4, t), 1e-4);
        }
    }

    @Test
    void cameraSwayReadsTheHeelAcrossTheView() {
        // looking south (yaw 0): right is -X; a ship heeled so its up leans toward -X dips on the right
        Vector3d up = new Vector3d(-Math.sin(Math.toRadians(10)), Math.cos(Math.toRadians(10)), 0);
        assertEquals(10.0, CameraSway.heelAcrossViewDegrees(up, 0.0), 1e-9);
        // looking west (yaw 90): right is -Z, so the same heel is fore and aft: no roll
        assertEquals(0.0, CameraSway.heelAcrossViewDegrees(up, Math.toRadians(90)), 1e-9);
        // looking north (yaw 180): the heel is to the left
        assertEquals(-10.0, CameraSway.heelAcrossViewDegrees(up, Math.toRadians(180)), 1e-9);
    }
}

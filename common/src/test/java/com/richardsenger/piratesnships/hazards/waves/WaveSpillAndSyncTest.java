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
        ClientWaves.Blend b = new ClientWaves.Blend(0.1, 350.0, 0.1, 350.0, SeaState.CALM, 0, 1)
                .next(new WaveSyncPayload(SeaState.STORM.ordinal(), 1.2f, 10.0f, 60), 100);
        assertEquals(0.1, b.amplitude(100), 1e-6);
        assertEquals(0.65, b.amplitude(130), 1e-6);
        assertEquals(1.2, b.amplitude(500), 1e-6);
        assertEquals(360.0, b.direction(130), 1e-4);
        assertEquals(370.0, b.direction(160), 1e-4);
        assertEquals(SeaState.STORM, b.state());
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

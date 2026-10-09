package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SurfaceRulesTest {

    @Test
    void seaCellIsStillWaterAtSeaLevelMinusOneWithAirAbove() {
        assertTrue(SurfaceRules.isSeaCell(62, 63, true, true));
        assertFalse(SurfaceRules.isSeaCell(61, 63, true, true), "one below: not the surface block");
        assertFalse(SurfaceRules.isSeaCell(63, 63, true, true), "the sea level itself is air above the sea");
        assertFalse(SurfaceRules.isSeaCell(62, 63, false, true), "flowing water or no water");
        assertFalse(SurfaceRules.isSeaCell(62, 63, true, false), "a lily pad or a block on top");
        assertEquals(62, SurfaceRules.surfaceBlockY(63));
    }

    @Test
    void stillTopIsVanillasSourceTop() {
        assertEquals(62 + 8.0 / 9.0 - 0.001, SurfaceRules.stillTop(63), 1e-12);
    }

    @Test
    void depthFadeRisesFromOneBlockToFullDepth() {
        assertEquals(0.0, SurfaceRules.depthFade(0, 4));
        assertEquals(0.0, SurfaceRules.depthFade(1, 4), "one block of water: flat");
        assertEquals(1.0 / 3.0, SurfaceRules.depthFade(2, 4), 1e-12);
        assertEquals(2.0 / 3.0, SurfaceRules.depthFade(3, 4), 1e-12);
        assertEquals(1.0, SurfaceRules.depthFade(4, 4), "full from surface_full_depth");
        assertEquals(1.0, SurfaceRules.depthFade(16, 4));
        assertEquals(0.5, SurfaceRules.depthFade(2, 3), 1e-12);
        // clamped full depth
        assertEquals(1.0, SurfaceRules.depthFade(2, 0));
        assertEquals(SurfaceRules.depthFade(9, 16), SurfaceRules.depthFade(9, 99));
        double last = -1;
        for (int d = 0; d <= 16; d++) {
            double f = SurfaceRules.depthFade(d, 6);
            assertTrue(f >= last && f >= 0 && f <= 1, "monotone in [0, 1]");
            last = f;
        }
    }

    @Test
    void cellAndVertexFade() {
        assertEquals(0.0, SurfaceRules.cellFade(0, 4), "a cell with land lies flat");
        assertEquals(1.0, SurfaceRules.cellFade(7, 4));
        assertEquals(0.25, SurfaceRules.vertexFade(1.0, 0.25, 0.5, 1.0));
        assertEquals(0.0, SurfaceRules.vertexFade(1.0, 1.0, 1.0, 0.0), "a vertex touching land does not move");
    }

    @Test
    void replacingRenderersTurnTheSurfaceOff() {
        for (String id : SurfaceSupport.REPLACING_RENDERERS) {
            assertEquals(Optional.of(id), SurfaceSupport.blockingMod(Set.of("sable", id)::contains), id);
        }
        assertEquals(Set.of("sodium", "embeddium", "iris", "oculus", "vulkanmod"), Set.copyOf(SurfaceSupport.REPLACING_RENDERERS));
        assertEquals(Optional.empty(), SurfaceSupport.blockingMod(Set.of("sable", "geckolib", "jei")::contains));
        assertEquals(Optional.of("sodium"), SurfaceSupport.blockingMod(Set.of("iris", "sodium")::contains), "first in list order");

        assertTrue(SurfaceSupport.active(true, Optional.empty(), true));
        assertFalse(SurfaceSupport.active(false, Optional.empty(), true), "config off");
        assertFalse(SurfaceSupport.active(true, Optional.of("embeddium"), true), "fallback");
        assertFalse(SurfaceSupport.active(true, Optional.empty(), false), "no sea");
    }

    @Test
    void onlyOpenSkiesHaveASea() {
        assertTrue(SurfaceSupport.dimensionHasSea(true, false), "overworld");
        assertFalse(SurfaceSupport.dimensionHasSea(false, true), "nether");
        assertFalse(SurfaceSupport.dimensionHasSea(false, false), "end");
        assertFalse(SurfaceSupport.dimensionHasSea(true, true), "a roofed sky dimension");
    }
}

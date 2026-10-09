package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ALM10: the 3D spotlights' turn and fit ({@link Spotlight3dView}). */
class Spotlight3dViewTest {

    private static final float EPS = 1e-3f;

    @Test
    void turnsTwentyDegreesASecondSmoothlyWithThePartialTick() {
        assertEquals(0f, Spotlight3dView.angle(0, 0f), EPS);
        assertEquals(1f, Spotlight3dView.angle(1, 0f), EPS, "one degree a tick");
        assertEquals(20f, Spotlight3dView.angle(20, 0f), EPS, "twenty a second");
        assertEquals(10.5f, Spotlight3dView.angle(10, 0.5f), EPS, "the partial tick moves it between ticks");
        assertEquals(5f, Spotlight3dView.angle(365, 0f), EPS, "wraps at a full turn");
        float before = Spotlight3dView.angle(359, 0.99f);
        assertTrue(before > 359f && before < 360f);
        assertTrue(Spotlight3dView.angle(1_000_000_000L, 0.5f) < 360f, "a long-open book stays in range");
    }

    @Test
    void aCubeFitsItsSweptCircleAcrossAndItsLeanAlong() {
        Spotlight3dView.Bounds cube = new Spotlight3dView.Bounds(0f, 0f, 0f, 1f, 1f, 1f);
        assertEquals(0.5f, cube.centerX(), EPS);
        assertEquals((float) Math.sqrt(0.5), cube.turnRadius(), EPS, "half the diagonal");
        float s = Spotlight3dView.fitScale(cube, 80, 70);
        double t = Math.toRadians(Spotlight3dView.TILT_DEGREES);
        double across = 2 * Math.sqrt(0.5) * s;
        double along = (Math.cos(t) + 2 * Math.sqrt(0.5) * Math.sin(t)) * s;
        assertTrue(across <= 80 + EPS && along <= 70 + EPS, "inside the box: " + across + " x " + along);
        assertTrue(Math.abs(across - 80) < EPS || Math.abs(along - 70) < EPS, "touching one side");
    }

    @Test
    void aTallMobIsLimitedByItsHeightAndALongOneByItsLength() {
        // a crew member: 0.6 wide, 1.95 tall; the card inside its rim (88 x 76) less the margins
        int w = 88 - 2 * Spotlight3dView.MARGIN, h = 76 - 2 * Spotlight3dView.MARGIN;
        Spotlight3dView.Bounds man = new Spotlight3dView.Bounds(-0.3f, 0f, -0.3f, 0.3f, 1.95f, 0.3f);
        float sm = Spotlight3dView.fitScale(man, w, h);
        double t = Math.toRadians(Spotlight3dView.TILT_DEGREES);
        assertEquals(h, (1.95 * Math.cos(t) + 2 * man.turnRadius() * Math.sin(t)) * sm, 1e-2, "the height sets the scale");
        // a kraken-sized model, twelve blocks long: scaled far down, still inside
        Spotlight3dView.Bounds kraken = new Spotlight3dView.Bounds(-6f, -1f, -6f, 6f, 5f, 6f);
        float sk = Spotlight3dView.fitScale(kraken, w, h);
        assertTrue(sk < sm / 4, "scaled down: " + sk);
        assertTrue(2 * kraken.turnRadius() * sk <= w + EPS, "its swept circle fits across");
    }

    @Test
    void anEmptyModelOrBoxDrawsNothing() {
        assertEquals(0f, Spotlight3dView.fitScale(Spotlight3dView.Bounds.EMPTY, 80, 70));
        assertTrue(Spotlight3dView.Bounds.EMPTY.isEmpty());
        assertEquals(0f, Spotlight3dView.fitScale(new Spotlight3dView.Bounds(0f, 0f, 0f, 1f, 1f, 1f), 0, 70));
        // a flat item seen edge on still has a size: a sprite one pixel thick
        Spotlight3dView.Bounds sprite = new Spotlight3dView.Bounds(-0.5f, -0.5f, -0.03f, 0.5f, 0.5f, 0.03f);
        assertTrue(Spotlight3dView.fitScale(sprite, 80, 70) > 0f);
    }
}

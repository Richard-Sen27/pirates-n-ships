package com.richardsenger.piratesnships.sailing.effects;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The pure rules of a ship's wake (WAKE1): the waterline outline, the stem/stern/side frame, rates, spread, fades. */
class WakeRulesTest {

    private static final double EPS = 1e-9;
    private static final WakeRules R = WakeRules.DEFAULTS;

    // ---- WaterlineOutline ----

    private static boolean[][] grid(String... rows) {
        // rows are z, characters are x; '#' solid
        boolean[][] g = new boolean[rows[0].length()][rows.length];
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                g[x][z] = rows[z].charAt(x) == '#';
            }
        }
        return g;
    }

    private static Set<String> keys(List<int[]> cells) {
        Set<String> s = new HashSet<>();
        for (int[] c : cells) {
            s.add(c[0] + "," + c[1]);
        }
        return s;
    }

    @Test
    void solidBlockOutlineIsItsRim() {
        List<int[]> out = WaterlineOutline.outline(grid("###", "###", "###", "###"));
        assertEquals(10, out.size(), "3x4 block: all but the 2 inner cells");
        assertFalse(keys(out).contains("1,1"));
        assertFalse(keys(out).contains("1,2"));
    }

    @Test
    void hollowHullHasOnlyItsOuterSide() {
        // a closed hull shell around a dry hold: the cells facing the hold are not outline unless they face the sea too
        boolean[][] g = grid(
                ".###.",
                "##.##",
                "#...#",
                "#...#",
                "##.##",
                ".###.");
        Set<String> k = keys(WaterlineOutline.outline(g));
        assertTrue(k.contains("0,2") && k.contains("4,3") && k.contains("1,0") && k.contains("3,5"), "the shell's sea side");
        assertFalse(k.contains("1,1"), "a corner cell that faces only the hold and the shell");
        assertFalse(k.contains("3,4"), "a corner cell that faces only the hold and the shell");
        assertFalse(k.contains("2,1"), "the dry hold is not outline");
        assertEquals(18 - 4, k.size(), "all 18 shell cells but the four inner corners");
        // a thick shell: the inner layer faces only the hold and is excluded
        boolean[][] thick = grid(
                "#######",
                "#######",
                "##...##",
                "##...##",
                "#######",
                "#######");
        Set<String> t = keys(WaterlineOutline.outline(thick));
        assertFalse(t.contains("1,2"), "inner wall cell facing the hold");
        assertFalse(t.contains("2,1"), "inner wall cell facing the hold");
        assertTrue(t.contains("0,2"));
        assertTrue(t.contains("3,0"));
        assertEquals(2 * 7 + 2 * 4, t.size(), "the rim of 7x6");
    }

    @Test
    void notchOpenToTheSeaIsOutline() {
        Set<String> k = keys(WaterlineOutline.outline(grid(
                "#####",
                "##.##",
                "#####")));
        assertFalse(k.contains("2,1"), "open cell is never outline");
        // the cells around a closed pocket: 1,1 touches the pocket only
        assertFalse(k.contains("1,1"));
        Set<String> open = keys(WaterlineOutline.outline(grid(
                "##.##",
                "##.##",
                "#####")));
        assertTrue(open.contains("1,1"), "cell beside a notch the sea reaches");
    }

    @Test
    void emptyLayerHasNoOutline() {
        assertTrue(WaterlineOutline.outline(new boolean[0][0]).isEmpty());
        assertTrue(WaterlineOutline.outline(grid("...", "...")).isEmpty());
    }

    @Test
    void pickLayerPrefersTheWaterlineThenBelow() {
        assertEquals(5, WaterlineOutline.pickLayer(5, y -> true, 2, 2));
        assertEquals(4, WaterlineOutline.pickLayer(5, y -> y == 4 || y == 6, 2, 2), "down first at equal distance");
        assertEquals(6, WaterlineOutline.pickLayer(5, y -> y == 6, 2, 2));
        assertEquals(3, WaterlineOutline.pickLayer(5, y -> y <= 3, 2, 2));
        assertEquals(Integer.MIN_VALUE, WaterlineOutline.pickLayer(5, y -> y <= 2, 2, 2), "out of the water");
        assertEquals(Integer.MIN_VALUE, WaterlineOutline.pickLayer(5, y -> y == 7, 2, 1));
    }

    @Test
    void outlineIsDeterministic() {
        boolean[][] g = grid(".###.", "#####", "#####", ".###.");
        List<int[]> a = WaterlineOutline.outline(g), b = WaterlineOutline.outline(g);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i));
        }
    }

    // ---- WakeFrame ----

    /** Cell centres of the rim of a hull 3 wide (x 0..2) and 9 long (z 0..8), pointed at z = 0. */
    private static double[][] hull() {
        List<int[]> out = WaterlineOutline.outline(grid(
                ".#.",
                "###",
                "###",
                "###",
                "###",
                "###",
                "###",
                "###",
                "###"));
        double[] xs = new double[out.size()], zs = new double[out.size()];
        for (int i = 0; i < out.size(); i++) {
            xs[i] = 100.0 + out.get(i)[0] + 0.5;
            zs[i] = 200.0 + out.get(i)[1] + 0.5;
        }
        return new double[][] {xs, zs};
    }

    @Test
    void stemAndSternFollowTheCourse() {
        double[][] h = hull();
        WakeFrame north = WakeFrame.of(h[0], h[1], 0.0, -3.0);
        assertNotNull(north);
        assertArrayEquals(new double[] {0.0, -1.0}, north.course(), EPS);
        double[] stem = north.stem();
        assertEquals(101.5, stem[0], EPS, "stem on the centre line (the pointed bow)");
        assertEquals(200.5 - WakeFrame.OUTSET, stem[1], EPS, "just ahead of the foremost cell");
        assertEquals(3.0, north.sternWidth(), EPS);
        double[] sternMid = north.sternPoint(0.0);
        assertEquals(101.5, sternMid[0], EPS);
        assertEquals(208.5 + WakeFrame.OUTSET, sternMid[1], EPS, "just behind the rearmost row");
        // facing north, starboard is east (+x)
        assertEquals(103.0, north.sternPoint(1.0)[0], EPS);
        assertEquals(100.0, north.sternPoint(-1.0)[0], EPS);
        assertEquals(8.0 + 2 * WakeFrame.OUTSET, north.length(), EPS);

        // sailing astern: the stem is now the square end, the stern the pointed one
        WakeFrame south = WakeFrame.of(h[0], h[1], 0.0, 2.0);
        assertNotNull(south);
        assertEquals(208.5 + WakeFrame.OUTSET, south.stem()[1], EPS);
        assertEquals(200.5 - WakeFrame.OUTSET, south.sternPoint(0.0)[1], EPS);
        assertEquals(3.0, south.sternWidth(), EPS, "the pointed end and the row behind it within the band");
    }

    @Test
    void sidePointsLieOutsideOnTheirSide() {
        double[][] h = hull();
        WakeFrame f = WakeFrame.of(h[0], h[1], 0.0, -1.0);
        assertNotNull(f);
        assertTrue(f.sideCount() > 0);
        for (int i = 0; i < f.sideCount(); i++) {
            double[] p = f.side(i);
            if (f.sideSign(i) > 0) {
                assertEquals(102.5 + WakeFrame.OUTSET, p[0], EPS, "starboard side point, outside the planks");
            } else {
                assertEquals(100.5 - WakeFrame.OUTSET, p[0], EPS, "port side point, outside the planks");
            }
            assertTrue(p[1] > 200.5 + WakeFrame.STEM_BAND && p[1] < 208.5 - WakeFrame.STERN_BAND);
        }
    }

    @Test
    void sidewaysDriftShedsFromTheBeam() {
        double[][] h = hull();
        WakeFrame east = WakeFrame.of(h[0], h[1], 1.0, 0.0);
        assertNotNull(east);
        assertEquals(102.5 + WakeFrame.OUTSET, east.stem()[0], EPS, "the stem is the leading side");
        assertEquals(100.5 - WakeFrame.OUTSET, east.sternPoint(0.0)[0], EPS, "the trail leaves from the lee side");
        assertEquals(8.0 + 1.0, east.sternWidth(), 1.0, "as wide as the hull is long");
    }

    @Test
    void noFrameWithoutOutlineOrCourse() {
        assertNull(WakeFrame.of(new double[0], new double[0], 1.0, 0.0));
        double[][] h = hull();
        assertNull(WakeFrame.of(h[0], h[1], 0.0, 0.0));
    }

    @Test
    void frameIsDeterministic() {
        double[][] h = hull();
        WakeFrame a = WakeFrame.of(h[0], h[1], 0.3, -0.7), b = WakeFrame.of(h[0], h[1], 0.3, -0.7);
        assertNotNull(a);
        assertNotNull(b);
        assertArrayEquals(a.stem(), b.stem(), 0.0);
        assertArrayEquals(a.sternPoint(0.4), b.sternPoint(0.4), 0.0);
        assertEquals(a.sideCount(), b.sideCount());
    }

    // ---- WakeRules: rates and opacity by speed ----

    @Test
    void nothingBelowTheMinimumSpeed() {
        for (double v : new double[] {0.0, 0.5, 0.99, -3.0, Double.NaN}) {
            assertFalse(R.active(v), "speed " + v);
            assertEquals(0.0, R.sternRate(v, 5.0));
            assertEquals(0.0, R.bowRate(v));
            assertEquals(0.0, R.sideRate(v, 20.0));
            assertEquals(0.0, R.splashRate(v));
            assertEquals(0.0, R.opacity(v));
        }
        assertTrue(R.active(1.0));
        assertEquals(0.0, new WakeRules(64, 1, 8, 0.0, 160, 19.5).sternRate(5.0, 5.0), "density 0 switches it off");
    }

    @Test
    void ratesAndOpacityGrowWithSpeedUpToFull() {
        double prevStern = 0, prevBow = 0, prevAlpha = 0;
        for (double v = 1.0; v <= 8.0; v += 0.5) {
            double s = R.sternRate(v, 5.0), b = R.bowRate(v), a = R.opacity(v);
            assertTrue(s > prevStern && b > prevBow && a > prevAlpha, "rising at " + v);
            prevStern = s;
            prevBow = b;
            prevAlpha = a;
        }
        assertEquals(R.sternRate(8.0, 5.0), R.sternRate(20.0, 5.0), EPS, "full from wake_full_speed on");
        assertEquals(0.6 * 5.0, R.sternRate(8.0, 5.0), EPS, "density per block of stern width at full speed");
        assertEquals(0.6 * 5.0 * 0.3, R.sternRate(1.0, 5.0), EPS);
        assertEquals(0.2, R.opacity(1.0), EPS);
        assertEquals(0.65, R.opacity(8.0), EPS);
        assertEquals(0.65, R.opacity(30.0), EPS);
        assertEquals(R.sternRate(5.0, 1.0), R.sternRate(5.0, 0.2), EPS, "at least a block of stern");
    }

    @Test
    void waterlineFoamAndSplashOnlyWhenFast() {
        // 2 blocks/s: factor 1/7 < 0.35, 5 blocks/s: 4/7 > 0.5
        assertEquals(0.0, R.sideRate(2.0, 20.0));
        assertEquals(0.0, R.splashRate(2.0));
        assertTrue(R.sideRate(5.0, 20.0) > 0.0);
        assertTrue(R.splashRate(5.0) > 0.0);
        assertEquals(0.6 * WakeRules.SIDE_PER_BLOCK * 20.0, R.sideRate(8.0, 20.0), EPS);
        assertEquals(0.6 * WakeRules.SPLASH_PER_TICK, R.splashRate(8.0), EPS);
        assertEquals(0.0, R.sideRate(8.0, -4.0), "no negative length");
    }

    @Test
    void speedFactorIsLinearAndClamped() {
        assertEquals(0.0, R.speedFactor(0.5));
        assertEquals(0.0, R.speedFactor(1.0), EPS);
        assertEquals(0.5, R.speedFactor(4.5), EPS);
        assertEquals(1.0, R.speedFactor(8.0), EPS);
        assertEquals(1.0, R.speedFactor(80.0), EPS);
        WakeRules odd = new WakeRules(64, 5.0, 2.0, 1.0, 160, 19.5);
        assertTrue(odd.fullSpeed() > odd.minSpeed(), "full speed kept above the minimum");
    }

    // ---- the Kelvin spread and the drift ----

    @Test
    void bowWaveArmsLieAtTheKelvinAngle() {
        double cx = 0.6, cz = -0.8; // any unit course
        for (int side : new int[] {1, -1}) {
            double[] arm = R.armDirection(cx, cz, side);
            assertEquals(1.0, Math.hypot(arm[0], arm[1]), EPS);
            // the angle between the arm and straight astern
            double cos = -(arm[0] * cx + arm[1] * cz);
            assertEquals(19.5, Math.toDegrees(Math.acos(cos)), 1e-6);
            double[] stb = WakeRules.starboard(cx, cz);
            assertEquals(side, Math.signum(arm[0] * stb[0] + arm[1] * stb[1]), "arm on its own side");
        }
    }

    @Test
    void aWorldFixedBowQuadTracesTheArmSeenFromTheShip() {
        double speed = 6.0, cx = 0.0, cz = -1.0;
        double lateral = R.bowLateralPerTick(speed);
        assertEquals(speed * Math.tan(Math.toRadians(19.5)) / 20.0, lateral, EPS);
        double[] stb = WakeRules.starboard(cx, cz);
        int ticks = 60;
        // the quad drifts out in the world; the ship moves on at speed
        double qx = WakeRules.displacement(lateral, WakeRules.BOW_FRICTION, ticks) * stb[0];
        double qz = WakeRules.displacement(lateral, WakeRules.BOW_FRICTION, ticks) * stb[1];
        double shipX = cx * speed / 20.0 * ticks, shipZ = cz * speed / 20.0 * ticks;
        double rx = qx - shipX, rz = qz - shipZ; // quad seen from the ship
        double[] arm = R.armDirection(cx, cz, 1);
        double cross = rx * arm[1] - rz * arm[0];
        assertEquals(0.0, cross, 1e-9, "on the starboard arm");
        assertTrue(rx * arm[0] + rz * arm[1] > 0, "behind the stem, not ahead");
    }

    @Test
    void sternTrailOpensOutwardAndSettles() {
        double v = 8.0;
        assertTrue(R.sternLateralPerTick(v, 1.0) > 0.0, "starboard edge drifts to starboard");
        assertTrue(R.sternLateralPerTick(v, -1.0) < 0.0, "port edge drifts to port");
        assertEquals(0.0, R.sternLateralPerTick(v, 0.0), EPS, "the centre stays");
        assertEquals(R.sternLateralPerTick(v, 1.0), R.sternLateralPerTick(v, 5.0), EPS, "clamped to the edge");
        assertTrue(R.sternLateralPerTick(v, 1.0) < R.bowLateralPerTick(v), "slower than the bow wave");
        double edge = R.sternLateralPerTick(v, 1.0);
        double total = WakeRules.displacement(edge, WakeRules.STERN_FRICTION, R.lifeTicks());
        assertTrue(total > 1.0 && total < 4.0, "the trail widens by a few blocks per side over its life: " + total);
        assertTrue(total < edge / (1.0 - WakeRules.STERN_FRICTION), "bounded");
    }

    @Test
    void displacementMatchesTheParticleTickLoop() {
        for (double f : new double[] {1.0, 0.985, 0.9}) {
            double v = 0.05, x = 0.0;
            for (int t = 1; t <= 120; t++) {
                x += v;  // Particle#tick: move ...
                v *= f;  // ... then keep friction of the velocity
                assertEquals(x, WakeRules.displacement(0.05, f, t), 1e-12, "f " + f + " t " + t);
            }
        }
        assertEquals(0.0, WakeRules.displacement(1.0, 0.9, 0));
    }

    // ---- fades ----

    @Test
    void wakeFadesInAtOnceAndOutSlowly() {
        double life = 160;
        assertEquals(0.0, WakeRules.fade(0, life), EPS);
        assertEquals(1.0, WakeRules.fade(WakeRules.FADE_IN_TICKS, life), EPS, "full after the fade-in");
        assertTrue(WakeRules.fade(1, life) > 0.0);
        double prev = 1.0;
        for (double a = WakeRules.FADE_IN_TICKS; a <= life; a += 1.0) {
            double f = WakeRules.fade(a, life);
            assertTrue(f <= prev + EPS, "never brighter later, age " + a);
            assertTrue(f >= 0.0 && f <= 1.0);
            prev = f;
        }
        assertEquals(0.0, WakeRules.fade(life, life), EPS);
        assertEquals(0.0, WakeRules.fade(life + 1, life));
        assertEquals(0.0, WakeRules.fade(-1, life));
        assertTrue(WakeRules.fade(life * 0.5, life) > 0.5, "still bright mid-life");
    }

    // ---- range and lives ----

    @Test
    void rangeAndLives() {
        assertTrue(R.withinRadius(30, 40));
        assertFalse(R.withinRadius(50, 40.1));
        assertTrue(R.inRange(70, 62.9));
        assertFalse(R.inRange(140, 62.9));
        assertFalse(R.inRange(40, 62.9), "deep below the sea");
        assertEquals(120, R.bowLifeTicks());
        assertTrue(R.sternLength(8.0) > R.sternLength(1.0));
    }
}

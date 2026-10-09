package com.richardsenger.piratesnships.hazards.waves.boats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.WaveSpectrum;
import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceRules;
import org.junit.jupiter.api.Test;

class BoatWaveRuleTest {

    private static final double STILL_TOP = 62.0 + SurfaceRules.STILL_HEIGHT;
    /** Vanilla {@code Boat}: bounding box height, gravity, buoyancy factor {@code g / 0.65}, damping. */
    private static final double BOAT_HEIGHT = 0.5625, GRAVITY = 0.04, LIFT = 0.04 / 0.65, DAMP = 0.75;

    private static WaveField storm() {
        double a = SeaState.STORM.amplitude();
        return new WaveField(a, 90.0, WaveSpectrum.components(WaveSpectrum.DEFAULT_COMPONENTS, WaveSpectrum.peakWavelength(a)),
                WaveField.Origin.NONE, new WaveField.Groups(0.35 * WaveSpectrum.groupShare(a), 1200.0));
    }

    // ------------------------------------------------------------------ the open-sea rule

    @Test
    void fadeIsTheDrawnSeasDepthFade() {
        assertEquals(0.0, BoatWaveRule.fade(0), "not a sea cell");
        assertEquals(0.0, BoatWaveRule.fade(1), "one block deep: the beach");
        assertEquals(1.0, BoatWaveRule.fade(BoatWaveRule.FULL_DEPTH));
        assertEquals(1.0, BoatWaveRule.fade(40));
        for (int d = 0; d <= 16; d++) {
            assertEquals(SurfaceRules.cellFade(d, BoatWaveRule.FULL_DEPTH), BoatWaveRule.fade(d), "depth " + d);
        }
        assertEquals(0.4, BoatWaveRule.fade(3), 1e-12);
    }

    @Test
    void appliesOnlyOnTheOpenMovingSea() {
        WaveField storm = storm();
        assertTrue(BoatWaveRule.applies(true, storm, 8, false, false));
        assertFalse(BoatWaveRule.applies(false, storm, 8, false, false), "waves.boats off");
        assertFalse(BoatWaveRule.applies(true, WaveField.FLAT, 8, false, false), "flat sea");
        assertFalse(BoatWaveRule.applies(true, storm, 0, false, false), "not a sea cell: a lake, a river, under a block");
        assertFalse(BoatWaveRule.applies(true, storm, 1, false, false), "a one-block-deep pool or the beach");
        assertFalse(BoatWaveRule.applies(true, storm, 8, true, false), "inside a dry hull");
        assertFalse(BoatWaveRule.applies(true, storm, 8, false, true), "resting on a deck or a pier");
    }

    @Test
    void bandReachesOneBlockBeyondTheHighestCrest() {
        WaveField storm = storm();
        double reach = storm.maxHeight() + BoatWaveRule.BAND;
        assertTrue(BoatWaveRule.inBand(STILL_TOP, STILL_TOP, storm));
        assertTrue(BoatWaveRule.inBand(STILL_TOP + reach - 1e-6, STILL_TOP, storm));
        assertTrue(BoatWaveRule.inBand(STILL_TOP - reach + 1e-6, STILL_TOP, storm));
        assertFalse(BoatWaveRule.inBand(STILL_TOP + reach + 0.01, STILL_TOP, storm), "a boat on a high deck");
        assertFalse(BoatWaveRule.inBand(STILL_TOP - reach - 0.01, STILL_TOP, storm), "a sunken boat");
    }

    @Test
    void offsetIsTheFieldsPointLookupTimesTheFade() {
        WaveField storm = storm();
        double x = 1234.5, z = -876.25, t = 98765;
        double h = storm.heightAt(x, z, t);
        assertNotEquals(0.0, h);
        assertEquals(h, BoatWaveRule.offset(storm::heightAt, x, z, t, 1.0), 1e-12);
        assertEquals(0.4 * h, BoatWaveRule.offset(storm::heightAt, x, z, t, 0.4), 1e-12);
        assertEquals(0.0, BoatWaveRule.offset(storm::heightAt, x, z, t, 0.0));
        assertEquals(0.0, BoatWaveRule.offset(WaveField.FLAT::heightAt, x, z, t, 1.0));
        assertEquals(BoatWaveRule.offset(storm::heightAt, x, z, t + 1, 1.0) - h, BoatWaveRule.speed(storm::heightAt, x, z, t, 1.0), 1e-12);
    }

    @Test
    void slopeIsTheFadedGradientOfThePointLookup() {
        WaveField storm = storm();
        double x = -300.5, z = 77.0, t = 4321, e = 1e-4;
        double[] s = BoatWaveRule.slope(storm::slopeAt, x, z, t, 0.5);
        double dx = (storm.heightAt(x + e, z, t) - storm.heightAt(x - e, z, t)) / (2 * e);
        double dz = (storm.heightAt(x, z + e, t) - storm.heightAt(x, z - e, t)) / (2 * e);
        assertEquals(0.5 * dx, s[0], 1e-6);
        assertEquals(0.5 * dz, s[1], 1e-6);
    }

    @Test
    void tiltNormalIsLimitedAndUnit() {
        double[] flat = BoatWaveRule.tiltNormal(0.0, 0.0, 8.0);
        assertEquals(1.0, flat[1], 1e-12);
        double[] gentle = BoatWaveRule.tiltNormal(0.05, 0.0, 8.0); // 2.9°: under the limit, the true normal
        assertEquals(Math.toDegrees(Math.atan(0.05)), Math.toDegrees(Math.acos(gentle[1])), 1e-9);
        assertTrue(gentle[0] < 0.0, "the normal leans away from the rising side");
        double[] steep = BoatWaveRule.tiltNormal(0.3, -0.3, 8.0);
        assertEquals(8.0, Math.toDegrees(Math.acos(steep[1])), 1e-9);
        assertEquals(1.0, steep[0] * steep[0] + steep[1] * steep[1] + steep[2] * steep[2], 1e-12);
        assertEquals(1.0, BoatWaveRule.tiltNormal(0.3, 0.2, 0.0)[1], 1e-12, "0 degrees: no tilt");
    }

    // ------------------------------------------------------------------ vanilla's float loop on the moving sea

    /** Result of {@link #ride}: the boat's y half range, the share of ticks out of the water or awash. */
    private record Ride(double halfRange, double surfaceHalfRange, int outOfWater, int awash, double minLag, double maxLag) {
    }

    /**
     * Vanilla's {@code Boat#floatBoat} vertical loop for a boat at rest at {@code (x, z)} on the moving level, with the
     * status rule of {@code MixinBoat} (in water while the bottom is below the level) and optionally the carry.
     */
    private static Ride ride(WaveField field, double x, double z, boolean carry, int ticks) {
        double y = STILL_TOP - 0.37, v = 0.0;
        boolean wasAir = false;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE, minS = Double.MAX_VALUE, maxS = -Double.MAX_VALUE;
        double minLag = Double.MAX_VALUE, maxLag = -Double.MAX_VALUE;
        int out = 0, awash = 0, warm = 200;
        for (int t = 0; t < ticks + warm; t++) {
            double level = STILL_TOP + BoatWaveRule.offset(field::heightAt, x, z, t, 1.0);
            boolean inWater = y < level;
            if (inWater && wasAir) {
                y = level - BOAT_HEIGHT + 0.101; // vanilla's drop-in snap through getWaterLevelAbove
                v = 0.0;
            } else {
                v -= GRAVITY;
                if (inWater) {
                    double d1 = (level - y) / BOAT_HEIGHT;
                    v = (v + d1 * LIFT) * DAMP;
                    if (carry) {
                        v = BoatWaveRule.carry(v, BoatWaveRule.speed(field::heightAt, x, z, t, 1.0));
                    }
                }
            }
            wasAir = !inWater;
            y += v;
            if (t >= warm) {
                double next = STILL_TOP + BoatWaveRule.offset(field::heightAt, x, z, t + 1, 1.0);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minS = Math.min(minS, next);
                maxS = Math.max(maxS, next);
                minLag = Math.min(minLag, next - y);
                maxLag = Math.max(maxLag, next - y);
                if (y >= next) {
                    out++;
                }
                if (y + BOAT_HEIGHT < next) {
                    awash++;
                }
            }
        }
        return new Ride((maxY - minY) / 2.0, (maxS - minS) / 2.0, out, awash, minLag, maxLag);
    }

    @Test
    void withTheCarryABoatRidesAStormAndNeverLeavesOrSinksBelowTheSurface() {
        WaveField storm = storm();
        for (double[] p : new double[][] {{0.5, 0.5}, {1000.5, -2000.5}, {-31234.5, 4567.5}}) {
            Ride r = ride(storm, p[0], p[1], true, 1200);
            String where = "at " + p[0] + ", " + p[1] + ": " + r;
            assertEquals(0, r.outOfWater(), "the boat left the water " + where);
            assertEquals(0, r.awash(), "the boat went under " + where);
            assertTrue(r.halfRange() > 0.9 * r.surfaceHalfRange() && r.halfRange() < 1.1 * r.surfaceHalfRange(),
                    "the boat should rise and fall with the surface " + where);
            double ratio = r.halfRange() / SeaState.STORM.amplitude();
            assertTrue(ratio > 0.4 && ratio < 1.35, "half range " + ratio + " x the amplitude " + where);
        }
    }

    /**
     * Without the carry vanilla's damping makes the boat lag the surface: at the default storm its depth below the
     * level already swings by about a quarter block around vanilla's 0.37 (awash at 0.5625), and at
     * {@code waves.amplitude} 2 it goes under on the crests; with the carry it keeps about vanilla's resting depth.
     */
    @Test
    void theCarryKeepsTheBoatAtItsRestingDepth() {
        WaveField storm = storm();
        Ride vanilla = ride(storm, 0.5, 0.5, false, 1200);
        Ride carried = ride(storm, 0.5, 0.5, true, 1200);
        assertTrue(carried.maxLag() - carried.minLag() < 0.6 * (vanilla.maxLag() - vanilla.minLag()),
                "the carry should narrow the lag: " + carried + " vs " + vanilla);
        double a = 2.0 * SeaState.STORM.amplitude();
        WaveField big = new WaveField(a, 90.0, storm.components(), WaveField.Origin.NONE, storm.groups());
        Ride bigVanilla = ride(big, 0.5, 0.5, false, 1200);
        Ride bigCarried = ride(big, 0.5, 0.5, true, 1200);
        assertTrue(bigVanilla.awash() > 0, "expected vanilla's lag to put the boat under in a doubled storm: " + bigVanilla);
        assertEquals(0, bigCarried.awash(), "the carried boat went under in a doubled storm: " + bigCarried);
        assertEquals(0, bigCarried.outOfWater(), "the carried boat left the water in a doubled storm: " + bigCarried);
    }

    @Test
    void flatSeaKeepsVanillasRestingDepth() {
        Ride r = ride(WaveField.FLAT, 0.5, 0.5, true, 400);
        assertTrue(r.halfRange() < 1e-3, "a boat on a flat sea moved: " + r);
        assertTrue(r.minLag() > 0.3 && r.maxLag() < 0.45, "vanilla floats a boat about 0.37 below the water level: " + r);
    }

    // ------------------------------------------------------------------ items

    @Test
    void itemsAreCapturedNearTheSurfaceOnly() {
        assertTrue(BoatWaveRule.itemCaptured(63.0, 63.0));
        assertTrue(BoatWaveRule.itemCaptured(63.05, 63.0));
        assertFalse(BoatWaveRule.itemCaptured(63.5, 63.0), "still falling in");
        assertFalse(BoatWaveRule.itemCaptured(61.5, 63.0), "sunk: vanilla lets it rise");
    }

    @Test
    void anItemFollowsTheStormSurface() {
        WaveField storm = storm();
        double x = 40.5, z = -12.5, y = STILL_TOP - BoatWaveRule.ITEM_DRAFT;
        double worst = 0.0;
        for (int t = 0; t < 1400; t++) {
            double surface = STILL_TOP + BoatWaveRule.offset(storm::heightAt, x, z, t, 1.0);
            assertTrue(BoatWaveRule.itemCaptured(y, surface), "the item lost the surface at tick " + t);
            y += BoatWaveRule.itemVelocity(y, surface, BoatWaveRule.speed(storm::heightAt, x, z, t, 1.0));
            if (t > 100) {
                double next = STILL_TOP + BoatWaveRule.offset(storm::heightAt, x, z, t + 1, 1.0);
                worst = Math.max(worst, Math.abs(next - BoatWaveRule.ITEM_DRAFT - y));
            }
        }
        assertTrue(worst < 0.05, "the item strayed " + worst + " from its float level");
        assertEquals(BoatWaveRule.ITEM_MAX_PULL, BoatWaveRule.itemVelocity(50.0, 63.0, 0.0), 1e-12, "the pull is capped");
    }
}

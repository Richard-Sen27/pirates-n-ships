package com.richardsenger.piratesnships.sailing.effects;

import org.jetbrains.annotations.Nullable;

/**
 * Where a moving hull sheds its wake this tick (WAKE1, docs/design.md §5.4 "Wake"): from the world positions of its
 * waterline outline ({@link WaterlineOutline}, cell centres) and its course, the stem (the foremost point along the
 * course), the stern line (the rearmost row of the outline, with its width) and the side points (the rest), each
 * pushed half a block outward so the foam lies on the water beside the planks, not under them. The course is the
 * velocity's direction, not the hull's heading, so a ship drifting sideways sheds its trail from the lee side. Pure.
 */
public final class WakeFrame {

    /** Outline cells within this distance of the foremost one along the course count as the stem [blocks]. */
    public static final double STEM_BAND = 0.75;
    /** Outline cells within this distance of the rearmost one along the course count as the stern [blocks]. */
    public static final double STERN_BAND = 1.0;
    /** How far the stem, stern and sides lie outside the outline's cell centres [blocks]. */
    public static final double OUTSET = 0.6;

    private final double cx, cz, px, pz;
    private final double stemAlong, stemLat;
    private final double sternAlong, sternLatMin, sternLatMax;
    private final double[] sideX, sideZ;
    private final int[] sideSign;

    private WakeFrame(double cx, double cz, double stemAlong, double stemLat, double sternAlong, double sternLatMin,
                      double sternLatMax, double[] sideX, double[] sideZ, int[] sideSign) {
        this.cx = cx;
        this.cz = cz;
        this.px = -cz;
        this.pz = cx;
        this.stemAlong = stemAlong;
        this.stemLat = stemLat;
        this.sternAlong = sternAlong;
        this.sternLatMin = sternLatMin;
        this.sternLatMax = sternLatMax;
        this.sideX = sideX;
        this.sideZ = sideZ;
        this.sideSign = sideSign;
    }

    /**
     * The frame of an outline at world positions {@code (xs[i], zs[i])} on the course {@code (courseX, courseZ)} (need
     * not be normalised), or null when the outline is empty or the course has no length.
     */
    public static @Nullable WakeFrame of(double[] xs, double[] zs, double courseX, double courseZ) {
        int n = Math.min(xs.length, zs.length);
        double len = Math.sqrt(courseX * courseX + courseZ * courseZ);
        if (n == 0 || !(len > 1e-9)) {
            return null;
        }
        double cx = courseX / len, cz = courseZ / len;
        double px = -cz, pz = cx;
        double[] along = new double[n], lat = new double[n];
        double maxA = Double.NEGATIVE_INFINITY, minA = Double.POSITIVE_INFINITY, latSum = 0.0;
        for (int i = 0; i < n; i++) {
            along[i] = xs[i] * cx + zs[i] * cz;
            lat[i] = xs[i] * px + zs[i] * pz;
            maxA = Math.max(maxA, along[i]);
            minA = Math.min(minA, along[i]);
            latSum += lat[i];
        }
        double midLat = latSum / n;
        double stemLatSum = 0.0;
        int stemN = 0;
        double sternMin = Double.POSITIVE_INFINITY, sternMax = Double.NEGATIVE_INFINITY;
        int sides = 0;
        for (int i = 0; i < n; i++) {
            boolean stem = along[i] >= maxA - STEM_BAND;
            boolean stern = along[i] <= minA + STERN_BAND;
            if (stem) {
                stemLatSum += lat[i];
                stemN++;
            }
            if (stern) {
                sternMin = Math.min(sternMin, lat[i]);
                sternMax = Math.max(sternMax, lat[i]);
            }
            if (!stem && !stern) {
                sides++;
            }
        }
        double[] sx = new double[sides], sz = new double[sides];
        int[] sign = new int[sides];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (along[i] >= maxA - STEM_BAND || along[i] <= minA + STERN_BAND) {
                continue;
            }
            int s = lat[i] >= midLat ? 1 : -1;
            sx[k] = xs[i] + px * s * OUTSET;
            sz[k] = zs[i] + pz * s * OUTSET;
            sign[k] = s;
            k++;
        }
        return new WakeFrame(cx, cz, maxA + OUTSET, stemLatSum / stemN, minA - OUTSET, sternMin, sternMax, sx, sz, sign);
    }

    /** Unit course {@code {x, z}}. */
    public double[] course() {
        return new double[] {cx, cz};
    }

    /** World {@code {x, z}} of the stem, just ahead of the foremost outline cells. */
    public double[] stem() {
        return world(stemAlong, stemLat);
    }

    /** Width of the stern [blocks]: the spread of its outline cells across the course plus one block. */
    public double sternWidth() {
        return sternLatMax - sternLatMin + 1.0;
    }

    /**
     * World {@code {x, z}} of a point on the stern line, just behind the rearmost cells, at {@code lateral} in
     * [−1, 1] (−1 the port edge, +1 the starboard edge).
     */
    public double[] sternPoint(double lateral) {
        double mid = (sternLatMin + sternLatMax) * 0.5;
        double half = sternWidth() * 0.5;
        return world(sternAlong, mid + Math.max(-1.0, Math.min(1.0, lateral)) * half);
    }

    /** Number of side points: roughly the length of the waterline along both sides [blocks]. */
    public int sideCount() {
        return sideX.length;
    }

    /** World {@code {x, z}} of side point {@code i}, half a block outside the planks. */
    public double[] side(int i) {
        return new double[] {sideX[i], sideZ[i]};
    }

    /** +1 when side point {@code i} is on the starboard side, −1 on the port side. */
    public int sideSign(int i) {
        return sideSign[i];
    }

    /** Length of the waterline from stern to stem along the course [blocks]. */
    public double length() {
        return stemAlong - sternAlong;
    }

    private double[] world(double along, double lat) {
        return new double[] {cx * along + px * lat, cz * along + pz * lat};
    }
}

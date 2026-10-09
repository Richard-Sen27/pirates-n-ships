package com.richardsenger.piratesnships.sailing.ballast;

import java.util.Arrays;

/**
 * The shipwright's ballast (docs/design.md §5.2 "Trim by the stern", TRM1). Pure. A ship floats level by design: this
 * rule computes the constant ship-frame torque that cancels the static trim and heel moment of the ship as built, so
 * only what was not built in (cargo, flood water, damage, crew, waves) trims it relative to level.
 *
 * <h2>What Sable floats (refs/sable, read for this rule)</h2>
 * <ul>
 *   <li>Every ship voxel with a collider overlapping a <b>world</b> liquid voxel (whole cubes; partial fluid heights are
 *       ignored) gets the upward world force {@code 10.5 × overlapVolume × sable:volume} at the voxel's <b>cube
 *       centre</b> ({@code sable_rapier/src/main/rust/rapier/src/buoyancy.rs}, {@code compute_buoyancy} l.75-139 and
 *       {@code do_float} l.166-189; the overlap is the axis-aligned unit box around the transformed centre).
 *       {@code sable:volume} per state: {@code PhysicsBlockPropertyHelper#getVolume} l.70, baked into the collider by
 *       {@code RapierVoxelColliderBakery#buildPhysicsDataForBlock} l.59; states with an empty collision shape get no
 *       collider data and float nothing.</li>
 *   <li>Mass per state: {@code PhysicsBlockPropertyHelper#getMass} l.24 (0 when the state is not solid), at the
 *       state's own centre of mass ({@code MassTracker.BLOCK_CENTER_OF_MASS} l.33, a slab's is its half).</li>
 *   <li>Gravity comes from the dimension ({@code DimensionPhysicsData#getGravity}, 11 by default); the float constant
 *       does not scale with it.</li>
 *   <li>On top, our dry hull lift ({@code ship.hull.runtime.HullBuoyancy}): {@code 10.5 × dry_buoyancy_scale} per
 *       submerged block of dry compartment volume, against the fluid surface the hull runtime measures, which is
 *       {@link #DRY_SURFACE_DROP} below the whole-cube surface Sable floats on (a source block's height is 8/9).</li>
 * </ul>
 *
 * <h2>The rule (ship / plot frame, level pose: plot up = world up)</h2>
 * <pre>
 *   W        = Σ m_i · g                                         (blocks; containers at their empty load state)
 *   f_i(y_w) = clamp(y_w − (y_i − 0.5), 0, 1)                    (y_i = cube centre; dry cells use y_w − DRY_SURFACE_DROP)
 *   B(y_w)   = Σ k_i · f_i(y_w),  k_i = 10.5 · volume_i  (block)  or  10.5 · dryScale  (dry cell)
 *   solve B(y_w) = W for the waterline y_w by bisection          (B is continuous, piecewise linear, non-decreasing)
 *   CoB      = Σ k_i f_i p_i / B,     CoM = Σ m_i c_i / Σ m_i
 *   r        = CoB − CoM
 *   M_static = r × (0, B, 0) = (−r_z · B, 0, r_x · B)             (the moment that trims and heels the ship)
 *   τ        = −M_static = (r_z · B, 0, −r_x · B),  |τ| ≤ maxPerMass · Σ m_i · g
 * </pre>
 * A pure couple, so it is the same about any point; it has no yaw part. When the ship cannot float ({@code W} above
 * the buoyancy of everything submerged) the waterline sits at the top and {@code B} is that maximum. Off, or for a
 * ship without mass, the torque is zero. The cap keeps an absurd build listing: it limits the horizontal offset the
 * ballast can make good to {@code maxPerMass} blocks.
 */
public final class BallastRule {

    /** Sable's float force per block of displaced liquid (rust {@code buoyancy.rs#do_float}, l.189). */
    public static final double SABLE_FLOAT_FORCE = 10.5;
    /**
     * How far below Sable's whole-cube water surface the hull runtime's sea surface lies [blocks]: it reads the fluid
     * height of the top water block ({@code FluidState#getHeight}), 8/9 for a still source block.
     */
    public static final double DRY_SURFACE_DROP = 1.0 / 9.0;

    private static final int BISECTION_STEPS = 80;

    /**
     * @param enabled    {@code stability.auto_ballast}
     * @param maxPerMass {@code stability.auto_ballast_max_per_mass}: the torque cap per unit of weight [blocks]
     */
    public record Params(boolean enabled, double maxPerMass) {
        /**
         * The defaults; the server config declares its defaults from this instance. The cap of 3 blocks (TRM1,
         * measured on the 7×17 test hull, {@code BallastGameTests}): a three-storey stern castle across the beam over
         * the last three rows (80 kpg on 181) needs 2.08 blocks and is not capped; a one-block-wide tower of twenty
         * iron blocks on a boom six blocks aft needs 4.31 and is. Since the cap is a distance, longer ships make good
         * proportionally less: a hull twice as long with the same castle would need about 4.
         */
        public static final Params DEFAULTS = new Params(true, 3.0);

        public Params {
            maxPerMass = Math.max(0.0, maxPerMass);
        }
    }

    /**
     * The result in the ship (plot) frame, positions relative to the {@link Hull}'s reference point.
     *
     * @param mass      Σ m_i [kpg]
     * @param buoyancy  B at the waterline [kpg·blocks/s²] (= W unless the ship cannot float)
     * @param waterline y_w, the whole-cube water surface at the level pose
     * @param cob       centre of buoyancy {x, y, z} at y_w (zeros without buoyancy)
     * @param com       centre of mass {x, y, z}
     * @param torqueX   ballast torque about plot x [kpg·blocks²/s²]
     * @param torqueZ   ballast torque about plot z
     * @param capped    whether the cap cut the torque
     */
    public record Result(double mass, double buoyancy, double waterline, double[] cob, double[] com,
                         double torqueX, double torqueZ, boolean capped) {
        public static final Result NONE = new Result(0, 0, 0, new double[3], new double[3], 0, 0, false);

        /** The static moment of buoyancy about the centre of mass, x component (= −torqueX before the cap). */
        public double staticMomentX() {
            return -(cob[2] - com[2]) * buoyancy;
        }

        /** The static moment of buoyancy about the centre of mass, z component. */
        public double staticMomentZ() {
            return (cob[0] - com[0]) * buoyancy;
        }

        public double torqueMagnitude() {
            return Math.hypot(torqueX, torqueZ);
        }

        /** Horizontal distance between the centre of buoyancy and the centre of mass [blocks]. */
        public double offset() {
            return Math.hypot(cob[0] - com[0], cob[2] - com[2]);
        }

        @Override
        public String toString() {
            return String.format("mass %.2f, B %.1f, waterline %.3f, cob (%.3f, %.3f, %.3f), com (%.3f, %.3f, %.3f), torque (%.1f, 0, %.1f)%s",
                    mass, buoyancy, waterline, cob[0], cob[1], cob[2], com[0], com[1], com[2], torqueX, torqueZ, capped ? " capped" : "");
        }
    }

    /**
     * The ship as built: its blocks and dry cells in plot coordinates relative to a reference point (keep the numbers
     * small, plot coordinates are in the millions). Not thread-safe.
     */
    public static final class Hull {
        private double[] y = new double[64];
        private double[] x = new double[64];
        private double[] z = new double[64];
        private double[] k = new double[64];
        private double[] drop = new double[64];
        private int n;
        private double mass, mx, my, mz;

        /**
         * A block: cube centre {@code (cx, cy, cz)}, its mass {@code m} at {@code (mcx, mcy, mcz)} and its Sable volume.
         * Blocks without a collider pass {@code volume} 0 and {@code m} 0.
         */
        public Hull block(double cx, double cy, double cz, double volume, double m, double mcx, double mcy, double mcz) {
            if (m > 0.0) {
                mass += m;
                mx += m * mcx;
                my += m * mcy;
                mz += m * mcz;
            }
            if (volume > 0.0) {
                add(cx, cy, cz, SABLE_FLOAT_FORCE * volume, 0.0);
            }
            return this;
        }

        /** A block whose mass sits at its cube centre. */
        public Hull block(double cx, double cy, double cz, double volume, double m) {
            return block(cx, cy, cz, volume, m, cx, cy, cz);
        }

        /** A dry compartment cell (cell centre) lifting {@code liftPerBlock} per submerged block. */
        public Hull dryCell(double cx, double cy, double cz, double liftPerBlock) {
            if (liftPerBlock > 0.0) {
                add(cx, cy, cz, liftPerBlock, DRY_SURFACE_DROP);
            }
            return this;
        }

        private void add(double cx, double cy, double cz, double coefficient, double surfaceDrop) {
            if (n == y.length) {
                int c = n * 2;
                y = Arrays.copyOf(y, c);
                x = Arrays.copyOf(x, c);
                z = Arrays.copyOf(z, c);
                k = Arrays.copyOf(k, c);
                drop = Arrays.copyOf(drop, c);
            }
            x[n] = cx;
            y[n] = cy;
            z[n] = cz;
            k[n] = coefficient;
            drop[n] = surfaceDrop;
            n++;
        }

        public double mass() {
            return mass;
        }

        public int floatingElements() {
            return n;
        }

        /** Buoyancy at waterline {@code yw}: {B, Σ w·x, Σ w·y, Σ w·z}. */
        double[] buoyancyAt(double yw) {
            double b = 0, bx = 0, by = 0, bz = 0;
            for (int i = 0; i < n; i++) {
                double f = yw - drop[i] - (y[i] - 0.5);
                if (f <= 0.0) {
                    continue;
                }
                double w = k[i] * Math.min(f, 1.0);
                b += w;
                bx += w * x[i];
                by += w * y[i];
                bz += w * z[i];
            }
            return new double[] {b, bx, by, bz};
        }

        double lowest() {
            double lo = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) lo = Math.min(lo, y[i] - 0.5 + drop[i]);
            return lo;
        }

        double highest() {
            double hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) hi = Math.max(hi, y[i] + 0.5 + drop[i]);
            return hi;
        }
    }

    private BallastRule() {
    }

    /** The waterline at which {@code hull} floats level under {@code gravity}, by bisection (deterministic). */
    public static double waterline(Hull hull, double gravity) {
        double weight = hull.mass * gravity;
        double lo = hull.lowest(), hi = hull.highest();
        if (hull.buoyancyAt(hi)[0] <= weight) {
            return hi;
        }
        for (int i = 0; i < BISECTION_STEPS && hi - lo > 1.0e-10; i++) {
            double mid = 0.5 * (lo + hi);
            if (hull.buoyancyAt(mid)[0] < weight) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** The ballast for {@code hull} under {@code gravity} [blocks/s²]. */
    public static Result solve(Hull hull, double gravity, Params p) {
        if (!p.enabled() || !(hull.mass > 0.0) || !(gravity > 0.0) || hull.n == 0) {
            return Result.NONE;
        }
        double[] com = {hull.mx / hull.mass, hull.my / hull.mass, hull.mz / hull.mass};
        double yw = waterline(hull, gravity);
        double[] b = hull.buoyancyAt(yw);
        if (!(b[0] > 0.0)) {
            return new Result(hull.mass, 0.0, yw, new double[3], com, 0.0, 0.0, false);
        }
        double[] cob = {b[1] / b[0], b[2] / b[0], b[3] / b[0]};
        double tx = (cob[2] - com[2]) * b[0];
        double tz = -(cob[0] - com[0]) * b[0];
        double cap = p.maxPerMass() * hull.mass * gravity;
        double mag = Math.hypot(tx, tz);
        boolean capped = mag > cap;
        if (capped) {
            double s = mag > 0.0 ? cap / mag : 0.0;
            tx *= s;
            tz *= s;
        }
        return new Result(hull.mass, b[0], yw, cob, com, tx, tz, capped);
    }
}

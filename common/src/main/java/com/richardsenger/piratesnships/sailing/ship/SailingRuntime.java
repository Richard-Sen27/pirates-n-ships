package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.sailing.anchor.AnchorStatus;
import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.ForceContribution;
import com.richardsenger.piratesnships.sailing.force.HullDampingModel;
import com.richardsenger.piratesnships.sailing.force.RightingModel;
import com.richardsenger.piratesnships.sailing.force.SailInstance;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailType;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.force.ShipForceModel;
import com.richardsenger.piratesnships.sailing.force.ShipState;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Sailing state of one assembled ship (server): its sails, its bow, its plot-space extent, and the last force
 * evaluation. Built by {@link SailingRuntimes} from one plot scan and kept up to date by block changes, so the physics
 * substep does no block access.
 *
 * <h2>Derived quantities (per substep)</h2>
 * <ul>
 *   <li><b>Draft</b> = sea surface world Y (from the hull runtime's sea probes) − world Y of the hull bottom below the
 *       center of mass (plot point {@code (comX, minY, comZ)}).</li>
 *   <li><b>Submerged fraction</b> = {@code clamp(draft / full_draft, 0, 1)}: 0 when no sea touches the hull or the
 *       bottom is above the water, 1 from {@code full_draft} blocks of depth on. It scales the keel only.</li>
 *   <li><b>Keel center</b> = the middle of the hull's plot box in length and beam, at half the draft (halfway between
 *       the bottom and the waterline, never above the center of mass), relative to the COM, ship frame.</li>
 *   <li><b>Hull length</b> = the plot box's extent along the bow axis, <b>beam</b> the extent across it.</li>
 *   <li><b>Hull damping</b> ({@link HullDampingModel}) is applied to every afloat ship that turns, also one without
 *       sails or with the sailing forces off, as a full torque about the COM.</li>
 *   <li><b>Righting torque</b> ({@link RightingModel}, SH1) is applied to every afloat ship the same way. It lives here
 *       and not with the dry-volume lift in {@code HullRuntime} because this is where the hull's beam, length, height
 *       and draft (the plot box and the sea probes), its heel in the ship frame and the damping already are; the dry
 *       lift only knows compartment volumes.</li>
 *   <li><b>Heel cap</b>: the roll and pitch moment of sails and keel is scaled by {@code sail_heel_factor}, then limited
 *       by {@link RightingModel#limitHeel} ({@code stability.max_heel_torque_per_mass}, {@code max_heel_degrees}).</li>
 * </ul>
 */
public final class SailingRuntime {

    /**
     * One sail, keyed by its plot position: a square sail's head (its upper yard's middle block, with {@code square}
     * set) or a triangular sail's head cleat (with {@code triangle} set).
     */
    record Sail(SailType type, SailTrim trim, double area, @Nullable SquareSail square, @Nullable TriangularSail triangle) { }

    private final UUID id;
    private final BowFrame bow;
    private final Map<BlockPos, Sail> sails = new TreeMap<>();
    // every yard block of the ship, the yards they form (from the last relink) and the gap watched below each yard
    private final Set<BlockPos> yardBlocks = new HashSet<>();
    private List<YardRow> yards = List.of();
    private int watchedGap;
    // every cleat of the ship, and the columns watched below each stay's head: {x, z, minY, maxY}
    private final Set<BlockPos> cleatBlocks = new HashSet<>();
    private List<int[]> watchedColumns = List.of();
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int unfurled;

    // cached sail instances, relative to cachedCom
    private List<SailInstance> instances = List.of();
    private boolean instancesDirty = true;
    private final Vector3d cachedCom = new Vector3d(Double.NaN, 0, 0);

    // controls (server thread; read by the physics substep): helm and rudder step from block changes, the anchor
    private @Nullable BlockPos helm;
    private volatile int rudderStep;
    private volatile double wheelAngle; // HELM1: the helm wheel's angle [degrees, positive = starboard]
    private volatile @Nullable ShipAnchor anchor;
    private volatile @Nullable AnchorStatus anchorStatus;

    // per-tick caches
    private long windTime = Long.MIN_VALUE;
    private WindSample wind = WindSample.CALM;
    private SailingParams params = SailingParams.DEFAULTS;
    private double rudderAngle;
    private boolean anchorEnabled = true;

    // scratch (physics substep, server thread)
    private final Vector3d com = new Vector3d();
    private final Vector3d comWorld = new Vector3d();
    private final Vector3d lin = new Vector3d();
    private final Vector3d ang = new Vector3d();
    private final Vector3d tmp = new Vector3d();
    private final Vector3d tmp2 = new Vector3d();
    private final Quaterniond plotToWorld = new Quaterniond();
    private final Quaterniond shipToWorld = new Quaterniond();

    // last evaluation, for /pirates ship forces and tests (a reference to the model's own result: no extra cost)
    private volatile @Nullable ForceBreakdown lastBreakdown;
    private volatile double lastSubmerged;
    private volatile long lastEvaluation = Long.MIN_VALUE;

    SailingRuntime(UUID id, BowFrame bow, int[] bounds) {
        this.id = id;
        this.bow = bow;
        minX = bounds[0]; minY = bounds[1]; minZ = bounds[2];
        maxX = bounds[3]; maxY = bounds[4]; maxZ = bounds[5];
    }

    public UUID id() {
        return id;
    }

    public BowFrame bow() {
        return bow;
    }

    public int sailCount() {
        return sails.size();
    }

    public int unfurledCount() {
        return unfurled;
    }

    /** Plot positions of all sails, sorted (copy). */
    public List<BlockPos> sailPositions() {
        return new ArrayList<>(sails.keySet());
    }

    /** Trim of the sail at {@code plotPos}, or null when there is none. */
    public @Nullable SailTrim trimAt(BlockPos plotPos) {
        Sail s = sails.get(plotPos);
        return s == null ? null : s.trim();
    }

    public @Nullable ForceBreakdown lastBreakdown() {
        return lastBreakdown;
    }

    public double lastSubmerged() {
        return lastSubmerged;
    }

    /** Game time of the last force evaluation (Long.MIN_VALUE before the first). */
    public long lastEvaluation() {
        return lastEvaluation;
    }

    /** Plot position of the helm, or null when the ship has none. */
    public @Nullable BlockPos helm() {
        return helm;
    }

    /** Rudder step (positive = starboard), as set at the helm. */
    public int rudderStep() {
        return rudderStep;
    }

    /** Wheel angle of the helm [degrees, positive = starboard], the rudder's source with wheel steering (HELM1). */
    public double wheelAngle() {
        return wheelAngle;
    }

    /** Rudder angle used by the last tick's inputs [degrees, positive = starboard] (0 when steering is disabled). */
    public double rudderAngle() {
        return rudderAngle;
    }

    /** The ship's anchor, or null when it is stowed. */
    public @Nullable ShipAnchor anchor() {
        return anchor;
    }

    /** What the anchor did on the last game tick (AN2a), or null when it is stowed. */
    public @Nullable AnchorStatus anchorStatus() {
        return anchorStatus;
    }

    /**
     * Whether the ship counts as <b>anchored</b> (the predicate for mooring, crew, the HUD and other modules): its anchor
     * rests on the seabed and holds without dragging, and the ship is slower than {@code anchor_chain.at_rest_speed}.
     * Until then a dropped anchor is "anchor down, way on" ({@link #anchorDown()}). AN2a: before, any holding anchor
     * counted at once; now a ship dropping its anchor at speed is not anchored until the chain has stopped it.
     */
    public boolean isAnchored() {
        AnchorStatus s = anchorStatus;
        return anchor != null && s != null && s.anchored();
    }

    /** Whether the anchor is out (dropping, holding or raising), anchored or not. */
    public boolean anchorDown() {
        ShipAnchor a = anchor;
        return a != null && a.state().isOut();
    }

    /** Whether anchors are enabled for this ship this tick ({@code sailing_runtime.anchor_enabled}). */
    public boolean anchorEnabled() {
        return anchorEnabled;
    }

    /** Plot box {minX, minY, minZ, maxX, maxY, maxZ} (copy). */
    public int[] bounds() {
        return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
    }

    // ------------------------------------------------------------------ updates (server thread)

    void setHelm(@Nullable BlockPos plotPos, int step) {
        this.helm = plotPos == null ? null : plotPos.immutable();
        this.rudderStep = step;
    }

    void setWheelAngle(double degrees) {
        this.wheelAngle = degrees;
    }

    void setAnchor(@Nullable ShipAnchor anchor) {
        this.anchor = anchor;
        if (anchor == null) {
            this.anchorStatus = null;
        }
    }

    void setAnchorStatus(@Nullable AnchorStatus status) {
        this.anchorStatus = status;
    }

    void setControlInputs(double rudderAngle, boolean anchorEnabled) {
        this.rudderAngle = rudderAngle;
        this.anchorEnabled = anchorEnabled;
    }

    private void put(BlockPos plotPos, Sail sail) {
        Sail old = sails.put(plotPos.immutable(), sail);
        if (old != null && old.trim() != SailTrim.FURLED) unfurled--;
        if (sail.trim() != SailTrim.FURLED) unfurled++;
        include(plotPos);
        instancesDirty = true;
    }

    /** Changes only the trim of the sail at {@code plotPos} (the head of a square sail); false if there is none. */
    boolean setTrim(BlockPos plotPos, SailTrim trim) {
        Sail s = sails.get(plotPos);
        if (s == null) {
            return false;
        }
        if (s.trim() != trim) {
            put(plotPos, new Sail(s.type(), trim, s.area(), s.square(), s.triangle()));
        }
        return true;
    }

    // ------------------------------------------------------------------ square sails (yards, rule F5a)

    /** Every yard block position of the ship (copy). */
    List<BlockPos> yardBlocks() {
        return new ArrayList<>(yardBlocks);
    }

    boolean addYardBlock(BlockPos p) {
        include(p);
        return yardBlocks.add(p.immutable());
    }

    boolean removeYardBlock(BlockPos p) {
        return yardBlocks.remove(p);
    }

    /** The yards found by the last relink (copy). */
    public List<YardRow> yards() {
        return List.copyOf(yards);
    }

    /**
     * Whether a block change at {@code p} may change which yards pair: it lies in the mast column under one of the
     * yards, within the largest gap.
     */
    boolean watchesGap(BlockPos p) {
        for (YardRow r : yards) {
            if (p.getX() == r.middleX() && p.getZ() == r.middleZ() && p.getY() < r.y() && p.getY() >= r.y() - watchedGap) {
                return true;
            }
        }
        for (int[] c : watchedColumns) {
            if (p.getX() == c[0] && p.getZ() == c[1] && p.getY() >= c[2] && p.getY() <= c[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces all square sails with the result of a relink. {@code trims} reads the trim of a head (its block state),
     * {@code intact} the share of its cloth that is not torn (CAN3, 0..1), which scales the sail's area.
     */
    void replaceSquareSails(YardLinker.Linked linked, Function<BlockPos, SailTrim> trims,
                            java.util.function.ToDoubleBiFunction<BlockPos, SquareSail> intact, int maxGap) {
        sails.entrySet().removeIf(e -> e.getValue().square() != null);
        recountUnfurled();
        for (SquareSail s : linked.sails()) {
            BlockPos head = new BlockPos(s.upper().middleX(), s.upper().y(), s.upper().middleZ());
            double whole = Math.max(0.0, Math.min(1.0, intact.applyAsDouble(head, s)));
            put(head, new Sail(SailTypes.SQUARE, trims.apply(head), s.area() * whole, s, null));
        }
        yards = linked.rows();
        watchedGap = maxGap;
        instancesDirty = true;
    }

    private void recountUnfurled() {
        unfurled = 0;
        for (Sail s : sails.values()) {
            if (s.trim() != SailTrim.FURLED) unfurled++;
        }
    }

    // ------------------------------------------------------------------ triangular sails (stays and cleats, rule F5b)

    /** Every cleat position of the ship (copy). */
    List<BlockPos> cleatBlocks() {
        return new ArrayList<>(cleatBlocks);
    }

    boolean addCleat(BlockPos p) {
        include(p);
        return cleatBlocks.add(p.immutable());
    }

    boolean removeCleat(BlockPos p) {
        return cleatBlocks.remove(p);
    }

    /**
     * Replaces all triangular sails. {@code trims} reads the trim of a head (its block state); {@code columns} are the
     * cells {x, z, minY, maxY} below every stay's head where a block change may make or break a sail.
     */
    void replaceTriangularSails(List<TriangularSail> triangles, Function<BlockPos, SailTrim> trims, List<int[]> columns) {
        sails.entrySet().removeIf(e -> e.getValue().triangle() != null);
        recountUnfurled();
        for (TriangularSail t : triangles) {
            BlockPos head = new BlockPos(t.head().x(), t.head().y(), t.head().z());
            put(head, new Sail(SailTypes.FORE_AND_AFT, trims.apply(head), t.area(), null, t));
        }
        watchedColumns = List.copyOf(columns);
        instancesDirty = true;
    }

    /** The triangular sail headed at {@code plotPos}, or null. */
    public @Nullable TriangularSail triangularSailAt(BlockPos plotPos) {
        Sail s = sails.get(plotPos);
        return s == null ? null : s.triangle();
    }

    /** Type of the sail at {@code plotPos}, or null when there is none. */
    public @Nullable SailType typeAt(BlockPos plotPos) {
        Sail s = sails.get(plotPos);
        return s == null ? null : s.type();
    }

    /** Area of the sail at {@code plotPos} [blocks²], or NaN when there is none. */
    public double areaAt(BlockPos plotPos) {
        Sail s = sails.get(plotPos);
        return s == null ? Double.NaN : s.area();
    }

    /** The square sail headed at {@code plotPos}, or null (none, or a triangular sail). */
    public @Nullable SquareSail squareSailAt(BlockPos plotPos) {
        Sail s = sails.get(plotPos);
        return s == null ? null : s.square();
    }

    /** Grows the plot box to include a new block (removals do not shrink it until the next full scan). */
    void include(BlockPos p) {
        minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
        maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
    }

    void setTickInputs(long gameTime, WindSample wind, SailingParams params) {
        this.windTime = gameTime;
        this.wind = wind;
        this.params = params;
    }

    long windTime() {
        return windTime;
    }

    // ------------------------------------------------------------------ physics substep

    /**
     * One force evaluation and application. {@code seaWorldY} is NaN without sea. With {@code forcesEnabled} false only
     * the hull damping and the righting torque are applied. Returns false when nothing was applied: no unfurled sail,
     * not moving in water, and neither damping nor righting on. The anchor's chain is not applied here since AN2a
     * ({@code sailing.anchor.AnchorPhysics}, force group {@code pirates_n_ships:anchor}).
     *
     * @param gravity magnitude of the level's gravity [blocks/s²], for the righting torque
     */
    boolean physicsTick(ShipBody ship, double seaWorldY, double timeStep, long gameTime, double fullDraft, boolean sailsNeedWater,
                        double heelFactor, boolean forcesEnabled, HullDampingModel.Params damping,
                        RightingModel.Params stability, double gravity) {
        double mass = ship.mass();
        if (!(mass > 0.0) || !ship.centerOfMass(com)) {
            return false;
        }
        ship.velocities(lin, ang);
        ship.toWorld(tmp.set(com.x, minY, com.z), tmp2);
        double draft = Double.isFinite(seaWorldY) ? seaWorldY - tmp2.y : Double.NEGATIVE_INFINITY;
        double submerged = Math.min(Math.max(draft / fullDraft, 0.0), 1.0);
        lastSubmerged = submerged;
        boolean moving = lin.lengthSquared() > 1.0e-4 || ang.lengthSquared() > 1.0e-4;
        boolean idle = !forcesEnabled || unfurled == 0 && (submerged <= 0.0 || !moving);
        boolean damp = damping.enabled() && submerged > 0.0 && ang.lengthSquared() > 1.0e-8;
        boolean right = stability.enabled() && submerged > 0.0 && gravity > 0.0;
        if (idle && !damp && !right) {
            lastBreakdown = null;
            return false;
        }

        ship.toWorld(com, comWorld);
        bow.shipToWorld(ship.orientation(plotToWorld), shipToWorld);
        // keel center: box middle in length and beam, half the draft deep (plot y), relative to the COM, ship frame
        double waterlinePlotY = Math.min(com.y, com.y + (Double.isFinite(seaWorldY) ? seaWorldY - comWorld.y : 0.0));
        double keelPlotY = Math.min(com.y, (minY + Math.max(minY, waterlinePlotY)) * 0.5);
        tmp.set((minX + maxX + 1) * 0.5 - com.x, keelPlotY - com.y, (minZ + maxZ + 1) * 0.5 - com.z);
        Vector3d keelCenter = bow.toShip(tmp, new Vector3d());
        double length = bow.lengthOf(maxX - minX + 1, maxZ - minZ + 1);
        double beam = bow.beamOf(maxX - minX + 1, maxZ - minZ + 1);

        ShipState state = new ShipState(comWorld, shipToWorld, lin, ang, mass, submerged, length, keelCenter);
        // Hull damping: a pure torque, applied in full (never scaled by heelFactor, which is for the heeling of sails
        // and keel), also for a ship without sails, so that every floating ship stops rocking.
        ForceContribution d = damp ? HullDampingModel.compute(state, beam, damping) : null;
        // Righting torque (SH1): what Sable's block buoyancy lacks of a real hull's stability, never scaled by heelFactor.
        ForceContribution r = right ? RightingModel.compute(state,
                new RightingModel.Hull(beam, length, maxY - minY + 1, Math.max(0.0, draft)), gravity, stability) : null;
        ForceBreakdown f;
        double tx, ty, tz;
        Vector3d impulse;
        if (idle) {
            List<ForceContribution> own = new ArrayList<>(2);
            if (d != null) own.add(d);
            if (r != null) own.add(r);
            f = ForceBreakdown.of(own);
            tx = 0.0; ty = 0.0; tz = 0.0;
            impulse = tmp.zero();
        } else {
            List<SailInstance> active = sailsNeedWater && submerged <= 0.0 ? List.of() : instances(com);
            ShipForceModel.Rudder rudder = null;
            if (helm != null) {
                Vector3d rel = HullPoints.rudderPlot(bow, new int[] {minX, minY, minZ, maxX, maxY, maxZ}, new Vector3d()).sub(com);
                rudder = new ShipForceModel.Rudder(rudderAngle, bow.toShip(rel, rel));
            }
            ForceBreakdown forces = ShipForceModel.compute(wind, state, active, rudder, params);
            if (!forces.force().isFinite() || !forces.torque().isFinite()) {
                return false;
            }
            // Roll (ship z) and pitch (ship x) moments of sails and keel are scaled by heelFactor; yaw is kept. Minecraft
            // hulls are hollow and unballasted, so the full sail moment plus the keel's heeling couple capsized the 5x4x5
            // test hull within 2 s on a beam reach (spike 3 finding). The breakdown keeps the unscaled, physical values.
            // SH1: the scaled moments are then capped per mass and faded out toward stability.max_heel_degrees.
            tx = RightingModel.limitHeel(forces.torque().x() * heelFactor, RightingModel.pitch(state), mass, stability);
            ty = forces.torque().y();
            tz = RightingModel.limitHeel(forces.torque().z() * heelFactor, RightingModel.roll(state), mass, stability);
            impulse = bow.toPlot(forces.force(), tmp).mul(timeStep);
            f = d == null ? forces : ShipForceModel.withContribution(forces, d);
            f = r == null ? f : ShipForceModel.withContribution(f, r);
        }
        for (ForceContribution own : new ForceContribution[] {d, r}) {
            if (own == null) {
                continue;
            }
            if (!own.isFinite()) {
                return false;
            }
            tx += own.torque().x();
            ty += own.torque().y();
            tz += own.torque().z();
        }
        Vector3d angular = bow.toPlot(tmp2.set(tx, ty, tz), tmp2).mul(timeStep);
        ship.applySailingImpulse(impulse, angular);
        lastBreakdown = f;
        lastEvaluation = gameTime;
        return true;
    }

    /**
     * Sail instances relative to {@code com} (ship frame), rebuilt only after a sail change or a COM shift. A square
     * sail acts at the centroid of its drawn cloth ({@link SquareSail#centroid}), a triangular sail too
     * ({@link TriangularSail#centroid}); both types have a center of effort height of 0.
     */
    private List<SailInstance> instances(Vector3d com) {
        if (instancesDirty || com.distanceSquared(cachedCom) > 1.0e-4) {
            List<SailInstance> out = new ArrayList<>(sails.size());
            Vector3d rel = new Vector3d();
            for (Map.Entry<BlockPos, Sail> e : sails.entrySet()) {
                BlockPos p = e.getKey();
                Sail s = e.getValue();
                rel.set(p.getX() + 0.5 - com.x, p.getY() + 0.5 - com.y, p.getZ() + 0.5 - com.z);
                if (s.square() != null) {
                    double[] c = s.square().centroid(s.trim());
                    rel.add(s.square().upper().alongX() ? c[0] : 0.0, -c[1], s.square().upper().alongX() ? 0.0 : c[0]);
                } else if (s.triangle() != null) {
                    double[] c = s.triangle().centroid(s.trim());
                    rel.add(c[0], c[1], c[2]);
                }
                out.add(new SailInstance(s.type(), s.area(), s.trim(), bow.toShip(rel, new Vector3d())));
            }
            instances = Collections.unmodifiableList(out);
            cachedCom.set(com);
            instancesDirty = false;
        }
        return instances;
    }

    /** Velocity of the center of mass in the ship frame (x port, z forward) [m/s], into {@code dest}. For tests and debug. */
    public Vector3d shipFrameVelocity(ShipBody ship, Vector3d dest) {
        Vector3d l = new Vector3d();
        ship.velocities(l, new Vector3d());
        Quaterniond q = bow.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        return q.transformInverse(l, dest);
    }

    /** Heading of the bow as a compass bearing (0 = north), from the current pose. */
    public double headingDegrees(ShipBody ship) {
        Quaterniond q = bow.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        return WindSample.normalizeDegrees(Math.toDegrees(Math.atan2(f.x, -f.z)));
    }
}

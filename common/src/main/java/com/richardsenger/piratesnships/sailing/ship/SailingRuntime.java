package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.SailInstance;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailType;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.force.ShipForceModel;
import com.richardsenger.piratesnships.sailing.force.ShipState;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
 *   <li><b>Hull length</b> = the plot box's extent along the bow axis.</li>
 * </ul>
 */
public final class SailingRuntime {

    /** One sail block: type and trim, keyed by its plot position. */
    record Sail(SailType type, SailTrim trim) { }

    private final UUID id;
    private final BowFrame bow;
    private final Map<BlockPos, Sail> sails = new TreeMap<>();
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int unfurled;

    // cached sail instances, relative to cachedCom
    private List<SailInstance> instances = List.of();
    private boolean instancesDirty = true;
    private final Vector3d cachedCom = new Vector3d(Double.NaN, 0, 0);

    // controls (server thread; read by the physics substep): helm and rudder step from block changes, the anchor
    private @Nullable BlockPos helm;
    private volatile int rudderStep;
    private volatile @Nullable ShipAnchor anchor;

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

    /** Rudder angle used by the last tick's inputs [degrees, positive = starboard] (0 when steering is disabled). */
    public double rudderAngle() {
        return rudderAngle;
    }

    /** The ship's anchor, or null when it is stowed. */
    public @Nullable ShipAnchor anchor() {
        return anchor;
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

    void setAnchor(@Nullable ShipAnchor anchor) {
        this.anchor = anchor;
    }

    void setControlInputs(double rudderAngle, boolean anchorEnabled) {
        this.rudderAngle = rudderAngle;
        this.anchorEnabled = anchorEnabled;
    }

    void putSail(BlockPos plotPos, SailType type, SailTrim trim) {
        Sail old = sails.put(plotPos.immutable(), new Sail(type, trim));
        if (old != null && old.trim() != SailTrim.FURLED) unfurled--;
        if (trim != SailTrim.FURLED) unfurled++;
        include(plotPos);
        instancesDirty = true;
    }

    void removeSail(BlockPos plotPos) {
        Sail old = sails.remove(plotPos);
        if (old != null) {
            if (old.trim() != SailTrim.FURLED) unfurled--;
            instancesDirty = true;
        }
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
     * One force evaluation and application. {@code seaWorldY} is NaN without sea. Returns false when the ship was
     * skipped (no unfurled sail and not moving in water).
     */
    boolean physicsTick(ShipBody ship, double seaWorldY, double timeStep, long gameTime, double fullDraft, boolean sailsNeedWater,
                        double heelFactor) {
        double mass = ship.mass();
        if (!(mass > 0.0) || !ship.centerOfMass(com)) {
            return false;
        }
        ship.velocities(lin, ang);
        ship.toWorld(tmp.set(com.x, minY, com.z), tmp2);
        double draft = Double.isFinite(seaWorldY) ? seaWorldY - tmp2.y : Double.NEGATIVE_INFINITY;
        double submerged = Math.min(Math.max(draft / fullDraft, 0.0), 1.0);
        boolean moving = lin.lengthSquared() > 1.0e-4 || ang.lengthSquared() > 1.0e-4;
        boolean anchorOut = anchor != null && anchorEnabled;
        if (unfurled == 0 && !anchorOut && (submerged <= 0.0 || !moving)) {
            lastSubmerged = submerged;
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

        ShipState state = new ShipState(comWorld, shipToWorld, lin, ang, mass, submerged, length, keelCenter);
        List<SailInstance> active = sailsNeedWater && submerged <= 0.0 ? List.of() : instances(com);
        ShipForceModel.Rudder rudder = null;
        if (helm != null) {
            Vector3d rel = HullPoints.rudderPlot(bow, new int[] {minX, minY, minZ, maxX, maxY, maxZ}, new Vector3d()).sub(com);
            rudder = new ShipForceModel.Rudder(rudderAngle, bow.toShip(rel, rel));
        }
        ShipAnchor a = anchor;
        ShipForceModel.Anchor anchorInput = null;
        if (a != null && anchorEnabled && a.state().isOut()) {
            Vector3d hawse = new Vector3d(a.capstan().getX() + 0.5, a.capstan().getY() + 0.5, a.capstan().getZ() + 0.5).sub(com);
            anchorInput = new ShipForceModel.Anchor(a.state(), new Vector3d(a.point().x, a.point().y, a.point().z),
                    bow.toShip(hawse, hawse));
        }
        ForceBreakdown f = ShipForceModel.compute(wind, state, active, rudder, anchorInput, params);
        if (!f.force().isFinite() || !f.torque().isFinite()) {
            return false;
        }
        // Roll (ship z) and pitch (ship x) moments of sails and keel are scaled by heelFactor; yaw is kept. Minecraft
        // hulls are hollow and unballasted, so the full sail moment plus the keel's heeling couple capsized the 5x4x5
        // test hull within 2 s on a beam reach (spike 3 finding). The breakdown keeps the unscaled, physical values.
        double tx = f.torque().x() * heelFactor, ty = f.torque().y(), tz = f.torque().z() * heelFactor;
        Vector3d impulse = bow.toPlot(f.force(), tmp).mul(timeStep);
        Vector3d angular = bow.toPlot(tmp2.set(tx, ty, tz), tmp2).mul(timeStep);
        ship.applySailingImpulse(impulse, angular);
        lastBreakdown = f;
        lastSubmerged = submerged;
        lastEvaluation = gameTime;
        return true;
    }

    /** Sail instances relative to {@code com} (ship frame), rebuilt only after a sail change or a COM shift. */
    private List<SailInstance> instances(Vector3d com) {
        if (instancesDirty || com.distanceSquared(cachedCom) > 1.0e-4) {
            List<SailInstance> out = new ArrayList<>(sails.size());
            Vector3d rel = new Vector3d();
            for (Map.Entry<BlockPos, Sail> e : sails.entrySet()) {
                BlockPos p = e.getKey();
                rel.set(p.getX() + 0.5 - com.x, p.getY() + 0.5 - com.y, p.getZ() + 0.5 - com.z);
                out.add(new SailInstance(e.getValue().type(), e.getValue().trim(), bow.toShip(rel, new Vector3d())));
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

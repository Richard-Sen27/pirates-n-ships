package com.richardsenger.piratesnships.sailing.waves;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.WaveSpill;
import com.richardsenger.piratesnships.hazards.waves.WaveSprayPayload;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The sea acting on ships (docs/design.md §5.4, WV1), server side. Once per game tick, for every ship of ours that is
 * afloat (its hull runtime sees the sea), it samples the {@link WaveField} at the hull's four sample points
 * ({@link WaveTorqueRule#samplePoints}) and keeps:
 * <ul>
 *   <li>the roll and pitch torque of {@link WaveTorqueRule}, recorded in the {@code pirates_n_ships:waves} force group
 *       every physics substep ({@link ShipBody#applyWaveImpulse}); it changes every tick, so it wakes a resting body
 *       (docs/sable-notes.md §9.0i);</li>
 *   <li>the heave of {@link WaveHeaveRule}: an upward world force from the mean wave height under the hull (WAV2), in
 *       the same force group;</li>
 *   <li>the spill height ({@link WaveSpill}) that the hull runtime adds to the sea at the ship's outside ports
 *       ({@link #spillHeight});</li>
 *   <li>bow spray: in rough or storm seas, when the crest at the bow rises more than {@link #SPRAY_MIN} blocks above the
 *       bow's waterline point, a {@link WaveSprayPayload} goes to the players tracking that chunk, at most once per
 *       {@link #SPRAY_COOLDOWN} ticks per ship.</li>
 * </ul>
 * Ships that are not afloat, and every ship while waves are off or the sea is flat, get nothing. The hull damping
 * ({@code sailing.force.HullDampingModel}) is untouched and takes the energy out of the rolling.
 */
public final class WaveForces {

    /** Crest height above the bow's waterline point that throws spray [blocks]. */
    public static final double SPRAY_MIN = 0.35;
    /** Minimum ticks between two sprays of one ship. */
    public static final int SPRAY_COOLDOWN = 16;

    /** What the sea does to one ship this tick. */
    private static final class ShipWaves {
        long gameTime = Long.MIN_VALUE;
        BowFrame bow = BowFrame.SOUTH;
        WaveTorqueRule.Torque torque = WaveTorqueRule.Torque.ZERO;
        double rollSlope;
        double pitchSlope;
        double spill;
        /** Mean wave height under the hull [blocks] and the heave force [kpg·m/s², world up] (WAV2). */
        double meanHeight;
        double heave;
        long lastSpray = Long.MIN_VALUE / 2;
        int sprays;
        @Nullable HullAnalysis countedFor;
        int blocks;
    }

    private static final Map<ServerLevel, Map<UUID, ShipWaves>> LEVELS = new IdentityHashMap<>();

    private WaveForces() {
    }

    // ------------------------------------------------------------------ game tick

    /** End of a level tick (after {@link SeaStates#onLevelTick}): samples the sea for every afloat ship. */
    public static synchronized void onLevelTick(ServerLevel level) {
        WaveField field = SeaStates.field(level);
        Map<UUID, ShipWaves> m = LEVELS.computeIfAbsent(level, l -> new HashMap<>());
        if (field.isFlat()) {
            m.clear();
            return;
        }
        WaveTorqueRule.Params params = params();
        WaveHeaveRule.Params heave = heaveParams();
        SeaState state = SeaStates.current(level);
        boolean spill = HazardConfig.SPILL.get();
        long now = level.getGameTime();
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved()) {
                continue;
            }
            SailingRuntime rt = SailingRuntimes.get(level, ship.id());
            HullRuntime hull = HullRuntimes.get(level, ship.id());
            if (rt == null || hull == null || !hull.seesSea()) {
                m.remove(ship.id());
                continue;
            }
            sample(level, ship, rt, hull, field, params, heave, state, spill, now, m.computeIfAbsent(ship.id(), id -> new ShipWaves()));
        }
        m.values().removeIf(s -> s.gameTime < now);
    }

    private static void sample(ServerLevel level, ShipBody ship, SailingRuntime rt, HullRuntime hull, WaveField field,
                               WaveTorqueRule.Params params, WaveHeaveRule.Params heave, SeaState state, boolean spillOn, long now,
                               ShipWaves s) {
        Vector3d com = new Vector3d();
        double mass = ship.mass();
        if (!(mass > 0.0) || !ship.centerOfMass(com)) {
            s.gameTime = Long.MIN_VALUE;
            return;
        }
        int[] bounds = rt.bounds();
        BowFrame bow = rt.bow();
        Vector3d[] pts = WaveTorqueRule.samplePoints(bow, bounds, com.y);
        Vector3d[] world = new Vector3d[pts.length];
        for (int i = 0; i < pts.length; i++) {
            world[i] = ship.toWorld(pts[i], new Vector3d());
        }
        Vector3d c = ship.toWorld(WaveTorqueRule.center(bounds, com.y), new Vector3d());
        double[] h = new double[pts.length + 1];
        for (int i = 0; i < pts.length; i++) {
            h[i] = field.heightAround(c.x, c.z, world[i].x, world[i].z, now);
        }
        h[pts.length] = field.heightAround(c.x, c.z, c.x, c.z, now);
        double lengthSpan = Math.hypot(world[0].x - world[1].x, world[0].z - world[1].z);
        double beamSpan = Math.hypot(world[2].x - world[3].x, world[2].z - world[3].z);
        s.rollSlope = WaveTorqueRule.slope(h[2], h[3], beamSpan);
        s.pitchSlope = WaveTorqueRule.slope(h[0], h[1], lengthSpan);
        s.torque = WaveTorqueRule.torque(s.rollSlope, s.pitchSlope, mass, blocks(hull, s), params);
        s.spill = WaveSpill.height(h, spillOn);
        s.meanHeight = WaveHeaveRule.meanHeight(h);
        s.heave = WaveHeaveRule.force(s.meanHeight, mass, ship.gravity().length(), heave);
        s.bow = bow;
        s.gameTime = now;
        if (state.spray() && now - s.lastSpray >= SPRAY_COOLDOWN) {
            spray(level, ship, hull, com, pts[0], world[0], world[1], h[0], now, s);
        }
    }

    /** Sends spray when the crest at the bow rises more than {@link #SPRAY_MIN} above the bow's waterline point. */
    private static void spray(ServerLevel level, ShipBody ship, HullRuntime hull, Vector3d com, Vector3d bowPlot,
                              Vector3d bowWorld, Vector3d sternWorld, double hBow, long now, ShipWaves s) {
        double sea = hull.seaWorldY();
        Vector3d comWorld = ship.toWorld(com, new Vector3d());
        double waterlinePlotY = com.y + (sea - comWorld.y);
        Vector3d bowWaterline = ship.toWorld(new Vector3d(bowPlot.x, waterlinePlotY, bowPlot.z), new Vector3d());
        double excess = sea + hBow - bowWaterline.y;
        if (excess <= SPRAY_MIN) {
            return;
        }
        s.lastSpray = now;
        s.sprays++;
        double dx = bowWorld.x - sternWorld.x, dz = bowWorld.z - sternWorld.z;
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-6) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        WaveSprayPayload p = new WaveSprayPayload(bowWaterline.x, sea + Math.max(0.0, hBow), bowWaterline.z,
                (float) (dx / len), (float) (dz / len), (float) excess);
        Services.NETWORK.sendToTrackingChunk(level, new ChunkPos(net.minecraft.core.BlockPos.containing(p.x(), p.y(), p.z())), p);
    }

    /** Solid cells of the hull's current analysis, counted once per analysis. */
    private static int blocks(HullRuntime hull, ShipWaves s) {
        HullAnalysis a = hull.simulation().analysis();
        if (a != s.countedFor) {
            HullGrid g = a.grid();
            int n = 0;
            for (int i = 0, cells = g.cellCount(); i < cells; i++) {
                if (g.kind(i) == CellKind.SOLID) n++;
            }
            s.countedFor = a;
            s.blocks = n;
        }
        return s.blocks;
    }

    // ------------------------------------------------------------------ physics substep

    /** Physics substep: records this tick's wave torque and heave force of every afloat ship. */
    public static synchronized void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, ShipWaves> m = LEVELS.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Vector3d torque = new Vector3d();
        Vector3d lift = new Vector3d();
        Quaterniond q = new Quaterniond();
        for (Map.Entry<UUID, ShipWaves> e : m.entrySet()) {
            ShipWaves s = e.getValue();
            if (s.gameTime < now - 1 || s.torque.magnitude() <= 0.0 && s.heave == 0.0) {
                continue;
            }
            ShipBody ship = SableShips.byId(level, e.getKey());
            if (ship == null || ship.isRemoved()) {
                continue;
            }
            s.torque.toShipVector(torque);
            s.bow.toPlot(torque, torque).mul(timeStep);
            // the heave acts straight up in the world: into the body frame at this substep's orientation
            ship.orientation(q).transformInverse(lift.set(0.0, s.heave * timeStep, 0.0));
            ship.applyWaveImpulse(lift, torque);
        }
    }

    // ------------------------------------------------------------------ queries

    /**
     * The wave height to add to the sea at {@code ship}'s outside ports this tick ({@code FloodTickInput.waveHeight});
     * 0 when the ship gets no waves.
     */
    public static synchronized double spillHeight(ServerLevel level, UUID ship) {
        ShipWaves s = find(level, ship);
        return s == null ? 0.0 : s.spill;
    }

    /** This tick's wave torque on {@code ship} (ship frame), or zero. */
    public static synchronized WaveTorqueRule.Torque torque(ServerLevel level, UUID ship) {
        ShipWaves s = find(level, ship);
        return s == null ? WaveTorqueRule.Torque.ZERO : s.torque;
    }

    /** This tick's heave force on {@code ship} [kpg·m/s², world up; negative pulls down], or 0. */
    public static synchronized double heave(ServerLevel level, UUID ship) {
        ShipWaves s = find(level, ship);
        return s == null ? 0.0 : s.heave;
    }

    /** This tick's mean wave height under {@code ship} [blocks], or 0. */
    public static synchronized double meanHeight(ServerLevel level, UUID ship) {
        ShipWaves s = find(level, ship);
        return s == null ? 0.0 : s.meanHeight;
    }

    /** This tick's {@code {roll slope, pitch slope}} under {@code ship}, or zeros. */
    public static synchronized double[] slopes(ServerLevel level, UUID ship) {
        ShipWaves s = find(level, ship);
        return s == null ? new double[2] : new double[] {s.rollSlope, s.pitchSlope};
    }

    /** How many sprays {@code ship} has thrown since it was first sampled (tests). */
    public static synchronized int sprays(ServerLevel level, UUID ship) {
        Map<UUID, ShipWaves> m = LEVELS.get(level);
        ShipWaves s = m == null ? null : m.get(ship);
        return s == null ? 0 : s.sprays;
    }

    private static @Nullable ShipWaves find(ServerLevel level, UUID ship) {
        Map<UUID, ShipWaves> m = LEVELS.get(level);
        ShipWaves s = m == null ? null : m.get(ship);
        return s == null || s.gameTime < level.getGameTime() - 2 ? null : s;
    }

    public static synchronized void onShipRemoved(ServerLevel level, UUID ship) {
        Map<UUID, ShipWaves> m = LEVELS.get(level);
        if (m != null) {
            m.remove(ship);
        }
    }

    public static synchronized void clear() {
        LEVELS.clear();
    }

    /** The torque rule's tuning from the server config. */
    public static WaveTorqueRule.Params params() {
        return new WaveTorqueRule.Params(HazardConfig.WAVES_ENABLED.get(), HazardConfig.SHIP_TORQUE.get(),
                HazardConfig.MAX_TORQUE_PER_MASS.get(), HazardConfig.SIZE_EXPONENT.get());
    }

    /** The heave rule's tuning from the server config. */
    public static WaveHeaveRule.Params heaveParams() {
        return new WaveHeaveRule.Params(HazardConfig.WAVES_ENABLED.get() && HazardConfig.HEAVE.get(), HazardConfig.HEAVE_STRENGTH.get(),
                HazardConfig.HEAVE_PER_MASS.get());
    }
}

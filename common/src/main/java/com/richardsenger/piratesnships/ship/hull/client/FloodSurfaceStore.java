package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.ship.hull.runtime.CellSet;
import com.richardsenger.piratesnships.ship.hull.runtime.FloodSurfacePayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client copy of the ships' flood surfaces (FLD1, from {@link FloodSurfacePayload}), with the eased level per surface.
 * Plain data, no client classes, so the payload handler may name it on both sides; only the client thread touches
 * {@link #CLIENT}. Times are client game ticks plus the partial tick.
 *
 * <p>A surface whose compartment (same cell set) was already shown eases from the level it shows now to the new one
 * over the payload's interval, so it rises and falls smoothly although the server sends at most every
 * {@code region_rebuild_ticks}. A new compartment starts at its level.
 */
public final class FloodSurfaceStore {

    public static final FloodSurfaceStore CLIENT = new FloodSurfaceStore();

    /** One ship's surfaces and the up vector (plot frame) their levels are measured along. */
    public record Ship(UUID id, float upX, float upY, float upZ, List<Surface> surfaces) {
    }

    /** One compartment's surface: cells, the level it eases from and to, and a cached pivot ({@link #anchor}). */
    public static final class Surface {
        private final CellSet cells;
        private final double from, to, start, duration;
        private double anchorLevel = Double.NaN;
        private double[] anchor;

        Surface(CellSet cells, double from, double to, double start, double duration) {
            this.cells = cells;
            this.from = from;
            this.to = to;
            this.start = start;
            this.duration = duration;
        }

        public CellSet cells() {
            return cells;
        }

        /** The level (blocks above the cell set's min corner, along the ship's up vector) at time {@code now}. */
        public double level(double now) {
            return FloodSurfaceGeometry.ease(from, to, start, duration, now);
        }

        /** The level the surface eases towards (the last synced one). */
        public double target() {
            return to;
        }

        /** {@link FloodSurfaceGeometry#anchor} for the plane at {@code level} along {@code up}, cached per level. */
        public double[] anchor(double level, double upX, double upY, double upZ) {
            if (level != anchorLevel || anchor == null) {
                anchor = FloodSurfaceGeometry.anchor(cells, upX, upY, upZ, level);
                anchorLevel = level;
            }
            return anchor;
        }
    }

    private final Map<UUID, Ship> ships = new HashMap<>();

    /** Replaces a ship's surfaces with the payload's, received at time {@code now}. An empty payload clears the ship. */
    public void accept(FloodSurfacePayload payload, double now) {
        Ship old = ships.remove(payload.ship());
        if (payload.surfaces().isEmpty()) {
            return;
        }
        double up = Math.sqrt(payload.upX() * payload.upX() + payload.upY() * payload.upY() + payload.upZ() * payload.upZ());
        if (!(up > 1e-6)) {
            return;
        }
        List<Surface> next = new ArrayList<>(payload.surfaces().size());
        for (FloodSurfacePayload.Surface s : payload.surfaces()) {
            Surface before = old == null ? null : find(old, s.cells());
            double from = before == null ? s.level() : before.level(now);
            next.add(new Surface(s.cells(), from, s.level(), now, Math.max(1, payload.intervalTicks())));
        }
        ships.put(payload.ship(), new Ship(payload.ship(), (float) (payload.upX() / up), (float) (payload.upY() / up),
                (float) (payload.upZ() / up), List.copyOf(next)));
    }

    private static Surface find(Ship ship, CellSet cells) {
        for (Surface s : ship.surfaces()) {
            if (s.cells().equals(cells)) {
                return s;
            }
        }
        return null;
    }

    public void remove(UUID ship) {
        ships.remove(ship);
    }

    public void clear() {
        ships.clear();
    }

    public boolean isEmpty() {
        return ships.isEmpty();
    }

    public Collection<Ship> ships() {
        return ships.values();
    }
}

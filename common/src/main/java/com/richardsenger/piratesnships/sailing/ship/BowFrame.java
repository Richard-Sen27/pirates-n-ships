package com.richardsenger.piratesnships.sailing.ship;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The fixed rotation between a ship's plot frame (Sable's body frame, axis-aligned with its blocks) and the
 * {@link com.richardsenger.piratesnships.sailing.force.ShipFrame ship frame} ({@code +Z} bow, {@code +Y} up,
 * {@code +X} port). Pure.
 *
 * <p><b>Bow convention:</b> the bow is the horizontal direction the helmsman looks when standing at the helm, which is
 * the <em>opposite</em> of the helm block's {@code FACING} ({@code HelmBlock} faces the player who placed it, so the bow
 * is the direction that player was looking). The helm's facing is read in the plot, where blocks keep the state they
 * had at assembly, so the plot-frame bow direction never changes while the ship exists.
 *
 * <p>Conversions: {@code ship → plot} is a rotation about {@code +Y} by {@code atan2(dx, dz)}, which turns the ship's
 * {@code +Z} onto the plot bow {@code (dx, 0, dz)} and, because it is a proper rotation, the ship's port ({@code +X})
 * onto the plot direction left of the bow. {@code ship → world} is the Sable pose orientation (plot → world) times
 * {@code ship → plot}.
 *
 * @param dx plot x of the unit bow direction (−1, 0 or 1)
 * @param dz plot z of the unit bow direction (−1, 0 or 1); exactly one of {@code dx, dz} is non-zero
 */
public record BowFrame(int dx, int dz) {

    /** Bow along plot {@code +Z} (helm facing north): the identity rotation. */
    public static final BowFrame SOUTH = new BowFrame(0, 1);

    public BowFrame {
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            throw new IllegalArgumentException("bow must be a horizontal unit axis: " + dx + ", " + dz);
        }
    }

    /** Rotation ship frame → plot frame (new copy). */
    public Quaterniond shipToPlot() {
        return new Quaterniond().rotateY(Math.atan2(dx, dz));
    }

    /** Rotation ship frame → world, given the Sable pose orientation (plot → world); written into {@code dest}. */
    public Quaterniond shipToWorld(Quaterniondc plotToWorld, Quaterniond dest) {
        return dest.set(plotToWorld).mul(shipToPlot());
    }

    /** Ship-frame vector into the plot frame (exact for the four quarter turns). */
    public Vector3d toPlot(Vector3dc ship, Vector3d dest) {
        // rotateY(a) with sin a = dx, cos a = dz: x' = x·dz + z·dx, z' = −x·dx + z·dz
        double x = ship.x() * dz + ship.z() * dx;
        double z = -ship.x() * dx + ship.z() * dz;
        return dest.set(x, ship.y(), z);
    }

    /** Plot-frame vector into the ship frame (inverse of {@link #toPlot}). */
    public Vector3d toShip(Vector3dc plot, Vector3d dest) {
        double x = plot.x() * dz - plot.z() * dx;
        double z = plot.x() * dx + plot.z() * dz;
        return dest.set(x, plot.y(), z);
    }

    /** Length of a plot-space box along the bow axis. */
    public double lengthOf(double sizeX, double sizeZ) {
        return dx != 0 ? sizeX : sizeZ;
    }

    /** Extent across the bow axis (the beam) of a plot box of the given size. */
    public double beamOf(double sizeX, double sizeZ) {
        return dx != 0 ? sizeZ : sizeX;
    }

    /** Lower-case compass name of the plot bow direction ({@code north} = −Z), for storage and debug output. */
    public String name() {
        if (dx > 0) return "east";
        if (dx < 0) return "west";
        return dz > 0 ? "south" : "north";
    }

    /** Parses {@link #name()}; unknown names give {@link #SOUTH}. */
    public static BowFrame byName(String name) {
        return switch (name) {
            case "north" -> new BowFrame(0, -1);
            case "east" -> new BowFrame(1, 0);
            case "west" -> new BowFrame(-1, 0);
            default -> SOUTH;
        };
    }

    /** The bow for a helm whose block {@code FACING} is the horizontal step {@code (fx, fz)}: the opposite direction. */
    public static BowFrame fromHelmFacing(int fx, int fz) {
        return new BowFrame(-fx, -fz);
    }
}

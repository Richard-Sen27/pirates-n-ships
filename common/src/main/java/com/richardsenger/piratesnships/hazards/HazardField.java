package com.richardsenger.piratesnships.hazards;

/**
 * The force fields of the sea hazards (docs/design.md §12), pure maths without world access. Positions are relative to
 * the hazard's base (the water line at its axis): {@code dx}, {@code dz} horizontal, {@code dy} up. Results are
 * accelerations in blocks/tick² that an entity adds to its velocity once per tick.
 *
 * <ul>
 * <li>Every strength falls off linearly from the axis to 0 at the radius ({@link #falloff}).</li>
 * <li>Waterspout: a pull toward the axis and a lift that also fades linearly with height, to 0 at the top of the
 *     funnel (so a lifted entity hovers where lift and gravity balance instead of being thrown out of the top).</li>
 * <li>Whirlpool: a pull toward the centre and a spin along the circle, counter-clockwise seen from above; boats and
 *     swimmers in the inner third are also dragged down with the full {@code drag_down}.</li>
 * <li>Ships ({@link #shipImpulse}): the field acceleration {@code a} becomes an impulse of
 *     {@code a × scale × min(mass, cap)} kpg·m/s per tick, i.e. a light ship's velocity changes by {@code a} m/s per
 *     tick (1/20 of an entity's), a ship heavier than the cap by {@code a × cap / mass}.</li>
 * </ul>
 */
public final class HazardField {

    private HazardField() {
    }

    /** An acceleration (or impulse) vector. */
    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0, 0, 0);

        public Vec plus(Vec o) {
            return new Vec(x + o.x, y + o.y, z + o.z);
        }

        public Vec scale(double f) {
            return new Vec(x * f, y * f, z * f);
        }

        public double horizontalLength() {
            return Math.sqrt(x * x + z * z);
        }
    }

    /** Waterspout parameters (config {@code hazards.waterspouts}). */
    public record Spout(double radius, double funnelHeight, double pull, double lift) { }

    /** Whirlpool parameters (config {@code hazards.whirlpools}). */
    public record Pool(double radius, double pull, double spin, double dragDown) { }

    /** Linear falloff: 1 on the axis, 0 at and beyond {@code radius}. */
    public static double falloff(double r, double radius) {
        if (radius <= 0 || r >= radius) {
            return 0.0;
        }
        return 1.0 - Math.max(0.0, r) / radius;
    }

    /** Lift fade with height above the base: 1 at and below the water line, 0 at and above the funnel top. */
    public static double heightFade(double dy, double funnelHeight) {
        if (funnelHeight <= 0 || dy >= funnelHeight) {
            return 0.0;
        }
        return dy <= 0 ? 1.0 : 1.0 - dy / funnelHeight;
    }

    /** Unit vector from the point toward the axis (horizontal), or zero on the axis. */
    static Vec inward(double dx, double dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r < 1e-6 ? Vec.ZERO : new Vec(-dx / r, 0, -dz / r);
    }

    /** Unit tangent of the counter-clockwise circle seen from above (+y up, x east, z south), or zero on the axis. */
    static Vec tangent(double dx, double dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r < 1e-6 ? Vec.ZERO : new Vec(dz / r, 0, -dx / r);
    }

    /** Waterspout acceleration at an offset from its base: pull toward the axis plus the (height-faded) lift. */
    public static Vec waterspout(double dx, double dy, double dz, Spout p) {
        double f = falloff(Math.sqrt(dx * dx + dz * dz), p.radius());
        if (f <= 0) {
            return Vec.ZERO;
        }
        Vec pull = inward(dx, dz).scale(p.pull() * f);
        return pull.plus(new Vec(0, p.lift() * f * heightFade(dy, p.funnelHeight()), 0));
    }

    /**
     * Whirlpool acceleration at an offset from its centre: pull toward the centre, the counter-clockwise spin, and for
     * {@code dragged} entities (boats and swimmers) the full downward drag inside the inner third of the radius.
     */
    public static Vec whirlpool(double dx, double dz, boolean dragged, Pool p) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double f = falloff(r, p.radius());
        if (f <= 0) {
            return Vec.ZERO;
        }
        Vec a = inward(dx, dz).scale(p.pull() * f).plus(tangent(dx, dz).scale(p.spin() * f));
        if (dragged && r < p.radius() / 3.0) {
            a = a.plus(new Vec(0, -p.dragDown(), 0));
        }
        return a;
    }

    /**
     * New vertical speed after adding {@code dvy}: a positive (lifting) {@code dvy} never speeds the entity up past
     * {@code maxUp} (and never slows a faster one down); a downward {@code dvy} is always added.
     */
    public static double applyLift(double vy, double dvy, double maxUp) {
        if (dvy <= 0) {
            return vy + dvy;
        }
        if (vy >= maxUp) {
            return vy;
        }
        return Math.min(vy + dvy, maxUp);
    }

    /** The share of the full hazard force a ship of {@code mass} feels: {@code min(1, cap / mass)}; 0 without mass. */
    public static double massEffect(double mass, double cap) {
        if (mass <= 0 || cap <= 0) {
            return 0.0;
        }
        return Math.min(1.0, cap / mass);
    }

    /**
     * World-frame impulse in kpg·m/s for one game tick on a ship of {@code mass} from field acceleration {@code a}:
     * {@code a × scale × mass × massEffect(mass, cap)} = {@code a × scale × min(mass, cap)}.
     */
    public static Vec shipImpulse(Vec a, double mass, double cap, double scale) {
        return a.scale(scale * mass * massEffect(mass, cap));
    }
}

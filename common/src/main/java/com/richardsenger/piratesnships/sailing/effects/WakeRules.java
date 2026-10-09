package com.richardsenger.piratesnships.sailing.effects;

/**
 * Pure rules of a ship's wake (WAKE1, docs/design.md §5.4 "Wake"): how many foam quads a ship sheds per tick at its
 * stem (the bow wave), across its stern (the trail) and along its waterline, how opaque they are, how they drift and
 * how they fade, all by the ship's speed through the water. Nothing below {@link #minSpeed}; the rates and the opacity
 * grow linearly up to {@link #fullSpeed}. Client-only visuals, no gameplay effect; fed by the client config
 * ({@code wave_effects.wake*}) through {@code SeaEffectsConfig#wake()}.
 *
 * <p>Directions: the course {@code (cx, cz)} is the unit horizontal direction of the ship's velocity; starboard is
 * {@code (-cz, cx)} (facing north, −Z, starboard is east, +X) and port its opposite. Speeds are blocks per second,
 * drifts blocks per tick.
 *
 * @param radius        ships within this horizontal distance of the camera leave a wake [blocks]
 *                      ({@code wave_effects.wake_radius})
 * @param minSpeed      no wake below this speed through the water [blocks/s] ({@code wave_effects.wake_min_speed})
 * @param fullSpeed     the rates and the opacity are full from this speed on [blocks/s]
 *                      ({@code wave_effects.wake_full_speed})
 * @param density       foam quads per tick per block of stern width at full speed; the bow wave and the waterline
 *                      foam scale with it ({@code wave_effects.wake_density})
 * @param lifeTicks     life of a stern trail quad [ticks]; the bow wave lives {@link #BOW_LIFE_SHARE} of it
 *                      ({@code wave_effects.wake_life_ticks})
 * @param spreadDegrees half-angle of the bow wave's V off the course, the Kelvin angle 19.5° by default
 *                      ({@code wave_effects.wake_spread_degrees})
 */
public record WakeRules(double radius, double minSpeed, double fullSpeed, double density, int lifeTicks,
                        double spreadDegrees) {

    public static final WakeRules DEFAULTS = new WakeRules(64.0, 1.0, 8.0, 0.6, 160, 19.5);

    /** Height above the water surface, above the foam streaks (0.02) against z-fighting [blocks]. */
    public static final double LIFT = 0.03;
    /** Bow wave quads per tick per side at full speed, per unit of {@link #density}. */
    public static final double BOW_PER_SIDE = 1.5;
    /** Share of the speed range below which there is no waterline foam. */
    public static final double SIDE_MIN_FACTOR = 0.35;
    /** Waterline quads per tick per block of waterline (both sides) at full speed, per unit of {@link #density}. */
    public static final double SIDE_PER_BLOCK = 0.12;
    /** Life of a waterline quad [ticks]: short, it marks the hull, not the water. */
    public static final int SIDE_LIFE_TICKS = 16;
    /** Share of the speed range above which some bow foam rises as a splash. */
    public static final double SPLASH_MIN_FACTOR = 0.5;
    /** Splash streaks per tick at full speed, per unit of {@link #density}. */
    public static final double SPLASH_PER_TICK = 0.6;
    /** Life of a splash streak [ticks]. */
    public static final int SPLASH_LIFE_TICKS = 14;
    /** Life of a bow wave quad as a share of {@link #lifeTicks}. */
    public static final double BOW_LIFE_SHARE = 0.75;
    /**
     * The stern trail's sideways drift at the trail's edge, as a share of the bow wave's sideways speed: the trail
     * widens in a V, but more slowly than the bow wave's arms.
     */
    public static final double STERN_SPREAD_SHARE = 0.3;
    /** Velocity kept per tick by a stern quad (the drift dies away, so the trail settles at a finite width). */
    public static final double STERN_FRICTION = 0.985;
    /** Velocity kept per tick by a bow wave quad: none lost, so the arms stay straight lines at the Kelvin angle. */
    public static final double BOW_FRICTION = 1.0;
    /** Fade-in time of a wake quad [ticks]: foam appears at once. */
    public static final double FADE_IN_TICKS = 3.0;
    /** Share of the life over which a wake quad fades out (a smoothstep). */
    public static final double FADE_OUT_FRACTION = 0.8;

    public WakeRules {
        radius = Math.max(4.0, radius);
        minSpeed = Math.max(0.0, minSpeed);
        fullSpeed = Math.max(minSpeed + 0.1, fullSpeed);
        density = Math.max(0.0, density);
        lifeTicks = Math.max(10, lifeTicks);
        spreadDegrees = Math.max(0.0, Math.min(60.0, spreadDegrees));
    }

    /** Whether a ship moving at {@code speed} [blocks/s] leaves a wake at all. */
    public boolean active(double speed) {
        return speed > 0.0 && speed >= minSpeed && density > 0.0;
    }

    /** 0 at {@link #minSpeed} (and below) to 1 at {@link #fullSpeed} and above, linear between. */
    public double speedFactor(double speed) {
        if (!active(speed)) {
            return 0.0;
        }
        return clamp01((speed - minSpeed) / (fullSpeed - minSpeed));
    }

    /** Mean stern trail quads per tick across a stern {@code width} blocks wide (at least one block). */
    public double sternRate(double speed, double width) {
        if (!active(speed)) {
            return 0.0;
        }
        return density * Math.max(1.0, width) * (0.3 + 0.7 * speedFactor(speed));
    }

    /** Mean bow wave quads per tick on each side of the stem. */
    public double bowRate(double speed) {
        if (!active(speed)) {
            return 0.0;
        }
        return density * BOW_PER_SIDE * (0.25 + 0.75 * speedFactor(speed));
    }

    /** Mean waterline quads per tick along {@code sideLength} blocks of waterline; none when slow. */
    public double sideRate(double speed, double sideLength) {
        double t = speedFactor(speed);
        if (t <= SIDE_MIN_FACTOR) {
            return 0.0;
        }
        return density * SIDE_PER_BLOCK * Math.max(0.0, sideLength) * (t - SIDE_MIN_FACTOR) / (1.0 - SIDE_MIN_FACTOR);
    }

    /** Mean splash streaks rising at the stem per tick; none below half of the speed range. */
    public double splashRate(double speed) {
        double t = speedFactor(speed);
        if (t <= SPLASH_MIN_FACTOR) {
            return 0.0;
        }
        return density * SPLASH_PER_TICK * (t - SPLASH_MIN_FACTOR) / (1.0 - SPLASH_MIN_FACTOR);
    }

    /** Opacity of a fully faded-in wake quad: 0.2 at {@link #minSpeed} to 0.65 at {@link #fullSpeed}, 0 when idle. */
    public double opacity(double speed) {
        return active(speed) ? 0.2 + 0.45 * speedFactor(speed) : 0.0;
    }

    /** Life of a bow wave quad [ticks]. */
    public int bowLifeTicks() {
        return Math.max(10, (int) Math.round(lifeTicks * BOW_LIFE_SHARE));
    }

    /**
     * Sideways speed of a bow wave quad in the world [blocks/tick]: the ship leaves it behind at {@code speed} while it
     * moves out at {@code speed × tan(spread)}, so seen from the ship it runs back along a line {@link #spreadDegrees}
     * off the course: the Kelvin arm.
     */
    public double bowLateralPerTick(double speed) {
        return Math.max(0.0, speed) * Math.tan(Math.toRadians(spreadDegrees)) / 20.0;
    }

    /**
     * Sideways drift of a stern quad [blocks/tick] born at {@code lateral} in [−1, 1] across the stern (−1 port edge,
     * +1 starboard edge): outward, proportional to the distance from the centre line, so the trail opens in a V.
     */
    public double sternLateralPerTick(double speed, double lateral) {
        double l = Math.max(-1.0, Math.min(1.0, lateral));
        return bowLateralPerTick(speed) * STERN_SPREAD_SHARE * l;
    }

    /**
     * Horizontal unit direction {@code {x, z}} of a bow wave arm on {@code side} (+1 starboard, −1 port) for a course
     * {@code (cx, cz)}: back along the course and out by {@link #spreadDegrees}. The bow quads lie along it.
     */
    public double[] armDirection(double cx, double cz, int side) {
        double a = Math.toRadians(spreadDegrees);
        double s = side >= 0 ? 1.0 : -1.0;
        double px = -cz * s, pz = cx * s;
        return new double[] {-cx * Math.cos(a) + px * Math.sin(a), -cz * Math.cos(a) + pz * Math.sin(a)};
    }

    /** The starboard unit vector {@code {x, z}} of a course {@code (cx, cz)}. */
    public static double[] starboard(double cx, double cz) {
        return new double[] {-cz, cx};
    }

    /**
     * How far a particle with initial speed {@code v0} [blocks/tick] has moved after {@code ticks} ticks when it keeps
     * {@code friction} of its velocity each tick (vanilla's {@code Particle#tick}: move, then multiply).
     */
    public static double displacement(double v0, double friction, int ticks) {
        if (ticks <= 0) {
            return 0.0;
        }
        if (friction >= 1.0) {
            return v0 * ticks;
        }
        return v0 * (1.0 - Math.pow(friction, ticks)) / (1.0 - friction);
    }

    /**
     * Opacity factor in [0, 1] at {@code age} of {@code lifetime} ticks: in over {@link #FADE_IN_TICKS}, out over the
     * last {@link #FADE_OUT_FRACTION} of the life, both smoothsteps; so the trail is brightest close behind the stern.
     */
    public static double fade(double age, double lifetime) {
        if (lifetime <= 0.0 || age < 0.0 || age > lifetime) {
            return 0.0;
        }
        double in = smooth(age / FADE_IN_TICKS);
        double out = smooth((1.0 - age / lifetime) / FADE_OUT_FRACTION);
        return Math.min(in, out);
    }

    /** Length of a stern trail quad along the course [blocks]: a little longer at speed. */
    public double sternLength(double speed) {
        return 1.2 + speedFactor(speed);
    }

    /** Whether a camera at {@code camY} is near enough to the sea at {@code seaY} for a wake to be worth spawning. */
    public boolean inRange(double camY, double seaY) {
        return camY > seaY - 8.0 && camY < seaY + radius;
    }

    /** Whether a ship at horizontal offset {@code (dx, dz)} from the camera is within {@link #radius}. */
    public boolean withinRadius(double dx, double dz) {
        return dx * dx + dz * dz <= radius * radius;
    }

    private static double clamp01(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }

    private static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0 - 2.0 * c);
    }
}

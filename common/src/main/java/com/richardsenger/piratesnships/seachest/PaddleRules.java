package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.survival.cold.ColdWaterRules;

/**
 * Paddling a floating sea chest (work package SC2, docs/design.md §11 "With a paddle"). Pure, per game tick, in
 * blocks, ticks and degrees.
 *
 * <p><b>Input:</b> the rider's movement keys as vanilla sends them for every passenger
 * ({@code ServerboundPlayerInputPacket} sets the server player's {@code xxa}/{@code zza}, ±1 per held key), decoded
 * into four bits by {@link Input#of}.
 *
 * <p><b>Thrust:</b> forward accelerates the chest along its heading toward {@code paddle_speed} (back toward
 * {@code paddle_speed × reverse_factor}), with the same balance the wind drift uses: under the water's drag
 * ({@link FloatRules.Params#waterRetention()} of the horizontal velocity kept per tick) a constant acceleration
 * {@code v (1 - r) / r} settles at exactly {@code v}. Wind drift and currents add on top.
 *
 * <p><b>Turning:</b> left and right turn the heading by {@code turn_degrees} per second, also standing still. As in
 * vanilla, yaw 0 faces +Z and a right turn increases the yaw.
 *
 * <p><b>Effort:</b> every tick with a stroke (any key, paddle in hand) costs what swimming the stroke's still-water
 * distance would: vanilla's 0.01 exhaustion per block × {@code hunger_factor}.
 */
public final class PaddleRules {

    /** Vanilla's swimming exhaustion per block ({@code ServerPlayer.checkMovementStatistics}). */
    public static final float SWIM_EXHAUSTION_PER_BLOCK = 0.01F;
    /** A key counts as held from this much of vanilla's ±1 impulse (it decays by 0.98 per tick between packets). */
    public static final float KEY_THRESHOLD = 0.1F;

    /** The four movement keys of the rider. */
    public record Input(boolean forward, boolean back, boolean left, boolean right) {
        public static final Input NONE = new Input(false, false, false, false);

        /** Decodes vanilla's passenger input: {@code zza} > 0 forward, {@code xxa} > 0 left (vanilla's leftImpulse). */
        public static Input of(float xxa, float zza) {
            return new Input(zza > KEY_THRESHOLD, zza < -KEY_THRESHOLD, xxa > KEY_THRESHOLD, xxa < -KEY_THRESHOLD);
        }

        /** Net forward command: 1, -1 or 0 (both keys cancel). */
        public int thrust() {
            return (forward ? 1 : 0) - (back ? 1 : 0);
        }

        /** Net turn command: 1 right, -1 left, 0. */
        public int turn() {
            return (right ? 1 : 0) - (left ? 1 : 0);
        }

        public boolean any() {
            return thrust() != 0 || turn() != 0;
        }
    }

    /**
     * @param speed         paddling speed in still water [blocks/s]
     * @param reverseFactor backing speed as a fraction of {@code speed}
     * @param turnDegrees   turn rate [degrees/s]
     * @param hungerFactor  hunger as a multiple of vanilla's swimming hunger for the same distance
     */
    public record Params(double speed, double reverseFactor, double turnDegrees, double hungerFactor) {
    }

    /** One tick of paddling: heading change, horizontal acceleration {x, z} and whether a stroke was made. */
    public record Stroke(float yawDelta, double accelX, double accelZ, boolean paddling) {
        public static final Stroke NONE = new Stroke(0f, 0.0, 0.0, false);
    }

    private PaddleRules() {
    }

    /**
     * The stroke for one tick.
     *
     * @param canPaddle whether the rider makes way: paddling enabled, a paddle in hand, the chest afloat. Without it
     *                  the chest only drifts.
     * @param yaw       the chest's heading now [degrees]
     */
    public static Stroke stroke(Input in, boolean canPaddle, float yaw, Params p, FloatRules.Params water) {
        if (!canPaddle || !in.any()) return Stroke.NONE;
        float yawDelta = (float) (in.turn() * p.turnDegrees() / 20.0);
        double target = targetSpeed(in, p) / 20.0;
        double r = water.waterRetention();
        double accel = target * (1.0 - r) / r;
        double rad = Math.toRadians(yaw + yawDelta);
        return new Stroke(yawDelta, -Math.sin(rad) * accel, Math.cos(rad) * accel, true);
    }

    /** Signed still-water speed the input paddles toward [blocks/s]: forward positive, backing negative. */
    public static double targetSpeed(Input in, Params p) {
        int t = in.thrust();
        if (t > 0) return p.speed();
        if (t < 0) return -p.speed() * p.reverseFactor();
        return 0.0;
    }

    /**
     * Exhaustion for one tick: a stroke costs swimming the distance it covers in still water at full speed (a turn on
     * the spot or backing counts like a forward stroke) × {@code hungerFactor}. Zero without a stroke.
     */
    public static float exhaustion(Stroke s, Params p) {
        if (!s.paddling() || p.hungerFactor() <= 0.0) return 0.0F;
        return (float) (SWIM_EXHAUSTION_PER_BLOCK * p.speed() / 20.0 * p.hungerFactor());
    }

    /**
     * The cold-water view of a chest's rider (docs/design.md §14 through {@link ColdWaterRules}): a sea chest is no
     * boat, it rides low and the rider's legs hang in the water, so riding it does not exempt them and they count as
     * in the water whenever the chest is. Creative, leather armour and the warm effect still protect.
     */
    public static ColdWaterRules.Subject riderInColdWater(boolean alive, boolean chestInWater, boolean coldBiome,
                                                          boolean creative, boolean canFreeze, boolean warm) {
        return new ColdWaterRules.Subject(alive, chestInWater, coldBiome, creative, canFreeze, false, false, warm, () -> false);
    }
}

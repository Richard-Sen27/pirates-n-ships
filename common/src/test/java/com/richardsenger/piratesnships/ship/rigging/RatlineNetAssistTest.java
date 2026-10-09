package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetAssist.Frame;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetAssist.Move;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RatlineNetAssist#move} (RL3): the climb in the plot frame composed with the ship's motion at the climber's feet
 * in the world, and a climber kept on a 62° net while its ship sails and turns, whether the assist or Sable carries it.
 */
class RatlineNetAssistTest {

    private static final double EPS = 1.0e-9;
    /** The game's 62° net: ring A 0.3 over a chainplate, ring B 0.3 off a mast four blocks up and two across. */
    private static final Point A = new Point(0.5, 0.3, 0.5);
    private static final Point B = new Point(2.7, 4.5, 0.5);

    /**
     * A ship as a rigid body turning about the vertical: its plot point {@code centre} lies at world {@code position}, the
     * plot is yawed by {@code yaw}; per tick it moves by {@code linear} and turns by {@code yawRate} radians.
     * {@link #velocityAt} is the exact displacement of a plot point over the next tick (what Sable's client velocity is,
     * the pose change of a tick).
     */
    private static final class Ship implements Frame {
        final Vec3 centre;
        final Vec3 linear;
        final double yawRate;
        Vec3 position;
        double yaw;
        boolean carried;

        Ship(Vec3 centre, Vec3 position, double yaw, Vec3 linear, double yawRate) {
            this.centre = centre;
            this.position = position;
            this.yaw = yaw;
            this.linear = linear;
            this.yawRate = yawRate;
        }

        Quaterniond q(double angle) {
            return new Quaterniond().rotateY(angle);
        }

        Vec3 toWorld(Vec3 local, Vec3 pos, double angle) {
            return RatlineNetAssist.rotate(q(angle), local.subtract(centre)).add(pos);
        }

        Vec3 toWorld(Vec3 local) {
            return toWorld(local, position, yaw);
        }

        void step() {
            position = position.add(linear);
            yaw += yawRate;
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return RatlineNetAssist.rotate(q(yaw).conjugate(), world.subtract(position)).add(centre);
        }

        @Override
        public Vec3 dirToWorld(Vec3 local) {
            return RatlineNetAssist.rotate(q(yaw), local);
        }

        @Override
        public Vec3 dirToLocal(Vec3 world) {
            return RatlineNetAssist.rotate(q(yaw).conjugate(), world);
        }

        @Override
        public Vec3 velocityAt(Vec3 local) {
            return toWorld(local, position.add(linear), yaw + yawRate).subtract(toWorld(local));
        }

        @Override
        public boolean carries(Entity entity) {
            return carried;
        }
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-6, "x of " + actual);
        assertEquals(expected.y, actual.y, 1.0e-6, "y of " + actual);
        assertEquals(expected.z, actual.z, 1.0e-6, "z of " + actual);
    }

    private static double lineDistance(Vec3 p) {
        double dx = B.x() - A.x(), dy = B.y() - A.y(), dz = B.z() - A.z();
        double t = ((p.x - A.x()) * dx + (p.y - A.y()) * dy + (p.z - A.z()) * dz) / (dx * dx + dy * dy + dz * dz);
        t = Math.max(0, Math.min(1, t));
        return p.distanceTo(new Vec3(A.x() + dx * t, A.y() + dy * t, A.z() + dz * t));
    }

    @Test
    void theClimbTurnsWithTheShipAndTheShipsMotionIsCarried() {
        // a ship yawed a quarter turn, sailing at 0.2 blocks/tick: the plot's +x is the world's -z
        Ship ship = new Ship(Vec3.ZERO, new Vec3(100, 64, 100), Math.PI / 2, new Vec3(0.2, 0, 0), 0);
        Move move = RatlineNetAssist.move(ship, new Vec3(0.06, 0.1176, 0), new Vec3(1, 1, 0), false);
        assertVec(new Vec3(0, 0.1176, -0.06), move.velocity());
        assertVec(new Vec3(0.2, 0, 0), move.carry());
    }

    @Test
    void aTurningShipCarriesAPointAtItsRadius() {
        // turning 0.015 rad/tick about plot (0, 0, 0): a point 2 blocks out along x moves by about ω·r, across the radius
        Ship ship = new Ship(Vec3.ZERO, new Vec3(0, 64, 0), 0, Vec3.ZERO, 0.015);
        Vec3 carry = RatlineNetAssist.move(ship, Vec3.ZERO, new Vec3(2, 0, 0), false).carry();
        assertEquals(2 * 0.015, carry.length(), 1.0e-5);
        assertEquals(0, carry.x * 1 + carry.y * 0, 1.0e-3, "not across the radius: " + carry);
        // on the axis nothing moves
        assertVec(Vec3.ZERO, RatlineNetAssist.move(ship, Vec3.ZERO, new Vec3(0, 3, 0), false).carry());
    }

    @Test
    void whenSableCarriesTheClimberTheAssistAddsNothing() {
        Ship ship = new Ship(Vec3.ZERO, new Vec3(0, 64, 0), 0.3, new Vec3(0.2, 0.01, -0.1), 0.02);
        Move move = RatlineNetAssist.move(ship, new Vec3(0, 0.1176, 0), new Vec3(2, 1, 0), true);
        assertVec(Vec3.ZERO, move.carry());
        assertVec(new Vec3(0, 0.1176, 0), move.velocity());
    }

    /**
     * Climbs the 62° net for {@code ticks} on a ship sailing at {@code linear} and turning at {@code yawRate}; the climber
     * is carried by the assist ({@code assistCarries}, a climber Sable does not track), by Sable (its pose change, the
     * tracked case) or by no one (RL2, the velocity set absolutely). Returns {farthest from the line up to the closest
     * approach to ring B, that closest approach}.
     */
    private static double[] climb(Vec3 linear, double yawRate, int ticks, Carrier carrier) {
        Ship ship = new Ship(new Vec3(1.5, 0, 0.5), new Vec3(50, 64, 50), 0.4, linear, yawRate);
        ship.carried = carrier == Carrier.SABLE;
        Vec3 feet = ship.toWorld(new Vec3(A.x(), A.y(), A.z()));
        double worst = 0, worstSoFar = 0, best = Double.POSITIVE_INFINITY;
        for (int t = 0; t < ticks; t++) {
            Vec3 local = ship.toLocal(feet);
            double[] out = RatlineNetRules.assist(A, B, new Point(local.x, local.y, local.z), RatlineNetAssist.CLIMB_SPEED);
            Move move = RatlineNetAssist.move(ship, new Vec3(out[0], out[1], out[2]), local, ship.carried);
            Vec3 carry = carrier == Carrier.NONE ? Vec3.ZERO : move.carry();
            if (carrier == Carrier.SABLE) {
                // Sable moves a tracked entity by the pose change of the tick
                carry = ship.velocityAt(local);
            }
            feet = feet.add(move.velocity()).add(carry);
            ship.step();
            Vec3 now = ship.toLocal(feet);
            worstSoFar = Math.max(worstSoFar, lineDistance(now));
            double toB = now.distanceTo(new Vec3(B.x(), B.y(), B.z()));
            if (toB < best) {
                // up to the top: past ring B the climber leaves the net into the hoop
                best = toB;
                worst = worstSoFar;
            }
        }
        return new double[] {worst, best};
    }

    private enum Carrier { ASSIST, SABLE, NONE }

    @Test
    void aClimberKeepsToTheNetOfAShipUnderWay() {
        for (Carrier carrier : new Carrier[] {Carrier.ASSIST, Carrier.SABLE}) {
            double[] r = climb(new Vec3(0.2, 0, 0.05), 0, 60, carrier);
            assertTrue(r[0] < 0.05, carrier + ": " + r[0] + " blocks off the line");
            assertTrue(r[1] < 0.1, carrier + ": only within " + r[1] + " of ring B");
        }
        // RL2: nobody carries the climber, it falls behind the net
        double[] lag = climb(new Vec3(0.2, 0, 0.05), 0, 60, Carrier.NONE);
        assertTrue(lag[0] > 0.5 || lag[1] > 0.7, "an uncarried climber kept to the net: " + lag[0] + ", " + lag[1]);
    }

    @Test
    void aClimberKeepsToTheNetOfATurningHeavingShip() {
        for (Carrier carrier : new Carrier[] {Carrier.ASSIST, Carrier.SABLE}) {
            double[] r = climb(new Vec3(0.1, 0.02, 0), 0.015, 60, carrier);
            assertTrue(r[0] < 0.05, carrier + ": " + r[0] + " blocks off the line");
            assertTrue(r[1] < 0.1, carrier + ": only within " + r[1] + " of ring B");
        }
    }

    @Test
    void overTheTopRingTheClimbGoesStraightUpWithTheClimbersOwnMotion() {
        // feet 0.2 over ring B, climbing on, drifting towards the mast: no pull back to B, own sideways motion kept
        double[] up = RatlineNetRules.assist(A, B, new Point(2.6, 4.7, 0.5), 0.1176, 0.05, -0.3);
        assertEquals(0.05, up[0], EPS);
        assertEquals(0.1176, up[1], EPS);
        assertEquals(-RatlineNetRules.CLIMB_HORIZONTAL_LIMIT, up[2], EPS);
        // just under ring B the pull still holds the climber on the line
        double[] below = RatlineNetRules.assist(A, B, new Point(2.4, 4.4, 0.9), 0.1176, 0.05, 0);
        assertTrue(below[2] < 0, "not pulled back towards the line: " + below[2]);
        // holding or climbing down over the top: pulled back down onto ring B
        double[] hold = RatlineNetRules.assist(A, B, new Point(2.6, 4.9, 0.5), 0, 0.1, 0);
        assertTrue(hold[1] < 0 && hold[0] > 0, "not pulled back onto ring B: " + java.util.Arrays.toString(hold));
    }

    @Test
    void theLiftOffTheTopEndsAStepOverTheTopFitting() {
        net.minecraft.core.BlockPos top = new net.minecraft.core.BlockPos(2, 4, 0);
        assertTrue(RatlineNetAssist.liftingOff(top, new Vec3(2.7, 5.0, 0.5)));
        assertTrue(RatlineNetAssist.liftingOff(top, new Vec3(2.7, 5.0 + RatlineNetAssist.EXIT_LIFT - 0.01, 0.5)));
        assertTrue(!RatlineNetAssist.liftingOff(top, new Vec3(2.7, 5.0 + RatlineNetAssist.EXIT_LIFT, 0.5)), "lifted on");
        assertTrue(!RatlineNetAssist.liftingOff(top, new Vec3(2.7, 4.9, 0.5)), "still in the fitting");
        assertTrue(!RatlineNetAssist.liftingOff(top, new Vec3(3.1, 5.2, 0.5)), "beside the column");
    }

    @Test
    void onAStillShipTheMoveIsTheClimbAlone() {
        Ship ship = new Ship(Vec3.ZERO, new Vec3(0, 64, 0), 1.0, Vec3.ZERO, 0);
        Move move = RatlineNetAssist.move(ship, new Vec3(0.05, 0.1176, 0.02), new Vec3(1, 2, 3), false);
        assertTrue(move.carry().lengthSqr() < EPS);
        assertEquals(new Vec3(0.05, 0.1176, 0.02).length(), move.velocity().length(), 1.0e-9);
    }
}

package com.richardsenger.piratesnships.ship.decor.flag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

/** The flag's downwind facing and the world-to-ship-frame turn of the wind (§4.7, §5.1). */
class FlagWindTest {

    private static final double EPS = 1.0e-9;

    private static Quaterniond yaw(double degrees) {
        return new Quaterniond().rotateAxis(Math.toRadians(degrees), 0, 1, 0);
    }

    @Test
    void downwindSnapsToTheDominantAxis() {
        assertEquals(Direction.SOUTH, FlagWind.downwind(0, 1, Direction.NORTH));
        assertEquals(Direction.NORTH, FlagWind.downwind(0.2, -1, Direction.SOUTH));
        assertEquals(Direction.EAST, FlagWind.downwind(1, 0.3, Direction.NORTH));
        assertEquals(Direction.WEST, FlagWind.downwind(-1, -0.3, Direction.NORTH));
    }

    @Test
    void calmKeepsTheCurrentFacing() {
        assertEquals(Direction.EAST, FlagWind.downwind(0, 0, Direction.EAST));
        assertEquals(Direction.EAST, FlagWind.downwindOnShip(0, 0, yaw(90), Direction.EAST));
    }

    @Test
    void identityOrientationLeavesTheWindUnchanged() {
        assertArrayEquals(new double[]{0.3, -0.8}, FlagWind.shipFrame(0.3, -0.8, new Quaterniond()), EPS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            assertEquals(d, FlagWind.downwindOnShip(d.getStepX(), d.getStepZ(), new Quaterniond(), Direction.UP));
        }
    }

    @Test
    void quarterTurnMapsTheWindIntoThePlotFrame() {
        // A +90 degree yaw turns plot west (-X) to world south (+Z): a wind blowing south blows toward plot west.
        assertArrayEquals(new double[]{-1, 0}, FlagWind.shipFrame(0, 1, yaw(90)), EPS);
        assertEquals(Direction.WEST, FlagWind.downwindOnShip(0, 1, yaw(90), Direction.NORTH));
        assertEquals(Direction.SOUTH, FlagWind.downwindOnShip(1, 0, yaw(90), Direction.NORTH));
        assertEquals(Direction.EAST, FlagWind.downwindOnShip(0, 1, yaw(-90), Direction.NORTH));
    }

    @Test
    void halfTurnReversesTheWind() {
        assertArrayEquals(new double[]{-0.3, 0.8}, FlagWind.shipFrame(0.3, -0.8, yaw(180)), EPS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            assertEquals(d.getOpposite(), FlagWind.downwindOnShip(d.getStepX(), d.getStepZ(), yaw(180), Direction.UP));
        }
    }

    @Test
    void shipFrameRoundTripsThroughTheOrientation() {
        Quaterniond q = yaw(37).rotateAxis(Math.toRadians(8), 0, 0, 1); // turned and heeled
        double[] local = FlagWind.shipFrame(0.6, 0.8, q);
        org.joml.Vector3d back = q.transform(new org.joml.Vector3d(local[0], 0, local[1]));
        // Heel drops a little of the wind into the plot's vertical; the horizontal heading survives.
        assertEquals(Math.atan2(0.8, 0.6), Math.atan2(back.z, back.x), 0.02);
    }

    @Test
    void shipFrameDoesNotMutateTheOrientation() {
        Quaterniond q = yaw(90);
        Quaterniond copy = new Quaterniond(q);
        FlagWind.shipFrame(1, 0, q);
        assertEquals(copy, q);
    }
}

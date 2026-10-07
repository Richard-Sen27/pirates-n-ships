package com.richardsenger.piratesnships.crew.hammock;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HM2: the sleeper's yaw from the hammock's facing and the ship's orientation, checked through the render frame:
 * GeckoLib turns the model by {@code 180 − bodyYaw} about +y and has no mirror on z, so the model's back (file +z, where
 * the {@code sleep} animation lays the head) points to {@code -forward(yaw)} in the world. That must be the world
 * direction of the head half ({@code FACING}, turned by the ship).
 */
class SleepAxisTest {

    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** World direction opposite the entity's forward (-sin yaw, 0, cos yaw). */
    private static Vector3d behind(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector3d(Math.sin(r), 0, -Math.cos(r));
    }

    /** Direction of the file's +z after GeckoLib's turn by 180 - yaw about +y (right-handed, as Axis.YP). */
    private static Vector3d modelBack(float yaw) {
        return new Quaterniond().rotateY(Math.toRadians(180 - yaw)).transform(new Vector3d(0, 0, 1));
    }

    private static void assertDir(Vector3d expected, Vector3d actual, String what) {
        assertEquals(expected.x, actual.x, 1e-5, what + " x");
        assertEquals(expected.y, actual.y, 1e-5, what + " y");
        assertEquals(expected.z, actual.z, 1e-5, what + " z");
    }

    @Test
    void inTheWorldTheHeadLiesOverTheHeadHalf() {
        for (Direction facing : FACINGS) {
            float yaw = SleepAxis.yaw(facing, null);
            Vector3d toHead = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
            assertDir(toHead, behind(yaw), facing + " head");
            assertDir(toHead, modelBack(yaw), facing + " model back");
        }
    }

    @Test
    void worldYawsFaceTheFootHalf() {
        assertEquals(0f, SleepAxis.yaw(Direction.NORTH, null), 1e-4); // faces south, head north
        assertEquals(90f, SleepAxis.yaw(Direction.EAST, null), 1e-4); // faces west
        assertEquals(-180f, SleepAxis.yaw(Direction.SOUTH, null), 1e-4); // faces north
        assertEquals(-90f, SleepAxis.yaw(Direction.WEST, null), 1e-4); // faces east
    }

    @Test
    void onAShipTheAxisTurnsWithTheShip() {
        for (double turn : new double[] {0, 37, 90, 180, -90, 271}) {
            Quaterniond ship = new Quaterniond().rotateAxis(Math.toRadians(turn), 0, 1, 0);
            for (Direction facing : FACINGS) {
                float yaw = SleepAxis.yaw(facing, ship);
                Vector3d toHead = ship.transform(new Vector3d(facing.getStepX(), 0, facing.getStepZ()));
                assertDir(toHead, behind(yaw), facing + " on a ship turned " + turn);
                assertDir(toHead, modelBack(yaw), facing + " model back on a ship turned " + turn);
            }
        }
    }

    @Test
    void aShipTurned90DegreesTurnsAnEastHammockNorth() {
        // +90 degrees about +y maps plot east (+x) to world north (-z): the head lies north, the sleeper faces south
        Quaterniond ship = new Quaterniond().rotateAxis(Math.toRadians(90), 0, 1, 0);
        assertEquals(0f, SleepAxis.yaw(Direction.EAST, ship), 1e-3);
    }

    @Test
    void heelingAndPitchingDoNotTurnTheAxis() {
        Quaterniond heel = new Quaterniond().rotateAxis(Math.toRadians(25), 1, 0, 0); // about the hammock's own axis
        assertEquals(SleepAxis.yaw(Direction.EAST, null), SleepAxis.yaw(Direction.EAST, heel), 1e-3);
        Quaterniond turnedAndPitched = new Quaterniond().rotateAxis(Math.toRadians(90), 0, 1, 0)
                .rotateAxis(Math.toRadians(15), 0, 0, 1);
        assertEquals(0f, SleepAxis.yaw(Direction.EAST, turnedAndPitched), 1e-3);
    }

    @Test
    void continuousStaysWithinHalfATurn() {
        assertEquals(181f, SleepAxis.continuous(179f, -179f), 1e-4);
        assertEquals(-181f, SleepAxis.continuous(-179f, 179f), 1e-4);
        assertEquals(450f, SleepAxis.continuous(440f, 90f), 1e-4);
        assertEquals(10f, SleepAxis.continuous(0f, 10f), 1e-4);
    }
}

package com.richardsenger.piratesnships.sailing.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.ship.RudderSteps.Click;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Pure logic of D3b: rudder steps and clicks, rudder position, anchor ground search, persisted anchor codec. */
class ShipControlsLogicTest {

    // ------------------------------------------------------------------ rudder steps

    @Test
    void clicksStepAndStopAtTheLimit() {
        int s = 0;
        s = RudderSteps.apply(s, Click.STARBOARD, 3);
        assertEquals(1, s);
        s = RudderSteps.apply(s, Click.STARBOARD, 3);
        s = RudderSteps.apply(s, Click.STARBOARD, 3);
        s = RudderSteps.apply(s, Click.STARBOARD, 3);
        assertEquals(3, s, "stops at stepsPerSide");
        assertEquals(0, RudderSteps.apply(s, Click.MIDSHIPS, 3));
        assertEquals(2, RudderSteps.apply(s, Click.PORT, 3));
        int p = 0;
        for (int i = 0; i < 9; i++) p = RudderSteps.apply(p, Click.PORT, 3);
        assertEquals(-3, p);
    }

    @Test
    void storedStepBeyondAReducedLimitIsClamped() {
        assertEquals(2, RudderSteps.apply(5, Click.STARBOARD, 2));
        assertEquals(1, RudderSteps.apply(5, Click.PORT, 2));
        assertEquals(35.0, RudderSteps.angle(5, 2, 35.0), 1e-9);
    }

    @Test
    void angleIsProportionalAndSigned() {
        assertEquals(0.0, RudderSteps.angle(0, 3, 35.0), 1e-9);
        assertEquals(35.0 / 3, RudderSteps.angle(1, 3, 35.0), 1e-9);
        assertEquals(-35.0, RudderSteps.angle(-3, 3, 35.0), 1e-9);
        assertEquals(30.0, RudderSteps.angle(1, 1, 30.0), 1e-9);
    }

    @Test
    void propertyMappingRoundTripsAndMidshipsIsFive() {
        for (int s = -RudderSteps.MAX_STEPS; s <= RudderSteps.MAX_STEPS; s++) {
            assertEquals(s, RudderSteps.fromProperty(RudderSteps.toProperty(s)));
        }
        assertEquals(5, RudderSteps.toProperty(0));
        assertEquals(10, RudderSteps.toProperty(99));
    }

    @Test
    void clickSideIsSeenFromTheHelmsman() {
        // facing north (0,-1): helmsman north of the helm looking south, his right hand is west (-x)
        assertEquals(Click.STARBOARD, RudderSteps.click(-0.4, -0.5, 0, -1));
        assertEquals(Click.PORT, RudderSteps.click(0.4, -0.5, 0, -1));
        assertEquals(Click.MIDSHIPS, RudderSteps.click(0.1, -0.5, 0, -1));
        // facing south (0,1): looking north, right is east (+x)
        assertEquals(Click.STARBOARD, RudderSteps.click(0.4, 0.5, 0, 1));
        // facing east (1,0): looking west, right is north (-z)
        assertEquals(Click.STARBOARD, RudderSteps.click(0.5, -0.4, 1, 0));
        assertEquals(Click.PORT, RudderSteps.click(0.5, 0.4, 1, 0));
        // facing west (-1,0): looking east, right is south (+z)
        assertEquals(Click.STARBOARD, RudderSteps.click(-0.5, 0.4, -1, 0));
    }

    @Test
    void starboardClickAgreesWithTheShipFrame() {
        // for every helm facing, the helmsman's right hand is the ship's starboard (−PORT) of the bow frame
        int[][] facings = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (int[] f : facings) {
            BowFrame bow = BowFrame.fromHelmFacing(f[0], f[1]);
            Vector3d starboardPlot = bow.toPlot(new Vector3d(ShipFrame.PORT).negate(), new Vector3d());
            assertEquals(Click.STARBOARD, RudderSteps.click(starboardPlot.x * 0.4, starboardPlot.z * 0.4, f[0], f[1]),
                    "facing " + f[0] + "," + f[1]);
        }
    }

    // ------------------------------------------------------------------ rudder position

    @Test
    void rudderSitsAtTheSternKeelForAllBows() {
        int[] b = {10, 64, 20, 14, 67, 28}; // 5 wide (x), 9 long (z), bottom y 64
        // bow +Z: stern face at minZ
        assertVec(12.5, 64.5, 20, HullPoints.rudderPlot(new BowFrame(0, 1), b, new Vector3d()));
        // bow -Z: stern face at maxZ+1
        assertVec(12.5, 64.5, 29, HullPoints.rudderPlot(new BowFrame(0, -1), b, new Vector3d()));
        // bow +X: stern face at minX
        assertVec(10, 64.5, 24.5, HullPoints.rudderPlot(new BowFrame(1, 0), b, new Vector3d()));
        // bow -X: stern face at maxX+1
        assertVec(15, 64.5, 24.5, HullPoints.rudderPlot(new BowFrame(-1, 0), b, new Vector3d()));
    }

    @Test
    void rudderIsAsternInTheShipFrame() {
        int[] b = {0, 0, 0, 4, 3, 8};
        Vector3d com = new Vector3d(2.5, 2.0, 4.5);
        for (BowFrame bow : new BowFrame[] {new BowFrame(0, 1), new BowFrame(0, -1), new BowFrame(1, 0), new BowFrame(-1, 0)}) {
            Vector3d rel = HullPoints.rudderPlot(bow, b, new Vector3d()).sub(com);
            Vector3d ship = bow.toShip(rel, new Vector3d());
            assertTrue(ship.z < -2.0, bow + ": rudder not astern: " + ship);
            assertEquals(0.0, ship.x, 1e-9, bow + ": rudder off the centerline");
            assertTrue(ship.y < 0, bow + ": rudder above the COM");
        }
    }

    // ------------------------------------------------------------------ anchor ground

    @Test
    void anchorLandsOnTheFirstSolidBlockWithinReach() {
        // capstan at y=9, water down to y=2, stone at y=1: lands on top of the stone (y=2)
        assertEquals(OptionalInt.of(2), AnchorGround.floorY(9, 32, y -> y <= 1));
        assertEquals(OptionalInt.of(2), AnchorGround.floorY(9, 8, y -> y <= 1), "exactly in reach");
        assertEquals(OptionalInt.empty(), AnchorGround.floorY(9, 7, y -> y <= 1), "one block short");
        assertEquals(OptionalInt.empty(), AnchorGround.floorY(9, 3, y -> y <= 1));
        assertEquals(OptionalInt.of(9), AnchorGround.floorY(9, 1, y -> y == 8), "ground right below");
    }

    // ------------------------------------------------------------------ persistence

    @Test
    void shipAnchorRoundTripsThroughNbtAndJson() {
        for (AnchorState s : new AnchorState[] {AnchorState.RAISED.drop(), AnchorState.HOLDING, AnchorState.RAISING}) {
            ShipAnchor a = new ShipAnchor(s, new Vec3(12.5, 2.0, -3000.25), new Vec3(0.25, -4.0, 1.5), 7.125,
                    s == AnchorState.HOLDING, new BlockPos(20_000_001, 70, -19_999_000), new Vec3(20_000_003.9, 69.75, -19_998_999.5));
            var nbt = ShipAnchor.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow();
            assertEquals(a, ShipAnchor.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
            var json = ShipAnchor.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
            assertEquals(a, ShipAnchor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    private static void assertVec(double x, double y, double z, Vector3d v) {
        assertEquals(x, v.x, 1e-9, "x of " + v);
        assertEquals(y, v.y, 1e-9, "y of " + v);
        assertEquals(z, v.z, 1e-9, "z of " + v);
    }
}

package com.richardsenger.piratesnships.ship.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RejoinTransformTest {

    /** An L-shaped piece far out in its own plot (plot coordinates are large in Sable). */
    private static final List<BlockPos> PIECE = List.of(
            new BlockPos(160_008, 100, 160_008), new BlockPos(160_009, 100, 160_008), new BlockPos(160_010, 100, 160_008),
            new BlockPos(160_010, 100, 160_009), new BlockPos(160_010, 101, 160_009));

    /**
     * Where the piece's block centers really are in the keeper's plot when the absorbed frame is turned by
     * {@code yawDegrees} (JOML rotateY, the frame convention of {@link DisassemblyMath}) and tilted by
     * {@code tiltDegrees} about X, with its first block's center at {@code at}.
     */
    private static List<Vec3> exact(double yawDegrees, double tiltDegrees, Vec3 at) {
        Quaterniond q = rotation(yawDegrees, tiltDegrees);
        Vec3 a = Vec3.atCenterOf(PIECE.get(0));
        List<Vec3> out = new ArrayList<>();
        for (BlockPos p : PIECE) {
            Vec3 d = Vec3.atCenterOf(p).subtract(a);
            Vector3d v = q.transform(new Vector3d(d.x, d.y, d.z));
            out.add(new Vec3(v.x + at.x, v.y + at.y, v.z + at.z));
        }
        return out;
    }

    private static Quaterniond rotation(double yawDegrees, double tiltDegrees) {
        return new Quaterniond().rotateY(Math.toRadians(yawDegrees)).rotateX(Math.toRadians(tiltDegrees));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void snapsEveryQuarterTurn(int turns) {
        double yaw = 90.0 * turns + 2.0; // a little off
        Vec3 at = new Vec3(320_004.5 + 0.2, 64.5 - 0.1, 320_011.5 + 0.15);
        RejoinTransform.Fit fit = RejoinTransform.fit(rotation(yaw, 0), PIECE, exact(yaw, 0, at));
        assertEquals(turns, fit.transform().turns());
        assertEquals(new BlockPos(320_004, 64, 320_011), fit.transform().target());
        assertEquals(2.0, fit.yawErrorDegrees(), 1e-6);
        assertTrue(fit.aligned(5, 0.5), "a 2° and 0.27 block error is within the limits: " + fit);
        // the snapped blocks are the exact ones rounded to their cells
        List<Vec3> perfect = exact(90.0 * turns, 0, Vec3.atCenterOf(fit.transform().target()));
        for (int i = 0; i < PIECE.size(); i++) {
            assertEquals(BlockPos.containing(perfect.get(i)), fit.transform().apply(PIECE.get(i)), "block " + i + " at " + turns + " turns");
        }
    }

    @Test
    void quarterTurnIsCounterClockwiseLikeSable() {
        // one counter-clockwise quarter turn (seen from above) takes east (+x) to north (−z)
        RejoinTransform t = new RejoinTransform(1, BlockPos.ZERO, new BlockPos(100, 0, 100));
        assertEquals(new BlockPos(100, 0, 99), t.apply(new BlockPos(1, 0, 0)));
        assertEquals(new Vec3(100.5, 0.5, 98.5), t.apply(new Vec3(2.5, 0.5, 0.5)));
    }

    @Test
    void relativeRotationIsKeeperInverseTimesAbsorbed() {
        Quaterniond keeper = new Quaterniond().rotateY(Math.toRadians(37));
        Quaterniond absorbed = new Quaterniond().rotateY(Math.toRadians(37 + 90));
        RejoinTransform.Fit fit = RejoinTransform.fit(RejoinTransform.relative(keeper, absorbed), PIECE,
                exact(90, 0, new Vec3(0.5, 0.5, 0.5)));
        assertEquals(1, fit.transform().turns());
        assertEquals(0.0, fit.yawErrorDegrees(), 1e-6);
    }

    @Test
    void angleLimit() {
        assertTrue(RejoinTransform.fit(rotation(4.9, 0), PIECE, exact(4.9, 0, Vec3.ZERO.add(0.5, 0.5, 0.5))).aligned(5, 0.5));
        assertFalse(RejoinTransform.fit(rotation(5.5, 0), PIECE, exact(5.5, 0, new Vec3(0.5, 0.5, 0.5))).aligned(5, 0.5));
        assertFalse(RejoinTransform.fit(rotation(30, 0), PIECE, exact(30, 0, new Vec3(0.5, 0.5, 0.5))).aligned(5, 0.5));
        assertFalse(RejoinTransform.fit(rotation(84, 0), PIECE, exact(84, 0, new Vec3(0.5, 0.5, 0.5))).aligned(5, 0.5));
        assertTrue(RejoinTransform.fit(rotation(86, 0), PIECE, exact(86, 0, new Vec3(0.5, 0.5, 0.5))).aligned(5, 0.5));
        // tilt: within 5° of level
        assertTrue(RejoinTransform.fit(rotation(0, 4), PIECE, exact(0, 4, new Vec3(0.5, 0.5, 0.5))).aligned(5, 0.5));
        RejoinTransform.Fit tilted = RejoinTransform.fit(rotation(0, 8), PIECE, exact(0, 8, new Vec3(0.5, 0.5, 0.5)));
        assertEquals(8.0, tilted.tiltDegrees(), 1e-6);
        assertFalse(tilted.aligned(5, 0.5));
    }

    @Test
    void gapLimit() {
        RejoinTransform.Fit near = RejoinTransform.fit(rotation(0, 0), PIECE, exact(0, 0, new Vec3(10.5 + 0.4, 0.5, 0.5)));
        assertEquals(0.4, near.gap(), 1e-9);
        assertTrue(near.aligned(5, 0.5));
        RejoinTransform.Fit off = RejoinTransform.fit(rotation(0, 0), PIECE, exact(0, 0, new Vec3(10.5 + 0.4, 0.5 + 0.4, 0.5)));
        assertEquals(Math.hypot(0.4, 0.4), off.gap(), 1e-9);
        assertFalse(off.aligned(5, 0.5), "0.57 blocks off the grid");
        // 1.2 blocks further: snaps to one block further, 0.2 off
        RejoinTransform.Fit further = RejoinTransform.fit(rotation(0, 0), PIECE, exact(0, 0, new Vec3(10.5 + 1.2, 0.5, 0.5)));
        assertEquals(new BlockPos(11, 0, 0), further.transform().target());
        assertEquals(0.2, further.gap(), 1e-9);
    }
}

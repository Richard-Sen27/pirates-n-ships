package com.richardsenger.piratesnships.ship.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisassemblyMathTest {

    @Test
    void snapsYawToNearestQuarterTurn() {
        assertEquals(0, DisassemblyMath.quarterTurns(new Quaterniond()));
        assertEquals(0, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(44))));
        assertEquals(0, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(-44))));
        assertEquals(1, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(46))));
        assertEquals(1, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(100))));
        assertEquals(2, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(179))));
        assertEquals(2, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(-179))));
        assertEquals(3, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(-90))));
        assertEquals(3, DisassemblyMath.quarterTurns(new Quaterniond().rotationY(Math.toRadians(275))));
    }

    @Test
    void yawIgnoresSmallTilt() {
        Quaterniond q = new Quaterniond().rotationY(Math.toRadians(88)).rotateX(Math.toRadians(4));
        assertEquals(1, DisassemblyMath.quarterTurns(q));
    }

    @Test
    void tiltIsAngleFromLevel() {
        assertEquals(0, DisassemblyMath.tiltDegrees(new Quaterniond().rotationY(1.3)), 1e-9);
        assertEquals(20, DisassemblyMath.tiltDegrees(new Quaterniond().rotateAxis(Math.toRadians(20), 1, 0, 0)), 1e-9);
        assertEquals(7, DisassemblyMath.tiltDegrees(new Quaterniond().rotationY(0.7).rotateZ(Math.toRadians(7))), 1e-9);
    }

    @Test
    void targetMatchesSableAssemblyTransformFormula() {
        // Sable's AssemblyTransform: (center − anchorCenter).yRot(k·π/2) + goalCenter, then BlockPos.containing.
        BlockPos anchor = new BlockPos(10_000 * 16 + 8, 64, -3);
        BlockPos goal = new BlockPos(-37, 70, 512);
        for (int k = 0; k < 4; k++) {
            for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-3, -2, -3), anchor.offset(3, 2, 3))) {
                Vec3 v = p.getCenter().subtract(anchor.getCenter()).yRot((float) (k * Math.PI / 2.0)).add(goal.getCenter());
                assertEquals(BlockPos.containing(v), DisassemblyMath.target(p.immutable(), anchor, goal, k), "k=" + k + " p=" + p);
            }
        }
    }

    @Test
    void entityTargetFollowsBlockTarget() {
        BlockPos anchor = new BlockPos(100, 64, 100);
        BlockPos goal = new BlockPos(5, 60, 5);
        // An entity standing on top of the block east of the anchor.
        Vec3 onDeck = new Vec3(101.5, 65.0, 100.5);
        for (int k = 0; k < 4; k++) {
            BlockPos block = DisassemblyMath.target(anchor.east(), anchor, goal, k);
            Vec3 t = DisassemblyMath.target(onDeck, anchor, goal, k);
            assertEquals(block.getX() + 0.5, t.x, 1e-9);
            assertEquals(block.getY() + 1.0, t.y, 1e-9);
            assertEquals(block.getZ() + 0.5, t.z, 1e-9);
        }
    }
}

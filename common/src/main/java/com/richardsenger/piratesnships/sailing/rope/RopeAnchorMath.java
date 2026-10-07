package com.richardsenger.piratesnships.sailing.rope;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Where a rope ties onto an anchor block (RP1), pure: anchors are mounted like levers (an {@link AttachFace} plus a
 * horizontal facing that points away from the support on a wall), and their tie point sits on the block's middle line,
 * {@code inset} blocks from the block center toward the support (the cleat's horn bar, the ring's eye).
 */
public final class RopeAnchorMath {

    private RopeAnchorMath() {
    }

    /** The direction from the support out to the anchor, as vanilla's {@code FaceAttachedHorizontalDirectionalBlock}. */
    public static Direction outward(AttachFace face, Direction facing) {
        return switch (face) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> facing;
        };
    }

    /** The tie point relative to the block's minimum corner ({@code {x, y, z}}, each in 0..1). */
    public static double[] point(AttachFace face, Direction facing, double inset) {
        Direction out = outward(face, facing);
        return new double[] {
                0.5 - out.getStepX() * inset,
                0.5 - out.getStepY() * inset,
                0.5 - out.getStepZ() * inset};
    }
}

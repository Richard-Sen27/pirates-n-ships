package com.richardsenger.piratesnships.ship.assembly;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Pure math for rejoining a split-off piece to its ship (RS2): how the absorbed piece's plot maps onto the keeper's plot
 * grid. No Sable, no world access.
 *
 * <p>The snapped map is Sable's {@code AssemblyTransform} ({@code api/SubLevelAssemblyHelper.java} l.513-560), which
 * {@code SableShips#moveBlocksBetween} hands the blocks to: {@code anchor} (absorbed plot) lands on {@code target}
 * (keeper plot), rotated by {@code turns} counter-clockwise quarter turns about the anchor's center
 * ({@link DisassemblyMath} convention: {@code Vec3.yRot(k·π/2)} = JOML {@code rotationY(k·π/2)}).
 *
 * @param turns  counter-clockwise quarter turns (0..3) from the absorbed plot frame to the keeper plot frame
 * @param anchor a block of the absorbed piece (absorbed plot coordinates)
 * @param target the keeper plot cell the anchor lands on
 */
public record RejoinTransform(int turns, BlockPos anchor, BlockPos target) {

    /** Where an absorbed plot block lands in the keeper's plot. */
    public BlockPos apply(BlockPos absorbedPlot) {
        return DisassemblyMath.target(absorbedPlot, anchor, target, turns);
    }

    /** Where an absorbed plot position (an entity, a hook point) lands in the keeper's plot. */
    public Vec3 apply(Vec3 absorbedPlot) {
        return DisassemblyMath.target(absorbedPlot, anchor, target, turns);
    }

    /**
     * How well the absorbed piece lies on the keeper's grid.
     *
     * @param transform the snapped map
     * @param tiltDegrees angle between the two pieces' up axes
     * @param yawErrorDegrees distance of the relative yaw from the nearest multiple of 90°
     * @param gap translation error after snapping, in blocks: distance from the pieces' actual offset to the snapped one
     */
    public record Fit(RejoinTransform transform, double tiltDegrees, double yawErrorDegrees, double gap) {

        /** Within {@code maxAngle} of level and of a quarter turn, and within {@code maxGap} of the grid. */
        public boolean aligned(double maxAngle, double maxGap) {
            return tiltDegrees <= maxAngle && yawErrorDegrees <= maxAngle && gap <= maxGap;
        }
    }

    /**
     * The relative rotation from the absorbed plot frame to the keeper plot frame: {@code keeper⁻¹ · absorbed} for
     * body-to-world orientations.
     */
    public static Quaterniond relative(Quaterniondc keeper, Quaterniondc absorbed) {
        return new Quaterniond(keeper).conjugate().mul(absorbed).normalize();
    }

    /**
     * Snaps the absorbed piece onto the keeper's grid.
     *
     * @param relative   rotation from the absorbed plot frame to the keeper plot frame ({@link #relative})
     * @param blocks     the absorbed piece's blocks (absorbed plot), not empty; the first becomes the anchor
     * @param exactCenters where each block's center actually is now, in keeper plot coordinates (same order)
     */
    public static Fit fit(Quaterniondc relative, List<BlockPos> blocks, List<Vec3> exactCenters) {
        if (blocks.isEmpty() || blocks.size() != exactCenters.size()) {
            throw new IllegalArgumentException("blocks and centers must be non-empty and of equal size");
        }
        double tilt = DisassemblyMath.tiltDegrees(relative);
        double yaw = Math.toDegrees(DisassemblyMath.yaw(relative));
        long steps = Math.round(yaw / 90.0);
        int turns = Math.floorMod((int) steps, 4);
        double yawError = Math.abs(yaw - steps * 90.0);

        BlockPos anchor = blocks.get(0);
        Vector3d sum = new Vector3d();
        for (int i = 0; i < blocks.size(); i++) {
            // offset that would put this block exactly where it is: exact − rotated(p − anchor)
            Vec3 rotated = DisassemblyMath.target(Vec3.atCenterOf(blocks.get(i)), anchor, BlockPos.ZERO, turns)
                    .subtract(Vec3.atCenterOf(BlockPos.ZERO));
            Vec3 e = exactCenters.get(i);
            sum.add(e.x - rotated.x, e.y - rotated.y, e.z - rotated.z);
        }
        sum.div(blocks.size());
        Vec3 mean = new Vec3(sum.x, sum.y, sum.z);
        BlockPos target = BlockPos.containing(mean);
        double gap = mean.distanceTo(Vec3.atCenterOf(target));
        return new Fit(new RejoinTransform(turns, anchor, target), tilt, yawError, gap);
    }
}

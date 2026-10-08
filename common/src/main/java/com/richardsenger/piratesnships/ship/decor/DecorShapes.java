package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Outline shapes of the ship decor blocks (ART2). Every hand-made decor model faces north and is turned by its block
 * state like a furnace (east = 90 degrees clockwise seen from above); {@link #rotate} turns a box given in that north
 * frame the same way, so the outline follows the model.
 */
public final class DecorShapes {

    private DecorShapes() {
    }

    /**
     * The box {@code {x0, y0, z0, x1, y1, z1}} (pixels, north frame) turned to {@code facing}: east maps (x, z) to
     * (16 - z, x), south to (16 - x, 16 - z), west to (z, 16 - x). Up and down return the box unchanged.
     */
    public static double[] rotate(double[] box, Direction facing) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        return switch (facing) {
            case EAST -> new double[]{16 - z1, y0, x0, 16 - z0, y1, x1};
            case SOUTH -> new double[]{16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0};
            case WEST -> new double[]{z0, y0, 16 - x1, z1, y1, 16 - x0};
            default -> box.clone();
        };
    }

    /** The union of {@code boxes} (north frame) turned to each horizontal direction. */
    public static Map<Direction, VoxelShape> horizontal(double[]... boxes) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double[] r = rotate(b, d);
                shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
            out.put(d, shape);
        }
        return out;
    }

    /** Shorthand for a box literal. */
    static double[] b(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[]{x0, y0, z0, x1, y1, z1};
    }
}

package com.richardsenger.piratesnships.almanac.client;

import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Where {@code AlmanacRenderer} lays vanilla's book model on the block (ALM1, docs/design.md §3.4), without client
 * classes so a JUnit test can compose it with the model's part transforms.
 *
 * <p>The book model's spine runs along its y axis, its covers ({@code left_lid}, {@code right_lid}) hinge 1 px either
 * side of it in z and point along +x when closed; opening turns them by the lid angle about y, the right one towards +z
 * and the left one towards -z. On the block:
 * <ul>
 *   <li>the spine runs along the block's facing axis (towards the reader) and the open side faces up: model y becomes
 *       the facing axis, model x becomes up, model z the spread across;</li>
 *   <li>closed ({@code open} 0), the whole book is turned a quarter turn about its spine so it lies flat on its right
 *       cover, shifted half a cover sideways so it lies centred; opening turns it back, so it ends lying open with both
 *       covers rising {@code 90° - LID_ANGLE} from the spine. The right cover never dips below the table on the way;</li>
 *   <li>it is lifted just enough that the lowest cover rests on the block's floor.</li>
 * </ul>
 * The lid angle is fixed per {@code open} ({@link #LID_ANGLE} times it; the renderer passes time 0 to the model so
 * vanilla's floating "breath" stays out).
 */
public final class AlmanacBookPose {

    /** The model's cover angle when fully open, in radians: {@code BookModel.setupAnim} at time 0 opens each lid by 1.25. */
    public static final float LID_ANGLE = 1.25f;
    /** The book model scaled up a little to the size of a thick tome (15 px across when open, 11 px along the spine). */
    public static final float SCALE = 1.1f;
    /** The covers hinge this far either side of the spine, in model px (the model's lid offsets). */
    static final float HINGE = 1.0f;
    /** A cover's width in model px: how far a closed book reaches sideways from its spine. */
    static final float COVER = 6.0f;
    /** Clearance above the floor, in model px, against z-fighting. */
    static final float CLEARANCE = 0.02f;

    private AlmanacBookPose() {
    }

    /** How far the book is turned about its spine: -90° closed (lying on its right cover), 0 open. */
    public static float tilt(float open) {
        return (float) (-Math.PI / 2) * (1.0f - open);
    }

    /**
     * The lift of the spine above the block's floor, in model px: the depth the right cover's hinge has sunk below the
     * spine at the current {@link #tilt}, so that hinge (the lowest point of the book) always rests on the floor.
     */
    public static float lift(float open) {
        return HINGE * (float) Math.sin(-tilt(open)) + CLEARANCE;
    }

    /** The sideways shift that centres the closed book, in model px. */
    public static float shift(float open) {
        return -COVER / 2.0f * (1.0f - open);
    }

    /** The yaw that turns the north-facing pose to {@code facing}, in radians. */
    public static float yaw(Direction facing) {
        return (float) Math.toRadians(180.0 - facing.toYRot());
    }

    /**
     * The block-space transform (0..1 in each axis) the book model is drawn with when {@code open} (0..1) and the block
     * faces {@code facing}. The model's own part transforms (in px, divided by 16 as {@code ModelPart} does) come after.
     */
    public static Matrix4f pose(Direction facing, float open) {
        float px = SCALE / 16.0f;
        return new Matrix4f()
                .translate(0.5f, lift(open) * px, 0.5f)
                .rotateY(yaw(facing))
                .translate(shift(open) * px, 0.0f, 0.0f)
                .rotateY((float) (Math.PI / 2))
                .rotateZ((float) (Math.PI / 2))
                .rotateY(tilt(open))
                .scale(SCALE);
    }
}

package com.richardsenger.piratesnships.ship.template;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a ship template goes: pure maths, no world access. Rotations match vanilla's
 * {@code StructureTemplate.transform} with no mirror and the pivot at the template origin, so a template placed with
 * {@code StructurePlaceSettings.setRotation(r)} at {@code origin} puts its local block {@code p} at
 * {@code origin + rotate(p, r)}.
 */
public final class TemplatePlacement {

    private TemplatePlacement() {
    }

    /** The horizontal direction nearest to a yaw (degrees, Minecraft convention: 0 = south, 90 = west). */
    public static Direction snapFacing(float yRot) {
        return Direction.fromYRot(yRot);
    }

    /** The rotation that turns the template's {@code bow} direction into {@code facing}. */
    public static Rotation rotationFor(Direction bow, Direction facing) {
        if (!bow.getAxis().isHorizontal() || !facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("horizontal directions only: " + bow + ", " + facing);
        }
        int steps = Math.floorMod(facing.get2DDataValue() - bow.get2DDataValue(), 4);
        return switch (steps) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** A template-local position rotated about the template origin (vanilla's transform, pivot zero, no mirror). */
    public static BlockPos rotate(BlockPos local, Rotation rotation) {
        int x = local.getX(), y = local.getY(), z = local.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-z, y, x);
            case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
            case NONE -> local;
        };
    }

    /** The template's box after rotation, relative to the template origin. */
    public static BoundingBox rotatedBox(Vec3i size, Rotation rotation) {
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
            throw new IllegalArgumentException("empty template size " + size);
        }
        return BoundingBox.fromCorners(rotate(BlockPos.ZERO, rotation),
                rotate(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1), rotation));
    }

    /**
     * The template origin for a ship placed in front of someone standing at {@code feet} looking {@code facing}: the
     * rotated box starts {@code gap} blocks ahead (its near edge), is centred on {@code feet} sideways, and the
     * template row {@code waterline} lands on {@code surfaceY}.
     */
    public static BlockPos origin(Vec3i size, Rotation rotation, Direction facing, BlockPos feet, int gap, int surfaceY, int waterline) {
        BoundingBox box = rotatedBox(size, rotation);
        int minX, minZ;
        if (facing.getAxis() == Direction.Axis.Z) {
            minX = feet.getX() - (box.getXSpan() - 1) / 2;
            minZ = facing.getStepZ() > 0 ? feet.getZ() + gap : feet.getZ() - gap - (box.getZSpan() - 1);
        } else {
            minZ = feet.getZ() - (box.getZSpan() - 1) / 2;
            minX = facing.getStepX() > 0 ? feet.getX() + gap : feet.getX() - gap - (box.getXSpan() - 1);
        }
        return new BlockPos(minX - box.minX(), surfaceY - waterline, minZ - box.minZ());
    }

    /** Where template-local {@code local} ends up in the world. */
    public static BlockPos toWorld(BlockPos local, Rotation rotation, BlockPos origin) {
        return rotate(local, rotation).offset(origin);
    }

    /** The world box of a placed template. */
    public static BoundingBox worldBox(Vec3i size, Rotation rotation, BlockPos origin) {
        return rotatedBox(size, rotation).moved(origin.getX(), origin.getY(), origin.getZ());
    }

    /**
     * The water surface in one column: the highest {@code y} in {@code [bottom, top]} that is water with no water
     * directly above it, scanning down from {@code top}.
     */
    public static OptionalInt waterSurface(int top, int bottom, IntPredicate isWater) {
        for (int y = top; y >= bottom; y--) {
            if (isWater.test(y) && !isWater.test(y + 1)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /** The first cell the template would occupy that is blocked, in iteration order. */
    public static Optional<BlockPos> firstBlocked(Iterable<BlockPos> cells, Predicate<BlockPos> blocked) {
        for (BlockPos p : cells) {
            if (blocked.test(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}

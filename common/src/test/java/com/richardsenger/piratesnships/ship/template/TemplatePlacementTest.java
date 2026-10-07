package com.richardsenger.piratesnships.ship.template;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatePlacementTest {

    private static final Vec3i SLOOP = new Vec3i(9, 21, 29);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void yawSnapsToTheNearestQuarter() {
        assertEquals(Direction.SOUTH, TemplatePlacement.snapFacing(0));
        assertEquals(Direction.SOUTH, TemplatePlacement.snapFacing(44));
        assertEquals(Direction.WEST, TemplatePlacement.snapFacing(46));
        assertEquals(Direction.NORTH, TemplatePlacement.snapFacing(180));
        assertEquals(Direction.NORTH, TemplatePlacement.snapFacing(-170));
        assertEquals(Direction.EAST, TemplatePlacement.snapFacing(-90));
        assertEquals(Direction.EAST, TemplatePlacement.snapFacing(270));
    }

    @Test
    void rotationTurnsTheBowToTheFacing() {
        for (Direction bow : Direction.Plane.HORIZONTAL) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Rotation r = TemplatePlacement.rotationFor(bow, facing);
                assertEquals(facing, r.rotate(bow), bow + " -> " + facing);
            }
        }
        assertEquals(Rotation.NONE, TemplatePlacement.rotationFor(Direction.NORTH, Direction.NORTH));
        assertEquals(Rotation.CLOCKWISE_90, TemplatePlacement.rotationFor(Direction.NORTH, Direction.EAST));
        assertEquals(Rotation.CLOCKWISE_180, TemplatePlacement.rotationFor(Direction.NORTH, Direction.SOUTH));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, TemplatePlacement.rotationFor(Direction.NORTH, Direction.WEST));
        assertThrows(IllegalArgumentException.class, () -> TemplatePlacement.rotationFor(Direction.UP, Direction.NORTH));
    }

    @Test
    void rotateMatchesVanillaStructureTransform() {
        List<BlockPos> samples = List.of(BlockPos.ZERO, new BlockPos(4, 8, 22), new BlockPos(8, 20, 28), new BlockPos(-3, 1, 5));
        for (Rotation r : Rotation.values()) {
            for (BlockPos p : samples) {
                assertEquals(StructureTemplate.transform(p, Mirror.NONE, r, BlockPos.ZERO), TemplatePlacement.rotate(p, r), r + " " + p);
            }
        }
    }

    @Test
    void rotatedBoxHasTheSwappedSpans() {
        BoundingBox none = TemplatePlacement.rotatedBox(SLOOP, Rotation.NONE);
        assertEquals(new BoundingBox(0, 0, 0, 8, 20, 28), none);
        BoundingBox cw = TemplatePlacement.rotatedBox(SLOOP, Rotation.CLOCKWISE_90);
        assertEquals(29, cw.getXSpan());
        assertEquals(9, cw.getZSpan());
        assertEquals(21, cw.getYSpan());
    }

    @Test
    void originPutsTheShipAheadCentredAndOnTheWaterline() {
        BlockPos feet = new BlockPos(100, 70, -50);
        int surface = 62;
        int waterline = 2;
        int gap = 3;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Rotation r = TemplatePlacement.rotationFor(Direction.NORTH, facing);
            BlockPos origin = TemplatePlacement.origin(SLOOP, r, facing, feet, gap, surface, waterline);
            BoundingBox box = TemplatePlacement.worldBox(SLOOP, r, origin);
            // waterline row on the surface, keel row below it
            assertEquals(surface - waterline, box.minY(), facing.toString());
            assertEquals(surface, origin.getY() + waterline);
            // near edge exactly gap blocks ahead
            switch (facing) {
                case NORTH -> assertEquals(feet.getZ() - gap, box.maxZ());
                case SOUTH -> assertEquals(feet.getZ() + gap, box.minZ());
                case EAST -> assertEquals(feet.getX() + gap, box.minX());
                case WEST -> assertEquals(feet.getX() - gap, box.maxX());
                default -> throw new AssertionError();
            }
            // centred sideways (9 wide: 4 either side)
            if (facing.getAxis() == Direction.Axis.Z) {
                assertEquals(feet.getX() - 4, box.minX());
                assertEquals(feet.getX() + 4, box.maxX());
            } else {
                assertEquals(feet.getZ() - 4, box.minZ());
                assertEquals(feet.getZ() + 4, box.maxZ());
            }
            // the bow (template z = 0) is the far end, in the facing direction
            BlockPos bow = TemplatePlacement.toWorld(new BlockPos(4, 0, 0), r, origin);
            BlockPos stern = TemplatePlacement.toWorld(new BlockPos(4, 0, 28), r, origin);
            BlockPos d = bow.subtract(stern);
            assertEquals(facing, Direction.getNearest(d.getX(), 0, d.getZ()), facing.toString());
            // every template corner lands inside the world box
            for (BlockPos c : List.of(BlockPos.ZERO, new BlockPos(8, 20, 28), new BlockPos(8, 0, 0), new BlockPos(0, 20, 28))) {
                assertTrue(box.isInside(TemplatePlacement.toWorld(c, r, origin)), facing + " " + c);
            }
        }
    }

    @Test
    void waterSurfaceIsTheTopWaterRow() {
        Set<Integer> water = Set.of(55, 56, 57, 58, 59, 60, 61, 62);
        assertEquals(OptionalInt.of(62), TemplatePlacement.waterSurface(74, 22, water::contains));
        // a range that starts below the surface sees no surface (the caller searches from above its own head)
        assertEquals(OptionalInt.empty(), TemplatePlacement.waterSurface(60, 22, water::contains));
        // no water in range
        assertEquals(OptionalInt.empty(), TemplatePlacement.waterSurface(50, 22, water::contains));
        // a water pocket under a dock (air at 63, then water again above an air gap) counts its own top
        Set<Integer> layered = Set.of(10, 11, 12, 20);
        assertEquals(OptionalInt.of(20), TemplatePlacement.waterSurface(30, 0, layered::contains));
        assertEquals(OptionalInt.of(12), TemplatePlacement.waterSurface(19, 0, layered::contains));
    }

    @Test
    void firstBlockedFindsTheFirstOverlap() {
        List<BlockPos> cells = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        assertTrue(TemplatePlacement.firstBlocked(cells, p -> false).isEmpty());
        assertEquals(new BlockPos(1, 0, 0), TemplatePlacement.firstBlocked(cells, p -> p.getX() >= 1).orElseThrow());
    }
}

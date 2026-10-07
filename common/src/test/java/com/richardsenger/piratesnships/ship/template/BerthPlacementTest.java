package com.richardsenger.piratesnships.ship.template;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** SW1: a template moored at a berth: centred along the bow axis, one water column off the berth, waterline on the surface. */
class BerthPlacementTest {

    private static final Vec3i SLOOP = new Vec3i(9, 21, 29);
    private static final BlockPos BERTH = new BlockPos(100, 62, 200);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BoundingBox box(Direction bow, Direction side) {
        Rotation r = TemplatePlacement.rotationFor(Direction.NORTH, bow);
        BlockPos origin = TemplatePlacement.berthOrigin(SLOOP, r, bow, side, BERTH, 1, BERTH.getY(), 2);
        return TemplatePlacement.worldBox(SLOOP, r, origin);
    }

    @Test
    void northBowEastSide() {
        BoundingBox b = box(Direction.NORTH, Direction.EAST);
        assertEquals(new BoundingBox(101 + 0, 60, 186, 109, 80, 214), b);
        // centred on the berth along z, the near side one column east of the berth, waterline row 2 on y 62
        assertEquals(BERTH.getZ(), (b.minZ() + b.maxZ()) / 2);
        assertEquals(BERTH.getX() + 1, b.minX());
        assertEquals(BERTH.getY() - 2, b.minY());
    }

    @Test
    void northBowWestSide() {
        BoundingBox b = box(Direction.NORTH, Direction.WEST);
        assertEquals(BERTH.getX() - 1, b.maxX());
        assertEquals(9, b.getXSpan());
        assertEquals(BERTH.getZ(), (b.minZ() + b.maxZ()) / 2);
    }

    @Test
    void eastBowBothSides() {
        BoundingBox south = box(Direction.EAST, Direction.SOUTH);
        assertEquals(29, south.getXSpan());
        assertEquals(9, south.getZSpan());
        assertEquals(BERTH.getX(), (south.minX() + south.maxX()) / 2);
        assertEquals(BERTH.getZ() + 1, south.minZ());
        BoundingBox north = box(Direction.EAST, Direction.NORTH);
        assertEquals(BERTH.getZ() - 1, north.maxZ());
    }

    @Test
    void theBowPointsAlongTheBerth() {
        // the template's bow row (z 0, north) ends up at the far end along the berth's bow direction
        for (Direction bow : Direction.Plane.HORIZONTAL) {
            Direction side = bow.getClockWise();
            Rotation r = TemplatePlacement.rotationFor(Direction.NORTH, bow);
            BlockPos origin = TemplatePlacement.berthOrigin(SLOOP, r, bow, side, BERTH, 1, BERTH.getY(), 2);
            BlockPos bowTip = TemplatePlacement.toWorld(new BlockPos(4, 2, 0), r, origin);
            BlockPos sternTip = TemplatePlacement.toWorld(new BlockPos(4, 2, 28), r, origin);
            BlockPos d = bowTip.subtract(sternTip);
            assertEquals(bow, Direction.getNearest((float) d.getX(), (float) d.getY(), (float) d.getZ()), "bow " + bow);
        }
    }

    @Test
    void sideMustBePerpendicular() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplatePlacement.berthOrigin(SLOOP, Rotation.NONE, Direction.NORTH, Direction.SOUTH, BERTH, 1, 62, 2));
    }

    @Test
    void sidesAwayFromThePierComeFirst() {
        // pier to the west of a north-pointing berth: try east first
        assertEquals(List.of(Direction.EAST, Direction.WEST), TemplatePlacement.berthSides(Direction.NORTH, d -> d == Direction.WEST));
        // pier to the east: west first
        assertEquals(List.of(Direction.WEST, Direction.EAST), TemplatePlacement.berthSides(Direction.NORTH, d -> d == Direction.EAST));
        // open water or piers on both sides: starboard (clockwise of the bow) first
        assertEquals(List.of(Direction.EAST, Direction.WEST), TemplatePlacement.berthSides(Direction.NORTH, d -> false));
        assertEquals(List.of(Direction.SOUTH, Direction.NORTH), TemplatePlacement.berthSides(Direction.EAST, d -> true));
    }
}

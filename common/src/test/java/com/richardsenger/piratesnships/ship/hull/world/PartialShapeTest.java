package com.richardsenger.piratesnships.ship.hull.world;

import static com.richardsenger.piratesnships.ship.hull.CellFaces.ALL;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.DOWN;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.EAST;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.NORTH;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.SOUTH;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.UP;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.WEST;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Face coverage of vanilla partial blocks ({@link HullBlockClassifier#uncoveredFaces}), with vanilla bootstrapped. */
class PartialShapeTest {

    private static final int SIDES = NORTH | SOUTH | WEST | EAST;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static int faces(BlockState s) {
        return HullBlockClassifier.uncoveredFaces(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    private static int faces(BlockState s, CellKind kind) {
        return HullBlockClassifier.uncoveredFaces(s, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, kind);
    }

    @Test
    void fullBlocksHaveNone() {
        assertEquals(0, faces(Blocks.OAK_PLANKS.defaultBlockState()));
        assertEquals(0, faces(Blocks.GLASS.defaultBlockState()));
        assertEquals(0, faces(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)));
    }

    @Test
    void slabHalves() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertEquals(UP | SIDES, faces(slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
        assertEquals(DOWN | SIDES, faces(slab.setValue(SlabBlock.TYPE, SlabType.TOP)));
    }

    @Test
    void stairsByFacingAndHalf() {
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState();
        // the tall back of a stair is on its facing side
        assertEquals(UP | SOUTH | WEST | EAST, faces(stair.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM)));
        assertEquals(UP | NORTH | WEST | EAST, faces(stair.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM)));
        assertEquals(DOWN | NORTH | SOUTH | WEST, faces(stair.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP)));
        assertEquals(DOWN | NORTH | SOUTH | EAST, faces(stair.setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP)));
    }

    @Test
    void trapdoorsOpenAndClosed() {
        BlockState t = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH);
        assertEquals(UP | SIDES, faces(t.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.HALF, Half.BOTTOM)));
        assertEquals(DOWN | SIDES, faces(t.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.HALF, Half.TOP)));
        // an open trapdoor facing north stands against the south side of its cell
        assertEquals(ALL & ~SOUTH, faces(t.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM)));
        assertEquals(ALL & ~SOUTH, faces(t.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.TOP)));
    }

    @Test
    void openingsUseBothStates() {
        BlockState closed = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, false);
        assertEquals(ALL, faces(closed, CellKind.OPENING));
        assertEquals(ALL, faces(Blocks.OAK_DOOR.defaultBlockState(), CellKind.OPENING));
    }

    @Test
    void paneAndCarpet() {
        assertEquals(ALL, faces(Blocks.GLASS_PANE.defaultBlockState()), "a lone pane is a post");
        assertEquals(UP | SIDES, faces(Blocks.WHITE_CARPET.defaultBlockState()));
    }

    @Test
    void solidKindsOnly() {
        assertEquals(UP | SIDES, faces(Blocks.OAK_SLAB.defaultBlockState(), CellKind.SOLID));
        assertEquals(0, faces(Blocks.WHITE_CARPET.defaultBlockState(), CellKind.AIR), "air cells are never partial");
        assertEquals(0, faces(Blocks.STONE.defaultBlockState(), CellKind.SOLID));
    }
}

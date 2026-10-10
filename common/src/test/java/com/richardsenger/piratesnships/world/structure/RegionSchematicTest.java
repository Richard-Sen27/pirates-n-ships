package com.richardsenger.piratesnships.world.structure;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * STR5: {@link RegionSchematic}'s bytes depend on the blocks alone. The NeoForge and Fabric dumps of the same
 * placement differed only in {@code Offset}, the arena's absolute world position; it is now relative to the test's
 * origin, so the same blocks at two world positions write the same file.
 */
class RegionSchematicTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A small structure: a floor, a post, a stair and a chest, everything else air. */
    private static Map<BlockPos, BlockState> structure() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                blocks.put(new BlockPos(x, 0, z), (x + z) % 2 == 0 ? Blocks.SAND.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        blocks.put(new BlockPos(1, 1, 1), Blocks.SPRUCE_LOG.defaultBlockState());
        blocks.put(new BlockPos(1, 2, 1), Blocks.SPRUCE_LOG.defaultBlockState());
        blocks.put(new BlockPos(3, 1, 2), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        blocks.put(new BlockPos(4, 1, 3), Blocks.CHEST.defaultBlockState());
        return blocks;
    }

    /** {@link #structure()} moved by {@code origin}, inserted in the given order (the map's order must not matter). */
    private static BlockGetter world(BlockPos origin, boolean reversed) {
        Map<BlockPos, BlockState> placed = new HashMap<>();
        var entries = new ArrayList<>(structure().entrySet());
        if (reversed) {
            Collections.reverse(entries);
        }
        entries.forEach(e -> placed.put(e.getKey().offset(origin), e.getValue()));
        return new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return placed.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
    }

    private static BoundingBox region(BlockPos origin) {
        return new BoundingBox(-2, -1, -2, 6, 4, 5).moved(origin.getX(), origin.getY(), origin.getZ());
    }

    private static byte[] bytes(CompoundTag tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(out));
        return out.toByteArray();
    }

    @Test
    void theSameBlocksAtTwoWorldPositionsWriteTheSameBytes() throws IOException {
        // the arena positions of the two loaders' runs of pirateIslandPlaced1 (seed 11)
        BlockPos neoforge = new BlockPos(-2739, -61, 2074);
        BlockPos fabric = new BlockPos(3795, -61, -2862);
        byte[] a = bytes(RegionSchematic.of(world(neoforge, false), region(neoforge), neoforge));
        byte[] b = bytes(RegionSchematic.of(world(fabric, true), region(fabric), fabric));
        assertArrayEquals(a, b);
    }

    @Test
    void offsetIsRelativeToTheOrigin() {
        BlockPos origin = new BlockPos(100, -60, -300);
        CompoundTag tag = RegionSchematic.of(world(origin, false), region(origin), origin);
        assertArrayEquals(new int[] {-2, -1, -2}, tag.getIntArray("Offset"));
        assertEquals(9, tag.getShort("Width"));
        assertEquals(6, tag.getShort("Height"));
        assertEquals(8, tag.getShort("Length"));
    }

    @Test
    void writtenFilesAreByteIdentical(@TempDir Path dir) throws IOException {
        BlockPos one = new BlockPos(7, 0, 7);
        BlockPos two = new BlockPos(-4000, 10, 9000);
        Path a = dir.resolve("a.schem");
        Path b = dir.resolve("b.schem");
        RegionSchematic.write(world(one, false), region(one), one, a);
        RegionSchematic.write(world(two, true), region(two), two, b);
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    }
}

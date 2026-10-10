package com.richardsenger.piratesnships.world.structure;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of STR2's layout check: {@link StructureLayout}'s numbers and text, the boardwalk rule, the varints. */
class StructureLayoutTest {

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StructureLayout.Piece piece(String name, int x0, int z0, int x1, int z1) {
        return new StructureLayout.Piece(name, new BoundingBox(x0, 60, z0, x1, 66, z1));
    }

    @Test
    void templateNameIsTheLastPathSegment() {
        assertEquals("tent", StructureLayout.templateName("Single[Left[pirates_n_ships:pirate_island/tent]]"));
        assertEquals("camp_start", StructureLayout.templateName("LegacySingle[Left[pirates_n_ships:pirate_island/camp_start]]"));
        assertEquals("dock_head", StructureLayout.templateName("Single[Left[pirates_n_ships:village/dock_head]]"));
        assertEquals("?", StructureLayout.templateName("Empty"));
    }

    @Test
    void boundsAspectAndVariants() {
        StructureLayout layout = new StructureLayout(List.of(
                piece("camp_start", 0, 0, 12, 12), piece("path", 13, 3, 19, 9), piece("tent", 20, 3, 26, 9),
                piece("tent", 0, 13, 6, 19), piece("hut_stilts", 7, 13, 13, 20)));
        BoundingBox b = layout.bounds();
        assertEquals(new BoundingBox(0, 60, 0, 26, 66, 20), b);
        assertEquals(21.0 / 27.0, layout.aspect(), 1e-9);
        assertEquals(Set.of("tent", "hut_stilts"), layout.variants(Set.of("tent", "hut_stilts", "tavern_hut")));
        assertEquals("camp_start,path,tent,tent,hut_stilts", layout.sequence());
        assertEquals(2, layout.named("tent").size());
    }

    @Test
    void aLineHasALowAspect() {
        StructureLayout line = new StructureLayout(List.of(piece("camp_start", 0, 0, 12, 12),
                piece("path", 13, 3, 19, 9), piece("path", 20, 3, 26, 9), piece("path", 27, 3, 33, 9),
                piece("path", 34, 3, 40, 9), piece("path", 41, 3, 47, 9), piece("path_end", 48, 3, 50, 9)));
        assertTrue(line.aspect() < 0.5, "a row of trails is a line: " + line.aspect());
    }

    @Test
    void textDumpHasTheHeaderAndOneLinePerPiece() {
        StructureLayout layout = new StructureLayout(List.of(piece("camp_start", 0, 0, 12, 12), piece("tent", 13, 0, 19, 6)));
        List<String> lines = layout.lines("pirates_n_ships:pirate_island", 7L, Set.of("tent"), true);
        assertEquals("structure pirates_n_ships:pirate_island", lines.get(0));
        assertEquals("seed 7", lines.get(1));
        assertEquals("pieces 2", lines.get(2));
        assertEquals("bounds 0 60 0 19 66 12", lines.get(3));
        assertEquals("size 20 13", lines.get(4));
        assertEquals("aspect 0.650", lines.get(5));
        assertEquals("buildings tent", lines.get(6));
        assertEquals("variants 1 tent", lines.get(7));
        assertEquals("water_end true", lines.get(8));
        assertEquals("sequence camp_start,tent", lines.get(9));
        assertEquals("piece 1 tent 13 60 0 19 66 6", lines.get(11));
        assertEquals(12, lines.size());
    }

    @Test
    void trailBedBecomesBoardsOverWaterAndNothingElse() {
        for (var bed : List.of(Blocks.SAND, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.DIRT_PATH)) {
            assertSame(Blocks.SPRUCE_PLANKS, BoardwalkProcessor.overWater(bed.defaultBlockState()).getBlock(), bed.toString());
        }
        var log = Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState();
        assertSame(Blocks.SPRUCE_PLANKS, BoardwalkProcessor.overWater(log).getBlock());
        var fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        assertSame(fence, BoardwalkProcessor.overWater(fence), "stakes and props stay");
        var barrel = Blocks.BARREL.defaultBlockState();
        assertSame(barrel, BoardwalkProcessor.overWater(barrel));
    }

    @Test
    void landPiecesOverOpenWaterAreDroppedWithWhatHangsFromThem() {
        LandFilter.Water sea = (x, z) -> z < 0;                        // water north of z 0
        List<LandFilter.Node> nodes = List.of(
                new LandFilter.Node(new BoundingBox(0, 0, 0, 12, 7, 12), -1, false),      // 0 the camp on the shore
                new LandFilter.Node(new BoundingBox(4, -4, -16, 8, 4, -1), 0, false),     // 1 the jetty: rigid, stays
                new LandFilter.Node(new BoundingBox(13, 0, 0, 19, 3, 6), 0, true),        // 2 a trail along the beach
                new LandFilter.Node(new BoundingBox(13, 0, -9, 21, 3, -1), 2, true),      // 3 a bend out to sea
                new LandFilter.Node(new BoundingBox(22, 0, -2, 28, 6, 4), 3, true),       // 4 a hut on it, on land
                new LandFilter.Node(new BoundingBox(13, 0, -3, 19, 3, 3), 2, true),       // 5 a trail across the waterline
                new LandFilter.Node(new BoundingBox(20, 0, 7, 26, 6, 13), 2, true));      // 6 a hut inland
        boolean[] keep = LandFilter.keep(nodes, sea);
        assertArrayEquals(new boolean[] {true, true, true, false, false, true, true}, keep);
    }

    @Test
    void overWaterNeedsMostOfTheFootprint() {
        LandFilter.Water sea = (x, z) -> z < 0;
        assertTrue(LandFilter.overWater(new BoundingBox(0, 0, -10, 6, 3, -4), sea));
        assertTrue(!LandFilter.overWater(new BoundingBox(0, 0, 0, 6, 3, 6), sea));
        // half in, half out: the centre and the two seaward corners are wet (3 of 5)
        assertTrue(LandFilter.overWater(new BoundingBox(0, 0, -4, 6, 3, 2), sea));
        // mostly on land: only the two seaward corners are wet
        assertTrue(!LandFilter.overWater(new BoundingBox(0, 0, -2, 6, 3, 8), sea));
    }

    @Test
    void varintsAreSpongeVarints() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RegionSchematic.writeVarint(out, 0);
        RegionSchematic.writeVarint(out, 127);
        RegionSchematic.writeVarint(out, 128);
        RegionSchematic.writeVarint(out, 300);
        assertArrayEquals(new byte[] {0, 127, (byte) 0x80, 1, (byte) 0xAC, 2}, out.toByteArray());
    }

    @Test
    void regionSchematicHasTheSpongeFields() {
        net.minecraft.world.level.BlockGetter flat = new net.minecraft.world.level.BlockGetter() {
            @Override
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) {
                return null;
            }

            @Override
            public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
                return pos.getY() == 0 ? Blocks.SAND.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }

            @Override
            public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) {
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
        CompoundTag tag = RegionSchematic.of(flat, new BoundingBox(0, 0, 0, 2, 1, 3));
        assertEquals(2, tag.getInt("Version"));
        assertEquals(3, tag.getShort("Width"));
        assertEquals(2, tag.getShort("Height"));
        assertEquals(4, tag.getShort("Length"));
        assertEquals(2, tag.getInt("PaletteMax"));
        assertEquals(0, tag.getCompound("Palette").getInt("minecraft:sand"));
        assertEquals(1, tag.getCompound("Palette").getInt("minecraft:air"));
        byte[] data = tag.getByteArray("BlockData");
        assertEquals(24, data.length);
        for (int i = 0; i < 24; i++) assertEquals(i < 12 ? 0 : 1, data[i], "index " + i + " (y, z, x order)");
    }
}

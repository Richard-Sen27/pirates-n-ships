package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.world.port.Berth;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Berths read from the committed pier piece (ST1: berths at [0, 4, 9] and [6, 4, 9], both bows north). */
class BerthMarkersTest {

    private static final BlockPos AT = new BlockPos(1000, 58, -200);
    private static StructureTemplate pier;
    private static StructureTemplate dockHead;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        pier = template("pier");
        dockHead = template("dock_head");
    }

    static StructureTemplate template(String name) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("tools/schem_to_structure.py"))) root = root.getParent();
        assertNotNull(root, "repository root");
        StructureTemplate t = new StructureTemplate();
        t.load(BuiltInRegistries.BLOCK.asLookup(), NbtIo.readCompressed(
                root.resolve("common/src/main/resources/data/pirates_n_ships/structure/village/" + name + ".nbt"), NbtAccounter.unlimitedHeap()));
        return t;
    }

    private static List<Berth> expected(StructurePlaceSettings settings, Direction bow) {
        return List.of(new BlockPos(0, 4, 9), new BlockPos(6, 4, 9)).stream()
                .map(p -> new Berth(StructureTemplate.calculateRelativePosition(settings, p).offset(AT), bow))
                .sorted(Comparator.comparing(Berth::pos)).toList();
    }

    @Test
    void berthsUnderAllFourRotations() {
        for (Rotation r : Rotation.values()) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
            List<Berth> berths = BerthMarkers.fromTemplate(pier, AT, settings);
            assertEquals(expected(settings, r.rotate(Direction.NORTH)), berths, r.toString());
            // the same as vanilla's jigsaw lookup (what world generation hands to fromJigsaws)
            assertEquals(berths, BerthMarkers.fromJigsaws(pier.filterBlocks(AT, settings, Blocks.JIGSAW, true)), r.toString());
        }
    }

    @Test
    void berthsUnderAMirror() {
        StructurePlaceSettings leftRight = new StructurePlaceSettings().setMirror(Mirror.LEFT_RIGHT);
        assertEquals(expected(leftRight, Direction.SOUTH), BerthMarkers.fromTemplate(pier, AT, leftRight), "mirrored along z");
        StructurePlaceSettings frontBack = new StructurePlaceSettings().setMirror(Mirror.FRONT_BACK).setRotation(Rotation.CLOCKWISE_90);
        assertEquals(expected(frontBack, Direction.EAST), BerthMarkers.fromTemplate(pier, AT, frontBack), "mirrored along x, turned");
    }

    @Test
    void berthsLieAtSeaLevelBesideTheDeck() {
        // the deck is the pier's row y 5 and the sea surface its row y 4 (art/README.md)
        for (Berth b : BerthMarkers.fromTemplate(pier, BlockPos.ZERO, new StructurePlaceSettings())) {
            assertEquals(4, b.pos().getY());
            assertTrue(b.pos().getX() == 0 || b.pos().getX() == 6, "one block out from the 5-wide deck");
        }
    }

    @Test
    void otherJigsawsAreNotBerths() {
        assertTrue(BerthMarkers.fromTemplate(dockHead, AT, new StructurePlaceSettings()).isEmpty());
    }
}

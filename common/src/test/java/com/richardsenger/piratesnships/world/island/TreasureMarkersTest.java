package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.world.structure.StandAloneOps;
import com.richardsenger.piratesnships.world.village.BerthMarkers;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Treasure read from the committed treasure spot (ST2: the marker at [2, 0, 2], two below the surface row y 2). */
class TreasureMarkersTest {

    private static final BlockPos AT = new BlockPos(-300, 61, 4100);
    private static final BlockPos MARKER = new BlockPos(2, 0, 2);

    private static StructureTemplate spot;
    private static StructureTemplate camp;
    private static StructureTemplate jetty;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        spot = template("treasure_spot");
        camp = template("camp_start");
        jetty = template("jetty");
    }

    private static StructureTemplate template(String name) throws IOException {
        StructureTemplate t = new StructureTemplate();
        t.load(BuiltInRegistries.BLOCK.asLookup(), NbtIo.readCompressed(StandAloneOps.root().resolve(
                "common/src/main/resources/data/pirates_n_ships/structure/pirate_island/" + name + ".nbt"), NbtAccounter.unlimitedHeap()));
        return t;
    }

    @Test
    void treasureUnderAllFourRotations() {
        for (Rotation r : Rotation.values()) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
            List<BlockPos> treasure = TreasureMarkers.fromTemplate(spot, AT, settings);
            assertEquals(List.of(StructureTemplate.calculateRelativePosition(settings, MARKER).offset(AT)), treasure, r.toString());
            // the same as vanilla's jigsaw lookup (what world generation hands to fromJigsaws)
            assertEquals(treasure, TreasureMarkers.fromJigsaws(spot.filterBlocks(AT, settings, Blocks.JIGSAW, true)), r.toString());
            assertEquals(AT.getY(), treasure.get(0).getY(), "on the spot's row y 0");
        }
    }

    @Test
    void theMarkerIsBuriedTwoUnderTheSurface() {
        // the spot's surface is its row y 2; rows 0 and 1 above the marker column are sand once placed
        assertEquals(0, MARKER.getY());
        var surface = spot.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.SAND, false).stream()
                .filter(i -> i.pos().getX() == MARKER.getX() && i.pos().getZ() == MARKER.getZ()).map(i -> i.pos().getY()).toList();
        assertTrue(surface.contains(1), "sand above the marker: " + surface);
    }

    @Test
    void otherPiecesHaveNoTreasureAndTreasureIsNoBerth() {
        assertTrue(TreasureMarkers.fromTemplate(camp, AT, new StructurePlaceSettings()).isEmpty(), "camp");
        assertTrue(TreasureMarkers.fromTemplate(jetty, AT, new StructurePlaceSettings()).isEmpty(), "jetty");
        assertTrue(BerthMarkers.fromTemplate(spot, AT, new StructurePlaceSettings()).isEmpty(), "the treasure marker is no berth");
        assertEquals(2, BerthMarkers.fromTemplate(jetty, AT, new StructurePlaceSettings()).size(), "the jetty has two berths");
    }
}

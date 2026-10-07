package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.world.village.ShoreFacing;
import com.richardsenger.piratesnships.world.village.VillageLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generalised start-piece anchor (WG2) for both port kinds, and its values against the committed start pieces. */
class ShoreAnchorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StructureTemplate template(String path) throws IOException {
        StructureTemplate t = new StructureTemplate();
        t.load(BuiltInRegistries.BLOCK.asLookup(), NbtIo.readCompressed(StandAloneOps.root().resolve(
                "common/src/main/resources/data/pirates_n_ships/structure/" + path + ".nbt"), NbtAccounter.unlimitedHeap()));
        return t;
    }

    @Test
    void anchorLandsOnTheShoreFacingTheSeaForBothKinds() {
        BlockPos candidate = new BlockPos(100, 63, -40);
        for (ShoreAnchor anchor : List.of(ShoreAnchor.VILLAGE, ShoreAnchor.PIRATE_CAMP)) {
            for (Direction sea : ShoreFacing.ORDER) {
                Rotation r = ShoreFacing.rotationFacing(sea);
                BlockPos edge = VillageLayout.seaEdge(candidate, sea, 7);
                BlockPos origin = anchor.startOrigin(edge, r);
                BlockPos anchored = StructureTemplate.transform(new BlockPos(anchor.x(), 0, anchor.z()), Mirror.NONE, r, BlockPos.ZERO).offset(origin);
                assertEquals(edge, anchored, anchor + " " + sea);
                // the next row inland (local z + 1) is one step away from the sea
                BlockPos inland = StructureTemplate.transform(new BlockPos(anchor.x(), 0, anchor.z() + 1), Mirror.NONE, r, BlockPos.ZERO).offset(origin);
                assertEquals(edge.relative(sea.getOpposite()), inland, anchor + " " + sea);
                // the ground probe is the start piece's centre column, anchor.x inland
                assertEquals(edge.relative(sea.getOpposite(), anchor.x()), anchor.groundProbe(edge, sea));
            }
        }
    }

    @Test
    void villageAnchorIsWg1sDockHeadMaths() {
        BlockPos edge = new BlockPos(-17, 64, 230);
        for (Rotation r : Rotation.values()) assertEquals(VillageLayout.startOrigin(edge, r), ShoreAnchor.VILLAGE.startOrigin(edge, r));
        assertEquals(VillageLayout.DOCK_PIER_X, ShoreAnchor.VILLAGE.x());
    }

    @Test
    void campOriginPutsTheCampInlandCentredOnTheEdge() {
        // sea to the north: the camp spans x -6..+6 around the edge column and z 0..12 inland (south) of the edge row
        BlockPos edge = new BlockPos(0, 64, 0);
        BlockPos origin = ShoreAnchor.PIRATE_CAMP.startOrigin(edge, Rotation.NONE);
        assertEquals(new BlockPos(-6, 64, 0), origin);
        // sea to the east (clockwise 90): local +x runs south, local +z runs west; the camp lies west of the edge
        BlockPos east = ShoreAnchor.PIRATE_CAMP.startOrigin(edge, Rotation.CLOCKWISE_90);
        BlockPos farCorner = StructureTemplate.transform(new BlockPos(12, 0, 12), Mirror.NONE, Rotation.CLOCKWISE_90, BlockPos.ZERO).offset(east);
        assertEquals(new BlockPos(-12, 64, 6), farCorner);
    }

    @Test
    void anchorsSitOnTheSeaConnectorsOfTheCommittedStartPieces() throws IOException {
        assertConnector(template("village/dock_head"), ShoreAnchor.VILLAGE, "pirates_n_ships:pier_out");
        assertConnector(template("pirate_island/camp_start"), ShoreAnchor.PIRATE_CAMP, "pirates_n_ships:jetty_out");
    }

    private static void assertConnector(StructureTemplate t, ShoreAnchor anchor, String name) {
        StructureTemplate.StructureBlockInfo info = t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW, false).stream()
                .filter(i -> i.nbt() != null && name.equals(i.nbt().getString("name"))).findFirst().orElseThrow();
        assertEquals(new BlockPos(anchor.x(), 0, anchor.z()), info.pos(), name);
        assertEquals(Direction.NORTH, JigsawBlock.getFrontFacing(info.state()), name + " points out to sea (−z)");
    }

    @Test
    void codecRoundTrip() {
        ShoreAnchor a = new ShoreAnchor(6, 0);
        var json = ShoreAnchor.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        assertEquals("{\"x\":6,\"z\":0}", json.toString());
        assertEquals(a, ShoreAnchor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(ShoreAnchor.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"x\":-1,\"z\":0}")).isError());
    }
}

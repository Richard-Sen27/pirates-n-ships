package com.richardsenger.piratesnships.world.village;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageLayoutTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dockHeadSeaEdgeLandsOnTheShoreFacingTheSea() {
        BlockPos candidate = new BlockPos(100, 63, -40);
        for (Direction sea : ShoreFacing.ORDER) {
            Rotation r = ShoreFacing.rotationFacing(sea);
            BlockPos edge = VillageLayout.seaEdge(candidate, sea, 7);
            assertEquals(candidate.relative(sea, 6), edge);
            BlockPos origin = VillageLayout.startOrigin(edge, r);
            // the template's local (5, 0, 0) (pier connector on the sea edge row) is placed on the edge
            BlockPos connector = StructureTemplate.transform(new BlockPos(VillageLayout.DOCK_PIER_X, 0, 0), Mirror.NONE, r, BlockPos.ZERO).offset(origin);
            assertEquals(edge, connector, sea.toString());
            // the next row inland (local z 1) is one step away from the sea
            BlockPos inland = StructureTemplate.transform(new BlockPos(VillageLayout.DOCK_PIER_X, 0, 1), Mirror.NONE, r, BlockPos.ZERO).offset(origin);
            assertEquals(edge.relative(sea.getOpposite()), inland, sea.toString());
        }
    }

    @Test
    void randomForRotationDrawsTheWantedRotationFirst() {
        ChunkPos chunk = new ChunkPos(-7, 12);
        for (long seed : new long[]{0L, 42L, -123456789L}) {
            for (Rotation wanted : Rotation.values()) {
                WorldgenRandom r = VillageLayout.randomForRotation(seed, chunk, wanted);
                assertEquals(wanted, Rotation.getRandom(r), seed + " " + wanted);
                // deterministic
                assertEquals(VillageLayout.randomForRotation(seed, chunk, wanted).nextLong(),
                        nextAfterRotation(VillageLayout.randomForRotation(seed, chunk, wanted)));
            }
        }
    }

    private static long nextAfterRotation(WorldgenRandom r) {
        return r.nextLong();
    }
}

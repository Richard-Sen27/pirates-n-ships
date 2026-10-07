package com.richardsenger.piratesnships.world.port;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The port record with treasure sites (WG2) survives the saved-data codec, and WG1 records without them still load. */
class PortCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Port ISLAND = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "pirate_island_-40_96"),
            PortKind.PIRATE_ISLAND, Level.OVERWORLD, new BlockPos(-40, 64, 96), new BoundingBox(-80, 40, 60, 10, 80, 140),
            Climate.TROPICAL, List.of(new Berth(new BlockPos(-42, 62, 75), Direction.NORTH), new Berth(new BlockPos(-38, 62, 75), Direction.NORTH)),
            List.of(new TreasureSite(new BlockPos(-40, 61, 110), false), new TreasureSite(new BlockPos(-20, 62, 120), true)));

    @Test
    void roundTripWithTreasuresThroughNbtAndJson() {
        Tag nbt = Port.CODEC.encodeStart(NbtOps.INSTANCE, ISLAND).getOrThrow();
        assertEquals(ISLAND, Port.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        JsonElement json = Port.CODEC.encodeStart(JsonOps.INSTANCE, ISLAND).getOrThrow();
        assertEquals(ISLAND, Port.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(2, json.getAsJsonObject().getAsJsonArray("treasures").size());
    }

    @Test
    void indexRoundTripKeepsTreasures() {
        PortIndex index = PortIndex.EMPTY.with(ISLAND);
        Tag nbt = PortIndex.CODEC.encodeStart(NbtOps.INSTANCE, index).getOrThrow();
        PortIndex back = PortIndex.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(ISLAND.treasures(), back.byId(ISLAND.id()).orElseThrow().treasures());
    }

    @Test
    void wg1RecordsWithoutTreasuresStillLoad() {
        Port village = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_1_2"), PortKind.SEAFARER_VILLAGE,
                Level.OVERWORLD, new BlockPos(1, 64, 2), new BoundingBox(0, 60, 0, 10, 70, 10), Climate.TEMPERATE, List.of());
        JsonObject json = Port.CODEC.encodeStart(JsonOps.INSTANCE, village).getOrThrow().getAsJsonObject();
        json.remove("treasures");
        Port back = Port.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(village, back);
        assertTrue(back.treasures().isEmpty());
    }

    @Test
    void lootedDefaultsToFalse() {
        JsonObject json = new JsonObject();
        json.add("pos", BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, new BlockPos(3, 4, 5)).getOrThrow());
        TreasureSite site = TreasureSite.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertFalse(site.looted());
        assertEquals(new BlockPos(3, 4, 5), site.pos());
    }
}

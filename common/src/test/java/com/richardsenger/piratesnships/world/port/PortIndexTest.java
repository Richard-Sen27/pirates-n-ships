package com.richardsenger.piratesnships.world.port;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortIndexTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Port port(String id, int x, int z, int radius) {
        BlockPos centre = new BlockPos(x, 63, z);
        return new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", id), PortKind.SEAFARER_VILLAGE, Level.OVERWORLD, centre,
                new BoundingBox(x - radius, 50, z - radius, x + radius, 80, z + radius), Climate.TEMPERATE,
                List.of(new Berth(new BlockPos(x - 3, 62, z - 9), Direction.NORTH), new Berth(new BlockPos(x + 3, 62, z - 9), Direction.NORTH)));
    }

    private static final Port A = port("village_0_0", 0, 0, 30);
    private static final Port B = port("village_200_-40", 200, -40, 30);
    private static final Port NETHER = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_5_5"), PortKind.PIRATE_ISLAND,
            Level.NETHER, new BlockPos(5, 63, 5), new BoundingBox(-25, 50, -25, 35, 80, 35), Climate.ARID, List.of());

    @Test
    void codecRoundTripsInNbtAndJson() {
        PortIndex index = PortIndex.EMPTY.with(A).with(B).with(NETHER);
        Tag nbt = PortIndex.CODEC.encodeStart(NbtOps.INSTANCE, index).getOrThrow();
        assertEquals(index, PortIndex.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        JsonElement json = PortIndex.CODEC.encodeStart(JsonOps.INSTANCE, index).getOrThrow();
        assertEquals(index, PortIndex.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(List.of(A, B, NETHER), index.all(), "insertion order kept");
    }

    @Test
    void savedDataRoundTrips() {
        PortRegistry registry = new PortRegistry();
        assertTrue(registry.add(A));
        assertTrue(!registry.add(A), "same id twice");
        registry.add(B);
        CompoundTag saved = registry.save(new CompoundTag(), null);
        assertEquals(registry.index(), PortRegistry.load(saved, null).index());
        assertEquals(PortIndex.EMPTY, PortRegistry.load(new CompoundTag(), null).index());
    }

    @Test
    void addingTheSameIdKeepsTheFirst() {
        PortIndex index = PortIndex.EMPTY.with(A);
        Port again = new Port(A.id(), PortKind.NAVY_OUTPOST, A.dimension(), A.centre(), A.box(), Climate.COLD, List.of());
        assertSame(index, index.with(again));
        assertEquals(PortKind.SEAFARER_VILLAGE, index.byId(A.id()).orElseThrow().kind());
        assertEquals(PortIndex.EMPTY.ports(), index.without(A.id()).ports());
    }

    @Test
    void lookups() {
        PortIndex index = PortIndex.EMPTY.with(A).with(B).with(NETHER);
        assertEquals(Optional.of(B), index.byId(B.id()));
        assertEquals(Optional.empty(), index.byId(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "nowhere")));
        assertEquals(Optional.of(A), index.nearest(Level.OVERWORLD, new BlockPos(90, 70, 0)));
        assertEquals(Optional.of(B), index.nearest(Level.OVERWORLD, new BlockPos(110, 70, 0)));
        assertEquals(Optional.of(NETHER), index.nearest(Level.NETHER, new BlockPos(1000, 70, 0)), "other dimension");
        assertEquals(Optional.empty(), index.nearest(Level.END, BlockPos.ZERO));
        assertEquals(Optional.of(A), index.containing(Level.OVERWORLD, new BlockPos(20, 60, -20)));
        assertEquals(Optional.empty(), index.containing(Level.OVERWORLD, new BlockPos(20, 90, -20)), "above the box");
        assertEquals(Optional.empty(), index.containing(Level.OVERWORLD, new BlockPos(100, 60, 0)), "between ports");
        assertEquals(Optional.of(NETHER), index.containing(Level.NETHER, new BlockPos(0, 60, 0)), "same xz, other dimension");
    }
}

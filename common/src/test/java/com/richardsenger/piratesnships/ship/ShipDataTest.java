package com.richardsenger.piratesnships.ship;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipDataTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ShipData FULL = new ShipData(UUID.randomUUID(), "Black Pearl", Optional.of(UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()), FlagReading.struck(FlagKind.JOLLY_ROGER),
            ResourceLocation.withDefaultNamespace("overworld"), 12345L);

    @Test
    void roundTripsThroughNbt() {
        var tag = ShipData.CODEC.encodeStart(NbtOps.INSTANCE, FULL).getOrThrow();
        assertEquals(FULL, ShipData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void roundTripsThroughJson() {
        var json = ShipData.CODEC.encodeStart(JsonOps.INSTANCE, FULL).getOrThrow();
        assertEquals(FULL, ShipData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void freshRecordUsesDefaults() {
        ShipData fresh = ShipData.create(UUID.randomUUID(), Optional.empty(), ResourceLocation.withDefaultNamespace("overworld"));
        var tag = ShipData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        ShipData back = ShipData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(fresh, back);
        assertEquals("", back.name());
        assertEquals(List.of(), back.crew());
        assertEquals("Renamed", back.withName("Renamed").name());
        assertEquals(FlagReading.NO_FLAG, back.flag());
        assertEquals(0L, back.blownCoverUntil());
    }

    @Test
    void flagAndBlownCoverAreKeptByTheOtherWithers() {
        ShipData d = FULL.withName("x").withFlag(FlagReading.flying(FlagKind.NAVY)).withBlownCoverUntil(99L);
        assertEquals(FlagReading.flying(FlagKind.NAVY), d.flag());
        assertEquals(99L, d.blownCoverUntil());
        assertEquals("x", d.name());
        assertEquals(true, d.coverBlown(98L));
        assertEquals(false, d.coverBlown(99L));
    }

    @Test
    void preFl2PlaceholderFlagLoadsAsNoFlag() {
        var tag = (CompoundTag) ShipData.CODEC.encodeStart(NbtOps.INSTANCE, FULL).getOrThrow();
        tag.putString("flag", "pirates");
        tag.remove("blown_cover_until");
        ShipData back = ShipData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(FlagReading.NO_FLAG, back.flag());
        assertEquals(0L, back.blownCoverUntil());
        assertEquals(FULL.name(), back.name());
    }
}

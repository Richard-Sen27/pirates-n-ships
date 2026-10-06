package com.richardsenger.piratesnships.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shows how a unit test uses Minecraft classes (codecs, vanilla registries) on the common classpath: detect the
 * version and bootstrap vanilla once per test class. Mod registry content is NOT available here (it needs a loader);
 * use a GameTest for that.
 */
class MinecraftBootstrapTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    record Marker(ResourceLocation id, int weight) {
        static final Codec<Marker> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Marker::id),
                Codec.INT.fieldOf("weight").forGetter(Marker::weight)).apply(i, Marker::new));
    }

    @Test
    void codecRoundTrip() {
        Marker m = new Marker(Constants.id("test"), 7);
        var json = Marker.CODEC.encodeStart(JsonOps.INSTANCE, m).getOrThrow();
        assertEquals(m, Marker.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void vanillaRegistriesAreBootstrapped() {
        assertEquals(ResourceLocation.withDefaultNamespace("stone"), BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
    }
}

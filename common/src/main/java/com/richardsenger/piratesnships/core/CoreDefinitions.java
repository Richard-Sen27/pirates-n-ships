package com.richardsenger.piratesnships.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.data.DefinitionType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Datapack definitions of the {@code core} module: one tiny test type that proves the whole path (datagen →
 * datapack → reload → server store → client sync). Feature modules follow this pattern.
 */
public final class CoreDefinitions {

    /** A dev/test definition: {@code data/<ns>/pirates_n_ships/test_marker/<path>.json}. */
    public record TestMarker(String label, int weight) {
        public static final Codec<TestMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("label").forGetter(TestMarker::label),
                Codec.intRange(0, 1000).optionalFieldOf("weight", 1).forGetter(TestMarker::weight)).apply(i, TestMarker::new));

        public static final StreamCodec<ByteBuf, TestMarker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TestMarker::label,
                ByteBufCodecs.VAR_INT, TestMarker::weight,
                TestMarker::new);
    }

    public static final DefinitionType<TestMarker> TEST_MARKER = DefinitionType.create("test_marker", TestMarker.CODEC, TestMarker.STREAM_CODEC);

    /** The generated default entry ({@code data/pirates_n_ships/pirates_n_ships/test_marker/example.json}). */
    public static final ResourceLocation EXAMPLE_ID = Constants.id("example");
    public static final TestMarker EXAMPLE = new TestMarker("Example marker", 3);

    private CoreDefinitions() {
    }

    public static void init() {
    }
}

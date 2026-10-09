package com.richardsenger.piratesnships.almanac.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Where a player last read the Almanac (docs/design.md §3.4, ALM2): a chapter and the screen's page index in it. The
 * index is the screen's (ALM3 may split a long data page), so it is only clamped to {@code 0..MAX_PAGE}, never checked
 * against the chapter's page count; the screen clamps it to what it lays out. {@link #NONE} when the player never read.
 */
public record AlmanacProgress(Optional<ResourceLocation> chapter, int page) {

    /** Highest page index stored. */
    public static final int MAX_PAGE = 9999;

    public static final AlmanacProgress NONE = new AlmanacProgress(Optional.empty(), 0);

    public static final Codec<AlmanacProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("chapter").forGetter(AlmanacProgress::chapter),
            Codec.INT.optionalFieldOf("page", 0).forGetter(AlmanacProgress::page)
    ).apply(i, AlmanacProgress::new));

    public static final StreamCodec<ByteBuf, AlmanacProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), AlmanacProgress::chapter,
            ByteBufCodecs.VAR_INT, AlmanacProgress::page,
            AlmanacProgress::new);

    public AlmanacProgress {
        page = Math.max(0, Math.min(MAX_PAGE, page));
        if (chapter.isEmpty()) page = 0;
    }

    public static AlmanacProgress at(ResourceLocation chapter, int page) {
        return new AlmanacProgress(Optional.of(chapter), page);
    }

    public boolean isNone() {
        return chapter.isEmpty();
    }
}

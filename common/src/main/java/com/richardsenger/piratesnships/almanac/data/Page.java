package com.richardsenger.piratesnships.almanac.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * One page of an Almanac chapter: its elements in reading order (docs/almanac.md). Pages are explicit, there is no
 * page-break element; the screen (ALM3) may still split a page that overflows.
 */
public record Page(List<PageElement> elements) {

    /** Most elements on one page. */
    public static final int MAX_ELEMENTS = 64;

    public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
            PageElement.CODEC.listOf(0, MAX_ELEMENTS).fieldOf("elements").forGetter(Page::elements)
    ).apply(i, Page::new));

    public static final StreamCodec<ByteBuf, Page> STREAM_CODEC =
            PageElement.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ELEMENTS)).map(Page::new, Page::elements);

    public Page {
        elements = List.copyOf(elements);
    }

    public static Page of(PageElement... elements) {
        return new Page(List.of(elements));
    }
}

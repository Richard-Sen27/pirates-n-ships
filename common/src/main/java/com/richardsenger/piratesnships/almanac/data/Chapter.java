package com.richardsenger.piratesnships.almanac.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

/**
 * One chapter of the Captain's Almanac (docs/design.md §3.4, ALM2), loaded from
 * {@code data/<namespace>/almanac/chapters/<path>.json}; its id is {@code <namespace>:<path>} (format in docs/almanac.md).
 *
 * @param id            the chapter id, from the file path
 * @param title         lang key of the chapter title (also the bookmark's tooltip)
 * @param order         position in the book; chapters are sorted by order, then id
 * @param bookmarkColor RGB colour of the chapter's bookmark ribbon
 * @param icon          item drawn on the bookmark; an unknown item fails the chapter
 * @param pages         the pages, at least one
 */
public record Chapter(ResourceLocation id, String title, int order, int bookmarkColor, ResourceLocation icon, List<Page> pages) {

    /** Most pages in one chapter. */
    public static final int MAX_PAGES = 256;
    /** The order of a chapter whose file names none: after the mod's own chapters. */
    public static final int DEFAULT_ORDER = 1000;
    /** The bookmark colour when a file names none: a dark red ribbon. */
    public static final int DEFAULT_BOOKMARK_COLOR = 0x8B1A1A;

    /** Book order: {@link #order}, then {@link #id}. */
    public static final Comparator<Chapter> ORDER = Comparator.comparingInt(Chapter::order).thenComparing(Chapter::id);

    public static final StreamCodec<ByteBuf, Chapter> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Chapter::id,
            AlmanacCodecs.STRING, Chapter::title,
            ByteBufCodecs.VAR_INT, Chapter::order,
            ByteBufCodecs.INT, Chapter::bookmarkColor,
            ResourceLocation.STREAM_CODEC, Chapter::icon,
            Page.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PAGES)), Chapter::pages,
            Chapter::new);

    public Chapter {
        pages = List.copyOf(pages);
    }

    /** The file contents of this chapter (everything but the id). */
    public Body body() {
        return new Body(title, order, bookmarkColor, icon, pages);
    }

    public int pageCount() {
        return pages.size();
    }

    /** What a chapter file holds: the chapter without its id, which comes from the file path. */
    public record Body(String title, int order, int bookmarkColor, ResourceLocation icon, List<Page> pages) {

        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                AlmanacCodecs.LANG_KEY.fieldOf("title").forGetter(Body::title),
                Codec.INT.optionalFieldOf("order", DEFAULT_ORDER).forGetter(Body::order),
                AlmanacCodecs.COLOR.optionalFieldOf("bookmark_color", DEFAULT_BOOKMARK_COLOR).forGetter(Body::bookmarkColor),
                AlmanacCodecs.ITEM_ID.fieldOf("icon").forGetter(Body::icon),
                Page.CODEC.listOf(1, MAX_PAGES).fieldOf("pages").forGetter(Body::pages)
        ).apply(i, Body::new));

        public Body {
            pages = List.copyOf(pages);
        }

        public Chapter withId(ResourceLocation id) {
            return new Chapter(id, title, order, bookmarkColor, icon, pages);
        }
    }
}

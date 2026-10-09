package com.richardsenger.piratesnships.almanac.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The whole Captain's Almanac: every loaded chapter in book order ({@link Chapter#ORDER}). Immutable. The server's book
 * comes from the datapack reload ({@link #server()}); the client keeps the last synced one
 * ({@code almanac.sync.ClientAlmanac}). Pure logic takes a book as a parameter.
 */
public final class AlmanacBook {

    /** Most chapters synced. */
    public static final int MAX_CHAPTERS = 512;

    public static final AlmanacBook EMPTY = new AlmanacBook(List.of());

    public static final StreamCodec<ByteBuf, AlmanacBook> STREAM_CODEC =
            Chapter.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHAPTERS)).map(AlmanacBook::of, AlmanacBook::chapters);

    private static volatile AlmanacBook server = EMPTY;

    private final List<Chapter> chapters;
    private final Map<ResourceLocation, Chapter> byId;

    private AlmanacBook(List<Chapter> sorted) {
        this.chapters = List.copyOf(sorted);
        Map<ResourceLocation, Chapter> map = new LinkedHashMap<>();
        for (Chapter c : sorted) map.put(c.id(), c);
        this.byId = map;
    }

    /** A book of {@code chapters}, sorted by order then id. A repeated id keeps its first chapter. */
    public static AlmanacBook of(Collection<Chapter> chapters) {
        Map<ResourceLocation, Chapter> unique = new LinkedHashMap<>();
        for (Chapter c : chapters) unique.putIfAbsent(c.id(), c);
        List<Chapter> sorted = new ArrayList<>(unique.values());
        sorted.sort(Chapter.ORDER);
        return sorted.isEmpty() ? EMPTY : new AlmanacBook(sorted);
    }

    /** The chapters in book order. */
    public List<Chapter> chapters() {
        return chapters;
    }

    public Optional<Chapter> chapter(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean isEmpty() {
        return chapters.isEmpty();
    }

    public int size() {
        return chapters.size();
    }

    /** The server's book from the last datapack reload; empty before the first one and after the server stopped. */
    public static AlmanacBook server() {
        return server;
    }

    static void setServer(AlmanacBook book) {
        server = book;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AlmanacBook other && chapters.equals(other.chapters);
    }

    @Override
    public int hashCode() {
        return chapters.hashCode();
    }

    @Override
    public String toString() {
        return "AlmanacBook" + chapters.stream().map(c -> c.id() + "(" + c.pageCount() + ")").toList();
    }
}

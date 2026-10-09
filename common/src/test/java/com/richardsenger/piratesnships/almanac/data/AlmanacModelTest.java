package com.richardsenger.piratesnships.almanac.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Almanac's chapter model (ALM2): codec round trips, validation, book order and the parser's broken-file handling. */
class AlmanacModelTest {

    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("book");
    private static final ResourceLocation GUNPOWDER = ResourceLocation.withDefaultNamespace("gunpowder");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static List<PageElement> everyElement() {
        return List.of(
                PageElement.Text.of("a.text"),
                new PageElement.Text("a.styled", true, true, Optional.of(0x12AB34)),
                new PageElement.Heading("a.heading"),
                new PageElement.ListItems(List.of("a.one", "a.two")),
                new PageElement.Illustration(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "textures/gui/x.png"), 0.1f, 0.2f, 0.5f, 0.75f),
                new PageElement.ItemIcon(GUNPOWDER, 0.25f, 0.5f, Optional.of("a.caption")),
                new PageElement.ItemIcon(BOOK, 0f, 1f, Optional.empty()),
                new PageElement.ShowMe("pirates_n_ships:cannon_loading", "a.show_me"),
                new PageElement.RecipePanel(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "helm")),
                new PageElement.Spotlight(GUNPOWDER));
    }

    private static <T> T wire(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec read everything it wrote");
        return back;
    }

    @Test
    void everyElementRoundTripsThroughJsonAndTheWire() {
        for (PageElement e : everyElement()) {
            JsonElement json = PageElement.CODEC.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
            assertEquals(e.kind().getSerializedName(), json.getAsJsonObject().get("type").getAsString(), "type field of " + e);
            assertEquals(e, PageElement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), "JSON round trip of " + e);
            assertEquals(e, wire(PageElement.STREAM_CODEC, e), "wire round trip of " + e);
        }
    }

    @Test
    void elementsDecodeFromTheDocumentedJson() {
        PageElement text = parseElement("{\"type\":\"text\",\"key\":\"k\",\"bold\":true,\"color\":\"#ff0000\"}");
        assertEquals(new PageElement.Text("k", true, false, Optional.of(0xFF0000)), text);
        assertEquals(new PageElement.ListItems(List.of("x", "y")), parseElement("{\"type\":\"list\",\"items\":[\"x\",\"y\"]}"));
        assertEquals(new PageElement.ShowMe("ns:scene", "lbl"), parseElement("{\"type\":\"show_me\",\"scene\":\"ns:scene\",\"label\":\"lbl\"}"));
        assertEquals(new PageElement.RecipePanel(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "helm")),
                parseElement("{\"type\":\"recipe\",\"recipe\":\"pirates_n_ships:helm\"}"));
        assertEquals(new PageElement.Spotlight(GUNPOWDER), parseElement("{\"type\":\"spotlight\",\"item\":\"minecraft:gunpowder\"}"));
    }

    private static PageElement parseElement(String json) {
        return PageElement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static boolean rejects(String json) {
        return PageElement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent();
    }

    @Test
    void invalidElementsAreRejected() {
        assertTrue(rejects("{\"type\":\"item\",\"item\":\"minecraft:no_such_item\",\"x\":0,\"y\":0}"), "unknown item");
        assertTrue(rejects("{\"type\":\"item\",\"item\":\"minecraft:air\",\"x\":0,\"y\":0}"), "air");
        assertTrue(rejects("{\"type\":\"sparkles\",\"key\":\"k\"}"), "unknown element type");
        assertTrue(rejects("{\"type\":\"spotlight\",\"item\":\"minecraft:no_such_item\"}"), "spotlight of an unknown item");
        assertTrue(rejects("{\"type\":\"recipe\",\"recipe\":\"Not An Id\"}"), "recipe panel without a valid id");
        assertTrue(rejects("{\"type\":\"illustration\",\"texture\":\"a:b.png\",\"x\":0.6,\"y\":0,\"w\":0.5,\"h\":0.5}"), "past the right edge");
        assertTrue(rejects("{\"type\":\"illustration\",\"texture\":\"a:b.png\",\"x\":0,\"y\":0,\"w\":0,\"h\":0.5}"), "no width");
        assertTrue(rejects("{\"type\":\"illustration\",\"texture\":\"a:b.png\",\"x\":-0.1,\"y\":0,\"w\":0.5,\"h\":0.5}"), "negative x");
        assertTrue(rejects("{\"type\":\"text\",\"key\":\"has space\"}"), "lang key with whitespace");
        assertTrue(rejects("{\"type\":\"text\",\"key\":\"k\",\"color\":\"red\"}"), "colour not #rrggbb");
        assertTrue(rejects("{\"type\":\"list\",\"items\":[]}"), "empty list");
        assertFalse(rejects("{\"type\":\"text\",\"key\":\"some.unknown.lang.key\"}"), "unknown lang keys are fine");
    }

    static Chapter chapter(String path, int order, int pages) {
        Page page = Page.of(everyElement().toArray(PageElement[]::new));
        return new Chapter(ResourceLocation.fromNamespaceAndPath("test", path), "test.almanac." + path + ".title", order, 0x112233, BOOK,
                java.util.Collections.nCopies(pages, page));
    }

    @Test
    void chapterBodyAndBookRoundTrip() {
        Chapter c = chapter("guns", 5, 3);
        JsonElement json = Chapter.Body.CODEC.encodeStart(JsonOps.INSTANCE, c.body()).getOrThrow();
        assertEquals(c, Chapter.Body.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().withId(c.id()));
        assertEquals("#112233", json.getAsJsonObject().get("bookmark_color").getAsString());
        assertEquals(c, wire(Chapter.STREAM_CODEC, c));

        AlmanacBook book = AlmanacBook.of(List.of(c, chapter("sails", 1, 1)));
        assertEquals(book, wire(AlmanacBook.STREAM_CODEC, book));
        assertEquals(AlmanacBook.EMPTY, wire(AlmanacBook.STREAM_CODEC, AlmanacBook.EMPTY));
    }

    @Test
    void chapterDefaultsApply() {
        Chapter.Body body = Chapter.Body.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"title\":\"t\",\"icon\":\"minecraft:book\",\"pages\":[{\"elements\":[]}]}")).getOrThrow();
        assertEquals(Chapter.DEFAULT_ORDER, body.order());
        assertEquals(Chapter.DEFAULT_BOOKMARK_COLOR, body.bookmarkColor());
        assertEquals(1, body.pages().size());
    }

    @Test
    void bookIsSortedByOrderThenId() {
        AlmanacBook book = AlmanacBook.of(List.of(chapter("zeta", 1, 1), chapter("late", 9, 1), chapter("alpha", 1, 1), chapter("first", -3, 1)));
        assertEquals(List.of("first", "alpha", "zeta", "late"), book.chapters().stream().map(c -> c.id().getPath()).toList());
        assertTrue(book.chapter(ResourceLocation.fromNamespaceAndPath("test", "late")).isPresent());
        assertTrue(book.chapter(ResourceLocation.fromNamespaceAndPath("test", "nope")).isEmpty());
    }

    @Test
    void parserSkipsBrokenFilesAndKeepsTheRest() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(ResourceLocation.fromNamespaceAndPath("test", "good"), JsonParser.parseString(
                "{\"title\":\"t.good\",\"order\":2,\"icon\":\"minecraft:book\",\"pages\":[{\"elements\":[{\"type\":\"heading\",\"key\":\"h\"}]}]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("test", "first"), JsonParser.parseString(
                "{\"title\":\"t.first\",\"order\":1,\"icon\":\"minecraft:gunpowder\",\"pages\":[{\"elements\":[]},{\"elements\":[]}]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("test", "bad_item"), JsonParser.parseString(
                "{\"title\":\"t\",\"icon\":\"minecraft:book\",\"pages\":[{\"elements\":[{\"type\":\"item\",\"item\":\"nope:thing\",\"x\":0,\"y\":0}]}]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("test", "no_pages"), JsonParser.parseString(
                "{\"title\":\"t\",\"icon\":\"minecraft:book\",\"pages\":[]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("test", "not_an_object"), JsonParser.parseString("[1, 2]"));
        files.put(ResourceLocation.fromNamespaceAndPath("test", "null"), null);

        ChapterParser.Result result = ChapterParser.parse(files);
        assertEquals(List.of("first", "good"), result.book().chapters().stream().map(c -> c.id().getPath()).toList());
        assertEquals(2, result.book().chapters().get(0).pageCount());
        assertEquals(4, result.errors().size(), "errors " + result.errors());
        assertTrue(result.errors().stream().anyMatch(e -> e.id().getPath().equals("bad_item") && e.message().contains("unknown item")),
                "the unknown item is named: " + result.errors());
    }

    @Test
    void progressClampsAndRoundTrips() {
        ResourceLocation ch = ResourceLocation.fromNamespaceAndPath("test", "c");
        assertEquals(0, AlmanacProgress.at(ch, -5).page());
        assertEquals(AlmanacProgress.MAX_PAGE, AlmanacProgress.at(ch, Integer.MAX_VALUE).page());
        assertEquals(0, new AlmanacProgress(Optional.empty(), 7).page(), "no chapter, no page");
        for (AlmanacProgress p : List.of(AlmanacProgress.NONE, AlmanacProgress.at(ch, 12))) {
            JsonElement json = AlmanacProgress.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
            assertEquals(p, AlmanacProgress.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            assertEquals(p, wire(AlmanacProgress.STREAM_CODEC, p));
        }
    }

    @Test
    void langKeysFollowTheConvention() {
        ResourceLocation ch = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "ships/hull");
        assertEquals("pirates_n_ships.almanac.ships.hull.title", AlmanacLang.title(ch));
        assertEquals("pirates_n_ships.almanac.ships.hull.p2.text", AlmanacLang.page(ch, 2, "text"));
    }
}

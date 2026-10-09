package com.richardsenger.piratesnships.almanac.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Almanac's inline markup (ALM3). */
class MarkupTest {

    private static final Markup.Style PLAIN = Markup.Style.PLAIN;

    private static List<Markup.Span> parse(String s) {
        return Markup.parse(s, PLAIN, id -> "Title of " + id.getPath());
    }

    private static Markup.TextSpan text(Markup.Span span) {
        return assertInstanceOf(Markup.TextSpan.class, span);
    }

    @Test
    void plainTextIsOneSpan() {
        List<Markup.Span> spans = parse("Hello, sailor.");
        assertEquals(List.of(new Markup.TextSpan("Hello, sailor.", PLAIN)), spans);
    }

    @Test
    void doubleStarsToggleBold() {
        List<Markup.Span> spans = parse("a **b** c");
        assertEquals(3, spans.size());
        assertEquals("a ", text(spans.get(0)).text());
        assertFalse(spans.get(0).style().bold());
        assertEquals("b", text(spans.get(1)).text());
        assertTrue(spans.get(1).style().bold());
        assertEquals(" c", text(spans.get(2)).text());
        assertFalse(spans.get(2).style().bold());
    }

    @Test
    void underscoresToggleItalicOnlyAtWordEdges() {
        List<Markup.Span> spans = parse("an _italic_ word and snake_case");
        assertEquals("an ", text(spans.get(0)).text());
        assertEquals("italic", text(spans.get(1)).text());
        assertTrue(spans.get(1).style().italic());
        assertEquals(" word and snake_case", text(spans.get(2)).text());
        assertFalse(spans.get(2).style().italic());
    }

    @Test
    void unclosedBoldRunsToTheEnd() {
        List<Markup.Span> spans = parse("x **rest");
        assertEquals(2, spans.size());
        assertTrue(spans.get(1).style().bold());
        assertEquals("rest", text(spans.get(1)).text());
    }

    @Test
    void coloursNestAndPop() {
        List<Markup.Span> spans = parse("{#ff0000}r{#00ff00}g{/}r{/}d");
        assertEquals(4, spans.size());
        assertEquals(0xFF0000, spans.get(0).style().color());
        assertEquals(0x00FF00, spans.get(1).style().color());
        assertEquals(0xFF0000, spans.get(2).style().color());
        assertFalse(spans.get(3).style().hasColor());
        assertEquals("rgrd", Markup.plain(spans));
    }

    @Test
    void itemTokenIsAnInlineIcon() {
        List<Markup.Span> spans = parse("load {item:minecraft:gunpowder} first, {item:stick} too");
        Markup.IconSpan powder = assertInstanceOf(Markup.IconSpan.class, spans.get(1));
        assertEquals(ResourceLocation.withDefaultNamespace("gunpowder"), powder.item());
        Markup.IconSpan stick = assertInstanceOf(Markup.IconSpan.class, spans.get(3));
        assertEquals(ResourceLocation.withDefaultNamespace("stick"), stick.item());
        assertEquals("load  first,  too", Markup.plain(spans));
    }

    @Test
    void malformedTokensStayLiteral() {
        assertEquals(List.of(new Markup.TextSpan("{foo} {#12} {item:Bad Id} [[ ]] [[open", PLAIN)),
                parse("{foo} {#12} {item:Bad Id} [[ ]] [[open"));
    }

    @Test
    void linksShowTheChapterTitleOrTheirOwnText() {
        List<Markup.Span> spans = parse("see [[cannons]] or [[other:ships|the ships]]");
        Markup.TextSpan first = text(spans.get(1));
        assertEquals("Title of cannons", first.text());
        assertEquals(ResourceLocation.fromNamespaceAndPath(Markup.DEFAULT_LINK_NAMESPACE, "cannons"), first.style().link());
        Markup.TextSpan second = text(spans.get(3));
        assertEquals("the ships", second.text());
        assertEquals(ResourceLocation.fromNamespaceAndPath("other", "ships"), second.style().link());
        assertEquals(null, spans.get(2).style().link());
    }

    @Test
    void backslashEscapes() {
        assertEquals(List.of(new Markup.TextSpan("**not bold** _x_ {#ff0000} [[y]] \\", PLAIN)),
                parse("\\*\\*not bold\\*\\* \\_x\\_ \\{#ff0000} \\[[y]] \\\\"));
    }

    @Test
    void baseStyleIsKept() {
        Markup.Style base = new Markup.Style(true, false, 0x123456, null);
        List<Markup.Span> spans = Markup.parse("**still bold** _and italic_", base, ResourceLocation::toString);
        for (Markup.Span s : spans) {
            assertTrue(s.style().bold());
            assertEquals(0x123456, s.style().color());
        }
        assertTrue(spans.getLast().style().italic());
    }
}

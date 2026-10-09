package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.data.Chapter;
import com.richardsenger.piratesnships.almanac.data.Page;
import com.richardsenger.piratesnships.almanac.data.PageElement;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Almanac's page layout (ALM3) with a fixed-width font: 6 px per character, 7 when bold. */
class PageLayoutTest {

    private static final FontMetrics MONO = (text, bold, italic) -> text.length() * (bold ? 7 : 6);
    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");
    private static final ResourceLocation PICTURE = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "textures/gui/almanac/x.png");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Lang keys are their own text unless mapped; links show "T:" + the chapter path. */
    private static PageLayout.TextSource lang(Map<String, String> map) {
        return new PageLayout.TextSource() {
            @Override
            public String text(String key) {
                return map.getOrDefault(key, key);
            }

            @Override
            public String chapterTitle(ResourceLocation chapter) {
                return "T:" + chapter.getPath();
            }
        };
    }

    private static List<PageLayout.ScreenPage> lay(int w, int h, PageElement... elements) {
        return PageLayout.layout(Page.of(elements), 0, w, h, MONO, lang(Map.of()));
    }

    private static List<PageLayout.TextRun> runs(PageLayout.ScreenPage page) {
        return page.elements().stream().filter(p -> p instanceof PageLayout.TextRun).map(p -> (PageLayout.TextRun) p).toList();
    }

    private static <T extends PageLayout.Placed> List<T> of(PageLayout.ScreenPage page, Class<T> type) {
        return page.elements().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void textWrapsAtWordsWithinTheWidth() {
        List<PageLayout.ScreenPage> pages = lay(60, 100, PageElement.Text.of("aaaa bbbb cccc dddd"));
        assertEquals(1, pages.size());
        List<PageLayout.TextRun> runs = runs(pages.getFirst());
        assertEquals(List.of("aaaa bbbb", "cccc dddd"), runs.stream().map(PageLayout.TextRun::text).toList());
        assertEquals(0, runs.get(0).y());
        assertEquals(PageLayout.LINE_H, runs.get(1).y());
        for (PageLayout.TextRun r : runs) {
            assertEquals(0, r.x());
            assertTrue(r.width() <= 60);
        }
    }

    @Test
    void aWordLongerThanTheLineIsBroken() {
        List<PageLayout.TextRun> runs = runs(lay(60, 100, PageElement.Text.of("abcdefghijklmnop")).getFirst());
        assertEquals(List.of("abcdefghij", "klmnop"), runs.stream().map(PageLayout.TextRun::text).toList());
    }

    @Test
    void newlinesBreakLines() {
        List<PageLayout.TextRun> runs = runs(lay(120, 100, PageElement.Text.of("a\nb\n\nc")).getFirst());
        assertEquals(List.of("a", "b", "c"), runs.stream().map(PageLayout.TextRun::text).toList());
        assertEquals(List.of(0, 10, 30), runs.stream().map(PageLayout.TextRun::y).toList());
    }

    @Test
    void overflowSpillsOntoContinuationPages() {
        // five lines on a page of three
        List<PageLayout.ScreenPage> pages = PageLayout.layout(Page.of(PageElement.Text.of("aaaa bbbb cccc dddd eeee")), 3,
                24, 30, MONO, lang(Map.of()));
        assertEquals(2, pages.size());
        assertFalse(pages.get(0).continuation());
        assertTrue(pages.get(1).continuation());
        assertEquals(3, pages.get(1).dataPage());
        assertEquals(List.of("aaaa", "bbbb", "cccc"), runs(pages.get(0)).stream().map(PageLayout.TextRun::text).toList());
        assertEquals(List.of("dddd", "eeee"), runs(pages.get(1)).stream().map(PageLayout.TextRun::text).toList());
        assertEquals(0, runs(pages.get(1)).getFirst().y());
    }

    @Test
    void headingsAreBoldCentredAndCloseToTheirText() {
        List<PageLayout.TextRun> runs = runs(lay(120, 100, new PageElement.Heading("Title"), PageElement.Text.of("x")).getFirst());
        PageLayout.TextRun heading = runs.getFirst();
        assertEquals((120 - 35) / 2, heading.x());
        assertTrue(heading.style().bold());
        assertEquals(PageLayout.HEADING_COLOR, heading.style().color());
        assertEquals(PageLayout.LINE_H + PageLayout.HEADING_GAP, runs.get(1).y());
    }

    @Test
    void paragraphsAreSpacedApart() {
        List<PageLayout.TextRun> runs = runs(lay(120, 100, PageElement.Text.of("a"), PageElement.Text.of("b")).getFirst());
        assertEquals(PageLayout.LINE_H + PageLayout.PARAGRAPH_GAP, runs.get(1).y());
    }

    @Test
    void aHeadingNeverEndsAPageAlone() {
        // two lines of text (y 0 and 10), a gap to 24; heading and one line would need 44 > 30
        List<PageLayout.ScreenPage> pages = lay(30, 30, PageElement.Text.of("aaaa bbbb"), new PageElement.Heading("Head"),
                PageElement.Text.of("cc"));
        assertEquals(2, pages.size());
        PageLayout.TextRun heading = runs(pages.get(1)).getFirst();
        assertEquals("Head", heading.text());
        assertEquals(0, heading.y());
    }

    @Test
    void listItemsGetBulletsAndHangingIndents() {
        List<PageLayout.ScreenPage> pages = lay(60, 100, new PageElement.ListItems(List.of("aaaa bbbb", "cc")));
        PageLayout.ScreenPage page = pages.getFirst();
        List<PageLayout.Bullet> bullets = of(page, PageLayout.Bullet.class);
        assertEquals(2, bullets.size());
        assertEquals(0, bullets.get(0).x());
        assertEquals(0, bullets.get(0).y());
        List<PageLayout.TextRun> runs = runs(page);
        // 51 px of text room: "aaaa bbbb" (54) wraps, its second line indented like the first
        assertEquals(List.of("aaaa", "bbbb", "cc"), runs.stream().map(PageLayout.TextRun::text).toList());
        for (PageLayout.TextRun r : runs) assertEquals(PageLayout.LIST_INDENT, r.x());
        assertEquals(2 * PageLayout.LINE_H + PageLayout.LIST_ITEM_GAP, runs.get(2).y());
        assertEquals(runs.get(2).y(), bullets.get(1).y());
    }

    @Test
    void illustrationsSitAtTheirUnitBox() {
        PageLayout.ScreenPage page = lay(100, 100, new PageElement.Illustration(PICTURE, 0.5f, 0.25f, 0.5f, 0.5f)).getFirst();
        PageLayout.Picture pic = of(page, PageLayout.Picture.class).getFirst();
        assertEquals(new PageLayout.Area(50, 25, 50, 50), pic.box());
        assertEquals(PICTURE, pic.texture());
    }

    @Test
    void textFlowsBesideAnIllustration() {
        PageLayout.ScreenPage page = lay(100, 100, new PageElement.Illustration(PICTURE, 0.5f, 0.25f, 0.5f, 0.5f),
                PageElement.Text.of("aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj")).getFirst();
        // the picture keeps x 48..100, y 23..77 free
        for (PageLayout.TextRun r : runs(page)) {
            boolean besidePicture = r.y() + PageLayout.LINE_H > 23 && r.y() < 77;
            if (besidePicture) assertTrue(r.x() + r.width() <= 48, r + " overlaps the illustration");
        }
        assertTrue(runs(page).stream().anyMatch(r -> r.y() == 30), "text continues beside the picture");
    }

    @Test
    void aFullWidthIllustrationPushesTextBelowIt() {
        PageLayout.ScreenPage page = lay(100, 100, new PageElement.Illustration(PICTURE, 0f, 0f, 1f, 0.5f),
                PageElement.Text.of("text")).getFirst();
        assertEquals(50 + PageLayout.OBSTACLE_PAD, runs(page).getFirst().y());
    }

    @Test
    void itemIconsCarryACentredCaption() {
        PageLayout.ScreenPage page = PageLayout.layout(Page.of(new PageElement.ItemIcon(STICK, 0.5f, 0.5f, Optional.of("k.cap"))),
                0, 100, 100, MONO, lang(Map.of("k.cap", "Gun"))).getFirst();
        PageLayout.ItemIcon icon = of(page, PageLayout.ItemIcon.class).getFirst();
        assertEquals(50, icon.x());
        assertEquals(50, icon.y());
        PageLayout.TextRun caption = runs(page).getFirst();
        assertEquals("Gun", caption.text());
        assertEquals(50 + 8 - 9, caption.x());
        assertEquals(50 + PageLayout.ITEM_SIZE + PageLayout.CAPTION_GAP, caption.y());
        assertEquals(PageLayout.CAPTION_COLOR, caption.style().color());
    }

    @Test
    void itemIconsStayInsideThePage() {
        PageLayout.ScreenPage page = lay(100, 80, new PageElement.ItemIcon(STICK, 1f, 1f, Optional.empty())).getFirst();
        PageLayout.ItemIcon icon = of(page, PageLayout.ItemIcon.class).getFirst();
        assertEquals(100 - PageLayout.ITEM_SIZE, icon.x());
        assertEquals(80 - PageLayout.ITEM_SIZE, icon.y());
    }

    @Test
    void showMeButtonsAreCentred() {
        PageLayout.ScreenPage page = PageLayout.layout(Page.of(new PageElement.ShowMe("pirates_n_ships:scene", "k.label")), 0,
                100, 100, MONO, lang(Map.of("k.label", "Show me"))).getFirst();
        PageLayout.Button b = of(page, PageLayout.Button.class).getFirst();
        int w = 7 * 6 + 2 * PageLayout.BUTTON_PAD;
        assertEquals(new PageLayout.Area((100 - w) / 2, 0, w, PageLayout.BUTTON_H), b.area());
        assertEquals("Show me", b.label());
        assertEquals("pirates_n_ships:scene", b.scene());
    }

    @Test
    void freeElementsStayOnTheFirstScreenPage() {
        List<PageLayout.ScreenPage> pages = lay(24, 30, new PageElement.ItemIcon(STICK, 0f, 0.9f, Optional.empty()),
                PageElement.Text.of("aaaa bbbb cccc dddd"));
        assertEquals(2, pages.size());
        assertEquals(1, of(pages.get(0), PageLayout.ItemIcon.class).size());
        assertEquals(0, of(pages.get(1), PageLayout.ItemIcon.class).size());
    }

    @Test
    void markupRunsSitSideBySide() {
        List<PageLayout.TextRun> runs = runs(lay(200, 100, PageElement.Text.of("a **b** c")).getFirst());
        assertEquals(List.of("a ", "b", " c"), runs.stream().map(PageLayout.TextRun::text).toList());
        assertEquals(List.of(0, 12, 19), runs.stream().map(PageLayout.TextRun::x).toList());
        assertTrue(runs.get(1).style().bold());
    }

    @Test
    void linksAndInlineIconsAreLaidOut() {
        PageLayout.ScreenPage page = lay(200, 100, PageElement.Text.of("x {item:stick} [[cannons]]")).getFirst();
        PageLayout.InlineIcon icon = of(page, PageLayout.InlineIcon.class).getFirst();
        assertEquals(12, icon.x());
        assertEquals(STICK, icon.item());
        PageLayout.TextRun link = runs(page).stream().filter(r -> r.style().link() != null).findFirst().orElseThrow();
        assertEquals("T:cannons", link.text());
        assertEquals(12 + PageLayout.INLINE_ICON + 6, link.x());
    }

    @Test
    void aChapterIsItsDataPagesInOrder() {
        Chapter chapter = new Chapter(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "c"), "c.title", 0, 0, STICK,
                List.of(Page.of(PageElement.Text.of("one")), Page.of(), Page.of(PageElement.Text.of("three"))));
        List<PageLayout.ScreenPage> pages = PageLayout.layoutChapter(chapter, 100, 100, MONO, lang(Map.of()));
        assertEquals(List.of(0, 1, 2), pages.stream().map(PageLayout.ScreenPage::dataPage).toList());
        assertTrue(pages.get(1).elements().isEmpty());
    }

    @Test
    void picturesFitTheirBoxKeepingTheAspect() {
        assertEquals(new PageLayout.Area(0, 25, 100, 50), PageLayout.fit(200, 100, new PageLayout.Area(0, 0, 100, 100)));
        assertEquals(new PageLayout.Area(35, 10, 30, 60), PageLayout.fit(16, 32, new PageLayout.Area(10, 10, 80, 60)));
    }
}

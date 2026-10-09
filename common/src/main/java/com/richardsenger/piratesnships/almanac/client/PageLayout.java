package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.data.Chapter;
import com.richardsenger.piratesnships.almanac.data.Page;
import com.richardsenger.piratesnships.almanac.data.PageElement;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Almanac's page layout engine (ALM3, docs/almanac.md). Pure: no client classes, font metrics and lang text are
 * injected. Lays one data {@link Page} into one or more screen pages of {@code width x height} GUI pixels (coordinates
 * relative to the page's text box, 0,0 top left):
 * <ul>
 *   <li>illustrations and item icons sit at their unit coordinates on the data page's first screen page (an item's
 *       caption is wrapped and centred under it) and are obstacles: a line that would cross one takes the widest free
 *       part of its row if that is at least a third of the page wide, else it moves below the obstacle;</li>
 *   <li>headings (bold, centred), paragraphs and bulleted lists wrap word by word with their {@link Markup} runs;
 *       "Show me" buttons are centred in the flow;</li>
 *   <li>what does not fit spills onto a continuation page (no free elements there), so one data page can become
 *       several screen pages. A heading never ends a page alone.</li>
 * </ul>
 */
public final class PageLayout {

    public static final int LINE_H = 10;
    public static final int PARAGRAPH_GAP = 4;
    public static final int HEADING_GAP = 2;
    public static final int LIST_INDENT = 9;
    public static final int LIST_ITEM_GAP = 1;
    /** Size of an icon inside a line of text. */
    public static final int INLINE_ICON = 9;
    /** Size of a free-placed item icon. */
    public static final int ITEM_SIZE = 16;
    public static final int BUTTON_H = 14;
    /** Space left and right of a button's label. */
    public static final int BUTTON_PAD = 8;
    /** Room kept free around illustrations and item icons. */
    public static final int OBSTACLE_PAD = 2;
    /** Space between an item icon and its caption. */
    public static final int CAPTION_GAP = 2;
    public static final int HEADING_COLOR = 0x5A2E12;
    public static final int CAPTION_COLOR = 0x7A6852;

    private PageLayout() {
    }

    /** Lang text for the layout: the screen uses {@code I18n}, tests a map. */
    public interface TextSource {

        /** The text of a lang key (the key itself when unknown). */
        String text(String key);

        /** The title a {@code [[chapter]]} link shows; the id when the chapter is unknown. */
        String chapterTitle(ResourceLocation chapter);
    }

    /** A rectangle in page pixels. */
    public record Area(int x, int y, int w, int h) {

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        Area pad(int p) {
            return new Area(x - p, y - p, w + 2 * p, h + 2 * p);
        }
    }

    /** Something drawn on a screen page. */
    public sealed interface Placed {
    }

    /** Text in one style on one line; links are hit-tested on {@code x, y, width, LINE_H}. */
    public record TextRun(int x, int y, int width, String text, Markup.Style style) implements Placed {

        public Area area() {
            return new Area(x, y, width, LINE_H);
        }
    }

    /** An item icon of {@link #INLINE_ICON} pixels inside a line. */
    public record InlineIcon(int x, int y, ResourceLocation item) implements Placed {
    }

    /** A list bullet; {@code x, y} is the top left of its line. */
    public record Bullet(int x, int y) implements Placed {
    }

    /** An illustration's box; the screen fits the image into it with {@link #fit}. */
    public record Picture(ResourceLocation texture, Area box) implements Placed {
    }

    /** A free-placed item icon of {@link #ITEM_SIZE} pixels. */
    public record ItemIcon(ResourceLocation item, int x, int y) implements Placed {

        public Area area() {
            return new Area(x, y, ITEM_SIZE, ITEM_SIZE);
        }
    }

    /** A "Show me" button. */
    public record Button(String scene, String label, Area area) implements Placed {
    }

    /**
     * One screen page.
     *
     * @param dataPage     index of the chapter's data page it comes from
     * @param continuation true for the second and later screen pages of a data page
     */
    public record ScreenPage(List<Placed> elements, int dataPage, boolean continuation) {

        public ScreenPage {
            elements = List.copyOf(elements);
        }

        public static ScreenPage empty() {
            return new ScreenPage(List.of(), 0, false);
        }
    }

    /** Every data page of {@code chapter}, one after another; at least one screen page. */
    public static List<ScreenPage> layoutChapter(Chapter chapter, int width, int height, FontMetrics metrics, TextSource text) {
        List<ScreenPage> out = new ArrayList<>();
        for (int i = 0; i < chapter.pageCount(); i++) out.addAll(layout(chapter.pages().get(i), i, width, height, metrics, text));
        if (out.isEmpty()) out.add(ScreenPage.empty());
        return out;
    }

    /** One data page; at least one screen page. */
    public static List<ScreenPage> layout(Page page, int dataPage, int width, int height, FontMetrics metrics, TextSource text) {
        Engine e = new Engine(dataPage, width, height, metrics, text);
        for (PageElement el : page.elements()) {
            if (el instanceof PageElement.Illustration ill) e.illustration(ill);
            else if (el instanceof PageElement.ItemIcon icon) e.itemIcon(icon);
        }
        for (PageElement el : page.elements()) {
            switch (el) {
                case PageElement.Heading hd -> e.heading(hd);
                case PageElement.Text tx -> e.text(tx);
                case PageElement.ListItems li -> e.list(li);
                case PageElement.ShowMe sm -> e.button(sm);
                default -> {
                }
            }
        }
        return e.finish();
    }

    /** {@code imgW x imgH} scaled to fit {@code box}, aspect kept, centred in it. */
    public static Area fit(int imgW, int imgH, Area box) {
        if (imgW <= 0 || imgH <= 0) return box;
        double s = Math.min(box.w() / (double) imgW, box.h() / (double) imgH);
        int w = Math.max(1, (int) Math.round(imgW * s));
        int h = Math.max(1, (int) Math.round(imgH * s));
        return new Area(box.x() + (box.w() - w) / 2, box.y() + (box.h() - h) / 2, w, h);
    }

    // ------------------------------------------------------------------ tokens

    private sealed interface Piece {
        int width();

        Markup.Style style();
    }

    private record TextPiece(String text, Markup.Style style, int width) implements Piece {
    }

    private record IconPiece(ResourceLocation item, Markup.Style style) implements Piece {
        @Override
        public int width() {
            return INLINE_ICON;
        }
    }

    private sealed interface Atom {
    }

    private record Space(TextPiece piece) implements Atom {
    }

    private record Word(List<Piece> pieces, int width) implements Atom {
    }

    private record Break() implements Atom {
    }

    /** A filled line: its pieces and width. */
    private record Line(List<Piece> pieces, int width) {
    }

    private static final class Engine {
        final int dataPage;
        final int w;
        final int h;
        final FontMetrics m;
        final TextSource t;
        final List<ScreenPage> pages = new ArrayList<>();
        List<Placed> cur = new ArrayList<>();
        List<Area> obstacles = new ArrayList<>();
        int y;
        boolean continuation;
        boolean afterHeading;

        Engine(int dataPage, int w, int h, FontMetrics m, TextSource t) {
            this.dataPage = dataPage;
            this.w = w;
            this.h = h;
            this.m = m;
            this.t = t;
        }

        List<ScreenPage> finish() {
            pages.add(new ScreenPage(cur, dataPage, continuation));
            return pages;
        }

        void newPage() {
            pages.add(new ScreenPage(cur, dataPage, continuation));
            cur = new ArrayList<>();
            obstacles = new ArrayList<>();
            y = 0;
            continuation = true;
        }

        // -------------------------------------------------------------- free elements

        void illustration(PageElement.Illustration ill) {
            int x0 = clamp(Math.round(ill.x() * w), 0, w - 1);
            int y0 = clamp(Math.round(ill.y() * h), 0, h - 1);
            int x1 = clamp(Math.round((ill.x() + ill.w()) * w), x0 + 1, w);
            int y1 = clamp(Math.round((ill.y() + ill.h()) * h), y0 + 1, h);
            Area box = new Area(x0, y0, x1 - x0, y1 - y0);
            cur.add(new Picture(ill.texture(), box));
            obstacles.add(box.pad(OBSTACLE_PAD));
        }

        void itemIcon(PageElement.ItemIcon icon) {
            int x = clamp(Math.round(icon.x() * w), 0, Math.max(0, w - ITEM_SIZE));
            int iy = clamp(Math.round(icon.y() * h), 0, Math.max(0, h - ITEM_SIZE));
            cur.add(new ItemIcon(icon.item(), x, iy));
            int left = x, right = x + ITEM_SIZE, bottom = iy + ITEM_SIZE;
            if (icon.caption().isPresent()) {
                Markup.Style base = new Markup.Style(false, false, CAPTION_COLOR, null);
                List<Atom> atoms = atoms(parse(icon.caption().get(), base));
                int maxW = Math.min(w, Math.max(48, w * 2 / 5));
                int[] i = {0};
                int ly = iy + ITEM_SIZE + CAPTION_GAP;
                while (true) {
                    Line line = fill(atoms, i, maxW);
                    if (line == null) break;
                    int lx = clamp(x + ITEM_SIZE / 2 - line.width() / 2, 0, Math.max(0, w - line.width()));
                    place(line, lx, ly);
                    left = Math.min(left, lx);
                    right = Math.max(right, lx + line.width());
                    ly += LINE_H;
                    bottom = ly;
                }
            }
            obstacles.add(new Area(left, iy, right - left, bottom - iy).pad(OBSTACLE_PAD));
        }

        // -------------------------------------------------------------- flow

        private void gap() {
            if (y > 0) y += afterHeading ? HEADING_GAP : PARAGRAPH_GAP;
            afterHeading = false;
        }

        /** Where a row of {@code lineH} pixels with at least {@code minW} free goes; may move down or to a new page. */
        private Area slot(int lineH, int indent, int minW) {
            while (true) {
                if (y > 0 && y + lineH > h) {
                    newPage();
                    continue;
                }
                List<Area> hits = new ArrayList<>();
                for (Area o : obstacles) if (o.y() < y + lineH && o.bottom() > y) hits.add(o);
                if (hits.isEmpty()) return new Area(indent, y, w - indent, lineH);
                hits.sort(Comparator.comparingInt(Area::x));
                int bestX = indent, bestW = 0, cursor = indent;
                for (Area o : hits) {
                    if (o.x() > cursor && o.x() - cursor > bestW) {
                        bestX = cursor;
                        bestW = Math.min(o.x(), w) - cursor;
                    }
                    cursor = Math.max(cursor, o.right());
                }
                if (w - cursor > bestW) {
                    bestX = cursor;
                    bestW = w - cursor;
                }
                if (bestW >= minW) return new Area(bestX, y, bestW, lineH);
                int below = Integer.MAX_VALUE;
                for (Area o : hits) below = Math.min(below, o.bottom());
                y = Math.max(y + 1, below);
            }
        }

        void heading(PageElement.Heading hd) {
            gap();
            if (y > 0 && y + 2 * LINE_H > h) newPage();
            paragraph(parse(hd.key(), new Markup.Style(true, false, HEADING_COLOR, null)), 0, true, false, w / 2);
            afterHeading = true;
        }

        void text(PageElement.Text tx) {
            gap();
            Markup.Style base = new Markup.Style(tx.bold(), tx.italic(), tx.color().orElse(Markup.Style.DEFAULT_COLOR), null);
            paragraph(parse(tx.key(), base), 0, false, false, w / 3);
        }

        void list(PageElement.ListItems li) {
            gap();
            boolean first = true;
            for (String key : li.keys()) {
                if (!first) y += LIST_ITEM_GAP;
                first = false;
                paragraph(parse(key, Markup.Style.PLAIN), LIST_INDENT, false, true, w / 3);
            }
        }

        void button(PageElement.ShowMe sm) {
            gap();
            String label = Markup.plain(parse(sm.label(), Markup.Style.PLAIN));
            int bw = Math.min(w, m.width(label, false, false) + 2 * BUTTON_PAD);
            Area s = slot(BUTTON_H, 0, bw);
            Area area = new Area(s.x() + (s.w() - bw) / 2, s.y(), bw, BUTTON_H);
            cur.add(new Button(sm.scene(), label, area));
            y = s.y() + BUTTON_H;
        }

        private void paragraph(List<Markup.Span> spans, int indent, boolean centred, boolean bullet, int minW) {
            List<Atom> atoms = atoms(spans);
            int[] i = {0};
            boolean first = true;
            while (i[0] < atoms.size()) {
                // a line break at the very start of a line still takes its line
                Area s = slot(LINE_H, indent, minW);
                Line line = fill(atoms, i, s.w());
                if (line == null) break;
                int x = s.x() + (centred ? Math.max(0, (s.w() - line.width()) / 2) : 0);
                if (first && bullet) cur.add(new Bullet(s.x() - LIST_INDENT, s.y()));
                place(line, x, s.y());
                y = s.y() + LINE_H;
                first = false;
            }
        }

        /** The next line from {@code atoms} at {@code i[0]} that fits {@code maxW}; null when nothing is left. */
        private Line fill(List<Atom> atoms, int[] i, int maxW) {
            while (i[0] < atoms.size() && atoms.get(i[0]) instanceof Space) i[0]++;
            if (i[0] >= atoms.size()) return null;
            List<Piece> line = new ArrayList<>();
            List<Piece> pending = new ArrayList<>();
            int lw = 0, pw = 0;
            while (i[0] < atoms.size()) {
                Atom a = atoms.get(i[0]);
                if (a instanceof Break) {
                    i[0]++;
                    break;
                }
                if (a instanceof Space sp) {
                    if (!line.isEmpty()) {
                        pending.add(sp.piece());
                        pw += sp.piece().width();
                    }
                    i[0]++;
                    continue;
                }
                Word word = (Word) a;
                if (line.isEmpty()) {
                    if (word.width() <= maxW) {
                        line.addAll(word.pieces());
                        lw = word.width();
                        i[0]++;
                        continue;
                    }
                    Word[] split = split(word, maxW);
                    line.addAll(split[0].pieces());
                    lw = split[0].width();
                    atoms.set(i[0], split[1]);
                    break;
                }
                if (lw + pw + word.width() > maxW) break;
                line.addAll(pending);
                line.addAll(word.pieces());
                lw += pw + word.width();
                pending.clear();
                pw = 0;
                i[0]++;
            }
            return new Line(line, lw);
        }

        /** A word too long for a line: the head that fits (at least one character) and the rest. */
        private Word[] split(Word word, int maxW) {
            List<Piece> head = new ArrayList<>();
            List<Piece> tail = new ArrayList<>();
            int hw = 0;
            boolean full = false;
            for (Piece p : word.pieces()) {
                if (full) {
                    tail.add(p);
                    continue;
                }
                if (hw + p.width() <= maxW) {
                    head.add(p);
                    hw += p.width();
                    continue;
                }
                full = true;
                if (p instanceof TextPiece tp) {
                    int k = 0;
                    while (k < tp.text().length()) {
                        int cw = m.width(tp.text().substring(0, k + 1), tp.style().bold(), tp.style().italic());
                        if (hw + cw > maxW) break;
                        k++;
                    }
                    if (k == 0 && head.isEmpty()) k = 1;
                    if (k > 0) {
                        TextPiece a = piece(tp.text().substring(0, k), tp.style());
                        head.add(a);
                        hw += a.width();
                    }
                    if (k < tp.text().length()) tail.add(piece(tp.text().substring(k), tp.style()));
                } else if (head.isEmpty()) {
                    head.add(p);
                    hw += p.width();
                } else {
                    tail.add(p);
                }
            }
            int tw = 0;
            for (Piece p : tail) tw += p.width();
            return new Word[]{new Word(head, hw), new Word(tail, tw)};
        }

        private void place(Line line, int x, int ly) {
            TextRun run = null;
            for (Piece p : line.pieces()) {
                if (p instanceof TextPiece tp) {
                    if (run != null && run.style().equals(tp.style()) && run.x() + run.width() == x) {
                        run = new TextRun(run.x(), ly, run.width() + tp.width(), run.text() + tp.text(), run.style());
                        cur.set(cur.size() - 1, run);
                    } else {
                        run = new TextRun(x, ly, tp.width(), tp.text(), tp.style());
                        cur.add(run);
                    }
                } else if (p instanceof IconPiece ip) {
                    cur.add(new InlineIcon(x, ly - 1, ip.item()));
                    run = null;
                }
                x += p.width();
            }
        }

        // -------------------------------------------------------------- tokens

        private List<Markup.Span> parse(String key, Markup.Style base) {
            return Markup.parse(t.text(key), base, t::chapterTitle);
        }

        private TextPiece piece(String s, Markup.Style style) {
            return new TextPiece(s, style, m.width(s, style.bold(), style.italic()));
        }

        private List<Atom> atoms(List<Markup.Span> spans) {
            List<Atom> out = new ArrayList<>();
            List<Piece> word = new ArrayList<>();
            for (Markup.Span span : spans) {
                if (span instanceof Markup.IconSpan icon) {
                    word.add(new IconPiece(icon.item(), icon.style()));
                    continue;
                }
                Markup.TextSpan ts = (Markup.TextSpan) span;
                String s = ts.text();
                int k = 0;
                while (k < s.length()) {
                    char c = s.charAt(k);
                    if (c == '\n') {
                        endWord(out, word);
                        out.add(new Break());
                        k++;
                    } else if (c == ' ') {
                        endWord(out, word);
                        out.add(new Space(piece(" ", ts.style())));
                        k++;
                    } else {
                        int e = k;
                        while (e < s.length() && s.charAt(e) != ' ' && s.charAt(e) != '\n') e++;
                        word.add(piece(s.substring(k, e), ts.style()));
                        k = e;
                    }
                }
            }
            endWord(out, word);
            return out;
        }

        private static void endWord(List<Atom> out, List<Piece> word) {
            if (word.isEmpty()) return;
            int ww = 0;
            for (Piece p : word) ww += p.width();
            out.add(new Word(List.copyOf(word), ww));
            word.clear();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

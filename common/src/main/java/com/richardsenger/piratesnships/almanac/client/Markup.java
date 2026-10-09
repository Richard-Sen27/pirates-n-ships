package com.richardsenger.piratesnships.almanac.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * The Almanac's inline markup inside lang text (ALM3, docs/almanac.md "Markup"). Pure, no client classes:
 * <ul>
 *   <li>{@code **bold**} toggles bold;</li>
 *   <li>{@code _italic_} toggles italic; an underscore opens only at the start of a word and closes only at its end,
 *       so {@code snake_case} stays literal;</li>
 *   <li>{@code {#rrggbb}text{/}} colours the text; colours nest, {@code {/}} returns to the one before;</li>
 *   <li>{@code {item:ns:id}} draws the item's icon inline (no namespace = {@code minecraft});</li>
 *   <li>{@code [[ns:path]]} links to that chapter's first page, shown as the chapter's title;
 *       {@code [[ns:path|text]]} shows {@code text} instead (no namespace = {@code pirates_n_ships});</li>
 *   <li>a backslash makes the next character literal ({@code \*}, {@code \_}, {@code \{}, {@code \[}, {@code \\}).</li>
 * </ul>
 * Anything malformed (an unknown {@code {...}} token, a bad id, an unclosed {@code [[}) stays literal text; unclosed
 * bold, italic or colour run to the end of the text.
 */
public final class Markup {

    /** Namespace of a link written without one. */
    public static final String DEFAULT_LINK_NAMESPACE = "pirates_n_ships";

    private Markup() {
    }

    /**
     * How a piece of text is drawn.
     *
     * @param color RGB ink, or {@link #DEFAULT_COLOR} for the page's default ink
     * @param link  the chapter a click jumps to, or null
     */
    public record Style(boolean bold, boolean italic, int color, @Nullable ResourceLocation link) {

        public static final int DEFAULT_COLOR = -1;
        public static final Style PLAIN = new Style(false, false, DEFAULT_COLOR, null);

        public Style withBold(boolean b) {
            return new Style(b, italic, color, link);
        }

        public Style withItalic(boolean i) {
            return new Style(bold, i, color, link);
        }

        public Style withColor(int c) {
            return new Style(bold, italic, c, link);
        }

        public Style withLink(@Nullable ResourceLocation l) {
            return new Style(bold, italic, color, l);
        }

        public boolean hasColor() {
            return color != DEFAULT_COLOR;
        }
    }

    /** A piece of parsed text. */
    public sealed interface Span {
        Style style();
    }

    public record TextSpan(String text, Style style) implements Span {
    }

    /** An item icon inside the text. */
    public record IconSpan(ResourceLocation item, Style style) implements Span {
    }

    /** Parses with no link labels: a link without {@code |text} shows its id. */
    public static List<Span> parse(String text, Style base) {
        return parse(text, base, ResourceLocation::toString);
    }

    /**
     * Parses {@code text}; every span starts from {@code base} (a paragraph's own bold, italic and colour), markup
     * turns bold or italic on where {@code base} has it off.
     *
     * @param linkLabel the text shown for {@code [[id]]} (the chapter's title)
     */
    public static List<Span> parse(String text, Style base, Function<ResourceLocation, String> linkLabel) {
        Parser p = new Parser(text, base, linkLabel);
        p.run();
        return p.out;
    }

    /** The text without markup, as it is shown (icons dropped). */
    public static String plain(List<Span> spans) {
        StringBuilder b = new StringBuilder();
        for (Span s : spans) if (s instanceof TextSpan t) b.append(t.text());
        return b.toString();
    }

    private static final class Parser {
        private final String s;
        private final Style base;
        private final Function<ResourceLocation, String> linkLabel;
        private final List<Span> out = new ArrayList<>();
        private final StringBuilder buf = new StringBuilder();
        private final Deque<Integer> colors = new ArrayDeque<>();
        private boolean bold;
        private boolean italic;

        Parser(String s, Style base, Function<ResourceLocation, String> linkLabel) {
            this.s = s;
            this.base = base;
            this.linkLabel = linkLabel;
        }

        private Style style() {
            Style st = base.withBold(base.bold() || bold).withItalic(base.italic() || italic);
            return colors.isEmpty() ? st : st.withColor(colors.peek());
        }

        private void flush() {
            if (buf.isEmpty()) return;
            add(new TextSpan(buf.toString(), style()));
            buf.setLength(0);
        }

        private void add(Span span) {
            if (span instanceof TextSpan t && !out.isEmpty() && out.getLast() instanceof TextSpan last
                    && last.style().equals(t.style())) {
                out.set(out.size() - 1, new TextSpan(last.text() + t.text(), t.style()));
            } else {
                out.add(span);
            }
        }

        private static boolean wordChar(char c) {
            return Character.isLetterOrDigit(c);
        }

        void run() {
            int i = 0;
            int n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < n) {
                    buf.append(s.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                    flush();
                    bold = !bold;
                    i += 2;
                    continue;
                }
                if (c == '_') {
                    char prev = i > 0 ? s.charAt(i - 1) : ' ';
                    char next = i + 1 < n ? s.charAt(i + 1) : ' ';
                    boolean opens = !italic && !wordChar(prev) && !Character.isWhitespace(next) && next != '_';
                    boolean closes = italic && !wordChar(next) && !Character.isWhitespace(prev);
                    if (opens || closes) {
                        flush();
                        italic = !italic;
                        i++;
                        continue;
                    }
                }
                if (c == '{') {
                    int end = s.indexOf('}', i + 1);
                    if (end > i && brace(s.substring(i + 1, end))) {
                        i = end + 1;
                        continue;
                    }
                }
                if (c == '[' && i + 1 < n && s.charAt(i + 1) == '[') {
                    int end = s.indexOf("]]", i + 2);
                    if (end > i && link(s.substring(i + 2, end))) {
                        i = end + 2;
                        continue;
                    }
                }
                buf.append(c);
                i++;
            }
            flush();
        }

        /** A {@code {...}} token; false leaves it literal. */
        private boolean brace(String body) {
            if (body.equals("/")) {
                flush();
                if (!colors.isEmpty()) colors.pop();
                return true;
            }
            if (body.length() == 7 && body.charAt(0) == '#') {
                int rgb = hex(body.substring(1));
                if (rgb < 0) return false;
                flush();
                colors.push(rgb);
                return true;
            }
            if (body.startsWith("item:")) {
                ResourceLocation id = ResourceLocation.tryParse(body.substring(5));
                if (id == null || body.length() == 5) return false;
                flush();
                out.add(new IconSpan(id, style()));
                return true;
            }
            return false;
        }

        private static int hex(String h) {
            int v = 0;
            for (int k = 0; k < h.length(); k++) {
                int d = Character.digit(h.charAt(k), 16);
                if (d < 0) return -1;
                v = v * 16 + d;
            }
            return v;
        }

        /** A {@code [[...]]} link; false leaves it literal. */
        private boolean link(String body) {
            int bar = body.indexOf('|');
            String target = (bar < 0 ? body : body.substring(0, bar)).trim();
            if (target.isEmpty()) return false;
            ResourceLocation id = target.indexOf(':') < 0
                    ? ResourceLocation.tryBuild(DEFAULT_LINK_NAMESPACE, target)
                    : ResourceLocation.tryParse(target);
            if (id == null) return false;
            String label = bar < 0 ? linkLabel.apply(id) : body.substring(bar + 1);
            if (label == null || label.isEmpty()) label = id.toString();
            flush();
            add(new TextSpan(label, style().withLink(id)));
            return true;
        }
    }
}

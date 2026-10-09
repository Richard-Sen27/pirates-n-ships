package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.client.Markup;
import com.richardsenger.piratesnships.almanac.data.AlmanacLang;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The information page a recipe viewer shows for one of our items (JEI1, docs/compat.md), taken from the Captain's
 * Almanac's text (the lang keys {@code pirates_n_ships.almanac.<chapter>.p<page>.<element>}, design.md §3.4). Pure:
 * works on a lang map, so JUnit runs it on the shipped {@code en_us.json}.
 *
 * <p>For each item, the first Almanac paragraph or list entry that
 * <ol>
 *   <li>draws the item's icon ({@code {item:ns:id}}), searching the inventory chapters ({@link #INVENTORY_CHAPTERS}:
 *       one line per block and item) first, then the others in book order; else</li>
 *   <li>names the item by its display name, with the same capitals as the name; else</li>
 *   <li>names it by a display name of two or more words, ignoring case; else</li>
 *   <li>names it by a one-word display name, ignoring case ("a paddle", "bandana").</li>
 * </ol>
 * The chapters for server owners ({@link #SKIPPED_CHAPTERS}) are never used. The text loses its markup (links become
 * their label, icons disappear) and its "(recipe: ...)" note, which the viewer shows as real recipes. An item no
 * paragraph names gets no page.
 */
public final class InfoPages {

    /** The chapters that list every block and item, searched first. */
    public static final List<String> INVENTORY_CHAPTERS = List.of("items", "blocks");
    /** The Quartermaster's chapters (commands, settings, datapacks, plans): not player text. */
    public static final Set<String> SKIPPED_CHAPTERS = Set.of("commands", "configuration", "datapacks", "not_yet");

    /** The Almanac text of one item and the title of the chapter it comes from. */
    public record Page(String text, String chapterTitle) {
    }

    /** One paragraph of the Almanac. */
    record Entry(String chapter, int page, String element, String raw) {
    }

    private static final String PREFIX = Constants.MOD_ID + ".almanac.";
    private static final Pattern PAGE_KEY = Pattern.compile("^(.+)\\.p(\\d+)\\.([a-z0-9_]+)$");

    private InfoPages() {
    }

    /**
     * The pages of {@code items}.
     *
     * @param lang  every lang key of ours with its text (the Almanac's among them)
     * @param items the items to find pages for, with their display names
     * @return the pages found, in the order of {@code items}
     */
    public static Map<ResourceLocation, Page> build(Map<String, String> lang, Map<ResourceLocation, String> items) {
        return build(lang, items, List.of());
    }

    /**
     * The pages of {@code items}, searching the chapters after the inventory ones in {@code chapterOrder} (chapter
     * paths, e.g. {@code first_ship}; the synced book's order), then any other chapter alphabetically.
     */
    public static Map<ResourceLocation, Page> build(Map<String, String> lang, Map<ResourceLocation, String> items,
                                                    List<String> chapterOrder) {
        List<Entry> entries = entries(lang, chapterOrder);
        Map<ResourceLocation, Page> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> item : items.entrySet()) {
            find(entries, item.getKey(), item.getValue()).ifPresent(e -> out.put(item.getKey(),
                    new Page(clean(e.raw(), lang), lang.getOrDefault(PREFIX + e.chapter() + ".title", e.chapter()))));
        }
        return out;
    }

    /** The Almanac's paragraphs and list entries in search order (headings, buttons and skipped chapters left out). */
    static List<Entry> entries(Map<String, String> lang, List<String> chapterOrder) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, String> e : lang.entrySet()) {
            if (!e.getKey().startsWith(PREFIX)) continue;
            Matcher m = PAGE_KEY.matcher(e.getKey().substring(PREFIX.length()));
            if (!m.matches()) continue;
            String chapter = m.group(1);
            String element = m.group(3);
            if (SKIPPED_CHAPTERS.contains(chapter) || element.equals("heading") || element.startsWith("show_me")) continue;
            out.add(new Entry(chapter, Integer.parseInt(m.group(2)), element, e.getValue()));
        }
        out.sort(Comparator.comparingInt((Entry e) -> chapterRank(e.chapter(), chapterOrder))
                .thenComparing(Entry::chapter)
                .thenComparingInt(Entry::page)
                .thenComparing(Entry::element, InfoPages::naturalCompare));
        return out;
    }

    static Optional<Entry> find(List<Entry> entries, ResourceLocation item, String name) {
        String icon = "{item:" + item + "}";
        for (Entry e : entries) {
            if (e.raw().contains(icon)) return Optional.of(e);
        }
        if (name == null || name.isBlank()) return Optional.empty();
        for (Entry e : entries) {
            if (containsPhrase(plain(e.raw()), name, false)) return Optional.of(e);
        }
        if (name.trim().indexOf(' ') > 0) {
            for (Entry e : entries) {
                if (containsPhrase(plain(e.raw()), name, true)) return Optional.of(e);
            }
        }
        for (Entry e : entries) {
            if (containsPhrase(plain(e.raw()), name, true)) return Optional.of(e);
        }
        return Optional.empty();
    }

    /** The text as a player reads it, without markup and without its "(recipe: ...)" note. */
    static String clean(String raw, Map<String, String> lang) {
        String text = Markup.plain(Markup.parse(raw, Markup.Style.PLAIN,
                id -> lang.getOrDefault(AlmanacLang.title(id), id.getPath())));
        return dropRecipeNote(text).replaceAll("\\s+", " ").trim();
    }

    private static String plain(String raw) {
        return Markup.plain(Markup.parse(raw, Markup.Style.PLAIN));
    }

    /** Removes every " (recipe: ...)" with its nested parentheses. */
    static String dropRecipeNote(String text) {
        String marker = "(recipe:";
        int start;
        while ((start = text.indexOf(marker)) >= 0) {
            int depth = 0;
            int end = -1;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) {
                    end = i;
                    break;
                }
            }
            if (end < 0) break; // unbalanced: leave the rest alone
            int from = start > 0 && text.charAt(start - 1) == ' ' ? start - 1 : start;
            text = text.substring(0, from) + text.substring(end + 1);
        }
        return text;
    }

    /** Whether {@code text} contains {@code phrase} with no letter or digit right before or after it. */
    static boolean containsPhrase(String text, String phrase, boolean ignoreCase) {
        String t = ignoreCase ? text.toLowerCase(Locale.ROOT) : text;
        String p = ignoreCase ? phrase.toLowerCase(Locale.ROOT) : phrase;
        int from = 0;
        int i;
        while ((i = t.indexOf(p, from)) >= 0) {
            int after = i + p.length();
            boolean startOk = i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1));
            boolean endOk = after >= t.length() || !Character.isLetterOrDigit(t.charAt(after));
            if (startOk && endOk) return true;
            from = i + 1;
        }
        return false;
    }

    private static int chapterRank(String chapter, List<String> chapterOrder) {
        int i = INVENTORY_CHAPTERS.indexOf(chapter);
        if (i >= 0) return i;
        int j = chapterOrder.indexOf(chapter);
        return INVENTORY_CHAPTERS.size() + (j >= 0 ? j : chapterOrder.size());
    }

    /** "text2" before "text10": digit runs compare by value. */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                int c = Long.compare(Long.parseLong(a.substring(si, i)), Long.parseLong(b.substring(sj, j)));
                if (c != 0) return c;
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}

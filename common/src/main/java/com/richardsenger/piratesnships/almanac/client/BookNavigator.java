package com.richardsenger.piratesnships.almanac.client;

import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Spreads of the Almanac (ALM3), pure. Every chapter starts on a left page; a spread shows screen pages
 * {@code page} (left, always even) and {@code page + 1} (right, may be past the chapter's end and then blank). Turning
 * past a chapter's last spread opens the next chapter. The page index is what {@code ClientAlmanac.reportPage} stores.
 *
 * @param chapters  number of chapters
 * @param pageCount screen pages of a chapter by index (at least 1)
 */
public record BookNavigator(int chapters, IntUnaryOperator pageCount) {

    /** A spread: the chapter index and its left page (even). */
    public record Spread(int chapter, int page) implements Comparable<Spread> {

        @Override
        public int compareTo(Spread o) {
            return chapter != o.chapter ? Integer.compare(chapter, o.chapter) : Integer.compare(page, o.page);
        }
    }

    private int count(int chapter) {
        return Math.max(1, pageCount.applyAsInt(chapter));
    }

    /** The first spread of the book. */
    public Spread first() {
        return new Spread(0, 0);
    }

    /** The left page of the chapter's last spread. */
    public int lastSpreadPage(int chapter) {
        return (count(chapter) - 1) / 2 * 2;
    }

    /** {@code page} of {@code chapter} as a valid spread: chapter clamped, page clamped and made even. */
    public Spread clamp(int chapter, int page) {
        if (chapters <= 0) return first();
        int c = Math.max(0, Math.min(chapters - 1, chapter));
        int p = Math.max(0, Math.min(count(c) - 1, page));
        return new Spread(c, p / 2 * 2);
    }

    public Optional<Spread> next(Spread s) {
        if (s.page() + 2 < count(s.chapter())) return Optional.of(new Spread(s.chapter(), s.page() + 2));
        if (s.chapter() + 1 < chapters) return Optional.of(new Spread(s.chapter() + 1, 0));
        return Optional.empty();
    }

    public Optional<Spread> previous(Spread s) {
        if (s.page() >= 2) return Optional.of(new Spread(s.chapter(), s.page() - 2));
        if (s.chapter() > 0) return Optional.of(new Spread(s.chapter() - 1, lastSpreadPage(s.chapter() - 1)));
        return Optional.empty();
    }
}

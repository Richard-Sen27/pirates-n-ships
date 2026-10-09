package com.richardsenger.piratesnships.almanac.client;

/**
 * Text widths for {@link PageLayout} (ALM3). The screen measures with the game's font; JUnit tests use a fixed-width
 * stub, so the layout stays pure.
 */
@FunctionalInterface
public interface FontMetrics {

    /** Width in GUI pixels of {@code text} drawn with these styles. */
    int width(String text, boolean bold, boolean italic);
}

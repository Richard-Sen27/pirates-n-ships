package com.richardsenger.piratesnships.compat;

import java.util.List;

/**
 * One translatable line of a recipe-viewer page, without Minecraft's text classes (so JUnit can compare it). An
 * argument is a {@link String} (shown as is), another {@code TextLine} (translated) or a {@code List<TextLine>}
 * (translated and joined with ", ").
 */
public record TextLine(String key, List<Object> args) {

    public TextLine {
        args = List.copyOf(args);
    }

    public static TextLine of(String key, Object... args) {
        return new TextLine(key, List.of(args));
    }
}

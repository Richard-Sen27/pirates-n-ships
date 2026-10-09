package com.richardsenger.piratesnships.almanac.data;

import net.minecraft.resources.ResourceLocation;

/**
 * The lang-key convention of Almanac chapters (docs/almanac.md): every key of chapter {@code <ns>:<path>} starts with
 * {@code <ns>.almanac.<path>} (slashes become dots). The title is {@code <prefix>.title}; page texts are
 * {@code <prefix>.p<page>.<name>} with the page counted from 1. The convention is not enforced (any key works); it keeps
 * generated chapters (ALM5) and hand-written datapacks from colliding.
 */
public final class AlmanacLang {

    private AlmanacLang() {
    }

    /** {@code <ns>.almanac.<path>}. */
    public static String prefix(ResourceLocation chapter) {
        return chapter.getNamespace() + ".almanac." + chapter.getPath().replace('/', '.');
    }

    /** {@code <ns>.almanac.<path>.title}. */
    public static String title(ResourceLocation chapter) {
        return prefix(chapter) + ".title";
    }

    /** {@code <ns>.almanac.<path>.p<page>.<name>}, page counted from 1. */
    public static String page(ResourceLocation chapter, int page, String name) {
        return prefix(chapter) + ".p" + page + "." + name;
    }
}

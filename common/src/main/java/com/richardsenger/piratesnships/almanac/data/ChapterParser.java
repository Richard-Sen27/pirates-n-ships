package com.richardsenger.piratesnships.almanac.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.core.data.DefinitionParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The pure step of chapter loading: chapter id → file JSON in, a sorted {@link AlmanacBook} plus the skipped files out.
 * A file that does not decode (bad JSON shape, unknown element type, unknown item, out-of-page coordinates, no pages)
 * is skipped and reported; it never affects the other chapters. No world, no {@code Services}: JUnit calls it after
 * {@code Bootstrap.bootStrap()}.
 */
public final class ChapterParser {

    /** The parsed book and the files that were skipped. */
    public record Result(AlmanacBook book, List<DefinitionParser.Error> errors) {
    }

    private ChapterParser() {
    }

    public static Result parse(Map<ResourceLocation, JsonElement> files) {
        DefinitionParser.Result<Chapter.Body> parsed = DefinitionParser.parse(Chapter.Body.CODEC, JsonOps.INSTANCE, files);
        List<Chapter> chapters = new ArrayList<>(parsed.values().size());
        parsed.values().forEach((id, body) -> chapters.add(body.withId(id)));
        return new Result(AlmanacBook.of(chapters), parsed.errors());
    }
}

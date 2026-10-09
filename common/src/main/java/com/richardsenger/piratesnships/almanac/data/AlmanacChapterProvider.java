package com.richardsenger.piratesnships.almanac.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datagen of the Almanac's built-in chapters (ALM5): the chapters made from docs/guide.md. {@code tools/gen_almanac.py}
 * writes them into the intermediate {@value #INTERMEDIATE} (chapter bodies in the file format of docs/almanac.md plus
 * the English text of every lang key they use); this provider reads it, checks every chapter with the chapter codec and
 * every key against the lang map, and writes {@code data/pirates_n_ships/almanac/chapters/<id>.json} and the lang
 * entries. The intermediate is no resource: it is found from the working directory of the data run (the repository).
 */
public final class AlmanacChapterProvider {

    /** The intermediate, relative to the repository root. */
    public static final String INTERMEDIATE = "common/src/datagen/almanac/guide_chapters.json";

    /** Section 0 of the guide, the book's first chapter. */
    public static final ResourceLocation WELCOME = Constants.id("welcome");
    /** Section 9 of the guide, "Weapons and combat", which holds the cannon's "Show me". */
    public static final ResourceLocation COMBAT = Constants.id("combat");
    /** The Ponder scene the combat chapter's "Show me" button plays (ALM4). */
    public static final String CANNON_LOADING_SCENE = Constants.id("cannon_loading").toString();

    private AlmanacChapterProvider() {
    }

    /** What the intermediate holds: chapter bodies by id (book file order) and the English text of every key. */
    public record Intermediate(Map<ResourceLocation, JsonObject> bodies, Map<String, String> lang) {
    }

    public static void gather(DataContributions data) {
        Intermediate in = read(locate());
        Map<String, String> used = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : in.bodies().entrySet()) {
            Chapter.Body body = Chapter.Body.CODEC.parse(JsonOps.INSTANCE, e.getValue())
                    .getOrThrow(msg -> new IllegalStateException("Almanac chapter " + e.getKey() + " from " + INTERMEDIATE + ": " + msg));
            for (String key : keys(body.withId(e.getKey()))) {
                String text = in.lang().get(key);
                if (text == null) throw new IllegalStateException("Almanac chapter " + e.getKey() + " uses lang key " + key + " that " + INTERMEDIATE + " has no text for");
                used.put(key, text);
            }
            data.encoded(PackOutput.Target.DATA_PACK, AlmanacReloadListener.DIRECTORY, e.getKey(), Chapter.Body.CODEC, body);
        }
        data.lang(l -> used.forEach(l::add));
    }

    /** Every lang key a chapter uses: its title, then each element's keys in page order. */
    public static List<String> keys(Chapter chapter) {
        List<String> out = new ArrayList<>();
        out.add(chapter.title());
        for (Page page : chapter.pages()) {
            for (PageElement el : page.elements()) {
                switch (el) {
                    case PageElement.Text t -> out.add(t.key());
                    case PageElement.Heading h -> out.add(h.key());
                    case PageElement.ListItems l -> out.addAll(l.keys());
                    case PageElement.ItemIcon i -> i.caption().ifPresent(out::add);
                    case PageElement.ShowMe s -> out.add(s.label());
                    case PageElement.Illustration ignored -> {
                    }
                }
            }
        }
        return out;
    }

    /** The intermediate, searched upward from the working directory (the data run's game directory or {@code common/}). */
    public static Path locate() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path d = dir; d != null; d = d.getParent()) {
            Path candidate = d.resolve(INTERMEDIATE);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("No " + INTERMEDIATE + " above " + dir + " (run python3 tools/gen_almanac.py)");
    }

    public static Intermediate read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        Map<ResourceLocation, JsonObject> bodies = new LinkedHashMap<>();
        for (JsonElement el : root.getAsJsonArray("chapters")) {
            JsonObject chapter = el.getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(chapter.get("id").getAsString());
            if (bodies.put(id, chapter.getAsJsonObject("body")) != null) throw new IllegalStateException("Duplicate almanac chapter " + id);
        }
        Map<String, String> lang = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("lang").entrySet()) lang.put(e.getKey(), e.getValue().getAsString());
        return new Intermediate(bodies, lang);
    }
}

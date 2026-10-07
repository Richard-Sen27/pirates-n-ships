package com.richardsenger.piratesnships.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated GuideME guide (tools/gen_guideme.py) fits docs/guide.md and the mod (run from {@code common/}):
 * every section has a page, every item id a page uses exists (ours: an item model and a lang name; vanilla: the
 * bootstrapped registry), every recipe shown exists, links and parents point at existing pages and headings, the
 * guide definition parses and its lang keys exist, the book recipe carries the guide id, and the committed files are
 * exactly what the generator writes now.
 */
class GuideBookTest {

    private static final String NS = "pirates_n_ships";
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path GUIDE_MD = REPO.resolve("docs/guide.md");
    private static final Path ASSETS = Path.of("src/main/resources/assets/" + NS);
    private static final Path PAGES = ASSETS.resolve("guides/" + NS + "/guide");
    private static final Path DEFINITION = ASSETS.resolve("guideme_guides/guide.json");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path LANG = GENERATED.resolve("assets/" + NS + "/lang/en_us.json");
    private static final Path RECIPES = GENERATED.resolve("data/" + NS + "/recipe");

    private static final Pattern SECTION = Pattern.compile("(?m)^## \\d+\\. (.+?)\\s*$");
    /** A tag that is no escaped "\\<"; code spans are removed before matching. */
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)<([A-Za-z]+)([^>]*)>");
    private static final Pattern CODE = Pattern.compile("`[^`]*`");
    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]+)\"");
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6} (.+?)\\s*$");
    private static final Set<String> TAGS = Set.of("ItemLink", "ItemIcon", "ItemGrid", "ItemImage", "RecipeFor", "Row", "SubPages");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One page: front matter lines and body. */
    record Page(String name, List<String> frontMatter, String body) {
        String value(String key) {
            for (String line : frontMatter) {
                String t = line.trim();
                if (t.startsWith(key + ":")) {
                    String v = t.substring(key.length() + 1).trim();
                    return v.startsWith("\"") ? v.substring(1, v.length() - 1).replace("\\\"", "\"") : v;
                }
            }
            return null;
        }

        List<String> itemIds() {
            List<String> out = new ArrayList<>();
            boolean in = false;
            for (String line : frontMatter) {
                if (line.equals("item_ids:")) in = true;
                else if (in && line.startsWith("  - ")) out.add(line.substring(4).trim());
                else in = false;
            }
            return out;
        }
    }

    private static Map<String, Page> pages() throws IOException {
        Map<String, Page> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(PAGES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                String text = Files.readString(p);
                assertTrue(text.startsWith("---\n"), p + " has no front matter");
                int end = text.indexOf("\n---\n", 4);
                assertTrue(end > 0, p + ": unterminated front matter");
                out.put(p.getFileName().toString(),
                        new Page(p.getFileName().toString(), List.of(text.substring(4, end).split("\n")), text.substring(end + 5)));
            }
        }
        assertFalse(out.isEmpty(), "no guide pages under " + PAGES.toAbsolutePath());
        return out;
    }

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
    }

    /** GuideME's heading anchor (AnchorIndexer.normalizeAnchor). */
    private static String anchor(String heading) {
        return heading.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "-");
    }

    private static Set<String> recipeResults() throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonElement result = JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("result");
                if (result != null && result.isJsonObject() && result.getAsJsonObject().has("id")) {
                    out.add(result.getAsJsonObject().get("id").getAsString());
                }
            }
        }
        return out;
    }

    /** Problems with one item id: ours needs an item model and a lang name, vanilla must be registered. */
    private static String checkItem(String raw, JsonObject lang) {
        ResourceLocation id = raw.contains(":") ? ResourceLocation.parse(raw) : ResourceLocation.fromNamespaceAndPath(NS, raw);
        if (id.getNamespace().equals(NS)) {
            String path = "models/item/" + id.getPath() + ".json";
            boolean model = Files.isRegularFile(ASSETS.resolve(path)) || Files.isRegularFile(GENERATED.resolve("assets/" + NS + "/" + path));
            boolean name = lang.has("item." + NS + "." + id.getPath()) || lang.has("block." + NS + "." + id.getPath());
            return model && name ? null : id + (model ? " has no lang name" : " is no item of ours");
        }
        if (id.getNamespace().equals("minecraft")) {
            return BuiltInRegistries.ITEM.containsKey(id) ? null : id + " is no vanilla item";
        }
        return id + ": unexpected namespace";
    }

    @Test
    void everySectionHasAPage() throws IOException {
        Matcher m = SECTION.matcher(Files.readString(GUIDE_MD));
        Set<String> titles = new TreeSet<>();
        for (Page p : pages().values()) titles.add(p.value("title"));
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(titles.contains(m.group(1)), "no guide page for section '" + m.group(1) + "' (run python3 tools/gen_guideme.py)");
        }
        assertTrue(n > 0, "no sections in docs/guide.md");
        assertEquals(n + 1, pages().size(), "one page per section plus index.md");
    }

    @Test
    void everyItemIdExists() throws IOException {
        JsonObject lang = lang();
        Set<String> recipes = recipeResults();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        Map<String, String> owners = new LinkedHashMap<>();
        for (Page page : pages().values()) {
            Matcher t = TAG.matcher(CODE.matcher(page.body()).replaceAll("``"));
            while (t.find()) {
                String tag = t.group(1);
                if (!TAGS.contains(tag)) {
                    problems.add(page.name() + ": unknown or unescaped tag <" + tag + ">");
                    continue;
                }
                Matcher id = ID_ATTR.matcher(t.group(2));
                if (!id.find()) continue;
                checked++;
                String problem = checkItem(id.group(1), lang);
                if (problem != null) problems.add(page.name() + " <" + tag + ">: " + problem);
                if (tag.equals("RecipeFor") && !recipes.contains(id.group(1))) {
                    problems.add(page.name() + ": no recipe makes " + id.group(1));
                }
            }
            String icon = page.value("icon");
            if (icon != null) {
                String problem = checkItem(icon, lang);
                if (problem != null) problems.add(page.name() + " icon: " + problem);
            }
            for (String owned : page.itemIds()) {
                String problem = checkItem(owned, lang);
                if (problem != null) problems.add(page.name() + " item_ids: " + problem);
                String before = owners.put(owned, page.name());
                if (before != null) problems.add(owned + " is in item_ids of both " + before + " and " + page.name());
            }
        }
        assertTrue(checked > 20, "only " + checked + " item tags in the guide");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void linksAndParentsPointAtPagesAndHeadings() throws IOException {
        Map<String, Page> pages = pages();
        assertTrue(pages.containsKey("index.md"), "no start page index.md");
        List<String> problems = new ArrayList<>();
        for (Page page : pages.values()) {
            String parent = page.value("parent");
            if (parent != null && !pages.containsKey(parent)) problems.add(page.name() + ": parent " + parent + " missing");
            if (!page.name().equals("index.md") && parent == null) problems.add(page.name() + ": no parent");
            if (page.value("title") == null) problems.add(page.name() + ": no navigation title");
            Matcher l = LINK.matcher(page.body());
            while (l.find()) {
                String href = l.group(1);
                if (href.startsWith("http://") || href.startsWith("https://")) continue;
                String file = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
                Page target = pages.get(file);
                if (target == null) {
                    problems.add(page.name() + ": link to missing page " + href);
                    continue;
                }
                if (href.contains("#")) {
                    String frag = href.substring(href.indexOf('#') + 1);
                    Matcher h = HEADING.matcher(target.body());
                    boolean found = false;
                    while (h.find()) found |= anchor(h.group(1)).equals(frag);
                    if (!found) problems.add(page.name() + ": link to missing heading " + href);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void guideDefinitionParsesAndItsLangExists() throws IOException {
        JsonObject def = JsonParser.parseString(Files.readString(DEFINITION)).getAsJsonObject();
        JsonObject settings = def.getAsJsonObject("item_settings");
        assertEquals(GuideBook.NAME_KEY, settings.getAsJsonObject("display_name").get("translate").getAsString());
        assertEquals(GuideBook.TOOLTIP_KEY, settings.getAsJsonArray("tooltip_lines").get(0).getAsJsonObject().get("translate").getAsString());
        assertEquals("guide", DEFINITION.getFileName().toString().replace(".json", ""), "file name is the guide id's path");
        assertEquals(GuideBook.GUIDE_ID.getPath(), "guide");
        JsonObject lang = lang();
        assertTrue(lang.has(GuideBook.NAME_KEY), "lang has no " + GuideBook.NAME_KEY + " (run ./gradlew :neoforge:runData)");
        assertTrue(lang.has(GuideBook.TOOLTIP_KEY), "lang has no " + GuideBook.TOOLTIP_KEY);
    }

    @Test
    void bookRecipeMakesTheGuideItemWithOurGuideId() throws IOException {
        Path file = RECIPES.resolve(GuideModule.RECIPE_ID.getPath() + ".json");
        assertTrue(Files.isRegularFile(file), "no generated " + file + " (run ./gradlew :neoforge:runData)");
        JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(GuideBook.stackJson(), recipe.getAsJsonObject("result"));
        assertEquals("pirates_n_ships:guide", recipe.getAsJsonObject("result").getAsJsonObject("components").get("guideme:guide_id").getAsString());
        assertEquals("neoforge:mod_loaded", recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(GuideModule.recipe(), recipe);
    }

    @Test
    void committedGuideIsWhatTheGeneratorWrites(@TempDir Path tmp) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_guideme.py").toString(), "--out", tmp.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        Set<String> fresh = relativeFiles(tmp);
        Set<String> committed = new TreeSet<>();
        for (String dir : List.of("guides", "guideme_guides")) {
            for (String f : relativeFiles(ASSETS.resolve(dir))) committed.add(dir + "/" + f);
        }
        assertEquals(committed, fresh, "generated file set differs (run python3 tools/gen_guideme.py)");
        for (String f : fresh) {
            Path committedFile = ASSETS.resolve(f);
            assertEquals(Files.readString(tmp.resolve(f)), Files.readString(committedFile),
                    f + " is out of date (run python3 tools/gen_guideme.py)");
        }
    }

    /**
     * The generator's item inventory is the registered items (lang keys): an item model that is only an
     * {@code overrides} target of another (a variant like pistol_loaded) is ignored, an item model nothing names or
     * references is an orphan that stops it. Runs against a temp copy of the inputs with fake models added.
     */
    @Test
    void generatorIgnoresOverrideVariantsAndRejectsOrphanModels(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("repo");
        copyFiles(GUIDE_MD, root.resolve("docs/guide.md"));
        copyFiles(ASSETS.resolve("models/item"), root.resolve("common/src/main/resources/assets/" + NS + "/models/item"));
        copyFiles(GENERATED.resolve("assets/" + NS + "/models/item"), root.resolve("common/src/generated/resources/assets/" + NS + "/models/item"));
        copyFiles(RECIPES, root.resolve("common/src/generated/resources/data/" + NS + "/recipe"));
        JsonObject lang = lang();
        lang.addProperty("item." + NS + ".foo", "Foo");
        Path langFile = root.resolve("common/src/generated/resources/assets/" + NS + "/lang/en_us.json");
        Files.createDirectories(langFile.getParent());
        Files.writeString(langFile, lang.toString());
        Path models = root.resolve("common/src/main/resources/assets/" + NS + "/models/item");
        Files.writeString(models.resolve("foo.json"), """
                {"parent": "minecraft:item/generated", "textures": {"layer0": "%1$s:item/foo"},
                 "overrides": [{"predicate": {"%1$s:loaded": 1.0}, "model": "%1$s:item/foo_loaded"}]}
                """.formatted(NS));
        Files.writeString(models.resolve("foo_loaded.json"), """
                {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/foo_loaded"}}
                """.formatted(NS));

        Path out = tmp.resolve("out");
        Generated ok = runGenerator(root, out);
        assertEquals(0, ok.exit(), "generator failed on an override variant:\n" + ok.output());
        List<String> owned = new ArrayList<>();
        for (String f : relativeFiles(out)) {
            String text = Files.readString(out.resolve(f));
            assertFalse(text.contains(NS + ":foo_loaded"), f + " lists the variant model foo_loaded as an item");
            for (String line : text.split("\n")) {
                if (line.startsWith("  - ")) owned.add(line.substring(4).trim());
            }
        }
        assertTrue(owned.contains(NS + ":foo"), "the fake item foo is in no page's item_ids: " + owned);
        assertEquals(new TreeSet<>(owned).size(), owned.size(), "an item is owned twice: " + owned);
        JsonObject realLang = lang();
        for (String id : owned) {
            String path = id.substring(id.indexOf(':') + 1);
            assertTrue(id.equals(NS + ":foo") || realLang.has("item." + NS + "." + path) || realLang.has("block." + NS + "." + path),
                    id + " is listed but is no registered item");
        }

        Files.writeString(models.resolve("bar_orphan.json"), """
                {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/bar_orphan"}}
                """.formatted(NS));
        Generated orphan = runGenerator(root, tmp.resolve("out2"));
        assertTrue(orphan.exit() != 0, "generator accepted an orphan item model:\n" + orphan.output());
        assertTrue(orphan.output().contains(NS + ":bar_orphan"), "failure does not name the orphan:\n" + orphan.output());
        assertFalse(orphan.output().contains(NS + ":foo_loaded"), "failure blames the variant:\n" + orphan.output());
    }

    private record Generated(int exit, String output) {
    }

    private static Generated runGenerator(Path root, Path out) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_guideme.py").toString(),
                "--root", root.toString(), "--out", out.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        return new Generated(p.exitValue(), output);
    }

    /** Copies a file, or the files directly in a directory, to target. */
    private static void copyFiles(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            return;
        }
        Files.createDirectories(target);
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> files = Files.list(source)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Files.copy(f, target.resolve(f.getFileName().toString()));
            }
        }
    }

    private static Set<String> relativeFiles(Path root) throws IOException {
        Set<String> out = new TreeSet<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(f -> out.add(root.relativize(f).toString().replace('\\', '/')));
        }
        return out;
    }
}
